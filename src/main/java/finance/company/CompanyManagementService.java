package finance.company;

import finance.account.AccountManager;

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
            case SET_STRATEGY -> setStrategy(company, strategy);
            case SET_SELL_RATIO -> setSellRatio(company, ratio);
            case UPGRADE_PRODUCTION -> upgrade(playerId, company, "PRODUCTION");
            case UPGRADE_STORAGE -> upgrade(playerId, company, "STORAGE");
            case UPGRADE_MANAGEMENT -> upgrade(playerId, company, "MANAGEMENT");
            case INVEST -> invest(playerId, company, amount);
            case WITHDRAW -> withdraw(playerId, company, amount);
        };
    }

    private static Result setStrategy(Company company, CompanyStrategy strategy) {
        CompanyStrategy safeStrategy = strategy != null ? strategy : CompanyStrategy.STABLE;
        company.setStrategy(safeStrategy);
        return new Result(true, "经营策略已调整为 " + safeStrategy.getDisplayName());
    }

    private static Result setSellRatio(Company company, double ratio) {
        if (!Double.isFinite(ratio)) {
            return new Result(false, "自动出售比例无效。");
        }
        company.setAutoSellRatio(ratio);
        return new Result(true, "自动出售比例已调整。");
    }

    private static Result upgrade(UUID playerId, Company company, String type) {
        long cost = company.getUpgradeCost(type);
        if (cost <= 0) {
            return new Result(false, "升级费用异常。");
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
            AccountManager.deposit(playerId, cost);
            return new Result(false, "该升级已达到上限。");
        }

        company.deposit(cost);
        return new Result(true, "公司升级成功，投入资金 " + cost);
    }

    private static Result invest(UUID playerId, Company company, long amount) {
        if (amount <= 0) {
            return new Result(false, "注资金额必须大于 0。");
        }
        if (!AccountManager.withdraw(playerId, amount)) {
            return new Result(false, "余额不足。");
        }

        company.deposit(amount);
        return new Result(true, "已向公司注资 " + amount);
    }

    private static Result withdraw(UUID playerId, Company company, long amount) {
        if (amount <= 0) {
            return new Result(false, "提取金额必须大于 0。");
        }
        if (!company.withdraw(amount)) {
            return new Result(false, "公司现金不足。");
        }

        AccountManager.deposit(playerId, amount);
        return new Result(true, "已从公司提取 " + amount);
    }
}
