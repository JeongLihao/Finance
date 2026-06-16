package finance.company;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 公司管理器 —— 所有公司的注册和查询入口。
 */
public class CompanyManager {

    private static final Map<UUID, Company> COMPANIES = new HashMap<>();

    public static void register(Company company) {
        COMPANIES.put(company.getCompanyId(), company);
    }

    public static Collection<Company> getCompanies() {
        return COMPANIES.values();
    }

    public static Company getCompany(UUID id) {
        return COMPANIES.get(id);
    }

    /** 清空所有公司（数据加载前调用） */
    public static void clearCompanies() {
        COMPANIES.clear();
    }
}
