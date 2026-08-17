package finance.governance;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TenderOffer {
    private final UUID id,buyerId,targetCompanyId,escrowId;
    private final String symbol;
    private final long price,targetShares,minShares,startDay,endDay,maxFunds;
    private CapitalActionStatus status;
    private final Map<UUID,Long> accepted=new LinkedHashMap<>();
    private final Map<UUID,Long> acceptedCosts=new LinkedHashMap<>();
    public TenderOffer(UUID id,UUID buyerId,UUID targetCompanyId,UUID escrowId,String symbol,long price,
                       long targetShares,long minShares,long startDay,long endDay,long maxFunds,CapitalActionStatus status){
        this.id=id;this.buyerId=buyerId;this.targetCompanyId=targetCompanyId;this.escrowId=escrowId;
        this.symbol=symbol;this.price=price;this.targetShares=targetShares;this.minShares=minShares;
        this.startDay=startDay;this.endDay=endDay;this.maxFunds=maxFunds;this.status=status;
    }
    public UUID id(){return id;}public UUID buyerId(){return buyerId;}public UUID targetCompanyId(){return targetCompanyId;}
    public UUID escrowId(){return escrowId;}public String symbol(){return symbol;}public long price(){return price;}
    public long targetShares(){return targetShares;}public long minShares(){return minShares;}public long startDay(){return startDay;}
    public long endDay(){return endDay;}public long maxFunds(){return maxFunds;}public CapitalActionStatus status(){return status;}
    public Map<UUID,Long>accepted(){return accepted;}public Map<UUID,Long>acceptedCosts(){return acceptedCosts;}
    public void status(CapitalActionStatus value){status=value;}public boolean accept(UUID holder,long quantity){return accept(holder,quantity,0);}
    public boolean accept(UUID holder,long quantity,long averageCost){long old=accepted.getOrDefault(holder,0L);if(holder==null||quantity<=0||averageCost<0||old>Long.MAX_VALUE-quantity)return false;long next=old+quantity;long oldCost=acceptedCosts.getOrDefault(holder,averageCost);BigInteger weighted=BigInteger.valueOf(old).multiply(BigInteger.valueOf(oldCost)).add(BigInteger.valueOf(quantity).multiply(BigInteger.valueOf(averageCost)));accepted.put(holder,next);acceptedCosts.put(holder,weighted.divide(BigInteger.valueOf(next)).longValue());return true;}
}
