package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompanyProposalManager {

    private static final List<CompanyProposal> PROPOSALS = new ArrayList<>();
    private static final int MAX_ACTIVE_PER_COMPANY = 5;

    private CompanyProposalManager() {
    }

    public static Result createProposal(UUID creatorId, UUID companyId, CompanyProposalType type,
                                        String textValue, long value1, long value2, long value3,
                                        long startMcDay, long endMcDay, double passRatio) {
        Company company = CompanyManager.getCompany(companyId);
        if (creatorId == null || company == null) {
            return Result.fail("公司不存在。");
        }
        if (!creatorId.equals(company.getOwnerId())) {
            return Result.fail("只有公司管理员可以创建提案。");
        }
        if (!company.isPublic()) {
            return Result.fail("只有上市公司支持股东投票。");
        }
        if (type == null || endMcDay <= startMcDay || passRatio <= 0 || passRatio > 1) {
            return Result.fail("提案参数无效。");
        }
        long activeCount = PROPOSALS.stream()
                .filter(p -> p.getCompanyId().equals(companyId))
                .filter(p -> p.getStatus() == CompanyProposalStatus.ACTIVE)
                .count();
        if (activeCount >= MAX_ACTIVE_PER_COMPANY) {
            return Result.fail("该公司活跃提案已达上限。");
        }
        Result validation = validateProposal(type, textValue, value1, value2, value3);
        if (!validation.success()) {
            return validation;
        }
        Stock stock = StockMarketManager.getStockByCompanyId(companyId);
        long currentHoldings = stock != null
                ? StockPortfolioManager.getHoldingsForCompany(stock.getSymbol())
                        .values().stream().mapToLong(Long::longValue).sum()
                : 0;
        long votingSharesSnapshot = stock != null
                ? Math.max(1, Math.max(stock.getFloatShares(), currentHoldings))
                : 0;
        CompanyProposal proposal = new CompanyProposal(companyId, creatorId, type,
                titleFor(type), textValue, value1, value2, value3, startMcDay, endMcDay,
                passRatio, FinanceConfig.minProposalParticipationRatio(), votingSharesSnapshot);
        PROPOSALS.add(proposal);
        addRecord(creatorId, company, TransactionType.COMPANY_PROPOSAL_CREATE, 0, 0, proposal.getTitle());
        EconomySavedData.markDirty();
        return Result.ok("提案已创建。");
    }

    public static Result vote(UUID playerId, UUID proposalId, boolean support, long currentMcDay) {
        CompanyProposal proposal = getProposal(proposalId);
        if (playerId == null || proposal == null) {
            return Result.fail("提案不存在。");
        }
        if (proposal.getStatus() != CompanyProposalStatus.ACTIVE) {
            return Result.fail("提案已结束。");
        }
        if (currentMcDay < proposal.getStartMcDay() || currentMcDay > proposal.getEndMcDay()) {
            return Result.fail("不在投票时间内。");
        }
        if (proposal.getVotes().containsKey(playerId)) {
            return Result.fail("你已经投过票。");
        }
        Stock stock = StockMarketManager.getStockByCompanyId(proposal.getCompanyId());
        long power = stock != null ? StockPortfolioManager.getHolding(playerId, stock.getSymbol()).getQuantity() : 0;
        if (power <= 0) {
            return Result.fail("你不是该公司的股东。");
        }
        proposal.addVote(playerId, support, power);
        Company company = CompanyManager.getCompany(proposal.getCompanyId());
        addRecord(playerId, company, TransactionType.COMPANY_PROPOSAL_VOTE, 0, power,
                proposal.getTitle() + (support ? " 赞成" : " 反对"));
        EconomySavedData.markDirty();
        return Result.ok("投票已提交，投票权 " + power + "。");
    }

    public static void tick(long currentMcDay) {
        boolean changed = false;
        for (CompanyProposal proposal : new ArrayList<>(PROPOSALS)) {
            if (proposal.getStatus() == CompanyProposalStatus.ACTIVE && currentMcDay >= proposal.getEndMcDay()) {
                finishProposal(proposal, currentMcDay);
                changed = true;
            }
        }
        if (changed) {
            EconomySavedData.markDirty();
        }
    }

    private static void finishProposal(CompanyProposal proposal, long currentMcDay) {
        long yes = proposal.getYesVotes();
        long no = proposal.getNoVotes();
        long total = yes + no;
        long votingSnapshot = resolveVotingSnapshot(proposal);
        double participation = votingSnapshot > 0 ? (double) total / votingSnapshot : 0.0;
        boolean enoughParticipation = votingSnapshot > 0
                && participation >= proposal.getMinParticipationRatio();
        boolean passed = enoughParticipation && total > 0 && (double) yes / total >= proposal.getPassRatio();
        Company company = CompanyManager.getCompany(proposal.getCompanyId());
        if (!passed || company == null) {
            String reason = !enoughParticipation
                    ? "参与率不足：" + total + "/" + votingSnapshot
                    : "未通过：" + yes + "/" + total;
            proposal.finish(CompanyProposalStatus.FAILED, reason);
            if (company != null) {
                addRecord(company.getOwnerId(), company, TransactionType.COMPANY_PROPOSAL_RESULT, 0, yes,
                        proposal.getTitle() + " 未通过");
            }
            return;
        }
        Result execution = executeProposal(proposal, company, currentMcDay);
        proposal.finish(execution.success() ? CompanyProposalStatus.PASSED : CompanyProposalStatus.FAILED,
                execution.message());
        addRecord(company.getOwnerId(), company, TransactionType.COMPANY_PROPOSAL_RESULT, 0, yes,
                proposal.getTitle() + " " + execution.message());
    }

    private static Result executeProposal(CompanyProposal proposal, Company company, long currentMcDay) {
        return switch (proposal.getType()) {
            case DIVIDEND -> {
                company.setDividendPolicy(proposal.getValue1() / 100.0,
                        company.effectiveDividendCycleDays(CompanyManager.getDividendCycleDays()));
                EconomySavedData.markDirty();
                yield Result.ok("已通过，本公司分红比例调整为 " + proposal.getValue1() + "%");
            }
            case SHARE_ISSUE -> {
                CompanyFinancingManager.Result result = CompanyFinancingManager.startProject(
                        company.getOwnerId(), company.getCompanyId(),
                        proposal.getValue1(), proposal.getValue2(), proposal.getValue3(), currentMcDay);
                yield result.success() ? Result.ok(result.message()) : Result.fail(result.message());
            }
            case RENAME -> CompanyManager.renameCompany(company.getCompanyId(), proposal.getTextValue())
                    ? Result.ok("已通过，公司改名为 " + proposal.getTextValue())
                    : Result.fail("已通过但改名执行失败");
            case FUND_USAGE -> Result.ok("已通过，资金用途：" + proposal.getTextValue()
                    + (proposal.getValue1() > 0 ? "，预算 " + proposal.getValue1() : ""));
        };
    }

    private static Result validateProposal(CompanyProposalType type, String textValue,
                                           long value1, long value2, long value3) {
        return switch (type) {
            case DIVIDEND -> value1 >= 0 && value1 <= 100
                    ? Result.ok("") : Result.fail("分红比例必须在 0-100 之间。");
            case SHARE_ISSUE -> value1 > 0 && value2 > 0 && value3 > 0
                    ? Result.ok("") : Result.fail("增发数量、发行价格和融资目标必须为正。");
            case RENAME -> textValue != null && !textValue.isBlank() && textValue.length() <= 32
                    ? Result.ok("") : Result.fail("新公司名不能为空且最多 32 字符。");
            case FUND_USAGE -> textValue != null && !textValue.isBlank() && textValue.length() <= 64
                    ? Result.ok("") : Result.fail("资金用途不能为空且最多 64 字符。");
        };
    }

    private static long resolveVotingSnapshot(CompanyProposal proposal) {
        if (proposal.getVotingSharesSnapshot() > 0) {
            return proposal.getVotingSharesSnapshot();
        }
        Stock stock = StockMarketManager.getStockByCompanyId(proposal.getCompanyId());
        if (stock == null) {
            return 0;
        }
        long currentHoldings = StockPortfolioManager.getHoldingsForCompany(stock.getSymbol())
                .values().stream().mapToLong(Long::longValue).sum();
        return Math.max(1, Math.max(stock.getFloatShares(), currentHoldings));
    }

    public static CompanyProposal getProposal(UUID proposalId) {
        for (CompanyProposal proposal : PROPOSALS) {
            if (proposal.getProposalId().equals(proposalId)) {
                return proposal;
            }
        }
        return null;
    }

    public static List<CompanyProposal> getProposals() {
        return PROPOSALS;
    }

    public static List<CompanyProposal> getProposalsForCompany(UUID companyId) {
        List<CompanyProposal> rows = new ArrayList<>();
        for (CompanyProposal proposal : PROPOSALS) {
            if (proposal.getCompanyId().equals(companyId)) {
                rows.add(proposal);
            }
        }
        return rows;
    }

    public static void addProposalDirect(CompanyProposal proposal) {
        if (proposal != null) {
            PROPOSALS.add(proposal);
        }
    }

    public static void clearProposalsDirect() {
        PROPOSALS.clear();
    }

    public static int cancelActiveProposalsForCompany(UUID companyId, String reason) {
        int cancelled = 0;
        for (CompanyProposal proposal : PROPOSALS) {
            if (!proposal.getCompanyId().equals(companyId)
                    || proposal.getStatus() != CompanyProposalStatus.ACTIVE) {
                continue;
            }
            proposal.finish(CompanyProposalStatus.FAILED, reason == null ? "公司退市" : reason);
            cancelled++;
        }
        if (cancelled > 0) {
            EconomySavedData.markDirty();
        }
        return cancelled;
    }

    private static String titleFor(CompanyProposalType type) {
        return switch (type) {
            case DIVIDEND -> "调整分红";
            case SHARE_ISSUE -> "增发融资";
            case RENAME -> "公司改名";
            case FUND_USAGE -> "资金用途";
        };
    }

    private static void addRecord(UUID playerId, Company company, TransactionType type, long amount, long quantity, String objectName) {
        if (company == null) {
            return;
        }
        AccountManager.addTransactionRecord(new TransactionRecord(
                playerId != null ? playerId : company.getCompanyId(),
                company.getCompanyId(),
                amount,
                type,
                playerId,
                company.getName() + "/" + objectName,
                quantity));
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }
}
