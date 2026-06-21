package finance.company;

import finance.data.EconomySavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 公司管理器 —— 所有公司的注册和查询入口。
 * <p>
 * 维护两级索引（ownerId → Company, name.toLowerCase() → Company），
 * 使 getCompanyByOwner 和 getCompanyByName 均为 O(1)。
 * </p>
 */
public class CompanyManager {

    private static final Map<UUID, Company> COMPANIES = new HashMap<>();
    private static final Map<UUID, Company> OWNER_INDEX = new HashMap<>();
    private static final Map<String, Company> NAME_INDEX = new HashMap<>();

    public static void register(Company company) {
        COMPANIES.put(company.getCompanyId(), company);
        if (company.getOwnerId() != null) {
            OWNER_INDEX.put(company.getOwnerId(), company);
        }
        NAME_INDEX.put(company.getName().toLowerCase(), company);
        EconomySavedData.markDirty();
    }

    public static void registerDirect(Company company) {
        COMPANIES.put(company.getCompanyId(), company);
        if (company.getOwnerId() != null) {
            OWNER_INDEX.put(company.getOwnerId(), company);
        }
        NAME_INDEX.put(company.getName().toLowerCase(), company);
    }

    public static Collection<Company> getCompanies() {
        return COMPANIES.values();
    }

    public static Company getCompany(UUID id) {
        return COMPANIES.get(id);
    }

    /** O(1) 按所有者 UUID 查找公司 */
    public static Company getCompanyByOwner(UUID ownerId) {
        return OWNER_INDEX.get(ownerId);
    }

    /** O(1) 按名称查找公司（忽略大小写） */
    public static Company getCompanyByName(String name) {
        return NAME_INDEX.get(name.toLowerCase());
    }

    public static boolean hasCompanyNamed(String name) {
        return NAME_INDEX.containsKey(name.toLowerCase());
    }

    /** 每日经营 tick —— 所有公司生产 + 自动交易（由 FinanceMod 每天调用一次） */
    public static void tickAll() {
        for (Company c : COMPANIES.values()) {
            c.produce();
            c.autoTrade();
        }
        EconomySavedData.markDirty();
    }

    /** 清空所有公司（数据加载前调用） */
    public static void clearCompanies() {
        COMPANIES.clear();
        OWNER_INDEX.clear();
        NAME_INDEX.clear();
        EconomySavedData.markDirty();
    }

    public static void clearCompaniesDirect() {
        COMPANIES.clear();
        OWNER_INDEX.clear();
        NAME_INDEX.clear();
    }
}
