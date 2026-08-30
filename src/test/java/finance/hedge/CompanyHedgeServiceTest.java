package finance.hedge;

import finance.company.*;
import finance.data.EconomySavedData;
import finance.data.serializer.CompanyHedgeDataSerializer;
import finance.futures.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompanyHedgeServiceTest {
    UUID owner,companyId,contractId;
    @BeforeEach void setup(){
        EconomySavedData.resetRuntimeState();owner=UUID.randomUUID();companyId=UUID.randomUUID();contractId=UUID.randomUUID();
        CompanyManager.registerDirect(new Company(companyId,"Hedge Co",CompanyType.RAW_MATERIALS,100_000,owner));
        FuturesMarketManager.putContractDirect(new FuturesContract(contractId,"HEDGE","iron",10,0,20,21,
                FuturesSettlementType.CASH,FuturesContractStatus.TRADING));
    }
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();}

    @Test void objectiveDoesNotCreatePositionAndTracksOnlyMatchingRealPosition(){
        var made=CompanyHedgeService.create(owner,companyId,contractId,HedgeObjectiveType.INPUT_COST,100,1,15,"create-1");
        assertTrue(made.success(),made.message());assertNull(MarginManager.findPosition(owner,contractId));
        var objective=CompanyHedgeManager.get(made.id());assertEquals(HedgeCoverageStatus.UNHEDGED,CompanyHedgeService.coverage(objective,1).status());
        MarginManager.putAccountDirect(new MarginAccount(owner,10_000,0,MarginRiskStatus.NORMAL,-1));
        MarginManager.putPositionDirect(new FuturesPosition(owner,contractId,5,100,100,25));
        var half=CompanyHedgeService.coverage(objective,2);assertEquals(5_000,half.coverageBps());assertEquals(HedgeCoverageStatus.PARTIAL,half.status());
        assertEquals(25,half.realizedPnl());assertTrue(half.personalAccount());
    }

    @Test void directionExpiryAndPersistenceAreExplicit(){
        var made=CompanyHedgeService.create(owner,companyId,contractId,HedgeObjectiveType.OUTPUT_PRICE,100,1,15,"create-2");
        MarginManager.putAccountDirect(new MarginAccount(owner,10_000,0,MarginRiskStatus.MARGIN_CALL,-1));
        MarginManager.putPositionDirect(new FuturesPosition(owner,contractId,-15,100,100,0));
        assertEquals(HedgeCoverageStatus.OVER_HEDGED,CompanyHedgeService.coverage(CompanyHedgeManager.get(made.id()),2).status());
        CompoundTag root=new CompoundTag();CompanyHedgeDataSerializer.save(root);CompanyHedgeManager.clearDirect();CompanyHedgeDataSerializer.load(root);
        assertNotNull(CompanyHedgeManager.get(made.id()));assertEquals(HedgeCoverageStatus.EXPIRED,CompanyHedgeService.coverage(CompanyHedgeManager.get(made.id()),16).status());
    }
}
