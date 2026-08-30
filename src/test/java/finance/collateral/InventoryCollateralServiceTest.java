package finance.collateral;

import finance.bank.BankingManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.data.serializer.InventoryCollateralDataSerializer;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.market.NpcMarketMaker;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class InventoryCollateralServiceTest {
    UUID owner; Company company; UUID custody; UUID bank;
    @BeforeEach void setup(){
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.register(new Commodity("pledge_test","minecraft:iron_ingot","Pledge Test",CommodityCategory.RAW_MATERIALS,100));
        NpcMarketMaker.seedNpcIfNeeded();BankingManager.ensureDefaultBanks();bank=BankingManager.banks().values().iterator().next().id();
        owner=UUID.randomUUID();company=new Company(UUID.randomUUID(),"Collateral Co",CompanyType.RAW_MATERIALS,1_000_000,owner);
        CompanyManager.registerDirect(company);CompanyGameplayManager.createForNewCompany(company);
        custody=CompanyInventoryFacade.custodyId(company.getCompanyId());CommodityInventoryManager.setCommodity(custody,"pledge_test",100);
    }
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();}

    @Test void pledgeReservationBlocksEveryOrdinaryRemovalAndSurvivesRestart(){
        var result=InventoryCollateralService.apply(owner,company.getCompanyId(),bank,"pledge_test",60,1,"apply");
        assertTrue(result.success(),result.message());InventoryCollateralAgreement agreement=InventoryCollateralManager.get(result.id());assertNotNull(agreement);
        assertEquals(60,InventoryCollateralManager.pledged(custody,"pledge_test"));assertEquals(40,InventoryCollateralManager.available(custody,"pledge_test"));
        assertFalse(CommodityInventoryManager.removeCommodity(custody,"pledge_test",41));assertTrue(CommodityInventoryManager.removeCommodity(custody,"pledge_test",40));
        CompoundTag root=new CompoundTag();InventoryCollateralDataSerializer.save(root);InventoryCollateralManager.clearDirect();InventoryCollateralDataSerializer.load(root);
        assertEquals(60,InventoryCollateralManager.pledged(custody,"pledge_test"));assertFalse(CommodityInventoryManager.removeCommodity(custody,"pledge_test",1));
    }

    @Test void failedLoanDoesNotLeaveReservation(){
        var result=InventoryCollateralService.apply(owner,company.getCompanyId(),UUID.randomUUID(),"pledge_test",50,1,"bad-bank");
        assertFalse(result.success());assertEquals(0,InventoryCollateralManager.pledged(custody,"pledge_test"));assertEquals(100,InventoryCollateralManager.available(custody,"pledge_test"));
    }

    @Test void bankruptcyWaitsForSecuredInventoryToSettleBeforeCompanyRemoval(){
        var result=InventoryCollateralService.apply(owner,company.getCompanyId(),bank,"pledge_test",60,1,"bankruptcy");
        assertTrue(result.success(),result.message());
        assertFalse(InventoryCollateralService.prepareCompanyBankruptcy(company.getCompanyId(),2),
                "first pass moves and offers physical collateral but must not report premature completion");
        assertTrue(InventoryCollateralService.prepareCompanyBankruptcy(company.getCompanyId(),3));
        var agreement=InventoryCollateralManager.get(result.id());
        assertEquals(InventoryCollateralStatus.LIQUIDATED,agreement.status());
        assertEquals(0,InventoryCollateralManager.pledged(custody,"pledge_test"));
        assertTrue(agreement.liquidationRecovered()>0);
    }

    @Test void dailyBatchRotatesWithoutDuplicatingOrStarvingAgreements(){
        InventoryCollateralManager.clearDirect();
        for(int i=0;i<5;i++) assertTrue(InventoryCollateralManager.restore(agreement(i)));
        var first=InventoryCollateralManager.nextBatch(3);
        var second=InventoryCollateralManager.nextBatch(3);
        assertEquals(3,first.size());assertEquals(3,second.size());
        assertEquals(3,new HashSet<>(first).size());assertEquals(3,new HashSet<>(second).size());
        var seen=new HashSet<UUID>();first.forEach(a->seen.add(a.id()));second.forEach(a->seen.add(a.id()));
        assertEquals(5,seen.size(),"two rotating batches must reach every agreement");
        assertEquals(1,InventoryCollateralManager.processingCursor());
    }

    private InventoryCollateralAgreement agreement(int index){
        return new InventoryCollateralAgreement(UUID.randomUUID(),company.getCompanyId(),bank,UUID.randomUUID(),
                custody,"pledge_test",1,index+1,100,50,5_000,5_000,7_000,9_000,
                InventoryCollateralStatus.ACTIVE);
    }
}
