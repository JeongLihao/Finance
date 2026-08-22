package finance.simulation;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.gameplay.company.*;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

/** Deterministic company-world simulation with periodic full save/load recovery. */
public final class CompanyGameplayLongRunSimulationService {
    public record Result(int days, int restarts, int producedDays, boolean noDoubleProduction,
                         boolean nonNegativeAssets, boolean referencesRecovered) {}
    private CompanyGameplayLongRunSimulationService() {}

    public static synchronized Result run(int days, long seed) {
        if (days < 1 || days > 1_000) throw new IllegalArgumentException("days");
        CompoundTag original = new EconomySavedData().save(new CompoundTag());
        try {
            EconomySavedData.resetRuntimeState();
            CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron",
                    CommodityCategory.RAW_MATERIALS, 10));
            UUID owner = stable("company-owner", seed), member = stable("company-member", seed);
            UUID companyId = stable("company", seed), warehouseId = stable("company-warehouse", seed);
            UUID facilityId = stable("company-facility", seed);
            Company company = new Company(companyId, "Simulation Works", CompanyType.RAW_MATERIALS,
                    10_000_000L, owner);
            company.setAutoSellRatio(0);
            CompanyManager.registerDirect(company);
            CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
            profile.putMember(new CompanyMemberRecord(member, CompanyMemberRole.WAREHOUSE_WORKER, 0));
            WarehouseRecord warehouse = new WarehouseRecord(warehouseId, "minecraft:overworld", BlockPos.ZERO,
                    owner, companyId, 100_000, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
            WarehouseManager.restore(warehouse); profile.bindWarehouse(warehouseId);
            CompanyFacilityManager.restore(new CompanyFacilityRecord(facilityId, companyId, "minecraft:overworld",
                    new BlockPos(1, 64, 1), CompanyFacilityType.FACTORY_CONTROLLER, 1,
                    CompanyFacilityStatus.ACTIVE, -1, warehouseId));

            Random random = new Random(seed); int restarts = 0, producedDays = 0;
            boolean noDouble = true, nonNegative = true, recovered = true;
            for (int day = 0; day < days; day++) {
                // Represents materials delivered by a company member. It is authoritative custody,
                // not a second copy in Company.inventory.
                CommodityInventoryManager.addCommodity(CompanyInventoryFacade.custodyId(companyId), "iron",
                        1 + random.nextInt(3));
                int legacyBefore = company.getInventoryAmount("iron");
                CompanyProductionService.DayResult first = CompanyProductionService.processCompanyDay(company, day);
                if (first.producedFacilities() > 0) producedDays++;
                int custodyAfter = CommodityInventoryManager.getCommodityAmount(
                        CompanyInventoryFacade.custodyId(companyId), "iron");
                CompanyProductionService.processCompanyDay(company, day);
                noDouble &= custodyAfter == CommodityInventoryManager.getCommodityAmount(
                        CompanyInventoryFacade.custodyId(companyId), "iron")
                        && legacyBefore == company.getInventoryAmount("iron");
                nonNegative &= company.getCash() >= 0 && custodyAfter >= 0;

                if (day > 0 && day % 30 == 0) {
                    CompoundTag saved = new EconomySavedData().save(new CompoundTag());
                    EconomySavedData.resetRuntimeState(); EconomySavedData.load(saved); restarts++;
                    company = CompanyManager.getCompany(companyId);
                    CompanyFacilityRecord facility = CompanyFacilityManager.get(facilityId);
                    recovered &= company != null && CompanyGameplayManager.get(companyId) != null
                            && WarehouseManager.get(warehouseId) != null && facility != null
                            && warehouseId.equals(facility.boundWarehouseId());
                    if (company == null) break;
                }
            }
            return new Result(days, restarts, producedDays, noDouble, nonNegative, recovered);
        } finally {
            EconomySavedData.resetRuntimeState(); EconomySavedData.load(original);
        }
    }

    private static UUID stable(String prefix, long seed) {
        return UUID.nameUUIDFromBytes((prefix + '-' + seed).getBytes(StandardCharsets.UTF_8));
    }
}
