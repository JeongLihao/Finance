package finance.stock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockPriceEngineTest {

    @Test
    void initializesWithCurrentPriceAndFairValue() {
        StockPriceEngine engine = new StockPriceEngine("IRON", 100, 100);

        assertEquals(100, engine.getCurrentPrice());
        assertEquals(100, engine.getFairValue());
    }

    @Test
    void buyTradeRaisesPrice() {
        StockPriceEngine engine = new StockPriceEngine("IRON", 100, 100);

        engine.recordTrade(100, 10, 100, true);

        assertTrue(engine.getCurrentPrice() > 100,
                "买入成交后价格应被推高");
    }

    @Test
    void sellTradeReducesBuyMomentum() {
        StockPriceEngine engine = new StockPriceEngine("IRON", 100, 100);
        engine.recordTrade(100, 10, 100, true);
        long priceAfterBuy = engine.getCurrentPrice();
        double momentumAfterBuy = engine.getTradeMomentum();

        engine.recordTrade(priceAfterBuy, 10, 100, false);

        assertTrue(engine.getTradeMomentum() < momentumAfterBuy,
                "卖出成交后交易动量应从买入压力中回落");
    }

    @Test
    void fairValueUsesRiskAdjustedFundamentals() {
        StockPriceEngine engine = new StockPriceEngine("IRON", 100, 100);

        engine.updateFairValue(1000, 10, 0, 1.0);

        assertEquals(90, engine.getFairValue(),
                "1000 资产 / 10 股，在最高行业景气度下应经过 0.90 风险折扣");
    }

    @Test
    void momentumDecaysOnTick() {
        StockPriceEngine engine = new StockPriceEngine("IRON", 100, 100);
        engine.recordTrade(100, 10, 100, true);
        double momentumBefore = engine.getTradeMomentum();

        engine.tickMomentum();

        assertTrue(Math.abs(engine.getTradeMomentum()) < Math.abs(momentumBefore),
                "tick 后交易动量应衰减");
    }

    @Test
    void priceIsClampedToFairValueBand() {
        StockPriceEngine engine = new StockPriceEngine("IRON", 100, 100);
        engine.setFairValue(100);
        engine.setCurrentPrice(10);

        engine.recalculateFromCurrent();

        assertTrue(engine.getCurrentPrice() >= 45,
                "价格不应低于 fairValue * 0.45 的下限");
    }
}
