package finance.stock;

/**
 * 股票定价引擎 P1 单元测试（IDEA 可直接编译验证）。
 * 测试：基本面锚、动量、噪音、均值回归。
 */
public class StockPriceEngineTest {

    public static void main(String[] args) {
        // Test 1: 初始化
        StockPriceEngine engine = new StockPriceEngine("IRON", 100, 100);
        assert engine.getCurrentPrice() == 100 : "初始价格应为 100";
        assert engine.getFairValue() == 100 : "初始 fairValue 应为 100";
        System.out.println("✓ Test 1: 初始化成功");

        // Test 2: 买入推高价格
        engine.recordTrade(100, 10, 100, true);
        long priceAfterBuy = engine.getCurrentPrice();
        assert priceAfterBuy > 100 : "买入后价格应上升，实际: " + priceAfterBuy;
        System.out.println("✓ Test 2: 买入推高价格 (100 -> " + priceAfterBuy + ")");

        // Test 3: 卖出压低价格
        long buyPrice = priceAfterBuy;
        engine.recordTrade(buyPrice, 10, 100, false);
        long priceAfterSell = engine.getCurrentPrice();
        assert priceAfterSell < buyPrice : "卖出后价格应下降，实际: " + priceAfterSell;
        System.out.println("✓ Test 3: 卖出压低价格 (" + buyPrice + " -> " + priceAfterSell + ")");

        // Test 4: 基本面更新
        engine.updateFairValue(1000, 10, 0);
        assert engine.getFairValue() == 100 : "fairValue = 1000/10 = 100";
        long priceAfterUpdate = engine.getCurrentPrice();
        assert priceAfterUpdate <= 100 : "价格应向 fairValue(100) 回归，实际: " + priceAfterUpdate;
        System.out.println("✓ Test 4: 基本面更新 (fairValue=100, price=" + priceAfterUpdate + ")");

        // Test 5: 动量衰减
        double momentumBefore = engine.getTradeMomentum();
        engine.tickMomentum();
        double momentumAfter = engine.getTradeMomentum();
        assert Math.abs(momentumAfter) < Math.abs(momentumBefore)
                : "动量应衰减，衰减前: " + momentumBefore + ", 衰减后: " + momentumAfter;
        System.out.println("✓ Test 5: 动量衰减 (" + momentumBefore + " -> " + momentumAfter + ")");

        // Test 6: 价格夹逼
        engine.setFairValue(100);
        engine.setCurrentPrice(10); // 低于下限（fairValue×0.3 = 30）
        engine.recalculateFromCurrent();
        long priceAfterClamp = engine.getCurrentPrice();
        assert priceAfterClamp >= 30 : "价格应不低于 30（fairValue×0.3），实际: " + priceAfterClamp;
        System.out.println("✓ Test 6: 价格夹逼 (10 -> " + priceAfterClamp + ")");

        System.out.println("\n✅ 所有 P1 测试通过！");
    }
}
