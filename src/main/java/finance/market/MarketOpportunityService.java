package finance.market;

import finance.commodity.CommodityInventoryManager;
import finance.contract.*;

import java.util.Comparator;
import java.util.function.ToIntFunction;

/** Small cached public market summary; it never exposes player/order identities. */
public final class MarketOpportunityService {
    private static long cachedDay=Long.MIN_VALUE;private static BaseSummary cached=BaseSummary.EMPTY;
    private MarketOpportunityService(){}
    public static synchronized OpportunitySummary summary(long day,ToIntFunction<String> deliverableAmount){
        if(day!=cachedDay){cachedDay=day;cached=build();}
        FinanceContract deliverable=ContractManager.contracts().values().stream().filter(c->c.status()==ContractStatus.OPEN&&deliverableAmount!=null&&deliverableAmount.applyAsInt(c.commodityId())>=c.requiredQuantity()).max(Comparator.comparingLong(FinanceContract::rewardAmount)).orElse(null);
        return new OpportunitySummary(cached.shortageCommodity,cached.shortageStock,cached.contractCommodity,cached.contractReward,deliverable==null?"":deliverable.commodityId(),deliverable==null?0:deliverable.rewardAmount(),cached.moverCommodity,cached.moverChange);
    }
    public static synchronized void invalidate(){cachedDay=Long.MIN_VALUE;cached=BaseSummary.EMPTY;}
    private static BaseSummary build(){var shortage=NpcMarketMaker.getAllMarketPrices().values().stream().map(p->new StockRow(p.getCommodityId(),CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID,p.getCommodityId()))).min(Comparator.comparingInt(StockRow::stock)).orElse(null);FinanceContract best=ContractManager.contracts().values().stream().filter(c->c.status()==ContractStatus.OPEN).max(Comparator.comparingLong(FinanceContract::rewardAmount)).orElse(null);MarketPrice mover=NpcMarketMaker.getAllMarketPrices().values().stream().max(Comparator.comparingDouble(p->Math.abs(p.getDayChange()))).orElse(null);return new BaseSummary(shortage==null?"":shortage.id,shortage==null?0:shortage.stock,best==null?"":best.commodityId(),best==null?0:best.rewardAmount(),mover==null?"":mover.getCommodityId(),mover==null?0:mover.getDayChange());}
    private record StockRow(String id,int stock){}private record BaseSummary(String shortageCommodity,int shortageStock,String contractCommodity,long contractReward,String moverCommodity,double moverChange){private static final BaseSummary EMPTY=new BaseSummary("",0,"",0,"",0);}
    public record OpportunitySummary(String shortageCommodity,int shortageStock,String bestContractCommodity,long bestContractReward,String deliverableCommodity,long deliverableReward,String moverCommodity,double moverChange){}
}
