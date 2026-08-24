package finance.exploration;

import finance.account.*;
import finance.config.FinanceConfig;
import finance.cycle.EconomyCycleService;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementStatus;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.*;

public final class ExplorationService {
    private ExplorationService(){}
    public static synchronized ExplorationResult request(ServerPlayer player,BlockPos boardPos){
        if(player==null||boardPos==null||!FinanceConfig.explorationEnabled()||!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.EXPLORATION))return ExplorationResult.fail("finance.exploration.disabled");
        long day=EconomyCycleService.currentMcDay(player.server);ExplorationAssignment active=ExplorationManager.activeFor(player.getUUID());if(active!=null)return ExplorationResult.ok("finance.exploration.already_active",active);
        long last=ExplorationManager.lastRequestDay(player.getUUID());if(last>=0&&day-last<FinanceConfig.explorationCooldownDays())return ExplorationResult.fail("finance.exploration.cooldown");
        Candidate candidate=findCandidate(player.serverLevel(),boardPos,FinanceConfig.explorationMaxDistance());if(candidate==null)return ExplorationResult.fail("finance.exploration.no_target");
        Account npc=AccountManager.getAccounts().get(NpcMarketMaker.NPC_UUID);long reward=FinanceConfig.explorationReward();if(npc==null||npc.getBalance()<reward)return ExplorationResult.fail("finance.exploration.no_budget");
        UUID escrowId=UUID.randomUUID();Account escrow=AccountManager.getOrCreateSystemAccount(escrowId);if(escrow.getBalance()!=0||!AccountManager.moveFunds(NpcMarketMaker.NPC_UUID,escrowId,reward)){AccountManager.getAccounts().remove(escrowId);return ExplorationResult.fail("finance.exploration.no_budget");}
        ExplorationAssignment a;try{a=new ExplorationAssignment(UUID.randomUUID(),player.getUUID(),escrowId,player.level().dimension().location().toString(),candidate.pos,candidate.type,theme(player.serverLevel(),boardPos),reward,day,day+FinanceConfig.explorationDeadlineDays(),ExplorationStatus.ACTIVE);}catch(RuntimeException ex){rollbackEscrow(escrowId,reward);return ExplorationResult.fail("finance.exploration.invalid");}
        if(!ExplorationManager.add(a)){rollbackEscrow(escrowId,reward);return ExplorationResult.fail("finance.exploration.limit");}ExplorationManager.recordRequest(player.getUUID(),day);
        AccountManager.addTransactionRecord(new TransactionRecord(NpcMarketMaker.NPC_UUID,escrowId,reward,TransactionType.EXPLORATION_ESCROW,player.getUUID(),a.theme(),1));EconomySavedData.markDirty();return ExplorationResult.ok("finance.exploration.created",a);
    }
    public static synchronized ExplorationResult verifyAt(ServerPlayer player,BlockPos pos,ExplorationTargetType type){
        ExplorationAssignment a=player==null?null:ExplorationManager.activeFor(player.getUUID());if(a==null||a.targetType()!=type||!a.dimensionId().equals(player.level().dimension().location().toString())||a.target().distSqr(pos)>36D)return ExplorationResult.fail("finance.exploration.not_target");
        long day=EconomyCycleService.currentMcDay(player.server);if(day>a.deadlineDay()){refund(a,true);return ExplorationResult.fail("finance.exploration.expired");}if(!targetStillValid(a)){refund(a,false);return ExplorationResult.fail("finance.exploration.target_missing");}
        Account escrow=AccountManager.getAccounts().get(a.escrowId());if(escrow==null||escrow.getBalance()!=a.reward()){a.quarantine();ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.EXPLORATION,finance.diagnostic.ModuleRunState.PAUSED,"exploration escrow mismatch",day);EconomySavedData.markDirty();return ExplorationResult.fail("finance.exploration.escrow_mismatch");}
        if(!AccountManager.moveFunds(a.escrowId(),player.getUUID(),a.reward()))return ExplorationResult.fail("finance.exploration.payment_blocked");a.complete();AccountManager.addTransactionRecord(new TransactionRecord(a.escrowId(),player.getUUID(),a.reward(),TransactionType.EXPLORATION_REWARD,player.getUUID(),a.theme(),1));finance.advancement.FinanceAdvancementTriggers.trigger(player,"field_survey");EconomySavedData.markDirty();return ExplorationResult.ok("finance.exploration.completed",a);
    }
    public static synchronized ExplorationResult cancel(ServerPlayer player,UUID id){ExplorationAssignment a=ExplorationManager.get(id);if(player==null||a==null||!a.playerId().equals(player.getUUID())||a.status()!=ExplorationStatus.ACTIVE)return ExplorationResult.fail("finance.exploration.invalid");return refund(a,false)?ExplorationResult.ok("finance.exploration.cancelled",a):ExplorationResult.fail("finance.exploration.refund_blocked");}
    public static void processDay(long day){for(ExplorationAssignment a:new ArrayList<>(ExplorationManager.assignments().values()))if(a.status()==ExplorationStatus.ACTIVE&&day>a.deadlineDay())refund(a,true);ExplorationManager.prune();}
    public static String direction(ExplorationAssignment a,BlockPos from){int dx=a.target().getX()-from.getX(),dz=a.target().getZ()-from.getZ();String ns=Math.abs(dz)<8?"":dz<0?"N":"S",ew=Math.abs(dx)<8?"":dx<0?"W":"E";String result=ns+ew;return result.isEmpty()?"HERE":result;}
    public static int distance(ExplorationAssignment a,BlockPos from){return (int)Math.min(Integer.MAX_VALUE,Math.sqrt(a.target().distSqr(from)));}
    private static void rollbackEscrow(UUID escrow,long reward){AccountManager.moveFunds(escrow,NpcMarketMaker.NPC_UUID,reward);AccountManager.getAccounts().remove(escrow);}
    private static boolean refund(ExplorationAssignment a,boolean expired){Account escrow=AccountManager.getAccounts().get(a.escrowId());Account npc=AccountManager.getAccounts().get(NpcMarketMaker.NPC_UUID);if(escrow==null||npc==null||escrow.getBalance()!=a.reward()||!npc.canDeposit(a.reward())||!AccountManager.moveFunds(a.escrowId(),NpcMarketMaker.NPC_UUID,a.reward()))return false;if(expired)a.expire();else a.cancel();AccountManager.addTransactionRecord(new TransactionRecord(a.escrowId(),NpcMarketMaker.NPC_UUID,a.reward(),TransactionType.EXPLORATION_REFUND,a.playerId(),a.theme(),1));EconomySavedData.markDirty();return true;}
    private static boolean targetStillValid(ExplorationAssignment a){if(a.targetType()==ExplorationTargetType.SETTLEMENT)return SettlementManager.settlements().values().stream().anyMatch(s->s.dimensionId().equals(a.dimensionId())&&s.anchor().equals(a.target())&&s.status()!=SettlementStatus.DISABLED);return WarehouseManager.all().stream().anyMatch(w->w.dimensionId().equals(a.dimensionId())&&w.blockPos().equals(a.target())&&w.status()!=WarehouseStatus.DISABLED&&w.status()!=WarehouseStatus.ORPHANED);}
    private static Candidate findCandidate(ServerLevel level,BlockPos origin,int maxDistance){String dimension=level.dimension().location().toString();long max=(long)maxDistance*maxDistance,min=48L*48L;List<Candidate> candidates=new ArrayList<>();SettlementManager.settlements().values().stream().filter(s->s.dimensionId().equals(dimension)&&s.status()!=SettlementStatus.DISABLED).forEach(s->candidates.add(new Candidate(s.anchor(),ExplorationTargetType.SETTLEMENT)));WarehouseManager.all().stream().filter(w->w.dimensionId().equals(dimension)&&w.status()!=WarehouseStatus.DISABLED&&w.status()!=WarehouseStatus.ORPHANED).forEach(w->candidates.add(new Candidate(w.blockPos(),ExplorationTargetType.WAREHOUSE)));return candidates.stream().filter(c->{double d=c.pos.distSqr(origin);return d>=min&&d<=max;}).min(Comparator.comparingDouble(c->c.pos.distSqr(origin))).orElse(null);}
    private static String theme(ServerLevel level,BlockPos pos){var key=level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value());String id=key==null?"":key.getPath();if(id.contains("desert")||id.contains("badlands"))return "desert_supply";if(id.contains("ocean")||id.contains("beach"))return "maritime";if(id.contains("swamp"))return "wetland";if(id.contains("mountain")||id.contains("peak")||id.contains("hill"))return "mountain";if(level.dimension()==Level.NETHER)return "nether";return "plains_trade";}
    private record Candidate(BlockPos pos,ExplorationTargetType type){}
}
