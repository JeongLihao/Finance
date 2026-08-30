package finance.collateral;

import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import java.math.BigInteger;

public final class CollateralValuationService {
    private CollateralValuationService(){}
    public record Valuation(long unitPrice,long discountedValue,int haircutBps){}
    public static Valuation value(String commodity,int quantity,Integer frozenHaircut){MarketPrice price=NpcMarketMaker.getMarketPrice(commodity);if(price==null||price.getMidPrice()<=0||quantity<=0)return null;int haircut=frozenHaircut==null?haircut(price):Math.max(3000,Math.min(7000,frozenHaircut));BigInteger gross=BigInteger.valueOf(price.getMidPrice()).multiply(BigInteger.valueOf(quantity));long discounted=gross.multiply(BigInteger.valueOf(10000-haircut)).divide(BigInteger.valueOf(10000)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();return discounted<=0?null:new Valuation(price.getMidPrice(),discounted,haircut);}
    private static int haircut(MarketPrice price){double change=Math.abs(price.getDayChange());int volatility=(int)Math.min(3000,Math.round(change*10000));return Math.max(3000,Math.min(7000,4000+volatility));}
    public static int ltvBps(long principal,long value){if(principal<=0)return 0;if(value<=0)return 10000;return BigInteger.valueOf(principal).multiply(BigInteger.valueOf(10000)).divide(BigInteger.valueOf(value)).min(BigInteger.valueOf(10000)).intValue();}
}
