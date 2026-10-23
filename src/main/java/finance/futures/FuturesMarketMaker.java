package finance.futures;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.chart.CandlestickService;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.feedback.FeedbackAudience;
import finance.feedback.FeedbackSeverity;
import finance.feedback.WorldEconomyEvent;
import finance.feedback.WorldEconomyFeedbackService;
import finance.feedback.WorldFeedbackType;
import finance.market.MarketMakerLedger;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.policy.MonetaryPolicyService;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 期货做市商（Phase 3，新建）—— 期货市场的中央对手方。
 *
 * <p>设计（见 npc-market-making-design.md §4）：玩家开多 → 做市商开等量空（反之亦然），
 * 净持仓记入 {@link MarketMakerLedger} 的 FUTURES 子账本。报价锚为理论期货价
 * {@code F = 现货中间价 × (1 + 无风险利率 × 剩余天数/365)}，现货中间价取
 * {@link NpcMarketMaker#getMarketPrice(String)}，无风险利率取
 * {@link MonetaryPolicyService#benchmarkRateBasisPoints()}（绝不硬编码）。</p>
 *
 * <p>铁律（P0 教训的延续）：做市商的一切赔付/交割有界 —— 保证金不足拒绝接单
 * （转订单簿排队），到期走现有现金结算流程（赔付上限=已收亏损+保证基金），
 * 绝不凭空印钱、绝不凭空变出商品。</p>
 *
 * <p>锁纪律：撮合路径（{@link #tryFill}、{@link #onContractSettled}）要求调用方持有
 * {@code FuturesMarketManager.class} 锁；内部状态（对敲追踪、冷却、告警）由私有
 * {@code LOCK} 保护，加锁顺序恒为 FuturesMarketManager → LOCK，不反转。</p>
 */
public final class FuturesMarketMaker {

    /**
     * 做市商保证金账户 UUID —— 结算家族 f002。
     * f001（{@link FuturesClearingService#CLEARING_MEMBER_ID}）已被清算占用，
     * 做市商保证金独立开户，风险隔离。
     */
    public static final UUID MARKET_MAKER_ID = MarketMakerLedger.FUTURES_MARGIN_UUID;

    /** 对敲告警最多保留条数（仿 DiagnosticManager 的有界历史）。 */
    public static final int MAX_ALERTS = 64;

    private static final Object LOCK = new Object();

    /** 手动暂停（Phase 4 管理后台用）；熔断为自动暂停，见 {@link #isCircuitTripped(UUID)}。 */
    private static volatile boolean paused;

    /** 玩家 → 对敲追踪事件（窗口内双向成交）。 */
    private static final Map<UUID, Deque<WashEvent>> WASH_EVENTS = new HashMap<>();

    /** 玩家 → 单边报价冷却到期时间（wall millis）。 */
    private static final Map<UUID, Long> WASH_COOLDOWN_UNTIL = new HashMap<>();

    /** 有界对敲告警历史（管理后台可审计）。 */
    private static final List<WashTradeAlert> ALERTS = new ArrayList<>();

    /** 时间源；测试可覆盖以模拟对敲窗口/冷却。 */
    static LongSupplier wallClock = System::currentTimeMillis;

    private FuturesMarketMaker() {}

    // ================================================================
    // 对外 API（Phase 4 GUI 用，方法名固定）
    // ================================================================

    /**
     * 当前做市商报价 {@code {bid, ask}}；无报价（暂停/熔断/无现货价/合约不可交易）返回 null。
     */
    public static long[] getQuote(UUID contractId) {
        synchronized (FuturesMarketManager.class) {
            FuturesContract c = FuturesMarketManager.contract(contractId);
            return computeQuote(c);
        }
    }

    /** 做市商在某合约的净持仓（有符号：正=净多头，负=净空头）。 */
    public static long getNetPosition(UUID contractId) {
        return MarketMakerLedger.getFuturesNetPosition(contractId);
    }

    /** 是否被手动暂停（熔断见 {@link #isCircuitTripped(UUID)}；含管理后台手动暂停状态）。 */
    public static boolean isPaused() {
        if (paused) {
            return true;
        }
        return finance.market.MarketMakerPauseState.isPaused(finance.market.MarketMakerLedger.SubMarket.FUTURES);
    }

    /** 手动暂停/恢复（Phase 4 管理后台服主工具）。 */
    public static void setPaused(boolean value) {
        paused = value;
        EconomySavedData.markDirty();
    }

    /** 某合约当前是否处于熔断（结算参考价偏离理论价超阈值）。 */
    public static boolean isCircuitTripped(UUID contractId) {
        synchronized (FuturesMarketManager.class) {
            FuturesContract c = FuturesMarketManager.contract(contractId);
            if (c == null) {
                return false;
            }
            Long theoretical = computeTheoreticalPrice(c);
            return theoretical != null && circuitTripped(c, theoretical);
        }
    }

    /** 对敲告警历史（有界，最新在末尾）。 */
    public static List<WashTradeAlert> recentAlerts() {
        synchronized (LOCK) {
            return List.copyOf(ALERTS);
        }
    }

    // ================================================================
    // 报价
    // ================================================================

    /**
     * 计算某合约的理论期货价 {@code F = 现货中间价 × (1 + r × 剩余天数/365)}。
     * 无现货价/合约不可交易/剩余天数为 0 时返回 null（不报价）。
     * 调用方必须持有 FuturesMarketManager.class 锁。
     */
    static Long computeTheoreticalPrice(FuturesContract c) {
        if (c == null || !c.canTrade()) {
            return null;
        }
        MarketPrice spot = NpcMarketMaker.getMarketPrice(c.commodityId());
        if (spot == null || spot.getMidPrice() <= 0) {
            return null; // 无现货价时不报价
        }
        long daysLeft = c.maturityDay() - CandlestickService.currentMcDay();
        if (daysLeft <= 0) {
            return null;
        }
        int rateBps = Math.max(0, MonetaryPolicyService.benchmarkRateBasisPoints());
        int annualDays = Math.max(1, FinanceConfig.annualMcDays());
        BigInteger carry = BigInteger.valueOf(spot.getMidPrice())
                .multiply(BigInteger.valueOf(rateBps))
                .multiply(BigInteger.valueOf(daysLeft))
                .divide(BigInteger.valueOf(10_000L).multiply(BigInteger.valueOf(annualDays)));
        BigInteger fair = BigInteger.valueOf(spot.getMidPrice()).add(carry);
        if (fair.signum() <= 0 || fair.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return null;
        }
        return fair.longValue();
    }

    private static boolean circuitTripped(FuturesContract c, long theoretical) {
        if (!FinanceConfig.futuresMmCircuitBreakerEnabled() || theoretical <= 0) {
            return false;
        }
        long reference = FuturesMarketManager.riskPrice(c.id());
        if (reference <= 0) {
            return false; // 无参考价时不熔断，交由保证金检查兜底
        }
        long deviation = Math.abs(reference - theoretical);
        BigInteger limit = BigInteger.valueOf(theoretical)
                .multiply(BigInteger.valueOf(Math.max(0, FinanceConfig.futuresMmCircuitBreakerDeviationBps())))
                .divide(BigInteger.valueOf(10_000));
        return BigInteger.valueOf(deviation).compareTo(limit) > 0;
    }

    /**
     * 计算双向报价。库存偏斜：净持仓逼近上限时报价整体偏移（极限为一个完整价差），
     * 超限方向在 {@link #tryFill} 中硬拒绝。
     * 调用方必须持有 FuturesMarketManager.class 锁。
     */
    private static long[] computeQuote(FuturesContract c) {
        if (paused) {
            return null;
        }
        Long theoretical = computeTheoreticalPrice(c);
        if (theoretical == null) {
            return null;
        }
        long fair = theoretical;
        if (circuitTripped(c, fair)) {
            return null;
        }
        double spread = FinanceConfig.futuresMmSpread();
        if (spread < 0) {
            spread = 0;
        } else if (spread > 1) {
            spread = 1;
        }
        double skew = 0;
        long cap = netPositionCap(c.id());
        long net = MarketMakerLedger.getFuturesNetPosition(c.id());
        if (cap > 0 && net != 0) {
            double fill = Math.max(-1.0, Math.min(1.0, (double) net / (double) cap));
            skew = fill * spread; // 净持仓触及上限时偏斜推到极限
        }
        double bidD = fair * (1.0 - spread - skew);
        double askD = fair * (1.0 + spread - skew);
        if (!(bidD >= 1) || !(askD > bidD) || askD >= Long.MAX_VALUE) {
            return null;
        }
        long tick = Math.max(1, FinanceConfig.futuresMinimumTick());
        long bid = floorToTick((long) Math.floor(bidD), tick);
        long ask = ceilToTick((long) Math.ceil(askD), tick);
        if (bid < 1 || ask <= bid) {
            return null;
        }
        return new long[] {bid, ask};
    }

    private static long floorToTick(long price, long tick) {
        return price - price % tick;
    }

    private static long ceilToTick(long price, long tick) {
        long rem = price % tick;
        if (rem == 0) {
            return price;
        }
        return price > Long.MAX_VALUE - (tick - rem) ? Long.MAX_VALUE : price + (tick - rem);
    }

    /** 净持仓上限 = max(1, round(持仓总量 × 比例))；下限 1 保证冷启动时能提供种子流动性。 */
    static long netPositionCap(UUID contractId) {
        double ratio = FinanceConfig.futuresMmNetPositionRatio();
        if (ratio < 0) {
            ratio = 0;
        } else if (ratio > 1) {
            ratio = 1;
        }
        long openInterest = Math.max(0, FuturesMarketManager.openInterest(contractId));
        long cap = (long) Math.ceil(openInterest * ratio);
        return Math.max(1, cap);
    }

    // ================================================================
    // 接单（中央对手方）
    // ================================================================

    /**
     * 玩家订单无对手盘时，做市商按报价吃下剩余。
     * 保证金检查不通过 → 返回（订单留订单簿）。
     * 调用方必须持有 FuturesMarketManager.class 锁（由 matchOrBook 调用）。
     */
    static void tryFill(FuturesOrder incoming) {
        if (incoming == null || incoming.remainingQuantity() <= 0) {
            return;
        }
        UUID player = incoming.playerId();
        if (player == null || MARKET_MAKER_ID.equals(player)) {
            return; // 自成交检查：做市商不与自身历史订单成交
        }
        UUID contractId = incoming.contractId();
        FuturesContract c = FuturesMarketManager.contract(contractId);
        if (c == null || !c.canTrade()) {
            return;
        }
        ensureCapital();
        FuturesOrderSide playerSide = incoming.side();
        if (isWashCoolingDown(player) && !isNetReducingSide(contractId, playerSide)) {
            return; // 对敲冷却：只报减仓单边
        }
        long[] quote = computeQuote(c);
        if (quote == null) {
            return;
        }
        long price = playerSide == FuturesOrderSide.BUY ? quote[1] : quote[0];
        if (playerSide == FuturesOrderSide.BUY ? incoming.limitPrice() < price : incoming.limitPrice() > price) {
            return; // 玩家限价与做市商报价不交叉 → 留订单簿
        }
        long qty = allowedQuantity(c, playerSide, incoming.remainingQuantity(), price);
        if (qty <= 0) {
            return;
        }
        FuturesOrderSide mmSide = playerSide == FuturesOrderSide.BUY ? FuturesOrderSide.SELL : FuturesOrderSide.BUY;
        if (!FuturesMarketManager.executeMmTrade(incoming, mmSide, price, qty)) {
            return; // 保证金检查不通过 → 留订单簿
        }
        MarketMakerLedger.adjustFuturesNetPosition(contractId, mmSide == FuturesOrderSide.BUY ? qty : -qty);
        trackWashTrade(player, c, playerSide == FuturesOrderSide.BUY, price, qty);
        EconomySavedData.markDirty();
    }

    /**
     * 结合净持仓上限与做市商保证金可支撑量，计算本次可接单量。
     * 净持仓超限后只允许减仓方向（|净持仓| 严格不扩大）。
     */
    private static long allowedQuantity(FuturesContract c, FuturesOrderSide playerSide, long wantedQty, long price) {
        if (c == null || wantedQty <= 0 || price <= 0) {
            return 0;
        }
        long dir = playerSide == FuturesOrderSide.BUY ? -1 : 1; // 做市商净持仓变动方向
        long net = MarketMakerLedger.getFuturesNetPosition(c.id());
        long cap = netPositionCap(c.id());
        long qty;
        try {
            long newNet = Math.addExact(net, Math.multiplyExact(dir, wantedQty));
            if (Math.abs(newNet) <= cap) {
                qty = wantedQty;
            } else if (dir * net < 0) {
                qty = Math.min(wantedQty, Math.addExact(Math.abs(net), cap)); // 减仓方向：允许拉回零点再反向至上限
            } else {
                qty = Math.max(0, cap - Math.abs(net)); // 加仓方向：超限后拒绝
            }
        } catch (ArithmeticException ex) {
            return 0;
        }
        if (qty <= 0) {
            return 0;
        }
        // 做市商侧保证金可支撑量（初始/维持/追加保证金率与玩家相同）
        MarginAccount account = MarginManager.account(MARKET_MAKER_ID);
        if (account == null) {
            return 0;
        }
        long currentRequirement = MarginManager.initialRequirement(MARKET_MAKER_ID);
        long available = account.cashBalance() - account.frozenForOrders() - Math.max(0, currentRequirement);
        long affordable = FuturesMath.maxOpenQuantity(
                Math.max(0, available), price, c.contractSize(), FinanceConfig.futuresInitialMarginBps(), qty);
        return Math.min(qty, affordable);
    }

    /**
     * 做市商保证金账户注资：只从做市商总账户（nil UUID）划转，绝不凭空印钱。
     * 总账户余额不足时按实际可划转额注资，注资不足则后续接单被保证金检查拒绝。
     */
    private static void ensureCapital() {
        long target = Math.max(0, FinanceConfig.futuresMmInitialCapital());
        if (target <= 0) {
            return;
        }
        MarginAccount account = MarginManager.account(MARKET_MAKER_ID);
        long current = account == null ? 0 : account.cashBalance();
        if (current >= target) {
            return;
        }
        long shortfall = target - current;
        long move = Math.min(shortfall, AccountManager.getBalance(MarketMakerLedger.MASTER_UUID));
        if (move <= 0) {
            return;
        }
        if (!AccountManager.withdraw(MarketMakerLedger.MASTER_UUID, move)) {
            return;
        }
        if (!MarginManager.creditCollateral(MARKET_MAKER_ID, move)) {
            AccountManager.deposit(MarketMakerLedger.MASTER_UUID, move); // 回滚
            return;
        }
        AccountManager.addTransactionRecord(new TransactionRecord(
                MarketMakerLedger.MASTER_UUID,
                MARKET_MAKER_ID,
                move,
                TransactionType.FUTURES_MARGIN_DEPOSIT,
                MARKET_MAKER_ID,
                "做市商保证金注资",
                1));
        EconomySavedData.markDirty();
    }

    // ================================================================
    // 对敲检测
    // ================================================================

    /** 对敲追踪事件（玩家视角方向）。 */
    private record WashEvent(UUID contractId, boolean playerBought, long notional, long atMillis) {}

    /** 对敲告警（有界历史，供管理后台审计）。 */
    public record WashTradeAlert(
            UUID playerId, UUID contractId, long mcDay, long tradeCount, long notional, String message) {}

    private static void trackWashTrade(UUID player, FuturesContract c, boolean playerBought, long price, long qty) {
        long notional = FuturesMath.notional(price, c.contractSize(), qty);
        if (notional < 0) {
            notional = Long.MAX_VALUE;
        }
        long now = wallClock.getAsLong();
        long windowMs = Math.max(1, FinanceConfig.futuresMmWashTradeWindowMinutes()) * 60_000L;
        synchronized (LOCK) {
            Deque<WashEvent> events = WASH_EVENTS.computeIfAbsent(player, k -> new ArrayDeque<>());
            while (!events.isEmpty() && now - events.peekFirst().atMillis() > windowMs) {
                events.pollFirst();
            }
            events.addLast(new WashEvent(c.id(), playerBought, notional, now));
            long count = 0, buys = 0, sells = 0, total = 0;
            for (WashEvent e : events) {
                if (!e.contractId().equals(c.id())) {
                    continue;
                }
                count++;
                if (e.playerBought()) {
                    buys++;
                } else {
                    sells++;
                }
                total = total > Long.MAX_VALUE - e.notional() ? Long.MAX_VALUE : total + e.notional();
            }
            if (buys > 0
                    && sells > 0
                    && (count > FinanceConfig.futuresMmWashTradeMaxTrades()
                            || total > FinanceConfig.futuresMmWashTradeMaxNotional())) {
                long cooldownMs = Math.max(1, FinanceConfig.futuresMmWashTradeCooldownMinutes()) * 60_000L;
                WASH_COOLDOWN_UNTIL.put(player, now + cooldownMs);
                long windowMinutes = FinanceConfig.futuresMmWashTradeWindowMinutes();
                String message = "玩家 " + player + " 在 " + windowMinutes + " 分钟内与期货做市商就合约 "
                        + c.code() + " 双向成交 " + count + " 笔（名义金额 " + total + "），疑似对敲，"
                        + "已临时限制为单边报价 " + FinanceConfig.futuresMmWashTradeCooldownMinutes() + " 分钟。";
                if (ALERTS.size() >= MAX_ALERTS) {
                    ALERTS.remove(0);
                }
                ALERTS.add(
                        new WashTradeAlert(player, c.id(), CandlestickService.currentMcDay(), count, total, message));
                // 走仓库现有告警总线（无需在线服务器即可记录，管理后台可见）
                WorldEconomyFeedbackService.record(new WorldEconomyEvent(
                        "wash-trade:" + player,
                        WorldFeedbackType.MAJOR_MARKET_EVENT,
                        FeedbackSeverity.WARNING,
                        c.id().toString(),
                        CandlestickService.currentMcDay(),
                        "",
                        null,
                        "finance.feedback.wash_trade_alert",
                        List.of(player.toString(), c.code(), Long.toString(count)),
                        FeedbackAudience.ADMIN,
                        Set.of(player)));
                EconomySavedData.markDirty();
            }
        }
    }

    private static boolean isWashCoolingDown(UUID player) {
        synchronized (LOCK) {
            Long until = WASH_COOLDOWN_UNTIL.get(player);
            if (until == null) {
                return false;
            }
            if (wallClock.getAsLong() >= until) {
                WASH_COOLDOWN_UNTIL.remove(player);
                return false;
            }
            return true;
        }
    }

    /**
     * 对敲冷却期内是否允许该方向：只允许让做市商净持仓向零回归的方向。
     * 净持仓为零时冷却期内完全不接单，彻底切断对敲往返。
     */
    private static boolean isNetReducingSide(UUID contractId, FuturesOrderSide playerSide) {
        long net = MarketMakerLedger.getFuturesNetPosition(contractId);
        if (net == 0) {
            return false;
        }
        long dir = playerSide == FuturesOrderSide.BUY ? -1 : 1;
        return dir * net < 0;
    }

    // ================================================================
    // 到期交割
    // ================================================================

    /**
     * 合约到期结算后清理做市商状态。
     *
     * <p>交割语义：本模组期货合约均为现金交割（{@code FuturesSettlementType.CASH}），
     * 做市商持仓随 {@link FuturesClearingService} 的标准盯市/到期结算流程现金轧差，
     * 赔付上限=已收亏损+保证基金（永不印钱）；做市商空头即使"缺商品库存"也只赔现金，
     * 不存在凭空变出商品的代码路径。此处清理子账本净持仓，避免过期合约残留污染
     * 后续报价与风控。</p>
     *
     * <p>调用方必须持有 FuturesMarketManager.class 锁（由 finalSettle 调用）。</p>
     */
    static void onContractSettled(UUID contractId) {
        if (contractId == null) {
            return;
        }
        MarketMakerLedger.clearFuturesNetPosition(contractId);
        synchronized (LOCK) {
            for (Deque<WashEvent> events : WASH_EVENTS.values()) {
                events.removeIf(e -> e.contractId().equals(contractId));
            }
        }
        EconomySavedData.markDirty();
    }

    // ================================================================
    // 重置（测试/世界切换）
    // ================================================================

    /** 清空对敲追踪、冷却、告警与暂停状态（不碰持久化与账本）。 */
    public static void resetDirect() {
        paused = false;
        wallClock = System::currentTimeMillis;
        synchronized (LOCK) {
            WASH_EVENTS.clear();
            WASH_COOLDOWN_UNTIL.clear();
            ALERTS.clear();
        }
    }
}
