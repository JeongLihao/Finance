package finance.npcinvestor;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.governance.ShareholderRegistryService;
import finance.rival.RivalManager;
import java.util.UUID;

/** 股东列表的身份标签（服务端计算后同步给客户端）：NPC 原型、竞争公司、创始人、未流通残差。 */
public final class HolderLabels {
    private HolderLabels() {}

    public static String label(UUID holder, Company company) {
        if (holder == null) return "";
        if (ShareholderRegistryService.SYSTEM_LIQUIDITY_HOLDER.equals(holder)) return "未流通";
        NpcInvestorArchetype archetype = NpcInvestorArchetype.fromAccount(holder);
        if (archetype != null) return archetype.displayName();
        UUID rivalCompany = RivalManager.companyOfInvestmentAccount(holder);
        if (rivalCompany != null) {
            Company rival = CompanyManager.getCompany(rivalCompany);
            return rival == null ? "竞争公司" : rival.getName();
        }
        if (company != null && holder.equals(company.getOwnerId())) return "创始人";
        return "";
    }

    /** NPC 投资者对该公司的情绪（-100～100）；其他股东返回 0。 */
    public static int sentiment(UUID holder, Company company) {
        NpcInvestorArchetype archetype = NpcInvestorArchetype.fromAccount(holder);
        if (archetype == null || company == null) return 0;
        return NpcInvestorManager.state(archetype).sentimentFor(company.getCompanyId());
    }
}
