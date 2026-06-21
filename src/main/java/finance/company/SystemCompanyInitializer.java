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

        Company ironCompany = new Company(
                UUID.randomUUID(),
                "铁锭原料",
                CompanyType.RAW_MATERIALS,
                INITIAL_CASH
        );
        ironCompany.addInventory("iron", INITIAL_STOCK);
        CompanyManager.registerDirect(ironCompany);

        Company stoneCompany = new Company(
                UUID.randomUUID(),
                "石材工坊",
                CompanyType.BUILDING_BLOCKS,
                INITIAL_CASH
        );
        stoneCompany.addInventory("stone", INITIAL_STOCK);
        CompanyManager.registerDirect(stoneCompany);

        Company wheatCompany = new Company(
                UUID.randomUUID(),
                "麦田农庄",
                CompanyType.FOOD,
                INITIAL_CASH
        );
        wheatCompany.addInventory("wheat", INITIAL_STOCK);
        CompanyManager.registerDirect(wheatCompany);

        EconomySavedData.markDirty();
    }
}
