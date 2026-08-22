package finance.gameplay.company;

import finance.account.AccountManager;
import finance.company.*;
import finance.commodity.*;
import finance.market.NpcMarketMaker;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompanyGameplayMarketServiceTest {
    @BeforeEach void setup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();AccountManager.clearAccountsDirect();NpcMarketMaker.clearMarketPrices();CommodityRegistry.register(new Commodity("iron","minecraft:iron_ingot","Iron",CommodityCategory.RAW_MATERIALS,10));}
    @AfterEach void cleanup(){CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();AccountManager.clearAccountsDirect();NpcMarketMaker.clearMarketPrices();}

    @Test void successfulSaleMovesCashAndGoodsAndRecordsVolumeExactlyOnce(){Company company=company(1_000);UUID custody=CompanyInventoryFacade.custodyId(company.getCompanyId());CommodityInventoryManager.setCommodity(custody,"iron",10);var npc=AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);npc.setBalance(10_000);long bid=NpcMarketMaker.getMarketPrice("iron").getBidPrice();assertTrue(CompanyGameplayMarketService.sell(company,"iron",5));assertEquals(5,CommodityInventoryManager.getCommodityAmount(custody,"iron"));assertEquals(5,CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID,"iron"));assertEquals(1_000+bid*5,company.getCash());assertEquals(10_000-bid*5,npc.getBalance());assertEquals(5,NpcMarketMaker.getMarketPrice("iron").getDayVolume());}

    @Test void everyPreflightFailureLeavesBothSidesUntouched(){Company company=company(Long.MAX_VALUE);UUID custody=CompanyInventoryFacade.custodyId(company.getCompanyId());CommodityInventoryManager.setCommodity(custody,"iron",10);var npc=AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);npc.setBalance(10_000);assertFalse(CompanyGameplayMarketService.sell(company,"iron",5));assertEquals(10,CommodityInventoryManager.getCommodityAmount(custody,"iron"));assertEquals(10_000,npc.getBalance());CompanyManager.clearCompaniesDirect();WarehouseManager.clearDirect();CommodityInventoryManager.clearInventoriesDirect();company=company(1_000);custody=CompanyInventoryFacade.custodyId(company.getCompanyId());CommodityInventoryManager.setCommodity(custody,"iron",10);CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID,"iron",Integer.MAX_VALUE);npc=AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);npc.setBalance(10_000);assertFalse(CompanyGameplayMarketService.sell(company,"iron",5));assertEquals(10,CommodityInventoryManager.getCommodityAmount(custody,"iron"));assertEquals(1_000,company.getCash());}

    private Company company(long cash){UUID owner=UUID.randomUUID();Company company=new Company(UUID.randomUUID(),"Seller",CompanyType.RAW_MATERIALS,cash,owner);CompanyManager.registerDirect(company);CompanyGameplayProfile profile=CompanyGameplayManager.createForNewCompany(company);WarehouseRecord warehouse=new WarehouseRecord(UUID.randomUUID(),"minecraft:overworld",BlockPos.ZERO,owner,company.getCompanyId(),4096,WarehouseStatus.ACTIVE,0,0,WarehousePermissionMode.OWNER_ONLY);WarehouseManager.restore(warehouse);profile.bindWarehouse(warehouse.warehouseId());return company;}
}
