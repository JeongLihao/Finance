package finance.gameplay.company;

import finance.company.*;
import finance.commodity.CommodityInventoryManager;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CompanyUpgradeServiceTest {
    private Company company; private CompanyFacilityRecord facility; private UUID owner,custody;
    @BeforeEach void setup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();owner=UUID.randomUUID();company=new Company(UUID.randomUUID(),"Upgrade",CompanyType.RAW_MATERIALS,100_000,owner);CompanyManager.registerDirect(company);CompanyGameplayProfile p=CompanyGameplayManager.createForNewCompany(company);WarehouseRecord w=new WarehouseRecord(UUID.randomUUID(),"minecraft:overworld",BlockPos.ZERO,owner,company.getCompanyId(),4096,WarehouseStatus.ACTIVE,0,0,WarehousePermissionMode.OWNER_ONLY);WarehouseManager.restore(w);p.bindWarehouse(w.warehouseId());facility=new CompanyFacilityRecord(UUID.randomUUID(),company.getCompanyId(),"minecraft:overworld",new BlockPos(2,64,2),CompanyFacilityType.FACTORY_CONTROLLER,1,CompanyFacilityStatus.ACTIVE,-1,w.warehouseId());CompanyFacilityManager.restore(facility);custody=CompanyInventoryFacade.custodyId(company.getCompanyId());CommodityInventoryManager.setCommodity(custody,"iron",100);CommodityInventoryManager.setCommodity(custody,"stone",100);}
    @AfterEach void cleanup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();}
    @Test void upgradeConsumesServerCalculatedCashAndMaterialsOnce(){long cash=company.getCash();assertTrue(CompanyUpgradeService.upgrade(owner,facility.facilityId(),"u1").success());assertEquals(2,facility.productionLevel());assertTrue(company.getCash()<cash);int iron=CommodityInventoryManager.getCommodityAmount(custody,"iron");assertFalse(CompanyUpgradeService.upgrade(owner,facility.facilityId(),"u1").success());assertEquals(iron,CommodityInventoryManager.getCommodityAmount(custody,"iron"));}
    @Test void insufficientMaterialsLeavesCashAndLevelUntouched(){CommodityInventoryManager.setCommodity(custody,"stone",0);long cash=company.getCash();assertFalse(CompanyUpgradeService.upgrade(owner,facility.facilityId(),"u2").success());assertEquals(cash,company.getCash());assertEquals(1,facility.productionLevel());}
}
