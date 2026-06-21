package finance.company;

import finance.data.EconomySavedData;

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
                "铁矿集团",
                CompanyType.MINING,
                INITIAL_CASH
        );
        ironMining.addInventory("iron", INITIAL_STOCK);
        ironMining.addInventory("coal", INITIAL_STOCK);
        CompanyManager.registerDirect(ironMining);

        Company coalEnergy = new Company(
                UUID.randomUUID(),
                "煤矿能源",
                CompanyType.ENERGY,
                INITIAL_CASH
        );
        coalEnergy.addInventory("coal", INITIAL_STOCK);
        CompanyManager.registerDirect(coalEnergy);

        Company wheatAgri = new Company(
                UUID.randomUUID(),
                "麦田农业",
                CompanyType.AGRICULTURE,
                INITIAL_CASH
        );
        wheatAgri.addInventory("wheat", INITIAL_STOCK);
        CompanyManager.registerDirect(wheatAgri);

        EconomySavedData.markDirty();
    }
}
