package finance.market;

import finance.data.EconomySavedData;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * 做市商手动暂停/恢复状态（Phase 4，管理后台服主工具）。
 *
 * <p>按子市场（commodity / stock / futures）记录管理员手动暂停开关，
 * 服务端 toggle＋NBT 持久化（见 {@code MarketDataSerializer}）。报价引擎侧约定：</p>
 * <ul>
 *   <li>Phase 2 的 {@code market.liquidity.QuoteEngine#isHalted} 在判定熔断/暂停时
 *       应一并考虑本状态（commodity / stock）；</li>
 *   <li>Phase 3 的 {@code futures.FuturesMarketMaker#isPaused} 应一并考虑本状态（futures）。</li>
 * </ul>
 * <p>具体接线由合并协调员完成；本类只负责状态的服务端切换与持久化。</p>
 */
public final class MarketMakerPauseState {

    private static final Map<MarketMakerLedger.SubMarket, Boolean> PAUSED =
            new EnumMap<>(MarketMakerLedger.SubMarket.class);

    static {
        for (MarketMakerLedger.SubMarket market : MarketMakerLedger.SubMarket.values()) {
            PAUSED.put(market, Boolean.FALSE);
        }
    }

    private MarketMakerPauseState() {}

    /** 某市场做市商是否被管理员手动暂停。 */
    public static synchronized boolean isPaused(MarketMakerLedger.SubMarket market) {
        if (market == null) {
            return false;
        }
        return Boolean.TRUE.equals(PAUSED.get(market));
    }

    /** 服务端切换（管理后台/指令调用），返回切换后的状态。 */
    public static synchronized boolean toggle(MarketMakerLedger.SubMarket market) {
        if (market == null) {
            return false;
        }
        boolean next = !isPaused(market);
        PAUSED.put(market, next);
        EconomySavedData.markDirty();
        return next;
    }

    /** 快照（序列化用）。 */
    public static synchronized Map<MarketMakerLedger.SubMarket, Boolean> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(PAUSED));
    }

    /** 反序列化恢复。 */
    public static synchronized void restore(MarketMakerLedger.SubMarket market, boolean paused) {
        if (market == null) {
            return;
        }
        PAUSED.put(market, paused);
        EconomySavedData.markDirty();
    }

    /** 全部恢复为运行中（世界切换/测试重置用）。 */
    public static synchronized void clearDirect() {
        for (MarketMakerLedger.SubMarket market : MarketMakerLedger.SubMarket.values()) {
            PAUSED.put(market, Boolean.FALSE);
        }
    }

    /** 市场键（admin GUI / 网络用）→ 子市场；非法返回 null。 */
    public static MarketMakerLedger.SubMarket fromKey(String key) {
        if (key == null) {
            return null;
        }
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "commodity" -> MarketMakerLedger.SubMarket.COMMODITY;
            case "stock" -> MarketMakerLedger.SubMarket.STOCK;
            case "futures" -> MarketMakerLedger.SubMarket.FUTURES;
            default -> null;
        };
    }

    /** 子市场 → 市场键。 */
    public static String toKey(MarketMakerLedger.SubMarket market) {
        if (market == null) {
            return "";
        }
        return switch (market) {
            case COMMODITY -> "commodity";
            case STOCK -> "stock";
            case FUTURES -> "futures";
        };
    }
}
