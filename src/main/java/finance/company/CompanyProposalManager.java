package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.governance.ShareholderRegistryService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompanyProposalManager {

    private static final List<CompanyProposal> PROPOSALS = new ArrayList<>();
    private static final int MAX_ACTIVE_PER_COMPANY = 5;
    public static final int MAX_PROPOSAL_DURATION_DAYS = 90;

    private CompanyProposalManager() {
    }

    public static Result createProposal(UUID creatorId, UUID companyId, CompanyProposalType type,
                                        String textValue, long value1, long value2, long value3,
                                        long startMcDay, long endMcDay, double passRatio) {
        Company company = CompanyManager.getCompany(companyId);
        if (creatorId == null || company == null) {
            return Result.fail("公司不存在。");
        }
        if (!finance.governance.GovernanceAuthorizationService.mayCreateProposal(creatorId,companyId)) return Result.fail("只有经营者、控制者或合格大股东可以创建提案。");
        if (!company.isPublic()) {
            return Result.fail("只有上市公司支持股东投票。");
        }
        if (type == null || startMcDay < 0 || endMcDay <= startMcDay
                || endMcDay - startMcDay > MAX_PROPOSAL_DURATION_DAYS
                || passRatio <= 0 || passRatio > 1) {
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
        ShareholderRegistryService.Snapshot registry = stock == null
                ? null : ShareholderRegistryService.snapshot(companyId, startMcDay);
        if (registry == null || !registry.consistent()) {
            return Result.fail("无法核对公司股本，已暂停新的治理提案。");
        }
        long votingSharesSnapshot = registry == null ? 0 : registry.holders().stream()
                .filter(holder -> !holder.id().equals(ShareholderRegistryService.SYSTEM_LIQUIDITY_HOLDER))
                .mapToLong(ShareholderRegistryService.Holder::total).sum();
        CompanyProposal proposal = new CompanyProposal(companyId, creatorId, type,
                titleFor(type), textValue, value1, value2, value3, startMcDay, endMcDay,
                passRatio, FinanceConfig.minProposalParticipationRatio(), votingSharesSnapshot);
        if (registry != null && registry.consistent()) {
            registry.holders().stream()
                    .filter(holder -> !holder.id().equals(ShareholderRegistryService.SYSTEM_LIQUIDITY_HOLDER))
                    .forEach(holder -> proposal.restoreVotingPower(holder.id(), holder.total()));
        }
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
        long power = proposal.getSnapshotPower(playerId);
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
        proposal.finish(CompanyProposalStatus.PASSED, "表决已通过，等待执行");
        Result execution = executeProposal(proposal, company, currentMcDay);
        if (execution.success() && executesImmediately(proposal.getType())) {
            proposal.finish(CompanyProposalStatus.EXECUTED, execution.message());
        } else if (!execution.success()) {
            proposal.finish(CompanyProposalStatus.FAILED, execution.message());
        } else {
            proposal.finish(CompanyProposalStatus.PASSED, execution.message());
        }
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
            case SHARE_BUYBACK -> {var r=finance.governance.CorporateActionManager.startBuyback(company.getOwnerId(),company.getCompanyId(),proposal.getValue1(),proposal.getValue2(),(int)proposal.getValue3(),currentMcDay,"proposal-"+proposal.getProposalId());yield r.success()?Result.ok(r.message()):Result.fail(r.message());}
            case TREASURY_RETIREMENT -> {var r=finance.governance.CorporateActionManager.retireTreasury(company.getOwnerId(),company.getCompanyId(),proposal.getValue1(),currentMcDay,"proposal-"+proposal.getProposalId());yield r.success()?Result.ok(r.message()):Result.fail(r.message());}
            case TENDER_OFFER_RESPONSE,CONTROL_TRANSFER -> Result.ok("表决已通过；控制权仅在真实股份交割后更新");
            case EMERGENCY_RECAPITALIZATION -> Result.ok("紧急再融资授权已生效，等待真实出资人执行");
            case MAJOR_ASSET_PURCHASE -> Result.ok("重大资产交易授权已生效，等待卖方确认并原子交割");
            case CAPITAL_PROJECT -> Result.ok("实体资本项目授权已生效，等待在设施旁创建项目并施工");
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
            case SHARE_BUYBACK -> value1>0&&value2>0&&value3>0&&value3<=90?Result.ok(""):Result.fail("回购价格、数量和期限必须有效。");
            case TREASURY_RETIREMENT -> value1>0?Result.ok(""):Result.fail("库存股注销数量必须为正。");
            case TENDER_OFFER_RESPONSE,CONTROL_TRANSFER -> value1>=0?Result.ok(""):Result.fail("治理参数无效。");
            case EMERGENCY_RECAPITALIZATION -> value1>0?Result.ok(""):Result.fail("紧急融资目标必须为正。");
            case MAJOR_ASSET_PURCHASE -> validAssetPurchase(textValue,value1,value2)
                    ?Result.ok(""):Result.fail("资产交易须使用“卖方公司UUID|商品ID”，且价格和数量必须为正。");
            case CAPITAL_PROJECT -> value1 > 0 && value2 >= 0 && value2 <= 1
                    && (textValue == null || textValue.length() <= 64)
                    ? Result.ok("") : Result.fail("资本项目授权需要正预算和有效项目类型。");
        };
    }

    private static boolean executesImmediately(CompanyProposalType type) {
        return switch (type) {
            case DIVIDEND, SHARE_ISSUE, RENAME, FUND_USAGE, SHARE_BUYBACK, TREASURY_RETIREMENT -> true;
            case TENDER_OFFER_RESPONSE, CONTROL_TRANSFER, EMERGENCY_RECAPITALIZATION, MAJOR_ASSET_PURCHASE, CAPITAL_PROJECT -> false;
        };
    }

    private static boolean validAssetPurchase(String textValue,long price,long quantity){
        if(textValue==null||textValue.length()>101||price<=0||quantity<=0||quantity>Integer.MAX_VALUE)return false;
        String[] parts=textValue.split("\\|",2);
        if(parts.length!=2||parts[1].isBlank()||parts[1].length()>64)return false;
        try{UUID.fromString(parts[0]);return true;}catch(IllegalArgumentException ignored){return false;}
    }

    public static boolean markExecuted(UUID proposalId, String result) {
        CompanyProposal proposal = getProposal(proposalId);
        if (proposal == null || proposal.getStatus() != CompanyProposalStatus.PASSED) return false;
        proposal.finish(CompanyProposalStatus.EXECUTED, result);
        EconomySavedData.markDirty();
        return true;
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
        return List.copyOf(PROPOSALS);
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
            case SHARE_BUYBACK -> "股份回购";
            case TREASURY_RETIREMENT -> "注销库存股";
            case TENDER_OFFER_RESPONSE -> "要约收购响应";
            case CONTROL_TRANSFER -> "控制权事项";
            case EMERGENCY_RECAPITALIZATION -> "紧急再融资";
            case MAJOR_ASSET_PURCHASE -> "重大资产收购";
            case CAPITAL_PROJECT -> "实体资本项目";
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
