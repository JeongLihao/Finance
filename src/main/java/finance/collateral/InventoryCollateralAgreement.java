package finance.collateral;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class InventoryCollateralAgreement {
    public static final int MAX_OPERATION_KEYS=64;
    private final UUID id,companyId,bankId,loanId,custodyId;
    private final String commodityId;
    private int pledgedQuantity;
    private final long createdDay,initialUnitPrice,initialDiscountedValue;
    private long lastValuationDay,currentDiscountedValue,marginCallDay=-1,liquidationRecovered;
    private final int haircutBps,initialLtvBps,maintenanceLtvBps,liquidationLtvBps;
    private InventoryCollateralStatus status;
    private final LinkedHashSet<String> operations=new LinkedHashSet<>();
    public InventoryCollateralAgreement(UUID id,UUID companyId,UUID bankId,UUID loanId,UUID custodyId,
                                        String commodityId,int pledgedQuantity,long createdDay,long initialUnitPrice,
                                        long initialDiscountedValue,int haircutBps,int initialLtvBps,
                                        int maintenanceLtvBps,int liquidationLtvBps,InventoryCollateralStatus status){
        if(id==null||companyId==null||bankId==null||loanId==null||custodyId==null||commodityId==null
                ||commodityId.isBlank()||commodityId.length()>64||pledgedQuantity<=0||createdDay<0
                ||initialUnitPrice<=0||initialDiscountedValue<=0||haircutBps<3000||haircutBps>7000
                ||initialLtvBps<=0||initialLtvBps>6000||maintenanceLtvBps<initialLtvBps
                ||liquidationLtvBps<maintenanceLtvBps||liquidationLtvBps>10000||status==null)
            throw new IllegalArgumentException("invalid collateral agreement");
        this.id=id;this.companyId=companyId;this.bankId=bankId;this.loanId=loanId;this.custodyId=custodyId;
        this.commodityId=commodityId;this.pledgedQuantity=pledgedQuantity;this.createdDay=createdDay;
        this.initialUnitPrice=initialUnitPrice;this.initialDiscountedValue=initialDiscountedValue;
        this.currentDiscountedValue=initialDiscountedValue;this.lastValuationDay=createdDay;
        this.haircutBps=haircutBps;this.initialLtvBps=initialLtvBps;this.maintenanceLtvBps=maintenanceLtvBps;
        this.liquidationLtvBps=liquidationLtvBps;this.status=status;
    }
    public UUID id(){return id;}public UUID companyId(){return companyId;}public UUID bankId(){return bankId;}public UUID loanId(){return loanId;}public UUID custodyId(){return custodyId;}public String commodityId(){return commodityId;}public int pledgedQuantity(){return pledgedQuantity;}public long createdDay(){return createdDay;}public long initialUnitPrice(){return initialUnitPrice;}public long initialDiscountedValue(){return initialDiscountedValue;}public long lastValuationDay(){return lastValuationDay;}public long currentDiscountedValue(){return currentDiscountedValue;}public long marginCallDay(){return marginCallDay;}public long liquidationRecovered(){return liquidationRecovered;}public int haircutBps(){return haircutBps;}public int initialLtvBps(){return initialLtvBps;}public int maintenanceLtvBps(){return maintenanceLtvBps;}public int liquidationLtvBps(){return liquidationLtvBps;}public InventoryCollateralStatus status(){return status;}public Set<String> operations(){return Set.copyOf(operations);}
    public boolean reservesInventory(){return status==InventoryCollateralStatus.PENDING||status==InventoryCollateralStatus.ACTIVE||status==InventoryCollateralStatus.MARGIN_CALL;}
    public boolean dependsOnCommodity(){return status!=InventoryCollateralStatus.REPAID&&status!=InventoryCollateralStatus.LIQUIDATED;}
    void value(long day,long value){lastValuationDay=Math.max(lastValuationDay,day);currentDiscountedValue=Math.max(0,value);}void status(InventoryCollateralStatus value,long day){status=value;if(value==InventoryCollateralStatus.MARGIN_CALL&&marginCallDay<0)marginCallDay=day;if(value!=InventoryCollateralStatus.MARGIN_CALL)marginCallDay=-1;}void quantity(int value){pledgedQuantity=Math.max(0,value);}void recordRecovery(long amount){if(amount<=0||liquidationRecovered>Long.MAX_VALUE-amount)throw new IllegalArgumentException("recovery overflow");liquidationRecovered+=amount;}public void restoreState(long last,long current,long margin,long recovered,Set<String> keys){lastValuationDay=Math.max(createdDay,last);currentDiscountedValue=Math.max(0,current);marginCallDay=Math.max(-1,margin);liquidationRecovered=Math.max(0,recovered);if(keys!=null)keys.forEach(this::recordOperation);}public boolean hasOperation(String key){return key!=null&&operations.contains(key);}public void recordOperation(String key){if(key==null||key.isBlank()||key.length()>96)return;operations.add(key);while(operations.size()>MAX_OPERATION_KEYS)operations.remove(operations.iterator().next());}
}
