package finance.gameplay.company;

import finance.company.*;
import finance.commodity.CommodityInventoryManager;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompanyWarehouseBindingServiceTest {
    @BeforeEach void setup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();}
    @AfterEach void cleanup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();}

    @Test void managerMustOwnWarehouseAndBindAndUnbindAreIdempotent(){UUID owner=UUID.randomUUID(),manager=UUID.randomUUID();Company company=new Company(UUID.randomUUID(),"Bind",CompanyType.RAW_MATERIALS,10_000,owner);CompanyManager.registerDirect(company);CompanyGameplayProfile profile=CompanyGameplayManager.createForNewCompany(company);profile.putMember(new CompanyMemberRecord(manager,CompanyMemberRole.MANAGER,0));WarehouseRecord warehouse=new WarehouseRecord(UUID.randomUUID(),"minecraft:overworld",BlockPos.ZERO,manager,null,4096,WarehouseStatus.ACTIVE,0,0,WarehousePermissionMode.OWNER_ONLY);WarehouseManager.restore(warehouse);assertTrue(CompanyWarehouseBindingService.bind(manager,company.getCompanyId(),warehouse.warehouseId(),"bind").success());assertFalse(CompanyWarehouseBindingService.bind(manager,company.getCompanyId(),warehouse.warehouseId(),"bind").success());assertTrue(CompanyWarehouseBindingService.unbind(manager,company.getCompanyId(),warehouse.warehouseId(),"unbind").success());assertFalse(CompanyWarehouseBindingService.unbind(manager,company.getCompanyId(),warehouse.warehouseId(),"unbind").success());}

    @Test void lastWarehouseCannotUnbindWhileCompanyCustodyWouldLoseCapacity(){UUID owner=UUID.randomUUID();Company company=new Company(UUID.randomUUID(),"Capacity",CompanyType.RAW_MATERIALS,10_000,owner);CompanyManager.registerDirect(company);CompanyGameplayManager.createForNewCompany(company);WarehouseRecord warehouse=new WarehouseRecord(UUID.randomUUID(),"minecraft:overworld",BlockPos.ZERO,owner,null,16,WarehouseStatus.ACTIVE,0,0,WarehousePermissionMode.OWNER_ONLY);WarehouseManager.restore(warehouse);assertTrue(CompanyWarehouseBindingService.bind(owner,company.getCompanyId(),warehouse.warehouseId(),"bind").success());CommodityInventoryManager.setCommodity(CompanyInventoryFacade.custodyId(company.getCompanyId()),"iron",1);assertFalse(CompanyWarehouseBindingService.unbind(owner,company.getCompanyId(),warehouse.warehouseId(),"unbind").success());assertEquals(company.getCompanyId(),warehouse.companyId());}
}
