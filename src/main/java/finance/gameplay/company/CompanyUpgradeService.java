package finance.gameplay.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.block.entity.CompanyFactoryControllerBlockEntity;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import finance.warehouse.PhysicalMaterialTransaction;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class CompanyUpgradeService {
    private CompanyUpgradeService() {}

    public static synchronized CompanyGameplayActionResult upgrade(ServerPlayer player, UUID facilityId,
                                                                    String operationKey) {
        if (!finance.diagnostic.ModuleHealthRegistry.mayWrite(
                finance.diagnostic.ModuleHealthRegistry.Module.COMPANY_GAMEPLAY))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.module_paused");
        CompanyFacilityRecord facility = CompanyFacilityManager.get(facilityId);
        Company company = facility == null ? null : CompanyManager.getCompany(facility.companyId());
        if (!validPhysicalRequest(player, facility) || company == null || operationKey == null
                || operationKey.isBlank() || operationKey.length() > 64
                || !CompanyMembershipService.hasPermission(company.getCompanyId(), player.getUUID(),
                CompanyPermission.MANAGE_PRODUCTION) || company.isBankruptcyRisk())
            return CompanyGameplayActionResult.fail("finance.company_gameplay.upgrade_denied");
        String key = player.getUUID() + ":upgrade:" + operationKey;
        if (facility.hasOperation(key))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        CompanyUpgradeRequirementService.Requirement requirement = CompanyUpgradeRequirementService.requirement(
                company.getType(), facility.type(), facility.productionLevel());
        if (requirement == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.max_level");
        if (company.getCash() < requirement.cash())
            return CompanyGameplayActionResult.fail("finance.company_gameplay.cash_insufficient");
        var plans = PhysicalMaterialTransaction.plan(player, requirement.materials());
        if (plans == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.materials_insufficient");
        if (!PhysicalMaterialTransaction.commit(player, plans))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.inventory_changed");
        if (!company.withdraw(requirement.cash())) {
            PhysicalMaterialTransaction.rollback(player, plans);
            return CompanyGameplayActionResult.fail("finance.company_gameplay.cash_changed");
        }
        if (!facility.upgrade()) {
            if (!company.deposit(requirement.cash()) || !PhysicalMaterialTransaction.rollback(player, plans))
                throw new IllegalStateException("facility upgrade compensation failed");
            return CompanyGameplayActionResult.fail("finance.company_gameplay.max_level");
        }
        facility.recordOperation(key);
        company.recordGameplayCost(requirement.cash());
        EconomySavedData.markDirty();
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(), facility.facilityId(),
                requirement.cash(), TransactionType.FACILITY_UPGRADE, player.getUUID(), company.getName(),
                facility.productionLevel()));
        return CompanyGameplayActionResult.ok("finance.company_gameplay.upgrade_success");
    }

    /** Commits only the facility-level mutation after a capital project has paid its escrow and materials. */
    public static synchronized boolean commitCapitalUpgrade(ServerPlayer player, UUID facilityId,
                                                             UUID companyId, int targetLevel,
                                                             String operationKey) {
        CompanyFacilityRecord facility = validCapitalTarget(player, facilityId, companyId, targetLevel);
        if (facility == null || operationKey == null || operationKey.isBlank()
                || operationKey.length() > 96 || facility.hasOperation(operationKey)
                || !facility.upgrade()) return false;
        facility.recordOperation(operationKey);
        finance.block.CompanyFactoryControllerBlock.updateIndicator(player.serverLevel(), facility.blockPos(),
                facility.facilityId());
        EconomySavedData.markDirty();
        return true;
    }

    public static CompanyFacilityRecord validCapitalTarget(ServerPlayer player, UUID facilityId,
                                                            UUID companyId, int targetLevel) {
        CompanyFacilityRecord facility = CompanyFacilityManager.get(facilityId);
        Company company = facility == null ? null : CompanyManager.getCompany(facility.companyId());
        if (company == null || companyId == null || !companyId.equals(company.getCompanyId())
                || !validPhysicalRequest(player, facility)
                || !CompanyMembershipService.hasPermission(companyId, player.getUUID(),
                CompanyPermission.MANAGE_PRODUCTION) || company.isBankruptcyRisk()) return null;
        CompanyUpgradeRequirementService.Requirement requirement = CompanyUpgradeRequirementService.requirement(
                company.getType(), facility.type(), facility.productionLevel());
        return requirement != null && facility.productionLevel() + 1 == targetLevel ? facility : null;
    }

    private static boolean validPhysicalRequest(ServerPlayer player, CompanyFacilityRecord facility) {
        if (player == null || facility == null || !player.isAlive()
                || !player.serverLevel().dimension().location().toString().equals(facility.dimensionId())
                || !player.serverLevel().isLoaded(facility.blockPos())) return false;
        double range = finance.config.FinanceConfig.terminalInteractionDistance();
        if (player.distanceToSqr(facility.blockPos().getX() + 0.5D, facility.blockPos().getY() + 0.5D,
                facility.blockPos().getZ() + 0.5D) > range * range) return false;
        return player.serverLevel().getBlockEntity(facility.blockPos()) instanceof CompanyFactoryControllerBlockEntity block
                && block.facilityId().equals(facility.facilityId());
    }
}
