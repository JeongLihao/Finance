package finance.settlement;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.data.serializer.SettlementDataSerializer;
import finance.diagnostic.ModuleHealthRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SettlementManagerTest {
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();CommodityRegistry.resetToDefaults();CommodityRegistry.register(new Commodity("wheat","minecraft:wheat","Wheat", CommodityCategory.FOOD,8));CommodityRegistry.register(new Commodity("stone","minecraft:stone","Stone",CommodityCategory.BUILDING_BLOCKS,3));CommodityRegistry.register(new Commodity("iron","minecraft:iron_ingot","Iron",CommodityCategory.RAW_MATERIALS,10));ModuleHealthRegistry.clear();}
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();CommodityRegistry.resetToDefaults();}

    @Test void repeatedDiscoveryAndNearbyRebuildRecoverStableIdentity(){UUID id=UUID.randomUUID();BlockPos pos=new BlockPos(10,64,10);SettlementRecord first=SettlementManager.registerOrRecover(id,"minecraft:overworld",pos,"Village");assertEquals(id,first.id());assertSame(first,SettlementManager.registerOrRecover(id,"minecraft:overworld",pos,"Village"));SettlementRecord copied=SettlementManager.registerOrRecover(id,"minecraft:overworld",pos.offset(100,0,0),"Copied");assertNotEquals(id,copied.id());SettlementManager.disable(id);SettlementRecord recovered=SettlementManager.registerOrRecover(UUID.randomUUID(),"minecraft:overworld",pos.offset(2,0,0),"Village");assertEquals(id,recovered.id());assertEquals(SettlementStatus.ACTIVE,recovered.status());}

    @Test void contributionAndPublicResponseAreBoundedAndPrivate(){SettlementRecord s=record();UUID owner=UUID.randomUUID(),other=UUID.randomUUID();for(int i=0;i<20;i++)s.addContribution(owner,50);assertEquals(500,s.points(owner));assertEquals(5,s.level(owner));LocalDemand open=demand(s.id(),DemandStatus.OPEN,null,100),mine=demand(s.id(),DemandStatus.ACCEPTED,owner,100),privateOther=demand(s.id(),DemandStatus.ACCEPTED,other,100);SettlementManager.addDemand(open);SettlementManager.addDemand(mine);SettlementManager.addDemand(privateOther);assertEquals(2,SettlementManager.publicRows(s.id(),owner).size());assertFalse(SettlementManager.publicRows(s.id(),owner).stream().anyMatch(d->other.equals(d.acceptedPlayerId())));}

    @Test void dailyGenerationIsIdempotentBoundedAndBackedByRealNpcBudget(){SettlementRecord s=record();AccountManager.getOrCreateSystemAccount(finance.market.NpcMarketMaker.NPC_UUID).deposit(100_000);long before=AccountManager.getAccounts().get(finance.market.NpcMarketMaker.NPC_UUID).getBalance();LocalDemand created=SettlementService.generate(s,1);assertNotNull(created);assertNull(SettlementService.generate(s,1));assertEquals(created.reward(),AccountManager.getAccounts().get(created.escrowAccountId()).getBalance());assertEquals(before-created.reward(),AccountManager.getAccounts().get(finance.market.NpcMarketMaker.NPC_UUID).getBalance());}

    @Test void insufficientBudgetNeverCreatesUnfundedDemandOrOrphanEscrow(){SettlementRecord s=record();AccountManager.getOrCreateSystemAccount(finance.market.NpcMarketMaker.NPC_UUID);int accountsBefore=AccountManager.getAccounts().size();assertNull(SettlementService.generate(s,2));assertTrue(SettlementManager.demands().isEmpty());assertEquals(accountsBefore,AccountManager.getAccounts().size());}

    @Test void persistencePreservesEscrowAndIsolatesCorruptRecords(){SettlementRecord s=record();LocalDemand demand=demand(s.id(),DemandStatus.OPEN,null,200);AccountManager.getOrCreateSystemAccount(demand.escrowAccountId()).deposit(200);SettlementManager.addDemand(demand);CompoundTag root=new CompoundTag();SettlementDataSerializer.save(root);root.getCompound(SettlementDataSerializer.ROOT).getList("Records",10).getCompound(0).putString("Name","x".repeat(80));SettlementManager.clearDirect();SettlementDataSerializer.load(root);assertTrue(SettlementManager.settlements().isEmpty());assertFalse(ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.SETTLEMENT));assertEquals(200,AccountManager.getAccounts().get(demand.escrowAccountId()).getBalance());}

    @Test void hundredSettlementFixtureRoundTripsWithinGlobalBounds(){AccountManager.getOrCreateSystemAccount(finance.market.NpcMarketMaker.NPC_UUID).deposit(10_000_000);for(int i=0;i<100;i++){SettlementRecord s=new SettlementRecord(UUID.randomUUID(),"minecraft:overworld",new BlockPos(i,64,i),"Village "+i,SettlementStatus.ACTIVE,i-1,-1,"");assertTrue(SettlementManager.restoreSettlement(s));assertNotNull(SettlementService.generate(s,i));}CompoundTag root=new CompoundTag();SettlementDataSerializer.save(root);SettlementManager.clearDirect();SettlementDataSerializer.load(root);assertEquals(100,SettlementManager.settlements().size());assertEquals(100,SettlementManager.demands().size());}

    @Test void casualtyAggregationTriggersOneBoundedRebuildState(){SettlementRecord s=record();assertFalse(s.noteCasualty(4));assertFalse(s.noteCasualty(4));assertTrue(s.noteCasualty(4));assertEquals(SettlementStatus.REBUILDING,s.status());assertFalse(s.noteCasualty(4));assertEquals(4,s.casualtyDay());assertEquals(4,s.casualtyCount());}

    @Test void pausedModuleDoesNotConsumeDailyGenerationMarker(){SettlementRecord s=record();ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.SETTLEMENT,finance.diagnostic.ModuleRunState.PAUSED,"test",1);assertNull(SettlementService.generate(s,7));assertEquals(-1,s.lastDemandDay());}

    private SettlementRecord record(){SettlementRecord s=new SettlementRecord(UUID.randomUUID(),"minecraft:overworld",BlockPos.ZERO,"Village",SettlementStatus.ACTIVE,-1,-1,"");assertTrue(SettlementManager.restoreSettlement(s));return s;}
    private LocalDemand demand(UUID settlement,DemandStatus status,UUID player,long reward){return new LocalDemand(UUID.randomUUID(),settlement,"wheat","food",8,reward,UUID.randomUUID(),0,5,status,player);}
}
