package finance.data.serializer;

import finance.collateral.*;
import finance.commodity.CommodityRegistry;
import finance.debt.*;
import finance.diagnostic.*;
import net.minecraft.nbt.*;

import java.util.*;

public final class InventoryCollateralDataSerializer {
    public static final String ROOT="InventoryCollateral";private static final int VERSION=2;
    private InventoryCollateralDataSerializer(){}
    public static void save(CompoundTag root){
        CompoundTag data=new CompoundTag();data.putInt("Version",VERSION);data.putInt("Cursor",InventoryCollateralManager.processingCursor());ListTag rows=new ListTag();
        for(var value:InventoryCollateralManager.agreements().values()){
            CompoundTag row=base(value.id(),value.custodyId(),value.commodityId(),value.pledgedQuantity());
            row.putUUID("Company",value.companyId());row.putUUID("Bank",value.bankId());row.putUUID("Loan",value.loanId());
            row.putLong("Created",value.createdDay());row.putLong("InitialUnit",value.initialUnitPrice());row.putLong("InitialValue",value.initialDiscountedValue());
            row.putLong("LastValuation",value.lastValuationDay());row.putLong("CurrentValue",value.currentDiscountedValue());row.putLong("MarginCall",value.marginCallDay());row.putLong("Recovered",value.liquidationRecovered());
            row.putInt("Haircut",value.haircutBps());row.putInt("InitialLtv",value.initialLtvBps());row.putInt("MaintenanceLtv",value.maintenanceLtvBps());row.putInt("LiquidationLtv",value.liquidationLtvBps());row.putString("Status",value.status().name());
            ListTag operations=new ListTag();for(String key:value.operations())operations.add(StringTag.valueOf(key));row.put("Operations",operations);rows.add(row);
        }
        for(var value:InventoryCollateralManager.orphans().values()){CompoundTag row=base(value.id(),value.custodyId(),value.commodityId(),value.quantity());row.putBoolean("Orphan",true);rows.add(row);}
        data.put("Records",rows);root.put(ROOT,data);
    }
    public static void load(CompoundTag root){
        InventoryCollateralManager.clearDirect();if(!root.contains(ROOT,Tag.TAG_COMPOUND))return;CompoundTag data=root.getCompound(ROOT);
        int version=data.getInt("Version");boolean invalid=version<1||version>VERSION;ListTag rows=data.getList("Records",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(rows.size(),InventoryCollateralManager.MAX_AGREEMENTS);i++){
            CompoundTag row=rows.getCompound(i);UUID id=NbtDataSupport.readUuidOrNull(row,"Id"),custody=NbtDataSupport.readUuidOrNull(row,"Custody");String commodity=row.getString("Commodity");int quantity=row.getInt("Quantity");
            try{
                if(id==null||custody==null||CommodityRegistry.getCommodity(commodity)==null||quantity<=0)throw new IllegalArgumentException();
                if(row.getBoolean("Orphan")){InventoryCollateralManager.restoreOrphan(new InventoryCollateralManager.OrphanReservation(id,custody,commodity,quantity));invalid=true;continue;}
                UUID company=NbtDataSupport.readUuidOrNull(row,"Company"),bank=NbtDataSupport.readUuidOrNull(row,"Bank"),loanId=NbtDataSupport.readUuidOrNull(row,"Loan");InventoryCollateralStatus status=NbtDataSupport.safeEnum(InventoryCollateralStatus.class,row.getString("Status"),null);CompanyLoan loan=loanId==null?null:CompanyLoanManager.loans().get(loanId);long recovered=version>=2?row.getLong("Recovered"):0;
                if(company==null||bank==null||loan==null||status==null||recovered<0||!company.equals(loan.companyId())||loan.lenderType()!=LoanLenderType.COMMERCIAL_BANK||!bank.equals(loan.lenderId()))throw new IllegalArgumentException();
                InventoryCollateralAgreement value=new InventoryCollateralAgreement(id,company,bank,loanId,custody,commodity,quantity,row.getLong("Created"),row.getLong("InitialUnit"),row.getLong("InitialValue"),row.getInt("Haircut"),row.getInt("InitialLtv"),row.getInt("MaintenanceLtv"),row.getInt("LiquidationLtv"),status);
                Set<String> operations=new LinkedHashSet<>();ListTag ops=row.getList("Operations",Tag.TAG_STRING);for(int op=Math.max(0,ops.size()-InventoryCollateralAgreement.MAX_OPERATION_KEYS);op<ops.size();op++){String key=ops.getString(op);if(!key.isBlank()&&key.length()<=96)operations.add(key);}
                value.restoreState(row.getLong("LastValuation"),row.getLong("CurrentValue"),row.getLong("MarginCall"),recovered,operations);if(!InventoryCollateralManager.restore(value))throw new IllegalArgumentException();
            }catch(RuntimeException damaged){invalid=true;if(id!=null&&custody!=null&&!commodity.isBlank()&&commodity.length()<=64&&quantity>0)try{InventoryCollateralManager.restoreOrphan(new InventoryCollateralManager.OrphanReservation(id,custody,commodity,quantity));}catch(RuntimeException ignored){}}
        }
        InventoryCollateralManager.restoreProcessingCursor(Math.max(0,data.getInt("Cursor")));if(rows.size()>InventoryCollateralManager.MAX_AGREEMENTS)invalid=true;
        if(invalid)ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.COLLATERAL,ModuleRunState.PAUSED,"collateral save invariant failed; reservations retained",0);
    }
    private static CompoundTag base(UUID id,UUID custody,String commodity,int quantity){CompoundTag row=new CompoundTag();row.putUUID("Id",id);row.putUUID("Custody",custody);row.putString("Commodity",commodity);row.putInt("Quantity",quantity);return row;}
}
