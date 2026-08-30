package finance.insurance;

import finance.collateral.*;
import finance.commodity.*;
import finance.company.*;
import finance.data.EconomySavedData;
import finance.gameplay.company.*;
import finance.market.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WorldInsuranceIntegrationTest {
    UUID owner,companyId,custody;Company company;
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();owner=UUID.randomUUID();companyId=UUID.randomUUID();company=new Company(companyId,"World Insurance",CompanyType.RAW_MATERIALS,100_000,owner);CompanyManager.registerDirect(company);CompanyGameplayManager.restore(new CompanyGameplayProfile(companyId,CompanyOperatingMode.PLAYER_DRIVEN));custody=CompanyInventoryFacade.custodyId(companyId);CommodityRegistry.register(new Commodity("insured_iron","minecraft:iron_ingot","Insured Iron",CommodityCategory.RAW_MATERIALS,100));NpcMarketMaker.putMarketPrice("insured_iron",new MarketPrice("insured_iron",100,.02));CommodityInventoryManager.setCommodity(custody,"insured_iron",100);}
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();}

    @Test void warehouseLossConsumesOnlyUnpledgedPhysicalCustody(){
        var pledge=new InventoryCollateralAgreement(UUID.randomUUID(),companyId,UUID.randomUUID(),UUID.randomUUID(),custody,"insured_iron",60,0,100,3600,4000,6000,7500,9000,InventoryCollateralStatus.ACTIVE);assertTrue(InventoryCollateralManager.restore(pledge));
        var event=InsuranceManager.createWarehouseAccident(companyId,"insured_iron",100,1,9);assertTrue(event.success(),event.message());
        assertEquals(60,CommodityInventoryManager.getCommodityAmount(custody,"insured_iron"));assertEquals(60,InventoryCollateralManager.pledged(custody,"insured_iron"));
        var evidence=InsuranceManager.events().get(event.id());assertEquals(40,evidence.quantityLost());assertTrue(evidence.evidence().contains("pledged=60"));
    }

    @Test void interruptionUsesFacilityStateAndServerFinancialHistory(){
        company.restoreFinancials(0,0,0,0,List.of(100L,120L,80L));
        var facility=new CompanyFacilityRecord(UUID.randomUUID(),companyId,"minecraft:overworld",BlockPos.ZERO,CompanyFacilityType.FACTORY_CONTROLLER,1,CompanyFacilityStatus.ACTIVE,0,null);facility.setStatus(CompanyFacilityStatus.MISSING_INPUT,1);facility.setLastProcessedDay(2);assertTrue(CompanyFacilityManager.restore(facility));
        var event=InsuranceManager.createBusinessInterruption(companyId,2,7);assertTrue(event.success(),event.message());
        var loss=InsuranceManager.events().get(event.id());assertEquals(200,loss.verifiedLoss());assertTrue(loss.evidence().contains("MISSING_INPUT"));
    }
}
