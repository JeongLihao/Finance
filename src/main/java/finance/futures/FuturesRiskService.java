package finance.futures;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;

import java.math.BigInteger;
import java.util.*;

/** Server-side margin-call and partial liquidation policy. */
public final class FuturesRiskService {
    private FuturesRiskService(){}
    public static synchronized void evaluateAll(long day){for(UUID owner:List.copyOf(MarginManager.accounts().keySet()))if(!owner.equals(FuturesClearingService.CLEARING_MEMBER_ID))evaluate(owner,day);}
    public static synchronized MarginRiskStatus evaluate(UUID owner,long day){
        MarginAccount a=MarginManager.account(owner);if(a==null||a.riskStatus()==MarginRiskStatus.DEFAULTED)return a==null?MarginRiskStatus.DEFAULTED:a.riskStatus();
        long equity=equity(owner),maintenance=MarginManager.maintenanceRequirement(owner),liquidation=MarginManager.liquidationRequirement(owner);
        if(equity<Math.max(0,liquidation)){a.setRiskStatus(MarginRiskStatus.LIQUIDATING);if(!FuturesMarketManager.cancelForPlayer(owner)){a.setRiskStatus(MarginRiskStatus.DEFAULTED);return a.riskStatus();}liquidate(owner,day);}
        else if(equity<Math.max(0,maintenance)){a.setRiskStatus(MarginRiskStatus.MARGIN_CALL);}
        else a.setRiskStatus(MarginRiskStatus.NORMAL);
        EconomySavedData.markDirty();return a.riskStatus();
    }
    public static synchronized void notifyOnline(net.minecraft.server.MinecraftServer server,long settlementDay){if(server==null||settlementDay<0)return;for(MarginAccount a:MarginManager.accounts().values())if(a.riskStatus()==MarginRiskStatus.MARGIN_CALL&&a.marginCallNotifiedDay()<settlementDay){var player=server.getPlayerList().getPlayer(a.ownerId());if(player!=null){finance.network.GuiFeedbackPacket.send(player,"期货保证金低于维持线：已禁止扩大风险，请入金或平仓");a.markMarginCallNotified(settlementDay);EconomySavedData.markDirty();}}}
    public static long equity(UUID owner){
        MarginAccount account=MarginManager.accounts().get(owner);
        BigInteger value=BigInteger.valueOf(account==null?0:account.cashBalance());
        for(var e:MarginManager.pendingVariations().entrySet())if(e.getKey().ownerId().equals(owner))value=value.add(BigInteger.valueOf(e.getValue()));
        for(FuturesPosition p:MarginManager.positions().values())if(p.ownerId().equals(owner)){FuturesContract c=FuturesMarketManager.contract(p.contractId());long price=FuturesMarketManager.riskPrice(p.contractId());if(c!=null&&price>0)try{value=value.add(BigInteger.valueOf(FuturesMath.signedPnl(p.settlementReferencePrice(),price,c.contractSize(),p.signedQuantity())));}catch(ArithmeticException ex){return Long.MIN_VALUE;}}
        return value.max(BigInteger.valueOf(Long.MIN_VALUE)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    private static void liquidate(UUID owner,long day){
        List<FuturesPosition> positions=MarginManager.positions().values().stream().filter(p->p.ownerId().equals(owner)).sorted(Comparator.comparingLong((FuturesPosition p)->riskContribution(p)).reversed()).toList();
        for(FuturesPosition position:positions){
            while(position.quantity()>0&&equity(owner)<Math.max(0,MarginManager.maintenanceRequirement(owner))){
                FuturesContract c=FuturesMarketManager.contract(position.contractId());long reference=FuturesMarketManager.riskPrice(position.contractId());if(c==null||reference<=0){MarginManager.account(owner).setRiskStatus(MarginRiskStatus.DEFAULTED);return;}
                long price=adversePrice(reference,position.side());FuturesOrderSide ownerSide=position.side()==FuturesSide.LONG?FuturesOrderSide.SELL:FuturesOrderSide.BUY;
                FuturesPosition.Preview ownerPreview=position.preview(ownerSide,1,price,c.contractSize());FuturesPosition system=MarginManager.findPosition(FuturesClearingService.CLEARING_MEMBER_ID,c.id());if(system==null)system=new FuturesPosition(FuturesClearingService.CLEARING_MEMBER_ID,c.id(),0,0,0,0);FuturesPosition.Preview systemPreview=system.preview(ownerSide==FuturesOrderSide.SELL?FuturesOrderSide.BUY:FuturesOrderSide.SELL,1,price,c.contractSize());
                if(!MarginManager.canCommitRiskReduction(owner,c.id(),ownerPreview)||systemPreview==null){MarginManager.account(owner).setRiskStatus(MarginRiskStatus.DEFAULTED);return;}
                long collateral=FuturesMath.margin(Math.max(price,reference),c.contractSize(),1,FinanceConfig.futuresInitialMarginBps());if(collateral<=0||!FuturesClearingService.allocateFundCollateral(collateral)){MarginManager.account(owner).setRiskStatus(MarginRiskStatus.DEFAULTED);return;}if(!MarginManager.canCommit(FuturesClearingService.CLEARING_MEMBER_ID,c.id(),systemPreview,0)){FuturesClearingService.reclaimFundCollateral(collateral);MarginManager.account(owner).setRiskStatus(MarginRiskStatus.DEFAULTED);return;}
                MarginManager.commitRiskReduction(owner,c.id(),ownerPreview);MarginManager.commit(FuturesClearingService.CLEARING_MEMBER_ID,c.id(),systemPreview,0);FuturesMarketManager.recordLiquidation(owner,c,price,1,ownerSide);
                AccountManager.addTransactionRecord(new TransactionRecord(owner,FuturesClearingService.CLEARING_MEMBER_ID,0,TransactionType.FUTURES_LIQUIDATION,owner,c.code(),1));
            }
        }
        long eq=equity(owner),maint=MarginManager.maintenanceRequirement(owner);MarginManager.account(owner).setRiskStatus(eq>=Math.max(0,maint)?MarginRiskStatus.NORMAL:MarginRiskStatus.DEFAULTED);
    }
    private static long riskContribution(FuturesPosition p){FuturesContract c=FuturesMarketManager.contract(p.contractId());return c==null?0:FuturesMath.margin(Math.max(1,FuturesMarketManager.riskPrice(p.contractId())),c.contractSize(),p.quantity(),FinanceConfig.futuresMaintenanceMarginBps());}
    private static long adversePrice(long reference,FuturesSide side){BigInteger slip=BigInteger.valueOf(reference).multiply(BigInteger.valueOf(FinanceConfig.futuresLiquidationSlippageBps())).divide(BigInteger.valueOf(10_000));if(side==FuturesSide.LONG)return Math.max(1,BigInteger.valueOf(reference).subtract(slip).longValue());return BigInteger.valueOf(reference).add(slip).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();}
}
