package finance.stock;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.governance.CorporateActionManager;
import finance.market.MarketMakerLedger;
import finance.market.liquidity.MarketMakerQuotes;
import finance.market.liquidity.QuoteView;
import finance.metrics.EconomyMetricsService;
import finance.marketdata.RecentTradeService;
import finance.marketdata.TradeDirection;
import finance.util.MathUtil;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;

import java.util.*;

/**
 * 股票订单簿管理器 —— 玩家限价单撮合 + 做市商保底流动性。
 *
 * <h3>撮合流程</h3>
 * <ol>
 *   <li>玩家挂单时先冻结资产（BUY 冻结资金 / SELL 冻结股票）</li>
 *   <li>遍历已有订单，寻找同股票、反向的可匹配订单</li>
 *   <li>价格匹配：买方出价 ≥ 卖方要价即可成交</li>
 *   <li>成交价 = 卖方定价，差价作为买方价格改善退还</li>
 *   <li>部分成交：剩余数量作为新订单挂回订单簿</li>
 *   <li>无对手盘时，做市商按 fairValue ± spread 报价成交</li>
 * </ol>
 */
public class StockOrderManager {

    private static final List<StockOrder> ORDERS = new ArrayList<>();

    /** 按股票代码分组，加速撮合 */
    private static final Map<String, List<StockOrder>> ORDERS_BY_SYMBOL = new HashMap<>();

    /** 成交记录（保留最近 500 条） */
    private static final List<StockTrade> TRADE_HISTORY = new ArrayList<>();

    private static final UUID MARKET_MAKER_UUID = new UUID(0, 0); // nil UUID

    /**
     * 做市商单只股票终身接盘上限（股数）。
     * 做市商提供保底流动性，而非无限印钞机；达到上限后不再接盘，
     * 玩家仍可通过订单簿相互交易。配合发行价上限与创始人锁定期，
     * 单只股票做市商印钞上限远低于一次攻击的固定成本。
     */
    public static final long MAX_MARKET_MAKER_BUY_SHARES = 200L;

    // ================================================================
    // 订单操作
    // ================================================================

    /**
     * 玩家挂买单 —— 冻结资金，尝试与卖单撮合，无对手盘时做市商成交。
     */
    public static OrderResult placeBuyOrder(UUID playerId, String symbol, long price, int quantity) {
        if (playerId == null || symbol == null || symbol.isEmpty()) {
            return OrderResult.fail("参数错误。");
        }
        symbol = StockMarketManager.normalizeSymbol(symbol);
        if (quantity <= 0 || price <= 0) {
            return OrderResult.fail("数量和价格必须为正。");
        }

        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) {
            return OrderResult.fail("未知股票: " + symbol);
        }

        // 停牌期间不可挂新单（要约收购进行中）
        if (stock.isTradingHalted()) {
            return OrderResult.fail("该股票停牌中（要约收购进行中），不可挂单。");
        }

        // 冻结资金
        long totalCost = MathUtil.multiplyExactOrNegative1(price, quantity);
        if (totalCost <= 0) {
            return OrderResult.fail("交易金额过大。");
        }
        if (!AccountManager.withdraw(playerId, totalCost)) {
            return OrderResult.fail("余额不足，需要: " + totalCost);
        }

