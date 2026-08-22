package finance.gameplay.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;

import java.util.UUID;

public final class CompanyUpgradeService {
    private CompanyUpgradeService() {}
    public static synchronized CompanyGameplayActionResult upgrade(UUID actor, UUID facilityId, String operationKey) {
        if(!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.COMPANY_GAMEPLAY))return CompanyGameplayActionResult.fail("finance.company_gameplay.module_paused");
        CompanyFacilityRecord facility = CompanyFacilityManager.get(facilityId);
        Company company = facility == null ? null : CompanyManager.getCompany(facility.companyId());
        if (company == null || actor == null || operationKey == null || operationKey.isBlank() || operationKey.length() > 64
                || !CompanyMembershipService.hasPermission(company.getCompanyId(), actor, CompanyPermission.MANAGE_PRODUCTION)
                || company.isBankruptcyRisk()) return CompanyGameplayActionResult.fail("finance.company_gameplay.upgrade_denied");
        String key = actor + ":" + operationKey;
        if (facility.hasOperation(key)) return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        CompanyUpgradeRequirementService.Requirement requirement = CompanyUpgradeRequirementService.requirement(
                company.getType(), facility.type(), facility.productionLevel());
        if (requirement == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.max_level");
        if (company.getCash() < requirement.cash()) return CompanyGameplayActionResult.fail("finance.company_gameplay.cash_insufficient");
        CompanyInventoryFacade.Consumption consumed = CompanyInventoryFacade.consumeInputAtomically(company, requirement.materials());
        if (consumed == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.materials_insufficient");
        if (!company.withdraw(requirement.cash())) {
            CompanyInventoryFacade.rollback(company, consumed);
            return CompanyGameplayActionResult.fail("finance.company_gameplay.cash_changed");
        }
        if (!facility.upgrade()) {
            if (!company.deposit(requirement.cash()) || !CompanyInventoryFacade.rollback(company, consumed))
                throw new IllegalStateException("facility upgrade compensation failed");
            return CompanyGameplayActionResult.fail("finance.company_gameplay.max_level");
        }
        facility.recordOperation(key); company.recordGameplayCost(requirement.cash()); EconomySavedData.markDirty();
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(), facility.facilityId(),
                requirement.cash(), TransactionType.FACILITY_UPGRADE, actor, company.getName(), facility.productionLevel()));
        return CompanyGameplayActionResult.ok("finance.company_gameplay.upgrade_success");
    }
}
