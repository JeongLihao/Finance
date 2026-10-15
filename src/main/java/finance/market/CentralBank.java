package finance.market;

import com.mojang.logging.LogUtils;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.chart.CandlestickService;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.futures.FuturesRiskService;
import finance.futures.MarginAccount;
import finance.futures.MarginManager;
import finance.futures.MarginRiskStatus;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.util.MathUtil;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 中央银行 —— 最后贷款人与最后库存缓冲池。
 *
 * <p>中央银行不直接替玩家成交，也不替代国际市场做市。
 * 正常波动由 {@link NpcMarketMaker} 自己吸收；只有市场现金、库存或价格
 * 进入失控区间时，中央银行才用准备金/储备库存进行调控。</p>
 */
public final class CentralBank {

    /** 中央银行账户 UUID，区别于国际市场 nil UUID。 */
    public static final UUID UUID = new UUID(0L, 1L);

    /** 基础准备金。央行承担战略兜底，而普通国际市场只承担日常交易。 */
    private static final long BASE_RESERVE_CASH = 50_000_000L;

    /** 每种商品战略储备目标。 */
    private static final int STRATEGIC_RESERVE_STOCK = 240_000;

    /** 国际市场现金下限，低于此值说明市场端货币不足。 */
    private static final double MARKET_CASH_FLOOR_RATIO = 0.25;

    /** 国际市场现金上限，高于此值说明市场端货币过剩。 */
    private static final double MARKET_CASH_CEILING_RATIO = 2.50;

    /** 库存进入危机区间才干预，软波动留给市场自己消化。 */
    private static final double STOCK_CRISIS_LOW = 0.35;
    private static final double STOCK_CRISIS_HIGH = 2.50;

    /** 单日最多修复偏离量的一部分，避免央行把价格一键打回基准。 */
    private static final double MAX_DAILY_STOCK_INTERVENTION = 0.35;

    private static int lastStockInterventions = 0;
    private static long lastCashDelta = 0;

    private static final Logger LOGGER = LogUtils.getLogger();

    private CentralBank() {}

    public static void seedIfNeeded() {
        long current = AccountManager.getBalance(UUID);
        if (current < BASE_RESERVE_CASH) {
            // Explicit central-bank issuance used only to establish the configured reserve floor.
            AccountManager.deposit(UUID, BASE_RESERVE_CASH - current);
        }
        for (var commodity : CommodityRegistry.getAllCommodities()) {
            String commodityId = commodity.getId();
            int currentStock = CommodityInventoryManager.getCommodityAmount(UUID, commodityId);
            if (currentStock < STRATEGIC_RESERVE_STOCK) {
                CommodityInventoryManager.setCommodity(UUID, commodityId, STRATEGIC_RESERVE_STOCK);
            }
        }
    }

    /**
     * 每日政策操作：先调库存，再调市场端现金，最后做跨市场做市商健康度检查（Phase 4）。
     *
     * <p>库存调控会实际在“国际市场库存池”和“央行储备库存池”之间转移商品；
     * 现金调控会在“国际市场账户”和“央行账户”之间转移货币。央行账户资金不足时，
     * 测试版会补足基础准备金，代表央行扩表。
     * 跨市场健康度检查不动上面的商品兜底逻辑，只追加期货保证金/股票库存/贷款还款检查。</p>
     */
    public static void dailyIntervention() {
        seedIfNeeded();
        lastStockInterventions = 0;
        lastCashDelta = 0;
        for (MarketPrice mp : NpcMarketMaker.getAllMarketPrices().values()) {
            interveneStock(mp);
        }
        interveneMarketCash();
        interveneCrossMarket();
        EconomySavedData.markDirty();
    }

