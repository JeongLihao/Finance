package finance.company;

import java.util.UUID;

/**
 * 系统公司初始化 —— 首次启动时自动创建基础蓝筹股公司。
 * 系统公司上限 5 家，当前创建 3 家。
 */
public class SystemCompanyInitializer {

    private static final int MAX_SYSTEM_COMPANIES = 5;

    public static void initialize() {
        if (!CompanyManager.getCompanies().isEmpty()) {
            return;
        }

        CompanyManager.register(new Company(
                UUID.randomUUID(),
                "Iron Mining Corp",
                CompanyType.MINING,
                500_000
        ));

        CompanyManager.register(new Company(
                UUID.randomUUID(),
                "Coal Energy Group",
                CompanyType.ENERGY,
                500_000
        ));

        CompanyManager.register(new Company(
                UUID.randomUUID(),
                "Wheat Agriculture Ltd",
                CompanyType.AGRICULTURE,
                500_000
        ));
    }
}
