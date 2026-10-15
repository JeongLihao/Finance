package finance.market;

import finance.account.AccountManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.stock.Stock;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 做市商分市场子账本 —— nil UUID 总账户之下的虚拟账本。
 *
 * <p>设计（见 npc-market-making-design.md §2.1）：做市商总账户（nil UUID
 * {@code 00000000-0000-0000-0000-000000000000}）按市场分设虚拟子账本
 * （commodity / stock / futures），记录各市场的资金占用与库存/持仓，
 * 独立风控、可独立审计。实现上是一套内存账本 + NBT 持久化，
 * 不动 {@link AccountManager} 主账。</p>
 *
 * <p>铁律（P0 教训的延续）：做市商向玩家的每一笔支付，必须从总账户余额中扣除；
 * 向玩家交付的每一单位资产，必须从子账本库存中扣除。余额/库存不足 → 拒绝报价，
 * 绝不凭空创造。</p>
 *
 * <p>Phase 1 只实现 stock 子账本；COMMODITY 沿用现有实物库存逻辑，
 * FUTURES 子账本为 Phase 3 预留结构。</p>
 */
public final class MarketMakerLedger {

    /** 做市商总账户 UUID（nil UUID，与商品做市商共用）。 */
    public static final UUID MASTER_UUID = new UUID(0, 0);

    /**
     * 期货做市商保证金账户 UUID（Phase 3 约定为 "f002" 账户）。
     *
     * <p>Phase 3 在 {@link finance.futures.MarginManager} 下为该 UUID 建立保证金账户；
     * Phase 4 的央行联动只读该账户（不存在即 Phase 3 未建账时跳过），绝不替 Phase 3 建账。</p>
     */
    public static final UUID FUTURES_MARGIN_UUID =
            UUID.fromString("00000000-0000-0000-0000-00000000f002");

    /** 子账本市场划分。 */
    public enum SubMarket {
        COMMODITY,
        STOCK,
        FUTURES
    }

    /**
     * 预留结果 —— 原子化预留接口的返回值。
     * 余额/库存不足时返回失败原因，调用方据此拒绝报价并转订单簿。
     */
    public record ReserveResult(boolean success, String reason) {
        public static ReserveResult ok() {
            return new ReserveResult(true, "");
        }

        public static ReserveResult fail(String reason) {
            return new ReserveResult(false, reason);
        }
    }

    /** 单笔上限中"目标库存 × 20%"一项的比例（对标商品做市商的 8% 参考库存思路）。 */
    private static final double SINGLE_TRADE_TARGET_RATIO = 0.2D;

    /** 各子账本：symbol → 库存股数（stock）/ 数量。 */
    private static final Map<SubMarket, Map<String, Long>> INVENTORY = new EnumMap<>(SubMarket.class);

    static {
        for (SubMarket market : SubMarket.values()) {
            INVENTORY.put(market, new HashMap<>());
        }
    }

    private MarketMakerLedger() {
    }

    // ================================================================
    // 查询
    // ================================================================

    /** 某子账本某标的的做市商库存（股数/数量），无记录视为 0。 */
    public static synchronized long getInventory(SubMarket market, String symbol) {
        if (market == null || symbol == null) {
            return 0;
        }
        return Math.max(0, INVENTORY.get(market).getOrDefault(symbol, 0L));
    }

    /** 股票子账本库存（股数）。 */
    public static long getStockInventory(String symbol) {
        return getInventory(SubMarket.STOCK, symbol);
    }

    /** 目标库存 = 流通股 × {@link FinanceConfig#stockTargetInventoryRatio()}。 */
    public static long targetInventory(long floatShares) {
        if (floatShares <= 0) {
            return 0;
        }
        double ratio = FinanceConfig.stockTargetInventoryRatio();
        if (ratio <= 0) {
            return 0;
        }
        return Math.max(0, Math.round(floatShares * ratio));
    }

    /** 库存上限 = 目标库存 × {@link FinanceConfig#stockMaxInventoryMultiplier()}；达到上限后只卖不买。 */
    public static long maxInventory(long floatShares) {
        long target = targetInventory(floatShares);
        if (target <= 0) {
            return 0;
        }
        double multiplier = FinanceConfig.stockMaxInventoryMultiplier();
        if (multiplier <= 0) {
            return target;
        }
        return Math.max(target, Math.round(target * multiplier));
    }

    /**
     * 单笔上限 = min(流通股 × 单笔比例，目标库存 × 20%)。
     * 现金可支撑量由调用方按成交价另行计算后取 min。
     */
    public static long maxSingleTradeQty(long floatShares) {
        if (floatShares <= 0) {
            return 0;
        }
        double floatRatio = FinanceConfig.stockMaxTradeFloatRatio();
        long floatCap = floatRatio <= 0 ? 0 : (long) (floatShares * floatRatio);
        long targetCap = (long) (targetInventory(floatShares) * SINGLE_TRADE_TARGET_RATIO);
        return Math.max(0, Math.min(floatCap, targetCap));
    }