    /**
     * Phase 4：跨市场做市商健康度检查（每日一次，随 dailyIntervention）。
     * <ul>
     *   <li>做市商总账户现金低于下限 → 沿用现有 {@link #interveneMarketCash} 注资逻辑（已在上方执行）；</li>
     *   <li>期货做市商保证金（f002 账户）风险指标跌破维持线 → 央行紧急贷款注资＋记账＋告警；
     *       强制降仓入口 Phase 3 尚未提供可调用的静态方法，本期只做贷款＋告警；</li>
     *   <li>股票某标的库存触上限 → 只告警不干预；</li>
     *   <li>有未偿紧急贷款且总账户余额健康 → 按配置比例划扣还款。</li>
     * </ul>
     */
    private static void interveneCrossMarket() {
        if (!FinanceConfig.cbMmHealthCheckEnabled()) {
            return;
        }
        repayEmergencyLoan();
        checkFuturesMarginRisk();
        checkStockInventoryCap();
    }

    /** 未偿紧急贷款还款：做市商总账户余额健康时才扣，绝不把余额扣到健康线以下。 */
    private static void repayEmergencyLoan() {
        long outstanding = CentralBankLoanLedger.outstanding(MarketMakerLedger.SubMarket.FUTURES);
        if (outstanding <= 0) {
            return;
        }
        long floor = NpcMarketMaker.getInitialNpcBalance();
        long masterCash = AccountManager.getBalance(MarketMakerLedger.MASTER_UUID);
        long repay = computeRepayAmount(masterCash, floor, outstanding, FinanceConfig.cbMmLoanRepaymentBps());
        if (repay <= 0) {
            return;
        }
        if (AccountManager.moveFunds(MarketMakerLedger.MASTER_UUID, UUID, repay)) {
            CentralBankLoanLedger.repay(MarketMakerLedger.SubMarket.FUTURES, repay);
            AccountManager.addTransactionRecord(new TransactionRecord(
                    MarketMakerLedger.MASTER_UUID, UUID, repay,
                    TransactionType.CENTRAL_BANK_MM_LOAN_REPAY,
                    MarketMakerLedger.MASTER_UUID, "做市商紧急贷款还款", 1));
            lastCashDelta += repay;
        }
    }

    /**
     * 还款额 = min(未偿, max(0, 余额 - 健康线) × 比例)，溢出安全。
     * 包级可见，供单元测试。
     */
    static long computeRepayAmount(long masterCash, long floor, long outstanding, int bps) {
        if (outstanding <= 0 || bps <= 0 || masterCash <= floor) {
            return 0;
        }
        long surplus = masterCash - floor;
        long byRatio;
        try {
            byRatio = Math.multiplyExact(surplus, bps) / 10_000;
        } catch (ArithmeticException ex) {
            byRatio = Long.MAX_VALUE; // 溢出时按"尽量多还"处理，外层再取 min(未偿)
        }
        return Math.min(outstanding, Math.max(0, byRatio));
    }

