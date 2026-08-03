package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;

import java.util.UUID;

/**
 * 玩家公司创建入口，供命令和 GUI 网络包共用同一套校验规则。
 */
public class CompanyCreationService {

    public static final long CREATE_COMPANY_COST = 10_000L;
    public static final long INITIAL_PLAYER_COMPANY_CASH = 5_000L;

    public record Result(boolean success, String message, Company company) {}

    public static Result createPlayerCompany(UUID ownerId, CompanyType type, String rawName) {
        if (ownerId == null) {
            return fail("缺少公司所有者。");
        }
        if (type == null) {
            return fail("未知行业。");
        }

        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > 32) {
            return fail("公司名称长度需为 1-32 个字符。");
        }

        if (CompanyManager.getCompanyByOwner(ownerId) != null) {
            return fail("你已经拥有一家公司，暂时不能重复创建。");
        }

        if (CompanyManager.hasCompanyNamed(name)) {
            return fail("公司名称已被占用: '" + name + "'。");
        }

        if (!AccountManager.withdraw(ownerId, CREATE_COMPANY_COST)) {
            return fail("余额不足，创建公司需要 " + CREATE_COMPANY_COST + "。");
        }

        Company company = new Company(
                UUID.randomUUID(),
                name,
                type,
                INITIAL_PLAYER_COMPANY_CASH,
                ownerId
        );
        seedInitialInventory(company);
        CompanyManager.register(company);
        AccountManager.addTransactionRecord(
                new TransactionRecord(
                        ownerId,
                        company.getCompanyId(),
                        CREATE_COMPANY_COST,
                        TransactionType.COMPANY_CREATE,
                        ownerId,
                        company.getName(),
                        1
                )
        );

        return new Result(true,
                "公司已创建: " + company.getName()
                        + " | 行业: " + company.getType().getDisplayName()
                        + " | 启动资金: " + INITIAL_PLAYER_COMPANY_CASH,
                company);
    }

    private static Result fail(String message) {
        return new Result(false, message, null);
    }

    private static void seedInitialInventory(Company company) {
        for (String commodityId : company.getType().getCommodityIds()) {
            company.addInventory(commodityId, 50);
        }
    }
}
