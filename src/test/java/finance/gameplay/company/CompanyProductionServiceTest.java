package finance.gameplay.company;

import finance.account.AccountManager;
import finance.company.*;
import finance.commodity.CommodityInventoryManager;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompanyProductionServiceTest {
    private Company company; private CompanyFacilityRecord facility; private UUID custody;
    @BeforeEach void setup() { CompanyManager.clearCompaniesDirect(); WarehouseManager.clearDirect(); CommodityInventoryManager.clearInventoriesDirect(); AccountManager.clearAccountsDirect();
        UUID owner=UUID.randomUUID(); company=new Company(UUID.randomUUID(),"Factory",CompanyType.RAW_MATERIALS,100_000,owner); CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile=CompanyGameplayManager.createForNewCompany(company); WarehouseRecord warehouse=new WarehouseRecord(UUID.randomUUID(),"minecraft:overworld",BlockPos.ZERO,owner,company.getCompanyId(),4096,WarehouseStatus.ACTIVE,0,0,WarehousePermissionMode.OWNER_ONLY); WarehouseManager.restore(warehouse); profile.bindWarehouse(warehouse.warehouseId());
        facility=new CompanyFacilityRecord(UUID.randomUUID(),company.getCompanyId(),"minecraft:overworld",new BlockPos(1,64,1),CompanyFacilityType.FACTORY_CONTROLLER,1,CompanyFacilityStatus.ACTIVE,-1,warehouse.warehouseId()); CompanyFacilityManager.restore(facility); custody=CompanyInventoryFacade.custodyId(company.getCompanyId()); }
    @AfterEach void cleanup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();AccountManager.clearAccountsDirect();}
    @Test void facilityProducesOncePerDayAndNeverRunsLegacyProductionToo(){
        long cash=company.getCash(); var first=CompanyProductionService.processCompanyDay(company,5); int produced=CommodityInventoryManager.getCommodityAmount(custody,"iron");
        assertEquals(1,first.producedFacilities()); assertEquals(100,produced); assertTrue(company.getCash()<cash);
        assertEquals(0,CompanyProductionService.processCompanyDay(company,5).producedFacilities());
        assertEquals(produced,CommodityInventoryManager.getCommodityAmount(custody,"iron"));
        assertEquals(0, company.getInventoryAmount("iron"), "same-day retry must not run hybrid legacy fallback");
    }
    @Test void bankruptcyPlacesFacilityOnHoldWithoutMovingAssets(){ company.setBankruptcyRisk(true,1); assertFalse(CompanyProductionService.processFacility(company,facility,2)); assertEquals(CompanyFacilityStatus.BANKRUPTCY_HOLD,facility.status()); assertEquals(0,CommodityInventoryManager.getCommodityAmount(custody,"iron")); }
}