    /**
     * 期货做市商保证金风险检查。
     * f002 账户不存在（Phase 3 未建账）则跳过；风险指标跌破维持线时发放紧急贷款并记账。
     */
    private static void checkFuturesMarginRisk() {
        UUID mm = MarketMakerLedger.FUTURES_MARGIN_UUID;
        MarginAccount account = MarginManager.accounts().get(mm);
        if (account == null) {
            return; // Phase 3 尚未建账，跳过
        }
        MarginRiskStatus status = account.riskStatus();
        boolean belowMaintenance = status == MarginRiskStatus.MARGIN_CALL
                || status == MarginRiskStatus.LIQUIDATING
                || status == MarginRiskStatus.DEFAULTED;
        if (!belowMaintenance) {
            return;
        }
        long equity = FuturesRiskService.equity(mm);
        long initialRequirement = MarginManager.initialRequirement(mm);
        long loan = computeEmergencyLoanAmount(equity, initialRequirement, FinanceConfig.cbMmEmergencyLoanCap());
        if (loan <= 0) {
            LOGGER.warn("[央行联动] 期货做市商保证金风险 {} 但无需注资（权益={}，初始保证金要求={}）",
                    status, equity, initialRequirement);
            return;
        }
        // 资金路径：央行账户 → f002 现金账户 → f002 保证金账户，全程走真实账户划转，不印钱。
        ensureReserveCash(loan);
        if (!AccountManager.moveFunds(UUID, mm, loan)) {
            LOGGER.warn("[央行联动] 期货做市商紧急贷款 {} 划转失败（央行余额={}）", loan, AccountManager.getBalance(UUID));
            return;
        }
        if (!MarginManager.deposit(mm, loan)) {
            // deposit 内部已回滚现金账户；把钱退回央行，避免账实不符。
            AccountManager.moveFunds(mm, UUID, loan);
            LOGGER.warn("[央行联动] 期货做市商紧急贷款 {} 转入保证金失败，已退回央行", loan);
            return;
        }
        CentralBankLoanLedger.recordLoan(MarketMakerLedger.SubMarket.FUTURES, loan,
                CandlestickService.currentMcDay());
        AccountManager.addTransactionRecord(new TransactionRecord(
                UUID, mm, loan, TransactionType.CENTRAL_BANK_MM_LOAN, mm, "做市商紧急贷款", 1));
        lastCashDelta -= loan;
        // 强制降仓：Phase 3 未提供可调用的静态减仓入口（约定访问器只有 getQuote/getNetPosition/isPaused），
        // 本期只做贷款＋告警，不碰 Phase 3 的成交路径。
        LOGGER.warn("[央行联动] 期货做市商保证金跌破维持线（{}），已发放紧急贷款 {}（未偿 {}）；"
                        + "强制降仓待 Phase 3 减仓入口就绪后接管。",
                status, loan, CentralBankLoanLedger.outstanding(MarketMakerLedger.SubMarket.FUTURES));
    }

    /**
     * 紧急贷款额 = min(补足到初始保证金的缺口, 配置上限)；缺口为正则贷。
     * 包级可见，供单元测试。
     */
    static long computeEmergencyLoanAmount(long equity, long initialRequirement, long cap) {
        if (cap <= 0 || initialRequirement < 0) {
            return 0;
        }
        long shortfall;
        try {
            shortfall = Math.subtractExact(initialRequirement, equity);
        } catch (ArithmeticException ex) {
            shortfall = Long.MAX_VALUE;
        }
        if (shortfall <= 0) {
            return 0;
        }
        return Math.min(shortfall, cap);
    }

    /**
     * 股票做市商库存触顶检查：只告警不干预。
     * 股票无实物交割压力，触顶后做市商自动只卖不买；触顶标的会同时暴露给管理后台总览。
     */
    private static void checkStockInventoryCap() {
        for (Stock stock : StockMarketManager.getListedStocks()) {
            String symbol = stock.getSymbol();
            long max = MarketMakerLedger.maxInventory(stock.getFloatShares());
            if (max <= 0) {
                continue;
            }
            long inventory = MarketMakerLedger.getStockInventory(symbol);
            if (inventory >= max) {
                LOGGER.warn("[央行联动] 股票做市商库存触及上限：{} 库存 {}/{} 股（只卖不买），央行不干预",
                        symbol, inventory, max);
            }
        }
    }

    private static void interveneStock(MarketPrice mp) {
        String commodityId = mp.getCommodityId();
        long target = MarketPrice.REFERENCE_STOCK;
        long marketStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodityId);

