package finance.market.liquidity;

/**
 * 做市商报价时的市场状态快照 —— 策略链的只读输入。
 *
 * <p>字段含义（见 npc-market-making-design.md §2.2）：</p>
 * <ul>
 *   <li>mid：中间价锚（商品 = MarketPrice 中间价，股票 = fairValue）；</li>
 *   <li>lastPrice：最新价（商品 = 中间价，股票 = 现价），用于均值回归与熔断判断；</li>
 *   <li>referencePrice：熔断参考价（商品 = 基准价，股票 = fairValue）；</li>
 *   <li>inventory / targetInventory：做市商当前库存与目标库存；</li>
 *   <li>volatility：近期波动率（对数收益率标准差，无量纲）；</li>
 *   <li>baseSpread：基础价差（商品 5%，股票 2%）；</li>
 *   <li>halted：是否已熔断（暂停报价）。</li>
 * </ul>
 */
public final class MarketState {

    private final long mid;
    private final long lastPrice;
    private final long referencePrice;
    private final long inventory;
    private final long targetInventory;
    private final double volatility;
    private final double baseSpread;
    private final boolean halted;

    public MarketState(
            long mid,
            long lastPrice,
            long referencePrice,
            long inventory,
            long targetInventory,
            double volatility,
            double baseSpread,
            boolean halted) {
        this.mid = mid;
        this.lastPrice = lastPrice;
        this.referencePrice = referencePrice;
        this.inventory = Math.max(0, inventory);
        this.targetInventory = Math.max(0, targetInventory);
        this.volatility = Math.max(0.0, volatility);
        this.baseSpread = Math.max(0.0, baseSpread);
        this.halted = halted;
    }

    public long getMid() {
        return mid;
    }

    public long getLastPrice() {
        return lastPrice;
    }

    public long getInventory() {
        return inventory;
    }

    public double getVolatility() {
        return volatility;
    }

    public double getBaseSpread() {
        return baseSpread;
    }

    public boolean isHalted() {
        return halted;
    }

    /**
     * 库存水位 = 实际库存 / 目标库存；目标 ≤ 0 时视为 1（中性，不偏斜）。
     */
    public double inventoryLevel() {
        if (targetInventory <= 0) {
            return 1.0;
        }
        return (double) inventory / (double) targetInventory;
    }
}
