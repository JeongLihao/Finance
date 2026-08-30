package finance.collateral;

import finance.commodity.CommodityInventoryManager;
import finance.data.EconomySavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public final class InventoryCollateralManager {
    public static final int MAX_AGREEMENTS=4096;
    private static final Map<UUID,InventoryCollateralAgreement> AGREEMENTS=new LinkedHashMap<>();
    private static final Map<UUID,OrphanReservation> ORPHANS=new LinkedHashMap<>();
    private static int processingCursor;
    private InventoryCollateralManager(){}
    public static synchronized InventoryCollateralAgreement get(UUID id){return id==null?null:AGREEMENTS.get(id);}
    public static synchronized Map<UUID,InventoryCollateralAgreement> agreements(){return Map.copyOf(AGREEMENTS);}
    public static synchronized boolean register(InventoryCollateralAgreement value){if(value==null||AGREEMENTS.size()>=finance.config.FinanceConfig.maxCollateralAgreements()||AGREEMENTS.putIfAbsent(value.id(),value)!=null)return false;EconomySavedData.markDirty();return true;}
    public static synchronized boolean restore(InventoryCollateralAgreement value){return value!=null&&AGREEMENTS.size()<MAX_AGREEMENTS&&AGREEMENTS.putIfAbsent(value.id(),value)==null;}
    public static synchronized void removePending(UUID id){InventoryCollateralAgreement value=AGREEMENTS.get(id);if(value!=null&&value.status()==InventoryCollateralStatus.PENDING)AGREEMENTS.remove(id);}
    public static synchronized int pledged(UUID custody,String commodity){long total=0;for(var value:AGREEMENTS.values())if(value.reservesInventory()&&value.custodyId().equals(custody)&&value.commodityId().equals(commodity))total+=value.pledgedQuantity();for(var value:ORPHANS.values())if(value.custodyId().equals(custody)&&value.commodityId().equals(commodity))total+=value.quantity();return(int)Math.min(Integer.MAX_VALUE,total);}
    public static synchronized int available(UUID custody,String commodity){return Math.max(0,CommodityInventoryManager.getCommodityAmount(custody,commodity)-pledged(custody,commodity));}
    public static synchronized boolean canRemove(UUID custody,String commodity,int amount){return amount>0&&available(custody,commodity)>=amount;}
    public static synchronized boolean moveToLiquidation(InventoryCollateralAgreement value){
        if(value==null||!value.reservesInventory()||AGREEMENTS.get(value.id())!=value)return false;
        int quantity=value.pledgedQuantity();UUID source=value.custodyId(),liquidation=value.id();
        if(quantity<=0||CommodityInventoryManager.getCommodityAmount(source,value.commodityId())<quantity
                ||!CommodityInventoryManager.canAddCommodity(liquidation,value.commodityId(),quantity))return false;
        InventoryCollateralStatus previous=value.status();value.status(InventoryCollateralStatus.LIQUIDATING,value.lastValuationDay());
        if(!CommodityInventoryManager.removeCommodity(source,value.commodityId(),quantity)){value.status(previous,value.lastValuationDay());return false;}
        if(!CommodityInventoryManager.addCommodity(liquidation,value.commodityId(),quantity)){
            if(!CommodityInventoryManager.addCommodity(source,value.commodityId(),quantity))throw new IllegalStateException("collateral liquidation rollback failed");
            value.status(previous,value.lastValuationDay());return false;
        }
        EconomySavedData.markDirty();return true;
    }
    public static synchronized List<InventoryCollateralAgreement> nextBatch(int limit){
        if(AGREEMENTS.isEmpty()||limit<=0)return List.of();
        int size=AGREEMENTS.size(),count=Math.min(limit,size),start=Math.floorMod(processingCursor,size);
        List<InventoryCollateralAgreement> out=new ArrayList<>(count);int index=0;
        for(var value:AGREEMENTS.values()){if(index++>=start&&out.size()<count)out.add(value);}
        if(out.size()<count)for(var value:AGREEMENTS.values()){if(out.size()>=count)break;out.add(value);}
        processingCursor=(start+count)%size;return List.copyOf(out);
    }
    public static synchronized InventoryCollateralAgreement findOperation(UUID companyId,String operation){if(companyId==null||operation==null)return null;for(var value:AGREEMENTS.values())if(value.companyId().equals(companyId)&&value.hasOperation(operation))return value;return null;}
    public static synchronized long totalRecovery(UUID loanId){long total=0;if(loanId==null)return 0;for(var value:AGREEMENTS.values())if(value.loanId().equals(loanId)){long add=value.liquidationRecovered();if(total>Long.MAX_VALUE-add)return Long.MAX_VALUE;total+=add;}return total;}
    public static synchronized boolean hasLiquidationPending(UUID loanId){if(loanId==null)return false;for(var value:AGREEMENTS.values())if(value.loanId().equals(loanId)&&(value.status()==InventoryCollateralStatus.LIQUIDATING||value.status()==InventoryCollateralStatus.RELEASE_PENDING))return true;return false;}
    public static synchronized List<InventoryCollateralAgreement> visibleTo(UUID companyId,boolean admin,int limit){if(limit<=0)return List.of();List<InventoryCollateralAgreement> out=new ArrayList<>(Math.min(limit,AGREEMENTS.size()));for(var value:AGREEMENTS.values()){if(admin||companyId!=null&&value.companyId().equals(companyId))out.add(value);if(out.size()>=limit)break;}return List.copyOf(out);}
    public static synchronized List<InventoryCollateralAgreement> forCompany(UUID companyId){if(companyId==null)return List.of();List<InventoryCollateralAgreement> out=new ArrayList<>();for(var value:AGREEMENTS.values())if(value.companyId().equals(companyId))out.add(value);return List.copyOf(out);}
    public static synchronized int processingCursor(){return processingCursor;}public static synchronized void restoreProcessingCursor(int value){processingCursor=Math.max(0,value);}
    public static synchronized Map<UUID,OrphanReservation> orphans(){return Map.copyOf(ORPHANS);}public static synchronized void restoreOrphan(OrphanReservation value){if(value!=null&&ORPHANS.size()<MAX_AGREEMENTS)ORPHANS.putIfAbsent(value.id(),value);}public static synchronized void clearDirect(){AGREEMENTS.clear();ORPHANS.clear();processingCursor=0;}
    public record OrphanReservation(UUID id,UUID custodyId,String commodityId,int quantity){public OrphanReservation{if(id==null||custodyId==null||commodityId==null||commodityId.isBlank()||commodityId.length()>64||quantity<=0)throw new IllegalArgumentException("invalid orphan reservation");}}
}
