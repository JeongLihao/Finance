package finance.data.serializer;

import finance.account.AccountManager;
import finance.commodity.CommodityRegistry;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.settlement.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.util.UUID;

public final class SettlementDataSerializer {
    public static final String ROOT="Settlements";
    public static final int VERSION=1;
    private SettlementDataSerializer(){}
    public static void save(CompoundTag root){
        CompoundTag data=new CompoundTag();data.putInt("Version",VERSION);ListTag records=new ListTag();
        for(SettlementRecord s:SettlementManager.settlements().values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",s.id());t.putString("Dimension",s.dimensionId());t.putLong("Anchor",s.anchor().asLong());
            t.putString("Name",s.displayName());t.putString("Status",s.status().name());t.putLong("LastDemandDay",s.lastDemandDay());
            t.putLong("LastEventDay",s.lastEventDay());t.putString("LastEventKey",s.lastEventKey());t.putLong("CasualtyDay",s.casualtyDay());t.putInt("CasualtyCount",s.casualtyCount());
            ListTag contributions=new ListTag();s.contributions().forEach((id,points)->{CompoundTag c=new CompoundTag();c.putUUID("Player",id);c.putInt("Points",points);contributions.add(c);});
            t.put("Contributions",contributions);records.add(t);
        }
        data.put("Records",records);ListTag demands=new ListTag();
        for(LocalDemand d:SettlementManager.demands().values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",d.id());t.putUUID("Settlement",d.settlementId());t.putUUID("Escrow",d.escrowAccountId());
            t.putString("Commodity",d.commodityId());t.putString("Theme",d.theme());t.putInt("Quantity",d.quantity());t.putLong("Reward",d.reward());
            t.putLong("Created",d.createdDay());t.putLong("Deadline",d.deadlineDay());t.putString("Status",d.status().name());if(d.acceptedPlayerId()!=null)t.putUUID("Player",d.acceptedPlayerId());
            ListTag ops=new ListTag();for(String op:d.operations())ops.add(StringTag.valueOf(op));t.put("Operations",ops);demands.add(t);
        }
        data.put("Demands",demands);root.put(ROOT,data);
    }
    public static void load(CompoundTag root){
        SettlementManager.clearDirect();if(!root.contains(ROOT,Tag.TAG_COMPOUND))return;CompoundTag data=root.getCompound(ROOT);boolean invalid=data.getInt("Version")!=VERSION;
        ListTag records=data.getList("Records",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(records.size(),SettlementManager.MAX_SETTLEMENTS);i++){CompoundTag t=records.getCompound(i);try{
            UUID id=NbtDataSupport.readUuidOrNull(t,"Id");String dimension=t.getString("Dimension"),name=t.getString("Name");SettlementStatus status=NbtDataSupport.safeEnum(SettlementStatus.class,t.getString("Status"),null);
            if(id==null||dimension.isBlank()||name.isBlank()||status==null||t.getLong("LastDemandDay") < -1
                    ||t.getLong("LastEventDay") < -1||t.getLong("CasualtyDay") < -1
                    ||t.getInt("CasualtyCount") < 0||t.getInt("CasualtyCount") > 100){invalid=true;continue;}
            SettlementRecord s=new SettlementRecord(id,dimension,BlockPos.of(t.getLong("Anchor")),name,status,t.getLong("LastDemandDay"),t.getLong("LastEventDay"),t.getString("LastEventKey"));
            s.restoreCasualties(t.getLong("CasualtyDay"),t.getInt("CasualtyCount"));ListTag cs=t.getList("Contributions",Tag.TAG_COMPOUND);
            if(cs.size()>SettlementRecord.MAX_CONTRIBUTORS)invalid=true;
            for(int c=0;c<Math.min(cs.size(),SettlementRecord.MAX_CONTRIBUTORS);c++){CompoundTag ct=cs.getCompound(c);UUID player=NbtDataSupport.readUuidOrNull(ct,"Player");int points=ct.getInt("Points");if(player==null||points<=0||points>500){invalid=true;continue;}s.restoreContribution(player,points);}
            if(!SettlementManager.restoreSettlement(s))invalid=true;
        }catch(RuntimeException ex){invalid=true;}}
        ListTag demands=data.getList("Demands",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(demands.size(),SettlementManager.MAX_DEMANDS);i++){CompoundTag t=demands.getCompound(i);try{
            UUID id=NbtDataSupport.readUuidOrNull(t,"Id"),settlement=NbtDataSupport.readUuidOrNull(t,"Settlement"),escrow=NbtDataSupport.readUuidOrNull(t,"Escrow");DemandStatus status=NbtDataSupport.safeEnum(DemandStatus.class,t.getString("Status"),null);String commodity=t.getString("Commodity");
            if(id==null||settlement==null||escrow==null||SettlementManager.get(settlement)==null||CommodityRegistry.getCommodity(commodity)==null||status==null){invalid=true;continue;}
            LocalDemand d=new LocalDemand(id,settlement,commodity,t.getString("Theme"),t.getInt("Quantity"),t.getLong("Reward"),escrow,t.getLong("Created"),t.getLong("Deadline"),status,NbtDataSupport.readUuidOrNull(t,"Player"));
            ListTag ops=t.getList("Operations",Tag.TAG_STRING);for(int op=Math.max(0,ops.size()-LocalDemand.MAX_OPERATION_KEYS);op<ops.size();op++)d.restoreOperation(ops.getString(op));
            var account=AccountManager.getAccounts().get(escrow);long balance=account==null?-1:account.getBalance();boolean shouldFund=status==DemandStatus.OPEN||status==DemandStatus.ACCEPTED;
            if((shouldFund&&balance!=d.reward())||(!shouldFund&&balance!=0)){d.quarantine();invalid=true;}if(!SettlementManager.restoreDemand(d))invalid=true;
        }catch(RuntimeException ex){invalid=true;}}
        if(records.size()>SettlementManager.MAX_SETTLEMENTS||demands.size()>SettlementManager.MAX_DEMANDS)invalid=true;
        if(invalid)ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.SETTLEMENT,ModuleRunState.PAUSED,"settlement save invariant failed",0);
    }
}
