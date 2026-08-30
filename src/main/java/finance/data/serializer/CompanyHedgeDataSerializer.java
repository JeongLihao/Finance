package finance.data.serializer;

import finance.company.CompanyManager;
import finance.futures.FuturesMarketManager;
import finance.hedge.*;
import net.minecraft.nbt.*;

import java.util.HashSet;
import java.util.UUID;

public final class CompanyHedgeDataSerializer {
    private CompanyHedgeDataSerializer(){}
    public static void save(CompoundTag root){
        ListTag list=new ListTag();for(CompanyHedgeObjective v:CompanyHedgeManager.all()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",v.id());t.putUUID("Company",v.companyId());t.putUUID("Operator",v.operatorId());t.putUUID("Contract",v.contractId());
            t.putString("Commodity",v.commodityId());t.putString("Type",v.type().name());t.putLong("Target",v.targetQuantity());t.putLong("Created",v.createdDay());t.putLong("Deadline",v.deadlineDay());t.putString("Operation",v.operationKey());list.add(t);
        }root.put("CompanyHedges",list);
    }
    public static void load(CompoundTag root){
        CompanyHedgeManager.clearDirect();if(!root.contains("CompanyHedges",Tag.TAG_LIST))return;ListTag list=root.getList("CompanyHedges",Tag.TAG_COMPOUND);HashSet<UUID> ids=new HashSet<>();
        for(int i=0;i<Math.min(CompanyHedgeManager.MAX_OBJECTIVES,list.size());i++){CompoundTag t=list.getCompound(i);UUID id=NbtDataSupport.readUuidOrNull(t,"Id"),company=NbtDataSupport.readUuidOrNull(t,"Company"),operator=NbtDataSupport.readUuidOrNull(t,"Operator"),contract=NbtDataSupport.readUuidOrNull(t,"Contract");HedgeObjectiveType type=NbtDataSupport.safeEnum(HedgeObjectiveType.class,t.getString("Type"),null);
            if(id==null||!ids.add(id)||CompanyManager.getCompany(company)==null||operator==null||FuturesMarketManager.contract(contract)==null||type==null)continue;
            try{CompanyHedgeManager.restore(new CompanyHedgeObjective(id,company,operator,contract,t.getString("Commodity"),type,t.getLong("Target"),t.getLong("Created"),t.getLong("Deadline"),t.getString("Operation")));}catch(IllegalArgumentException ignored){}
        }
    }
}
