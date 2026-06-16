package finance.company;

import java.util.UUID;

/**
 * 系统公司初始化 —— 首次启动时自动创建基础公司。
 */
public class SystemCompanyInitializer {

    private static final int INITIAL_CASH = 50_000;
    private static final int INITIAL_STOCK = 200;

    public static void initialize() {
        if (!CompanyManager.getCompanies().isEmpty()) {
            return;
        }

        Company ironMining = new Company(
                UUID.randomUUID(),
                "Iron Mining Corp",
                CompanyType.MINING,
                INITIAL_CASH
        );
        ironMining.addInventory("iron", INITIAL_STOCK);
        ironMining.addInventory("coal", INITIAL_STOCK);
        CompanyManager.register(ironMining);

        Company coalEnergy = new Company(
                UUID.randomUUID(),
                "Coal Energy Group",
                CompanyType.ENERGY,
                INITIAL_CASH
        );
        coalEnergy.addInventory("coal", INITIAL_STOCK);
        CompanyManager.register(coalEnergy);

        Company wheatAgri = new Company(
                UUID.randomUUID(),
                "Wheat Agriculture Ltd",
                CompanyType.AGRICULTURE,
                INITIAL_CASH
        );
        wheatAgri.addInventory("wheat", INITIAL_STOCK);
        CompanyManager.register(wheatAgri);

        Company steelMfg = new Company(
                UUID.randomUUID(),
                "Steel Manufacturing Inc",
                CompanyType.MANUFACTURING,
                INITIAL_CASH
        );
        steelMfg.addInventory("iron", INITIAL_STOCK);
        steelMfg.addInventory("steel", 100);
        CompanyManager.register(steelMfg);
    }
}
