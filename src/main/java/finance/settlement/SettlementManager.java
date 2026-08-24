package finance.settlement;

import finance.data.EconomySavedData;
import net.minecraft.core.BlockPos;
import java.util.*;

public final class SettlementManager {
    public static final int MAX_SETTLEMENTS=256, MAX_DEMANDS=4096, MAX_HISTORY=1024;
    private static final Map<UUID,SettlementRecord> SETTLEMENTS=new LinkedHashMap<>();
    private static final Map<UUID,LocalDemand> DEMANDS=new LinkedHashMap<>();
    private SettlementManager(){}
    public static SettlementRecord get(UUID id){return id==null?null:SETTLEMENTS.get(id);}
    public static LocalDemand demand(UUID id){return id==null?null:DEMANDS.get(id);}
    public static Map<UUID,SettlementRecord> settlements(){return Map.copyOf(SETTLEMENTS);}
    public static Map<UUID,LocalDemand> demands(){return Map.copyOf(DEMANDS);}
    public static SettlementRecord registerOrRecover(UUID proposed,String dimension,BlockPos pos,String name){
        SettlementRecord exact=get(proposed); if(exact!=null&&exact.dimensionId().equals(dimension)){
            if(exact.anchor().equals(pos)||(exact.status()==SettlementStatus.DISABLED&&exact.anchor().distSqr(pos)<=256D)){
                exact.activate(pos);EconomySavedData.markDirty();return exact;
            }
            proposed=null;
        }
        SettlementRecord recover=SETTLEMENTS.values().stream().filter(s->s.status()==SettlementStatus.DISABLED&&s.dimensionId().equals(dimension)&&s.anchor().distSqr(pos)<=256D).min(Comparator.comparingDouble(s->s.anchor().distSqr(pos))).orElse(null);
        if(recover!=null){recover.activate(pos);EconomySavedData.markDirty();return recover;}
        if(SETTLEMENTS.size()>=MAX_SETTLEMENTS)return null;
        SettlementRecord created=new SettlementRecord(proposed==null?UUID.randomUUID():proposed,dimension,pos,name,SettlementStatus.ACTIVE,-1,-1,"");SETTLEMENTS.put(created.id(),created);EconomySavedData.markDirty();return created;
    }
    public static boolean restoreSettlement(SettlementRecord record){if(record==null||SETTLEMENTS.size()>=MAX_SETTLEMENTS||SETTLEMENTS.putIfAbsent(record.id(),record)!=null)return false;return true;}
    public static boolean addDemand(LocalDemand demand){prune();if(demand==null||DEMANDS.size()>=MAX_DEMANDS||DEMANDS.putIfAbsent(demand.id(),demand)!=null)return false;EconomySavedData.markDirty();return true;}
    public static boolean restoreDemand(LocalDemand demand){return demand!=null&&DEMANDS.size()<MAX_DEMANDS&&DEMANDS.putIfAbsent(demand.id(),demand)==null;}
    public static List<LocalDemand> forSettlement(UUID id){return DEMANDS.values().stream().filter(d->id.equals(d.settlementId())).sorted(Comparator.comparingLong(LocalDemand::deadlineDay).thenComparing(LocalDemand::id)).toList();}
    public static List<LocalDemand> publicRows(UUID id,UUID player){return forSettlement(id).stream().filter(d->d.status()==DemandStatus.OPEN||player.equals(d.acceptedPlayerId())).limit(8).toList();}
    public static int activeForPlayer(UUID player){return (int)DEMANDS.values().stream().filter(d->d.status()==DemandStatus.ACCEPTED&&player.equals(d.acceptedPlayerId())).count();}
    public static void disable(UUID id){SettlementRecord r=get(id);if(r!=null){r.disable();EconomySavedData.markDirty();}}
    private static void prune(){List<LocalDemand> terminal=DEMANDS.values().stream().filter(d->d.status().terminal()).sorted(Comparator.comparingLong(LocalDemand::createdDay)).toList();for(int i=0;i<Math.max(0,terminal.size()-MAX_HISTORY);i++){LocalDemand d=terminal.get(i);var account=finance.account.AccountManager.getAccounts().get(d.escrowAccountId());if(account==null||(account.getBalance()==0&&account.getFrozenBalance()==0)){DEMANDS.remove(d.id());finance.account.AccountManager.getAccounts().remove(d.escrowAccountId());}}}
    public static void clearDirect(){SETTLEMENTS.clear();DEMANDS.clear();}
}
