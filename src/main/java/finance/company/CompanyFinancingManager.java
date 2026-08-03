package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.util.MathUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CompanyFinancingManager {

    private static final List<CompanyFinancingProject> PROJECTS = new ArrayList<>();
    private static final int DEFAULT_DURATION_DAYS = 7;
    private static final long MAX_ISSUE_QUANTITY = 1_000_000L;

    private CompanyFinancingManager() {
    }

    public static Result startProject(UUID ownerId, UUID companyId, long issueQuantity,
                                      long issuePrice, long fundingTarget, long currentMcDay) {
        Company company = CompanyManager.getCompany(companyId);
        if (ownerId == null || company == null) {
            return Result.fail("公司不存在。");
        }
        if (!ownerId.equals(company.getOwnerId())) {
            return Result.fail("只能为自己的公司发起融资。");
        }
        if (!company.isPublic()) {
            return Result.fail("公司上市后才能增发融资。");
        }
        Stock stock = StockMarketManager.getStockByCompanyId(companyId);
        if (stock == null) {
            return Result.fail("公司股票不存在。");
        }
        if (getProjectByCompany(companyId) != null) {
            return Result.fail("该公司已有进行中的融资项目。");
        }
        if (issueQuantity <= 0 || issueQuantity > MAX_ISSUE_QUANTITY || issuePrice <= 0 || fundingTarget <= 0) {
            return Result.fail("融资参数无效。");
        }
        long maxRaise = MathUtil.multiplyExactOrNegative1(issuePrice, safeInt(issueQuantity));
        if (maxRaise <= 0 || fundingTarget > maxRaise) {
            return Result.fail("融资目标不能超过本次最高可募集金额。");
        }

        CompanyFinancingProject project = new CompanyFinancingProject(
                companyId,
                stock.getSymbol(),
                issueQuantity,
                issuePrice,
                fundingTarget,
                currentMcDay + DEFAULT_DURATION_DAYS);
        PROJECTS.add(project);
        addRecord(ownerId, companyId, TransactionType.COMPANY_FINANCING_START,
                0, issueQuantity, company.getName() + "/" + stock.getSymbol());
        EconomySavedData.markDirty();
        return Result.ok("融资项目已发起，期限 " + DEFAULT_DURATION_DAYS + " 天。");
    }

    public static Result subscribe(UUID playerId, UUID projectId, long shares) {
        if (playerId == null || projectId == null || shares <= 0 || shares > Integer.MAX_VALUE) {
            return Result.fail("认购参数无效。");
        }
        CompanyFinancingProject project = getProject(projectId);
        if (project == null) {
            return Result.fail("融资项目不存在或已结束。");
        }
        Company company = CompanyManager.getCompany(project.getCompanyId());
        Stock stock = StockMarketManager.getStock(project.getSymbol());
        if (company == null || stock == null || !company.isPublic()) {
            cancelProject(project, "融资项目异常，已取消并退款。");
            return Result.fail("融资项目异常，已取消并退款。");
        }
        long acceptedShares = Math.min(shares, project.getRemainingShares());
        if (acceptedShares <= 0) {
            return Result.fail("本次增发已无剩余额度。");
        }
        long cost = MathUtil.multiplyExactOrNegative1(project.getIssuePrice(), safeInt(acceptedShares));
        if (cost <= 0) {
            return Result.fail("认购金额过大。");
        }
        if (!AccountManager.withdraw(playerId, cost)) {
            return Result.fail("余额不足，认购需要 " + cost);
        }

        project.addSubscription(playerId, acceptedShares);
        addRecord(playerId, project.getCompanyId(), TransactionType.COMPANY_FINANCING_SUBSCRIBE,
                cost, acceptedShares, company.getName() + "/" + project.getSymbol());
        if (project.isFunded()) {
            finalizeProject(project);
            return Result.ok("认购成功，融资目标已达成并完成增发。");
        }
        EconomySavedData.markDirty();
        return Result.ok("认购成功，当前进度 " + project.getRaisedAmount() + "/" + project.getFundingTarget() + "。");
    }

    public static void tick(long currentMcDay) {
        boolean changed = false;
        for (CompanyFinancingProject project : new ArrayList<>(PROJECTS)) {
            if (project.isFunded()) {
                finalizeProject(project);
                changed = true;
                continue;
            }
            if (currentMcDay >= project.getDeadlineMcDay()) {
                refundProject(project);
                PROJECTS.remove(project);
                changed = true;
            }
        }
        if (changed) {
            EconomySavedData.markDirty();
        }
    }

    public static CompanyFinancingProject getProject(UUID projectId) {
        for (CompanyFinancingProject project : PROJECTS) {
            if (project.getProjectId().equals(projectId)) {
                return project;
            }
        }
        return null;
    }

    public static CompanyFinancingProject getProjectByCompany(UUID companyId) {
        for (CompanyFinancingProject project : PROJECTS) {
            if (project.getCompanyId().equals(companyId)) {
                return project;
            }
        }
        return null;
    }

    public static CompanyFinancingProject getProjectBySymbol(String symbol) {
        String normalized = StockMarketManager.normalizeSymbol(symbol);
        for (CompanyFinancingProject project : PROJECTS) {
            if (project.getSymbol().equals(normalized)) {
                return project;
            }
        }
        return null;
    }

    public static List<CompanyFinancingProject> getProjects() {
        return PROJECTS;
    }

    public static void addProjectDirect(CompanyFinancingProject project) {
        if (project != null) {
            PROJECTS.add(project);
        }
    }

    public static void clearProjectsDirect() {
        PROJECTS.clear();
    }

    public static int cancelProjectsForCompany(UUID companyId) {
        int cancelled = 0;
        for (CompanyFinancingProject project : new ArrayList<>(PROJECTS)) {
            if (!project.getCompanyId().equals(companyId)) {
                continue;
            }
            refundProject(project);
            PROJECTS.remove(project);
            cancelled++;
        }
        if (cancelled > 0) {
            EconomySavedData.markDirty();
        }
        return cancelled;
    }

    private static void finalizeProject(CompanyFinancingProject project) {
        Company company = CompanyManager.getCompany(project.getCompanyId());
        Stock stock = StockMarketManager.getStock(project.getSymbol());
        if (company == null || stock == null) {
            refundProject(project);
            PROJECTS.remove(project);
            return;
        }
        long subscribedShares = project.getSubscribedShares();
        long raised = project.getRaisedAmount();
        if (subscribedShares <= 0 || raised <= 0) {
            return;
        }
        company.deposit(raised);
        stock.increaseShares(subscribedShares);
        for (Map.Entry<UUID, Long> entry : project.getSubscriptions().entrySet()) {
            StockPortfolioManager.addHolding(entry.getKey(), project.getSymbol(), entry.getValue(), project.getIssuePrice());
        }
        addRecord(company.getOwnerId(), project.getCompanyId(), TransactionType.COMPANY_FINANCING_SUCCESS,
                raised, subscribedShares, company.getName() + "/" + project.getSymbol());
        PROJECTS.remove(project);
        EconomySavedData.markDirty();
    }

    private static void refundProject(CompanyFinancingProject project) {
        Company company = CompanyManager.getCompany(project.getCompanyId());
        String objectName = (company != null ? company.getName() : "未知公司") + "/" + project.getSymbol();
        for (Map.Entry<UUID, Long> entry : project.getSubscriptions().entrySet()) {
            long refund = MathUtil.multiplyExactOrNegative1(project.getIssuePrice(), safeInt(entry.getValue()));
            if (refund > 0) {
                AccountManager.deposit(entry.getKey(), refund);
                addRecord(entry.getKey(), project.getCompanyId(), TransactionType.COMPANY_FINANCING_REFUND,
                        refund, entry.getValue(), objectName);
            }
        }
    }

    private static void cancelProject(CompanyFinancingProject project, String reason) {
        refundProject(project);
        PROJECTS.remove(project);
        EconomySavedData.markDirty();
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    private static void addRecord(UUID playerId, UUID companyId, TransactionType type,
                                  long amount, long quantity, String objectName) {
        AccountManager.addTransactionRecord(new TransactionRecord(
                playerId != null ? playerId : companyId,
                companyId,
                amount,
                type,
                playerId,
                objectName,
                quantity));
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