        // 创建订单并尝试撮合
        StockOrder order = new StockOrder(playerId, symbol, StockOrderType.BUY, price, quantity);
        return matchOrder(order, stock);
    }

    /**
     * 玩家挂卖单 —— 冻结股票，尝试与买单撮合，无对手盘时做市商成交。
     */
    public static OrderResult placeSellOrder(UUID playerId, String symbol, long price, int quantity) {
        if (playerId == null || symbol == null || symbol.isEmpty()) {
            return OrderResult.fail("参数错误。");
        }
        symbol = StockMarketManager.normalizeSymbol(symbol);
        if (quantity <= 0 || price <= 0) {
            return OrderResult.fail("数量和价格必须为正。");
        }

        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) {
            return OrderResult.fail("未知股票: " + symbol);
        }

        // 停牌期间不可挂新单（要约收购进行中）
        if (stock.isTradingHalted()) {
            return OrderResult.fail("该股票停牌中（要约收购进行中），不可挂单。");
        }

        // 要约收购方在要约期内不得减持目标公司股份（防"边收购边出货"操纵接受率）
        if (CorporateActionManager.isTenderBuyer(playerId, stock.getCompanyId())) {
            return OrderResult.fail("您有进行中的要约收购，期间不得减持目标公司股份。");
        }

        // 创始人锁定期内不可卖出
        if (stock.isFounderLocked(playerId)) {
            return OrderResult.fail("创始人股份锁定期内不可卖出（剩余 "
                    + stock.getFounderLockupDaysRemaining() + " 天）。");
        }

        // 检查玩家持股
        long holdings = StockPortfolioManager.getHolding(playerId, symbol).getQuantity();
        if (holdings < quantity) {
            return OrderResult.fail("持仓不足，拥有: " + holdings);
        }

        // 扣除股票（锁定）
        if (!StockPortfolioManager.removeHolding(playerId, symbol, quantity)) {
            return OrderResult.fail("持仓不足。");
        }

        // 创建订单并尝试撮合
        StockOrder order = new StockOrder(playerId, symbol, StockOrderType.SELL, price, quantity);
        return matchOrder(order, stock);
    }

    /**
     * 尝试撮合订单：优先与玩家对手盘撮合，无则做市商成交。
     */
    private static OrderResult matchOrder(StockOrder order, Stock stock) {
        int remainingQty = order.getQuantity();

        // 尝试与现有订单撮合：价格优先，同价时间优先。
        List<StockOrder> oppositeOrders = ORDERS_BY_SYMBOL.getOrDefault(order.getSymbol(), new ArrayList<>());
        sortOrderBook(oppositeOrders);
        Iterator<StockOrder> iterator = oppositeOrders.iterator();

        while (iterator.hasNext() && remainingQty > 0) {
            StockOrder opposite = iterator.next();

            // 方向必须相反
            if (opposite.getType() == order.getType()) {
                continue;
            }

            if (opposite.getPlayerId().equals(order.getPlayerId())) {
                continue;
            }

            // 价格必须匹配
            boolean priceMatch = order.getType() == StockOrderType.BUY
                    ? order.getPrice() >= opposite.getPrice()  // 买单出价 ≥ 卖单要价
                    : order.getPrice() <= opposite.getPrice(); // 卖单要价 ≤ 买单出价

            if (!priceMatch) {
                continue;
            }

            // 成交量 = min(订单剩余, 对手盘剩余)
            int tradeQty = Math.min(remainingQty, opposite.getQuantity());

            // 成交价取卖方定价
            long tradePrice = order.getType() == StockOrderType.BUY ? opposite.getPrice() : order.getPrice();
            long buyerLimitPrice = order.getType() == StockOrderType.BUY ? order.getPrice() : opposite.getPrice();
            UUID buyerId = order.getType() == StockOrderType.BUY ? order.getPlayerId() : opposite.getPlayerId();
            UUID sellerId = order.getType() == StockOrderType.BUY ? opposite.getPlayerId() : order.getPlayerId();

            // 执行成交。失败时不推进订单状态，避免账户/持仓和订单数量半成功。
            if (!executeTrade(buyerId, sellerId, order.getSymbol(),
                    tradePrice, tradeQty, stock, buyerLimitPrice, order.getType() == StockOrderType.BUY)) {
                order.setQuantity(remainingQty);
                addOrderDirect(order);
                EconomySavedData.markDirty();
                return OrderResult.ok("部分撮合遇到结算异常，剩余订单已挂入订单簿。");
            }

            remainingQty -= tradeQty;
            opposite.reduceQuantity(tradeQty);

            // 对手盘完全成交，移除
            if (opposite.getQuantity() <= 0) {
                iterator.remove();
                ORDERS.remove(opposite);
            }
        }

        // 还有剩余数量，尝试做市商成交；做市商不接则挂回订单簿。
        if (remainingQty > 0) {
            order.setQuantity(remainingQty);
            OrderResult marketMakerResult = marketMakerTrade(order, stock);
            if (marketMakerResult.success()) {
                return marketMakerResult;
            }
            if (marketMakerResult.terminal()) {
                EconomySavedData.markDirty();
                return marketMakerResult;
            }
            addOrderDirect(order);
            EconomySavedData.markDirty();
            return OrderResult.ok("订单已挂入订单簿，剩余数量: " + order.getQuantity());
        }

        EconomySavedData.markDirty();
        return OrderResult.ok("订单已全部成交。");
    }

    /**
     * 做市商成交 —— 保底流动性，Phase 2 动态报价（mid = fairValue，经策略链调整）。
     *
     * <p>Phase 1 正规化：做市商是"有独立资金账户、不能印钱"的对手方 ——</p>
     * <ul>
     *   <li>做市商买入（玩家卖出）：从 nil UUID 总账户真实扣款，库存记入股票子账本；
     *       余额不足或库存将超上限 → 拒绝，转订单簿；</li>
     *   <li>做市商卖出（玩家买入）：子账本库存必须足够，成交款真实进入总账户；
     *       库存不足 → 拒绝，转订单簿；</li>
     *   <li>单笔上限 = min(流通股 × 1%，目标库存 × 20%，现金可支撑量），
     *       超限部分转订单簿；</li>
     *   <li>MAX_MARKET_MAKER_BUY_SHARES（200 股）保留为终身熔断兜底；</li>
     *   <li>Phase 2：报价走 QuoteEngine（均值回归/库存偏斜/波动率价差），
     *       熔断时暂停报价 → 拒绝，转订单簿，偏离回归后自动恢复。</li>
     * </ul>
     */
    private static OrderResult marketMakerTrade(StockOrder order, Stock stock) {
        // 停牌期间做市商暂停报价（不消耗终身接盘额度）
        if (stock.isTradingPaused()) {
            return OrderResult.fail(stock.isBreakerHalted() ? "熔断中，做市商暂停报价。" : "该股票停牌中，做市商暂停报价。");
        }
        long fairValue = stock.getFairValue();
        if (fairValue <= 0) {
            return refundOrderAssets(order)
                    ? OrderResult.terminalFail("市场行情异常，请稍后重试。")
                    : OrderResult.fail("市场行情异常，资产仍保持锁定并已保留订单。");
        }

        // Phase 2 做市商动态报价：mid=fairValue，库存=股票子账本，波动率=价格快照滚动估计
        String symbol = order.getSymbol();
        QuoteView quote = MarketMakerQuotes.getQuote(MarketMakerQuotes.MARKET_STOCK, symbol);
        if (quote == null || quote.halted()) {
            return OrderResult.fail("做市商暂停报价（市场熔断），已转入订单簿。");
        }
        long bidPrice = quote.bid();
        long askPrice = quote.ask();
        long floatShares = stock.getFloatShares();

        if (order.getType() == StockOrderType.BUY) {
            // 玩家从做市商买入 —— 做市商是卖方：子账本库存必须足够，成交款进入总账户
            long tradePrice = askPrice;
            if (order.getPrice() < tradePrice) {
                return OrderResult.fail("做市商报价未满足。");
            }
            long inventory = MarketMakerLedger.getStockInventory(symbol);
            if (inventory <= 0) {
                return OrderResult.fail("做市商库存不足，已转入订单簿。");
            }
            long cap = Math.min(MarketMakerLedger.maxSingleTradeQty(floatShares), inventory);
            if (cap <= 0) {
                return OrderResult.fail("超过做市商单笔成交上限，已转入订单簿。");
            }
            long fillQty = Math.min(order.getQuantity(), cap);
            MarketMakerLedger.ReserveResult check = MarketMakerLedger.checkSellable(symbol, fillQty);
            if (!check.success()) {
                return OrderResult.fail(check.reason());
            }
            if (!executeTrade(order.getPlayerId(), MARKET_MAKER_UUID, symbol,
                    tradePrice, (int) fillQty, stock, order.getPrice(), true)) {
                return OrderResult.fail("做市商成交结算失败。");
            }
            return finishMarketMakerFill(order, fillQty, tradePrice);
        }

        // 玩家卖给做市商 —— 做市商是买方：总账户真实扣款，库存记入子账本
        long tradePrice = bidPrice;
        if (order.getPrice() > tradePrice) {
            return OrderResult.fail("做市商报价未满足。");
        }
        // 终身熔断兜底保留：单只股票做市商累计接盘达上限后不再接盘
        long lifetimeRemaining = MAX_MARKET_MAKER_BUY_SHARES - stock.getMarketMakerBoughtShares();
        if (lifetimeRemaining <= 0) {
            return OrderResult.fail("做市商接盘额度已用完。");
        }
        long cashCap = AccountManager.getBalance(MARKET_MAKER_UUID) / tradePrice;
        long headroom = MarketMakerLedger.maxInventory(floatShares) - MarketMakerLedger.getStockInventory(symbol);
        long cap = Math.min(MarketMakerLedger.maxSingleTradeQty(floatShares),
                Math.min(cashCap, Math.min(lifetimeRemaining, headroom)));
        if (cap <= 0) {
            return OrderResult.fail("做市商暂无接盘额度（资金/库存/终身上限），已转入订单簿。");
        }
        long fillQty = Math.min(order.getQuantity(), cap);
        MarketMakerLedger.ReserveResult check =
                MarketMakerLedger.checkBuyable(symbol, fillQty, tradePrice, floatShares);
        if (!check.success()) {
            return OrderResult.fail(check.reason());
        }
        if (!executeTrade(MARKET_MAKER_UUID, order.getPlayerId(), symbol,
                tradePrice, (int) fillQty, stock, tradePrice, false)) {
            return OrderResult.fail("做市商成交结算失败。");
        }
        stock.addMarketMakerBoughtShares(fillQty);
        return finishMarketMakerFill(order, fillQty, tradePrice);
    }

    /**
     * 做市商部分成交收尾：成交部分已在 executeTrade 内结算，
     * 剩余数量挂回订单簿（挂单时冻结的资产与剩余数量天然对应，
     * 沿用订单簿部分成交的记账方式）。
     */
    private static OrderResult finishMarketMakerFill(StockOrder order, long fillQty, long tradePrice) {
        long remaining = (long) order.getQuantity() - fillQty;
        EconomySavedData.markDirty();
        if (remaining > 0) {
            order.setQuantity((int) remaining);
            addOrderDirect(order);
            return OrderResult.ok("做市商部分成交 " + fillQty + " 股（成交价 " + tradePrice
                    + "），剩余 " + remaining + " 股已挂入订单簿。");
        }
        return OrderResult.ok("订单已与做市商成交，成交价: " + tradePrice);
    }

    /**
     * 执行成交 —— 更新账户、持仓、价格、交易记录。
     *
     * <p>做市商参与时：做市商买入从总账户真实扣款并记入子账本库存；
     * 做市商卖出从子账本扣减库存、成交款进入总账户。任一环节失败则整体回滚，
     * 绝不出现"玩家收钱但做市商未扣款"或"玩家得股但库存未扣减"的半成功状态。</p>
     */
    private static boolean executeTrade(UUID buyerId, UUID sellerId, String symbol,
                                      long tradePrice, int tradeQty, Stock stock,
                                      long buyerLimitPrice, boolean buyInitiated) {

        // 计算成交金额
        long totalValue = MathUtil.multiplyExactOrNegative1(tradePrice, tradeQty);
        if (totalValue <= 0) return false;
        long reservedValue = MathUtil.multiplyExactOrNegative1(buyerLimitPrice, tradeQty);
        if (reservedValue <= 0 || reservedValue < totalValue) return false;

        boolean mmBuyer = buyerId.equals(MARKET_MAKER_UUID);
        boolean mmSeller = sellerId.equals(MARKET_MAKER_UUID);
        boolean playerBuyer = !mmBuyer;
        boolean playerSeller = !mmSeller;
        long buyerRefund = playerBuyer ? reservedValue - totalValue : 0;
        if (buyerRefund > 0 && !AccountManager.canDeposit(buyerId, buyerRefund)) return false;
        if (playerSeller && !AccountManager.canDeposit(sellerId, totalValue)) return false;
        if (mmSeller && !AccountManager.canDeposit(MARKET_MAKER_UUID, totalValue)) return false;
        if (playerBuyer && !StockPortfolioManager.canAddHolding(buyerId, symbol, tradeQty)) return false;
        // 做市商侧预检：余额/库存不足直接失败（真正扣减在提交阶段原子完成）
        if (mmBuyer && !MarketMakerLedger.checkBuyable(symbol, tradeQty, tradePrice, stock.getFloatShares()).success()) {
            return false;
        }
        if (mmSeller && !MarketMakerLedger.checkSellable(symbol, tradeQty).success()) {
            return false;
        }

        // 更新账户：卖方收钱，买方已在挂单时冻结
        if (buyerRefund > 0 && !AccountManager.deposit(buyerId, buyerRefund)) return false;

        // 做市商买入：子账本库存预留 + 总账户真实扣款
        if (mmBuyer) {
            MarketMakerLedger.ReserveResult reserve =
                    MarketMakerLedger.tryReserveBuy(symbol, tradeQty, tradePrice, stock.getFloatShares());
            if (!reserve.success() || !AccountManager.withdraw(MARKET_MAKER_UUID, totalValue)) {
                if (reserve.success()) {
                    MarketMakerLedger.releaseBuy(symbol, tradeQty);
                }
                if (buyerRefund > 0) AccountManager.withdraw(buyerId, buyerRefund);
                return false;
            }
        }
        // 做市商卖出：子账本库存预留 + 成交款真实进入总账户
        if (mmSeller) {
            MarketMakerLedger.ReserveResult reserve = MarketMakerLedger.tryReserveSell(symbol, tradeQty);
            if (!reserve.success() || !AccountManager.deposit(MARKET_MAKER_UUID, totalValue)) {
                if (reserve.success()) {
                    MarketMakerLedger.releaseSell(symbol, tradeQty);
                }
                if (buyerRefund > 0) AccountManager.withdraw(buyerId, buyerRefund);
                return false;
            }
        }

        if (playerSeller && !AccountManager.deposit(sellerId, totalValue)) {
            rollbackMarketMakerLeg(mmBuyer, mmSeller, symbol, tradeQty, totalValue);
            if (buyerRefund > 0) AccountManager.withdraw(buyerId, buyerRefund);
            return false;
        }

        // 更新持仓（玩家卖方的股票已在挂单时扣除，这里无需操作）
        if (playerBuyer && !StockPortfolioManager.addHolding(buyerId, symbol, tradeQty, tradePrice)) {
            if (buyerRefund > 0) AccountManager.withdraw(buyerId, buyerRefund);
            if (playerSeller) AccountManager.withdraw(sellerId, totalValue);
            rollbackMarketMakerLeg(mmBuyer, mmSeller, symbol, tradeQty, totalValue);
            return false;
        }

        // 通知定价引擎（推动价格）
        stock.recordTrade(tradePrice, tradeQty, buyInitiated);

        // 记录成交
        StockTrade trade = new StockTrade(buyerId, sellerId, symbol, tradePrice, tradeQty);
        addTradeToHistory(trade);
        EconomyMetricsService.recordStockTrade(tradeQty);
        CandlestickService.recordTrade(MarketInstrumentType.STOCK, symbol, tradePrice, tradeQty);
        RecentTradeService.record(MarketInstrumentType.STOCK, symbol, tradePrice, tradeQty,
                buyInitiated ? TradeDirection.BUY : TradeDirection.SELL);
        if (playerBuyer) {
            AccountManager.addTransactionRecord(
                    new TransactionRecord(
                            buyerId,
                            sellerId,
                            totalValue,
                            TransactionType.STOCK_BUY,
                            buyerId,
                            symbol,
                            tradeQty
                    )
            );
        }
        if (playerSeller) {
            AccountManager.addTransactionRecord(
                    new TransactionRecord(
                            buyerId,
                            sellerId,
                            totalValue,
                            TransactionType.STOCK_SELL,
                            sellerId,
                            symbol,
                            tradeQty
                    )
            );
        }
        // 做市商成交单独记一笔（MARKET_MAKER_BUY/SELL），管理后台可审计做市商 P&L
        if (mmBuyer || mmSeller) {
            AccountManager.addTransactionRecord(
                    new TransactionRecord(
                            buyerId,
                            sellerId,
                            totalValue,
                            mmBuyer ? TransactionType.MARKET_MAKER_BUY : TransactionType.MARKET_MAKER_SELL,
                            playerBuyer ? buyerId : sellerId,
                            symbol,
                            tradeQty
                    )
            );
        }

        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 回滚做市商侧已提交的资金/库存变动（玩家侧结算失败时调用）。
     * 回滚本身的存取款理论上不会失败（资金刚经手），忽略返回值以保持原有回滚风格。
     */
    private static void rollbackMarketMakerLeg(boolean mmBuyer, boolean mmSeller,
                                               String symbol, long tradeQty, long totalValue) {
        if (mmBuyer) {
            AccountManager.deposit(MARKET_MAKER_UUID, totalValue);
            MarketMakerLedger.releaseBuy(symbol, tradeQty);
        }
        if (mmSeller) {
            AccountManager.withdraw(MARKET_MAKER_UUID, totalValue);
            MarketMakerLedger.releaseSell(symbol, tradeQty);
        }
    }

    /**
     * 取消订单 —— 退还冻结资产。
     */
    public static boolean cancelOrder(UUID orderId, UUID playerId) {
        Iterator<StockOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            StockOrder order = iterator.next();
            if (order.getOrderId().equals(orderId) && order.getPlayerId().equals(playerId)) {
                long amount = MathUtil.multiplyExactOrNegative1(order.getPrice(), order.getQuantity());
                if (!refundOrderAssets(order)) {
                    return false;
                }
                iterator.remove();
                List<StockOrder> symbolOrders = ORDERS_BY_SYMBOL.get(order.getSymbol());
                if (symbolOrders != null) {
                    symbolOrders.remove(order);
                    if (symbolOrders.isEmpty()) {
                        ORDERS_BY_SYMBOL.remove(order.getSymbol());
                    }
                }
                AccountManager.addTransactionRecord(
                        new TransactionRecord(
                                playerId,
                                playerId,
                                Math.max(0, amount),
                                TransactionType.STOCK_ORDER_CANCEL,
                                playerId,
                                order.getSymbol(),
                                order.getQuantity()
                        )
                );
                EconomySavedData.markDirty();
                return true;
            }
        }
        return false;
    }

    public static int cancelOrdersForSymbol(String symbol) {
        String normalized = StockMarketManager.normalizeSymbol(symbol);
        int cancelled = 0;
        Iterator<StockOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            StockOrder order = iterator.next();
            if (!normalized.equals(order.getSymbol())) {
                continue;
            }
            if (!refundOrderAssets(order)) {
                continue;
            }
            iterator.remove();
            cancelled++;
        }
        ORDERS_BY_SYMBOL.remove(normalized);
        if (cancelled > 0) {
            EconomySavedData.markDirty();
        }
        return cancelled;
    }

    private static boolean refundOrderAssets(StockOrder order) {
        if (order.getQuantity() <= 0) {
            return false;
        }
        if (order.getType() == StockOrderType.BUY) {
            long refund = MathUtil.multiplyExactOrNegative1(order.getPrice(), order.getQuantity());
            if (refund > 0) {
                return AccountManager.deposit(order.getPlayerId(), refund);
            }
        } else {
            return StockPortfolioManager.addHolding(order.getPlayerId(), order.getSymbol(), order.getQuantity(), 0);
        }
        return false;
    }

    // ================================================================
    // 查询
    // ================================================================

    public static List<StockOrder> getOrders() {
        return new ArrayList<>(ORDERS);
    }

    public static List<StockOrder> getOrdersBySymbol(String symbol) {
        return ORDERS_BY_SYMBOL.getOrDefault(symbol, new ArrayList<>());
    }

    public static List<StockTrade> getTradeHistory() {
        return new ArrayList<>(TRADE_HISTORY);
    }

    // ================================================================
    // 持久化支持
    // ================================================================

    public static void addOrderDirect(StockOrder order) {
        ORDERS.add(order);
        ORDERS_BY_SYMBOL.computeIfAbsent(order.getSymbol(), k -> new ArrayList<>()).add(order);
        sortOrderBook(ORDERS_BY_SYMBOL.get(order.getSymbol()));
    }

    public static void clearOrders() {
        ORDERS.clear();
        ORDERS_BY_SYMBOL.clear();
    }

    private static void addTradeToHistory(StockTrade trade) {
        TRADE_HISTORY.add(trade);
        if (TRADE_HISTORY.size() > 500) {
            TRADE_HISTORY.subList(0, TRADE_HISTORY.size() - 500).clear();
        }
    }

    public static void clearTradeHistory() {
        TRADE_HISTORY.clear();
    }

    public static void addTradeDirectly(StockTrade trade) {
        TRADE_HISTORY.add(trade);
        if (TRADE_HISTORY.size() > 500) {
            TRADE_HISTORY.subList(0, TRADE_HISTORY.size() - 500).clear();
        }
    }

    private static void sortOrderBook(List<StockOrder> orders) {
        orders.sort((a, b) -> {
            if (a.getType() != b.getType()) {
                return a.getType().compareTo(b.getType());
            }
            int priceCompare = a.getType() == StockOrderType.BUY
                    ? Long.compare(b.getPrice(), a.getPrice())
                    : Long.compare(a.getPrice(), b.getPrice());
            if (priceCompare != 0) {
                return priceCompare;
            }
            return a.getTimestamp().compareTo(b.getTimestamp());
        });
    }

    public record OrderResult(boolean success, String message, boolean terminal) {
        public static OrderResult ok(String message) {
            return new OrderResult(true, message, false);
        }

        public static OrderResult fail(String message) {
            return new OrderResult(false, message, false);
        }

        public static OrderResult terminalFail(String message) {
            return new OrderResult(false, message, true);
        }
    }
}