    // ================================================================
    // 原子化预留（check* 只校验不变更；try* 校验并变更）
    // ================================================================

    /**
     * 校验做市商能否买入（玩家卖出）：库存有 headroom 且总账户余额足够支付。
     * 只校验不变更，失败返回原因。
     */
    public static synchronized ReserveResult checkBuyable(String symbol, long qty, long price, long floatShares) {
        if (symbol == null || symbol.isEmpty()) {
            return ReserveResult.fail("股票代码无效。");
        }
        if (qty <= 0 || price <= 0) {
            return ReserveResult.fail("数量和价格必须为正。");
        }
        long maxInv = maxInventory(floatShares);
        long current = getStockInventory(symbol);
        if (current + qty > maxInv || current > maxInv - qty) {
            return ReserveResult.fail("做市商库存将超过上限（" + maxInv + " 股），暂不接盘。");
        }
        long totalValue;
        try {
            totalValue = Math.multiplyExact(price, qty);
        } catch (ArithmeticException ex) {
            return ReserveResult.fail("交易金额过大。");
        }
        if (AccountManager.getBalance(MASTER_UUID) < totalValue) {
            return ReserveResult.fail("做市商资金不足，暂不接盘。");
        }
        return ReserveResult.ok();
    }

    /**
     * 校验做市商能否卖出（玩家买入）：子账本库存足够。
     * 只校验不变更，失败返回原因。
     */
    public static synchronized ReserveResult checkSellable(String symbol, long qty) {
        if (symbol == null || symbol.isEmpty()) {
            return ReserveResult.fail("股票代码无效。");
        }
        if (qty <= 0) {
            return ReserveResult.fail("数量必须为正。");
        }
        long current = getStockInventory(symbol);
        if (current < qty) {
            return ReserveResult.fail("做市商库存不足（剩余 " + current + " 股）。");
        }
        return ReserveResult.ok();
    }

    /**
     * 做市商买入预留 —— 校验通过后库存 += qty（原子化）。
     * 资金实际扣减由调用方在同一结算流程内完成；若后续结算失败，
     * 调用方必须调用 {@link #releaseBuy} 回滚。
     */
    public static synchronized ReserveResult tryReserveBuy(String symbol, long qty, long price, long floatShares) {
        ReserveResult check = checkBuyable(symbol, qty, price, floatShares);
        if (!check.success()) {
            return check;
        }
        addInventory(SubMarket.STOCK, symbol, qty);
        return ReserveResult.ok();
    }

    /** 回滚一次 {@link #tryReserveBuy} 的库存变动。 */
    public static synchronized void releaseBuy(String symbol, long qty) {
        if (symbol == null || qty <= 0) {
            return;
        }
        Map<String, Long> stocks = INVENTORY.get(SubMarket.STOCK);
        long current = Math.max(0, stocks.getOrDefault(symbol, 0L));
        long updated = Math.max(0, current - qty);
        if (updated == 0) {
            stocks.remove(symbol);
        } else {
            stocks.put(symbol, updated);
        }
        EconomySavedData.markDirty();
    }

    /**
     * 做市商卖出预留 —— 校验通过后库存 -= qty（原子化）。
     * 成交款实际入账由调用方在同一结算流程内完成；若后续结算失败，
     * 调用方必须调用 {@link #releaseSell} 回滚。
     */
    public static synchronized ReserveResult tryReserveSell(String symbol, long qty) {
        ReserveResult check = checkSellable(symbol, qty);
        if (!check.success()) {
            return check;
        }
        Map<String, Long> stocks = INVENTORY.get(SubMarket.STOCK);
        long updated = Math.max(0, stocks.getOrDefault(symbol, 0L) - qty);
        if (updated == 0) {
            stocks.remove(symbol);
        } else {
            stocks.put(symbol, updated);
        }
        EconomySavedData.markDirty();
        return ReserveResult.ok();
    }

    /** 回滚一次 {@link #tryReserveSell} 的库存变动。 */
    public static synchronized void releaseSell(String symbol, long qty) {
        if (symbol == null || qty <= 0) {
            return;
        }
        addInventory(SubMarket.STOCK, symbol, qty);
    }

    /**
     * 直接增加某子账本库存（IPO 预留、反序列化恢复等非交易路径用）。
     * 饱和加法防溢出。
     */
    public static synchronized void addInventory(SubMarket market, String symbol, long qty) {
        if (market == null || symbol == null || symbol.isEmpty() || qty <= 0) {
            return;
        }
        Map<String, Long> book = INVENTORY.get(market);
        long current = Math.max(0, book.getOrDefault(symbol, 0L));
        long updated = current > Long.MAX_VALUE - qty ? Long.MAX_VALUE : current + qty;
        book.put(symbol, updated);
        EconomySavedData.markDirty();
    }

