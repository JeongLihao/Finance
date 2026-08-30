package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.contract.ContractManager;
import finance.contract.FinanceContract;
import finance.data.EconomySavedData;

import java.util.UUID;

public final class CompanyContractService {
    public static final int MAX_ACTIVE_PER_COMPANY = 8;
    private CompanyContractService() {}

    public static synchronized FinanceContract publishProcurement(UUID actor, UUID companyId, String commodityId,
                                                                   int quantity, long reward, long day,
                                                                   int durationDays, String operationKey) {
        Company company = CompanyManager.getCompany(companyId); CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (company == null || profile == null || actor == null || operationKey == null || operationKey.isBlank()
                || operationKey.length() > 64 || durationDays < 1 || durationDays > 30 || reward < quantity
                || company.isBankruptcyRisk()
                || !CompanyMembershipService.hasPermission(companyId, actor, CompanyPermission.PUBLISH_CONTRACT)) return null;
        String key = actor + ":" + operationKey;
        if (profile.hasOperation(key)) return null;
        long active = ContractManager.contracts().values().stream().filter(contract -> companyId.equals(contract.issuerId())
                && !contract.status().terminal()).count();
        if (active >= MAX_ACTIVE_PER_COMPANY || ContractManager.hasLiveForIssuerCommodity(companyId, commodityId)) return null;
        FinanceContract contract = ContractManager.createCompanyProcurement(companyId, commodityId, quantity,
                reward, day, day + durationDays);
        if (contract != null) { profile.recordOperation(key); EconomySavedData.markDirty(); }
        return contract;
    }
}
