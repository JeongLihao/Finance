package finance.settlement;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.NpcMarketMaker;
import finance.market.MarketPrice;
import finance.warehouse.CommodityItemResolver;
import finance.warehouse.InventoryTransactionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.math.BigInteger;

/** Event-driven settlement demand generation and atomic physical delivery. */
public final class SettlementService {
    public static final int MAX_ACTIVE_PER_PLAYER=4, MAX_OPEN_PER_SETTLEMENT=8;
    private SettlementService(){}

    public static SettlementRecord register(ServerPlayer player, finance.block.entity.SettlementTradeStationBlockEntity station){
        if(player==null||station==null||station.getLevel()!=player.serverLevel()||station.getBlockPos().distSqr(player.blockPosition())>64D)return null;
        String dimension=player.serverLevel().dimension().location().toString();
        SettlementRecord record=SettlementManager.registerOrRecover(station.settlementId(),dimension,station.getBlockPos(),
                "settlement."+shortId(station.settlementId()));
        if(record!=null&&!record.id().equals(station.settlementId()))station.assignIdentity(record.id());
        return record;
    }

    public static synchronized SettlementActionResult accept(ServerPlayer player,UUID settlementId,UUID demandId,String operationKey){
        if(!validKey(operationKey)||!near(player,settlementId))return SettlementActionResult.fail("finance.settlement.not_nearby");
        if(!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.SETTLEMENT))return SettlementActionResult.fail("finance.settlement.paused");
        SettlementRecord settlement=SettlementManager.get(settlementId); LocalDemand demand=SettlementManager.demand(demandId);
        if(settlement==null||settlement.status()==SettlementStatus.DISABLED||demand==null||!settlementId.equals(demand.settlementId())||demand.status()!=DemandStatus.OPEN)
            return SettlementActionResult.fail("finance.settlement.invalid_demand");
        String key=player.getUUID()+":"+operationKey;if(demand.hasOperation(key))return SettlementActionResult.fail("finance.settlement.duplicate");
        long day=day(player.serverLevel());if(day>demand.deadlineDay())return SettlementActionResult.fail("finance.settlement.expired");
        if(SettlementManager.activeForPlayer(player.getUUID())>=finance.config.FinanceConfig.settlementMaxActivePerPlayer())return SettlementActionResult.fail("finance.settlement.player_limit");
        if(balance(demand.escrowAccountId())!=demand.reward())return SettlementActionResult.fail("finance.settlement.escrow_mismatch");
        if(!demand.accept(player.getUUID()))return SettlementActionResult.fail("finance.settlement.invalid_demand");
        demand.recordOperation(key);EconomySavedData.markDirty();return SettlementActionResult.ok("finance.settlement.accepted");
    }

    public static synchronized SettlementActionResult deliver(ServerPlayer player,UUID settlementId,UUID demandId,String operationKey){
        if(!validKey(operationKey)||!near(player,settlementId))return SettlementActionResult.fail("finance.settlement.not_nearby");
        if(!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.SETTLEMENT))return SettlementActionResult.fail("finance.settlement.paused");
        SettlementRecord settlement=SettlementManager.get(settlementId);LocalDemand demand=SettlementManager.demand(demandId);
        if(settlement==null||demand==null||!settlementId.equals(demand.settlementId())||demand.status()!=DemandStatus.ACCEPTED
                ||!player.getUUID().equals(demand.acceptedPlayerId()))return SettlementActionResult.fail("finance.settlement.invalid_demand");
        String key=player.getUUID()+":"+operationKey;if(demand.hasOperation(key))return SettlementActionResult.fail("finance.settlement.duplicate");
        if(day(player.serverLevel())>demand.deadlineDay())return SettlementActionResult.fail("finance.settlement.expired");
        CommodityItemResolver.Resolution resolution=CommodityItemResolver.resolve(demand.commodityId());
        if(!resolution.valid())return SettlementActionResult.fail("finance.settlement.no_physical_item");
        InventoryTransactionService.RemovalPlan plan=InventoryTransactionService.planRemoval(player.getInventory(),resolution.item(),demand.quantity());
        if(plan==null)return SettlementActionResult.fail("finance.settlement.insufficient_items");
        if(balance(demand.escrowAccountId())!=demand.reward()||!AccountManager.canDeposit(player.getUUID(),demand.reward())
                ||!CommodityInventoryManager.canAddCommodity(settlementId,demand.commodityId(),demand.quantity()))
            return SettlementActionResult.fail("finance.settlement.preflight_failed");
        if(!InventoryTransactionService.commitRemoval(player.getInventory(),plan))return SettlementActionResult.fail("finance.settlement.inventory_changed");
        if(!CommodityInventoryManager.addCommodity(settlementId,demand.commodityId(),demand.quantity())){
            InventoryTransactionService.rollbackRemoval(player,plan);return SettlementActionResult.fail("finance.settlement.delivery_failed");
        }
        if(!AccountManager.moveFunds(demand.escrowAccountId(),player.getUUID(),demand.reward())){
            CommodityInventoryManager.removeCommodity(settlementId,demand.commodityId(),demand.quantity());
            InventoryTransactionService.rollbackRemoval(player,plan);return SettlementActionResult.fail("finance.settlement.payment_failed");
        }
        if(!demand.complete(player.getUUID()))throw new IllegalStateException("Demand state changed during synchronized settlement");
        finance.regional.RegionalCommodityMetricsService.recordDemandCompleted(settlement,demand,day(player.serverLevel()));
        demand.recordOperation(key);int points=settlement.addContribution(player.getUUID(),demand.theme().equals("rebuild")?25:10);
        if(demand.theme().equals("rebuild"))settlement.finishRebuild();
        AccountManager.addTransactionRecord(new TransactionRecord(demand.escrowAccountId(),player.getUUID(),demand.reward(),
                TransactionType.SETTLEMENT_DELIVERY,player.getUUID(),demand.commodityId(),demand.quantity()));
        finance.advancement.FinanceAdvancementTriggers.trigger(player,"first_village_help");
        if(finance.logistics.ShipmentManager.relatedTo(player.getUUID(),false).stream()
                .anyMatch(shipment->shipment.status()==finance.logistics.ShipmentStatus.DELIVERED))
            finance.advancement.FinanceAdvancementTriggers.trigger(player,"village_logistics");
        if(demand.theme().equals("rebuild"))finance.advancement.FinanceAdvancementTriggers.trigger(player,"village_rebuild");
        player.serverLevel().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.BELL_BLOCK,
                net.minecraft.sounds.SoundSource.BLOCKS,.7F,1.15F);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                player.getX(),player.getY()+1,player.getZ(),6,.3,.4,.3,0);
        EconomySavedData.markDirty();return SettlementActionResult.ok("finance.settlement.delivered.level."+settlement.level(player.getUUID()));
    }

    public static synchronized void processDay(MinecraftServer server,long day){
        for(SettlementRecord settlement:SettlementManager.settlements().values()){
            ServerLevel level=level(server,settlement.dimensionId());
            if(level!=null&&settlement.status()!=SettlementStatus.DISABLED&&settlement.status()!=SettlementStatus.QUARANTINED){
                net.minecraft.world.entity.raid.Raid raid=level.getRaidAt(settlement.anchor());
                if(raid!=null&&!raid.isStopped()&&settlement.status()!=SettlementStatus.RAID_ALERT)
                    settlement.raidAlert(day,"raid-start:"+day+":"+raid.getId());
                else if((raid==null||raid.isStopped())&&settlement.status()==SettlementStatus.RAID_ALERT)
                    settlement.rebuilding(day,"raid-end:"+day);
            }
            expire(settlement,day);
            if(settlement.status()==SettlementStatus.ACTIVE||settlement.status()==SettlementStatus.REBUILDING||settlement.status()==SettlementStatus.RAID_ALERT)
                generate(settlement,day);
        }
    }

    public static synchronized LocalDemand generate(SettlementRecord settlement,long day){
        if(settlement==null||!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.SETTLEMENT)||!settlement.markDemandDay(day))return null;
        long open=SettlementManager.forSettlement(settlement.id()).stream().filter(d->!d.status().terminal()).count();
        if(open>=finance.config.FinanceConfig.settlementMaxOpen())return null;
        String theme=settlement.status()==SettlementStatus.RAID_ALERT?"defense":settlement.status()==SettlementStatus.REBUILDING?"rebuild":switch((int)Math.floorMod(day+settlement.id().getLeastSignificantBits(),3)){case 0->"food";case 1->"materials";default->"stock";};
        String commodity=theme.equals("food")?"wheat":theme.equals("defense")?"iron":theme.equals("rebuild")?"stone":switch((int)Math.floorMod(day,3)){case 0->"wheat";case 1->"stone";default->"iron";};
        Commodity definition=CommodityRegistry.getCommodity(commodity);
        if(definition==null||net.minecraft.resources.ResourceLocation.tryParse(definition.getItemId())==null)return null;
        int quantity=16+(int)Math.floorMod(settlement.id().getMostSignificantBits()^day*31,3)*8;
        MarketPrice market=NpcMarketMaker.getMarketPrice(commodity);long unit=market==null?definition.getBasePrice():Math.max(1,market.getAskPrice());
        int localMultiplier=finance.regional.RegionalCommodityMetricsService.quoteMultiplierBps(settlement,commodity);
        BigInteger computed=BigInteger.valueOf(unit).multiply(BigInteger.valueOf(quantity))
                .multiply(BigInteger.valueOf(finance.config.FinanceConfig.settlementRewardBasisPoints()))
                .multiply(BigInteger.valueOf(localMultiplier)).divide(BigInteger.valueOf(100_000_000L));
        if(computed.signum()<=0||computed.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0)return null;
        long reward=computed.longValue();
        UUID escrow=UUID.randomUUID();AccountManager.getOrCreateSystemAccount(escrow);
        if(balance(NpcMarketMaker.NPC_UUID)<reward||!AccountManager.moveFunds(NpcMarketMaker.NPC_UUID,escrow,reward)){
            removeEmptyEscrow(escrow);return null;
        }
        LocalDemand demand=new LocalDemand(UUID.randomUUID(),settlement.id(),commodity,theme,quantity,reward,escrow,day,day+finance.config.FinanceConfig.settlementDeadlineDays(),DemandStatus.OPEN,null);
        if(!SettlementManager.addDemand(demand)){if(!AccountManager.moveFunds(escrow,NpcMarketMaker.NPC_UUID,reward))throw new IllegalStateException("demand escrow rollback failed");removeEmptyEscrow(escrow);return null;}
        finance.regional.RegionalCommodityMetricsService.recordDemandOpened(settlement,demand);
        AccountManager.addTransactionRecord(new TransactionRecord(NpcMarketMaker.NPC_UUID,escrow,reward,TransactionType.SETTLEMENT_ESCROW));
        EconomySavedData.markDirty();return demand;
    }

    private static void expire(SettlementRecord settlement,long day){for(LocalDemand d:SettlementManager.forSettlement(settlement.id()))if(!d.status().terminal()&&day>d.deadlineDay()){
        long balance=balance(d.escrowAccountId());if(balance==d.reward()&&AccountManager.moveFunds(d.escrowAccountId(),NpcMarketMaker.NPC_UUID,balance)){d.expire();d.refunded();finance.regional.RegionalCommodityMetricsService.recordDemandExpired(settlement,d,day);AccountManager.addTransactionRecord(new TransactionRecord(d.escrowAccountId(),NpcMarketMaker.NPC_UUID,balance,TransactionType.SETTLEMENT_REFUND));}
        else {d.quarantine();ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.SETTLEMENT,finance.diagnostic.ModuleRunState.PAUSED,"settlement escrow mismatch",day);}EconomySavedData.markDirty();}}

    public static void raidEvent(ServerLevel level,BlockPos pos,boolean finished,String eventKey){long day=day(level);nearest(level,pos,96).ifPresent(s->{if(eventKey.equals(s.lastEventKey()))return;if(finished)s.rebuilding(day,eventKey);else s.raidAlert(day,eventKey);EconomySavedData.markDirty();});}
    public static Optional<SettlementRecord> nearest(ServerLevel level,BlockPos pos,int radius){String dim=level.dimension().location().toString();double max=(double)radius*radius;return SettlementManager.settlements().values().stream().filter(s->s.dimensionId().equals(dim)&&s.anchor().distSqr(pos)<=max).min(Comparator.comparingDouble(s->s.anchor().distSqr(pos)));}
    private static ServerLevel level(MinecraftServer server,String dimension){for(ServerLevel level:server.getAllLevels())if(level.dimension().location().toString().equals(dimension))return level;return null;}
    private static boolean near(ServerPlayer p,UUID settlement){SettlementRecord s=SettlementManager.get(settlement);return p!=null&&s!=null&&p.serverLevel().dimension().location().toString().equals(s.dimensionId())&&p.blockPosition().distSqr(s.anchor())<=64D&&p.serverLevel().getBlockEntity(s.anchor()) instanceof finance.block.entity.SettlementTradeStationBlockEntity be&&settlement.equals(be.settlementId());}
    public static boolean isNearby(ServerPlayer player,UUID settlementId){return near(player,settlementId);}
    private static long balance(UUID id){var account=AccountManager.getAccounts().get(id);return account==null?-1:account.getBalance();}
    private static void removeEmptyEscrow(UUID id){var account=AccountManager.getAccounts().get(id);if(account!=null&&account.getBalance()==0&&account.getFrozenBalance()==0)AccountManager.getAccounts().remove(id);}
    private static long day(ServerLevel l){return l.getGameTime()/24000L;}private static boolean validKey(String k){return k!=null&&!k.isBlank()&&k.length()<=64;}private static String shortId(UUID id){return id.toString().substring(0,8);}
}
