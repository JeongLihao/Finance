package finance.data.serializer;

import finance.account.AccountManager;
import finance.diagnostic.*;
import finance.exploration.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.util.UUID;

public final class ExplorationDataSerializer {
    public static final String ROOT="Exploration";public static final int VERSION=1;
    private ExplorationDataSerializer(){}
    public static void save(CompoundTag root){CompoundTag data=new CompoundTag();data.putInt("Version",VERSION);ListTag assignments=new ListTag();
        for(ExplorationAssignment a:ExplorationManager.assignments().values()){CompoundTag t=new CompoundTag();t.putUUID("Id",a.id());t.putUUID("Player",a.playerId());t.putUUID("Escrow",a.escrowId());t.putString("Dimension",a.dimensionId());t.putLong("Target",a.target().asLong());t.putString("Type",a.targetType().name());t.putString("Theme",a.theme());t.putLong("Reward",a.reward());t.putLong("Created",a.createdDay());t.putLong("Deadline",a.deadlineDay());t.putString("Status",a.status().name());assignments.add(t);}data.put("Assignments",assignments);
        ListTag cooldowns=new ListTag();ExplorationManager.cooldowns().forEach((player,day)->{CompoundTag t=new CompoundTag();t.putUUID("Player",player);t.putLong("Day",day);cooldowns.add(t);});data.put("Cooldowns",cooldowns);root.put(ROOT,data);}
    public static void load(CompoundTag root){ExplorationManager.clearDirect();if(!root.contains(ROOT,Tag.TAG_COMPOUND))return;CompoundTag data=root.getCompound(ROOT);boolean invalid=data.getInt("Version")!=VERSION;ListTag list=data.getList("Assignments",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(list.size(),ExplorationManager.MAX_ASSIGNMENTS);i++){CompoundTag t=list.getCompound(i);try{UUID id=NbtDataSupport.readUuidOrNull(t,"Id"),player=NbtDataSupport.readUuidOrNull(t,"Player"),escrow=NbtDataSupport.readUuidOrNull(t,"Escrow");ExplorationTargetType type=NbtDataSupport.safeEnum(ExplorationTargetType.class,t.getString("Type"),null);ExplorationStatus status=NbtDataSupport.safeEnum(ExplorationStatus.class,t.getString("Status"),null);if(id==null||player==null||escrow==null||type==null||status==null){invalid=true;continue;}ExplorationAssignment a=new ExplorationAssignment(id,player,escrow,t.getString("Dimension"),BlockPos.of(t.getLong("Target")),type,t.getString("Theme"),t.getLong("Reward"),t.getLong("Created"),t.getLong("Deadline"),status);var account=AccountManager.getAccounts().get(escrow);long balance=account==null?-1:account.getBalance();if(status==ExplorationStatus.ACTIVE?balance!=a.reward():balance!=0){a.quarantine();invalid=true;}if(!ExplorationManager.restore(a))invalid=true;}catch(RuntimeException ex){invalid=true;}}
        ListTag cooldowns=data.getList("Cooldowns",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(cooldowns.size(),ExplorationManager.MAX_COOLDOWNS);i++){CompoundTag t=cooldowns.getCompound(i);UUID player=NbtDataSupport.readUuidOrNull(t,"Player");long day=t.getLong("Day");if(player==null||day<0){invalid=true;continue;}ExplorationManager.restoreCooldown(player,day);}if(list.size()>ExplorationManager.MAX_ASSIGNMENTS||cooldowns.size()>ExplorationManager.MAX_COOLDOWNS)invalid=true;if(invalid)ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.EXPLORATION,ModuleRunState.PAUSED,"exploration save invariant failed",0);}
}