    /**
     * 存量股票建仓 —— 对已上市但无库存的股票，按 fairValue 从做市商总账户
     * 拨款建仓至目标库存。可重复调用，幂等（已有库存则直接返回成功）。
     * 总账户余额不足时返回 false，不抛异常、不阻断调用方流程。
     *
     * @return true = 已有库存或建仓成功；false = 建仓失败（参数非法/余额不足/金额溢出）
     */
    public static synchronized boolean seedStockInventory(Stock stock) {
        if (stock == null) {
            return false;
        }
        String symbol = stock.getSymbol();
        if (symbol == null || symbol.isEmpty()) {
            return false;
        }
        if (getStockInventory(symbol) > 0) {
            return true; // 幂等：已有库存，跳过
        }
        long target = targetInventory(stock.getFloatShares());
        if (target <= 0) {
            return false;
        }
        long fairValue = stock.getFairValue();
        if (fairValue <= 0) {
            return false;
        }
        long cost;
        try {
            cost = Math.multiplyExact(fairValue, target);
        } catch (ArithmeticException ex) {
            return false;
        }
        // 从做市商总账户拨款建仓 —— 建仓款支付给"市场"（无对手方账户），属一次性引导注资；
        // 余额不足则跳过，不阻断服务器启动。
        if (!AccountManager.withdraw(MASTER_UUID, cost)) {
            return false;
        }
        addInventory(SubMarket.STOCK, symbol, target);
        return true;
    }

    // ================================================================
    // 期货子账本净持仓（Phase 3 期货做市商）
    //
    // 与 stock 子账本不同：期货净持仓是有符号的（正=净多头，负=净空头），
    // 以合约 UUID 字符串为键。stock 部分原样不动。
    // ================================================================

    /**
     * 期货做市商在某合约的净持仓（有符号：正=净多头，负=净空头），无记录视为 0。
     */
    public static synchronized long getFuturesNetPosition(UUID contractId) {
        if (contractId == null) {
            return 0;
        }
        return INVENTORY.get(SubMarket.FUTURES).getOrDefault(contractId.toString(), 0L);
    }

    /**
     * 按有符号 delta 调整期货净持仓（饱和运算防溢出）。
     * 净持仓归零时删除条目，保持账本干净。
     */
    public static synchronized void adjustFuturesNetPosition(UUID contractId, long delta) {
        if (contractId == null || delta == 0) {
            return;
        }
        Map<String, Long> book = INVENTORY.get(SubMarket.FUTURES);
        String key = contractId.toString();
        long current = book.getOrDefault(key, 0L);
        long updated;
        try {
            updated = Math.addExact(current, delta);
        } catch (ArithmeticException ex) {
            updated = delta > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        if (updated == 0) {
            book.remove(key);
        } else {
            book.put(key, updated);
        }
        EconomySavedData.markDirty();
    }

    /** 清空某合约的期货净持仓记录（合约到期结算后调用）。 */
    public static synchronized void clearFuturesNetPosition(UUID contractId) {
        if (contractId == null) {
            return;
        }
        if (INVENTORY.get(SubMarket.FUTURES).remove(contractId.toString()) != null) {
            EconomySavedData.markDirty();
        }
    }

    /** 反序列化恢复期货净持仓（含负值；0 视为无记录）。 */
    public static synchronized void restoreFuturesNetPosition(UUID contractId, long netPosition) {
        if (contractId == null) {
            return;
        }
        Map<String, Long> book = INVENTORY.get(SubMarket.FUTURES);
        if (netPosition == 0) {
            book.remove(contractId.toString());
        } else {
            book.put(contractId.toString(), netPosition);
        }
        EconomySavedData.markDirty();
    }

    // ================================================================
    // 持久化支持
    // ================================================================

    /** 返回各子账本库存的深拷贝（序列化用）。 */
    public static synchronized Map<SubMarket, Map<String, Long>> getAllInventories() {
        Map<SubMarket, Map<String, Long>> copy = new EnumMap<>(SubMarket.class);
        for (Map.Entry<SubMarket, Map<String, Long>> entry : INVENTORY.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** 反序列化恢复单条库存记录（非法的市场/数量会被忽略）。 */
    public static synchronized void restoreInventory(SubMarket market, String symbol, long inventory) {
        if (market == null || symbol == null || symbol.isEmpty() || inventory <= 0) {
            return;
        }
        INVENTORY.get(market).put(symbol, inventory);
        EconomySavedData.markDirty();
    }

    /** 清空全部子账本（世界切换/测试重置用）。 */
    public static synchronized void clearDirect() {
        for (Map<String, Long> book : INVENTORY.values()) {
            book.clear();
        }
    }
}
