package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;

import java.util.UUID;

public final class CompanyWarehouseBindingService {
    private CompanyWarehouseBindingService() {}

    public static synchronized CompanyGameplayActionResult bind(UUID actor, UUID companyId, UUID warehouseId, String key) {
        if(!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.COMPANY_GAMEPLAY))return CompanyGameplayActionResult.fail("finance.company_gameplay.module_paused");
        Company company = CompanyManager.getCompany(companyId); WarehouseRecord warehouse = WarehouseManager.get(warehouseId);
        CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (company == null || warehouse == null || profile == null || actor == null || key == null || key.isBlank()
                || key.length() > 64 || warehouse.companyId() != null
                || !warehouse.ownerId().equals(actor)
                || !CompanyMembershipService.hasPermission(companyId, actor, CompanyPermission.MANAGE_PRODUCTION))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.warehouse_bind_denied");
        String scoped = actor + ":" + key;
        if (profile.hasOperation(scoped)) return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        if (!profile.bindWarehouse(warehouseId)) return CompanyGameplayActionResult.fail("finance.company_gameplay.warehouse_limit");
        warehouse.bindCompany(companyId); profile.recordOperation(scoped); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.warehouse_bound");
    }

    public static synchronized CompanyGameplayActionResult unbind(UUID actor, UUID companyId, UUID warehouseId, String key) {
        WarehouseRecord warehouse = WarehouseManager.get(warehouseId); CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (actor == null || companyId == null || key == null || key.isBlank() || key.length() > 64
                || warehouse == null || profile == null || !companyId.equals(warehouse.companyId())
                || !warehouse.ownerId().equals(actor) || !CompanyMembershipService.hasPermission(companyId, actor,
                CompanyPermission.MANAGE_PRODUCTION)) return CompanyGameplayActionResult.fail("finance.company_gameplay.warehouse_bind_denied");
        String scoped = actor + ":" + key;
        if (profile.hasOperation(scoped)) return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        UUID custody = CompanyInventoryFacade.custodyId(companyId);
        if (WarehouseManager.usedCapacity(custody) > WarehouseManager.totalCapacity(custody) - warehouse.capacityUnits())
            return CompanyGameplayActionResult.fail("finance.company_gameplay.warehouse_not_empty");
        profile.unbindWarehouse(warehouseId); warehouse.bindCompany(null); profile.recordOperation(scoped); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.warehouse_unbound");
    }
}
