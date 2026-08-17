package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;

import java.util.UUID;

public final class CompanyManagementService {

    private CompanyManagementService() {
    }

    public record Result(boolean success, String message) {
    }

    public static Result apply(UUID playerId,
                               Company company,
                               CompanyManagementAction action,
                               CompanyStrategy strategy,
                               long amount,
                               double ratio) {
        if (playerId == null) {
            return new Result(false, "无效玩家。");
        }
        if (company == null) {
            return new Result(false, "你还没有公司。");
        }
        if (!playerId.equals(company.getOwnerId())) {
            return new Result(false, "只能管理自己的公司。");
        }
        if (action == null) {
            return new Result(false, "无效公司操作。");
        }

        return switch (action) {
            case SET_STRATEGY -> setStrategy(playerId, company, strategy);
            case SET_SELL_RATIO -> setSellRatio(playerId, company, ratio);
            case UPGRADE_PRODUCTION -> upgrade(playerId, company, "PRODUCTION");
            case UPGRADE_STORAGE -> upgrade(playerId, company, "STORAGE");
            case UPGRADE_MANAGEMENT -> upgrade(playerId, company, "MANAGEMENT");
            case INVEST -> invest(playerId, company, amount);
            case WITHDRAW -> withdraw(playerId, company, amount);
        };
    }

    private static Result setStrategy(UUID playerId, Company company, CompanyStrategy strategy) {
        CompanyStrategy safeStrategy = strategy != null ? strategy : CompanyStrategy.STABLE;
        company.setStrategy(safeStrategy);
        recordCompanyAction(playerId, company, 0, 0);
        return new Result(true, "经营策略已调整为 " + safeStrategy.getDisplayName());
    }

    private static Result setSellRatio(UUID playerId, Company company, double ratio) {
        if (!Double.isFinite(ratio)) {
            return new Result(false, "自动出售比例无效。");
        }
        company.setAutoSellRatio(ratio);
        recordCompanyAction(playerId, company, 0, Math.round(company.getAutoSellRatio() * 100));
        return new Result(true, "自动出售比例已调整。");
    }

    private static Result upgrade(UUID playerId, Company company, String type) {
        long cost = company.getUpgradeCost(type);
        if (cost <= 0) {
            return new Result(false, "升级费用异常。");
        }
        if (!company.canDeposit(cost)) {
            return new Result(false, "Company cash balance is at its limit.");
        }
        if (!AccountManager.withdraw(playerId, cost)) {
            return new Result(false, "余额不足，升级需要 " + cost);
        }

        boolean upgraded = switch (type) {
            case "PRODUCTION" -> company.upgradeProduction();
            case "STORAGE" -> company.upgradeStorage();
            case "MANAGEMENT" -> company.upgradeManagement();
            default -> false;
        };

        if (!upgraded) {
            if (!AccountManager.deposit(playerId, cost)) {
                return new Result(false, "Upgrade failed and refund could not be settled.");
            }
            return new Result(false, "该升级已达到上限。");
        }

        if (!company.deposit(cost)) {
            AccountManager.deposit(playerId, cost);
            return new Result(false, "Company settlement failed.");
        }
        recordCompanyAction(playerId, company, cost, 1);
        return new Result(true, "公司升级成功，投入资金 " + cost);
    }

    private static Result invest(UUID playerId, Company company, long amount) {
        if (amount <= 0) {
            return new Result(false, "注资金额必须大于 0。");
        }
        if (!company.canDeposit(amount)) {
            return new Result(false, "Company cash balance is at its limit.");
        }
        if (!AccountManager.withdraw(playerId, amount)) {
            return new Result(false, "余额不足。");
        }

        if (!company.deposit(amount)) {
            AccountManager.deposit(playerId, amount);
            return new Result(false, "Investment settlement failed.");
        }
        recordCompanyAction(playerId, company, amount, 0);
        return new Result(true, "已向公司注资 " + amount);
    }

    private static Result withdraw(UUID playerId, Company company, long amount) {
        if (amount <= 0) {
            return new Result(false, "提取金额必须大于 0。");
        }
        if (!AccountManager.canDeposit(playerId, amount)) {
            return new Result(false, "Player account balance is at its limit.");
        }
        if (!company.withdraw(amount)) {
            return new Result(false, "公司现金不足。");
        }

        if (!AccountManager.deposit(playerId, amount)) {
            company.deposit(amount);
            return new Result(false, "Withdrawal settlement failed.");
        }
        recordCompanyAction(playerId, company, amount, 0);
        return new Result(true, "已从公司提取 " + amount);
    }

    private static void recordCompanyAction(UUID playerId, Company company, long amount, long quantity) {
        AccountManager.addTransactionRecord(
                new TransactionRecord(
                        playerId,
                        company.getCompanyId(),
                        amount,
                        TransactionType.COMPANY_ACTION,
                        playerId,
                        company.getName(),
                        quantity
                )
        );
    }
}