        if (marketStock > Math.round(target * STOCK_CRISIS_HIGH)) {
            long surplus = marketStock - target;
            int qty = safeInt(Math.round(surplus * MAX_DAILY_STOCK_INTERVENTION));
            if (qty <= 0) return;

            long price = Math.max(1, mp.getBidPrice());
            long payment = MathUtil.multiplyExactOrNegative1(price, qty);
            ensureReserveCash(payment);
            if (payment > 0 && AccountManager.moveFunds(UUID, NpcMarketMaker.NPC_UUID, payment)) {
                if (!transferCommodity(NpcMarketMaker.NPC_UUID, UUID, commodityId, qty)) {
                    AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, UUID, payment);
                    return;
                }
                mp.recomputePrice(marketStock - qty);
                lastStockInterventions++;
                lastCashDelta -= payment;
            }
            return;
        }

        if (marketStock < Math.round(target * STOCK_CRISIS_LOW)) {
            long shortage = target - marketStock;
            int wanted = safeInt(Math.round(shortage * MAX_DAILY_STOCK_INTERVENTION));
            if (wanted <= 0) return;

            int reserve = CommodityInventoryManager.getCommodityAmount(UUID, commodityId);
            int qty = Math.min(wanted, reserve);
            if (qty <= 0) {
                // 极端短缺时建立少量战略储备投放，防止市场完全断货。
                qty = Math.max(1, wanted / 2);
            } else {
                if (!transferCommodity(UUID, NpcMarketMaker.NPC_UUID, commodityId, qty)) return;
            }
            if (reserve <= 0 && !CommodityInventoryManager.addCommodity(
                    NpcMarketMaker.NPC_UUID, commodityId, qty)) return; // explicit emergency commodity issuance
            long price = Math.max(1, mp.getAskPrice());
            long receipt = MathUtil.multiplyExactOrNegative1(price, qty);
            if (receipt > 0) {
                long marketCash = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
                long paid = Math.min(marketCash, receipt);
                if (paid > 0 && AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, UUID, paid)) {
                    lastCashDelta += paid;
                }
            }
            mp.recomputePrice(marketStock + qty);
            lastStockInterventions++;
        }
    }

    private static void interveneMarketCash() {
        long targetCash = NpcMarketMaker.getInitialNpcBalance();
        long marketCash = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        long floor = Math.round(targetCash * MARKET_CASH_FLOOR_RATIO);
        long ceiling = Math.round(targetCash * MARKET_CASH_CEILING_RATIO);

        if (marketCash < floor) {
            long injection = Math.max(1, (targetCash - marketCash) / 3);
            ensureReserveCash(injection);
            if (AccountManager.moveFunds(UUID, NpcMarketMaker.NPC_UUID, injection)) {
                lastCashDelta -= injection;
            }
        } else if (marketCash > ceiling) {
            long withdrawal = Math.max(1, (marketCash - targetCash) / 3);
            if (AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, UUID, withdrawal)) {
                lastCashDelta += withdrawal;
            }
        }
    }

    public static String getLastInterventionSummary() {
        long loanOutstanding = CentralBankLoanLedger.outstanding(MarketMakerLedger.SubMarket.FUTURES);
        return "库存干预 " + lastStockInterventions + " 项，央行现金净变化 " + lastCashDelta
                + "，做市商紧急贷款未偿 " + loanOutstanding
                + "，票据最近投放 " + finance.fixedincome.CentralBankBillManager.lastPolicyIssuance();
    }

    private static void ensureReserveCash(long amount) {
        if (amount <= 0) return;
        long current = AccountManager.getBalance(UUID);
        if (current < amount) {
            AccountManager.deposit(UUID, Math.max(BASE_RESERVE_CASH, amount - current));
        }
    }

    private static int safeInt(long value) {
        if (value <= 0) return 0;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static boolean transferCommodity(java.util.UUID from, java.util.UUID to,
                                               String commodityId, int quantity) {
        if (quantity <= 0 || CommodityInventoryManager.getCommodityAmount(from, commodityId) < quantity
                || !CommodityInventoryManager.canAddCommodity(to, commodityId, quantity)) return false;
        if (!CommodityInventoryManager.removeCommodity(from, commodityId, quantity)) return false;
        if (CommodityInventoryManager.addCommodity(to, commodityId, quantity)) return true;
        CommodityInventoryManager.addCommodity(from, commodityId, quantity);
        return false;
    }
}
