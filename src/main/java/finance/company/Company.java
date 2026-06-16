package finance.company;

import java.util.UUID;

/**
 * 公司实体 —— 代表一个可交易的企业。
 * 当前仅包含基础属性，股票/股东/IPO 等后续扩展。
 */
public class Company {

    private final UUID companyId;
    private final String name;
    private final CompanyType type;
    private long cash;

    public Company(UUID companyId, String name, CompanyType type, long cash) {
        this.companyId = companyId;
        this.name = name;
        this.type = type;
        this.cash = cash;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public CompanyType getType() {
        return type;
    }

    public long getCash() {
        return cash;
    }

    public void deposit(long amount) {
        cash += amount;
    }

    public boolean withdraw(long amount) {
        if (cash < amount) {
            return false;
        }
        cash -= amount;
        return true;
    }
}
