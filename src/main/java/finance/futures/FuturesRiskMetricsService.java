package finance.futures;

import java.math.BigInteger;
import java.util.*;

/** Aggregate-only derivatives risk metrics; it never exposes another player's private positions. */
public final class FuturesRiskMetricsService {
    private FuturesRiskMetricsService(){}
    public static Snapshot snapshot(){BigInteger notional=BigInteger.ZERO,margin=BigInteger.ZERO;long totalOi=0,maxContractOi=0,maxAccountExposure=0;int calls=0,liquidating=0,defaults=0;
        for(FuturesContract c:FuturesMarketManager.contracts().values()){long oi=FuturesMarketManager.openInterest(c.id());totalOi=saturated(totalOi,oi);maxContractOi=Math.max(maxContractOi,oi);long price=FuturesMarketManager.riskPrice(c.id());if(price>0)notional=notional.add(BigInteger.valueOf(price).multiply(BigInteger.valueOf(c.contractSize())).multiply(BigInteger.valueOf(oi)));}
        for(MarginAccount a:MarginManager.accounts().values()){margin=margin.add(BigInteger.valueOf(a.cashBalance()));if(a.riskStatus()==MarginRiskStatus.MARGIN_CALL)calls++;else if(a.riskStatus()==MarginRiskStatus.LIQUIDATING)liquidating++;else if(a.riskStatus()==MarginRiskStatus.DEFAULTED)defaults++;long exposure=0;for(FuturesPosition p:MarginManager.positions().values())if(p.ownerId().equals(a.ownerId())){FuturesContract c=FuturesMarketManager.contract(p.contractId());if(c!=null){long n=FuturesMath.notional(Math.max(1,FuturesMarketManager.riskPrice(c.id())),c.contractSize(),p.quantity());if(n>0)exposure=saturated(exposure,n);}}maxAccountExposure=Math.max(maxAccountExposure,exposure);}
        double concentration=totalOi==0?0:(double)maxContractOi/totalOi*100;return new Snapshot(cap(notional),cap(margin),totalOi,concentration,calls,liquidating,defaults,FuturesClearingService.guaranteeFund(),FuturesClearingService.todayFundUse(),maxAccountExposure);}
    private static long saturated(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}private static long cap(BigInteger x){return x.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();}
    public record Snapshot(long notional,long totalMargin,long openInterest,double largestContractConcentration,int marginCalls,int liquidating,int defaults,long guaranteeFund,long todayFundUse,long maxAccountExposure){}
}
