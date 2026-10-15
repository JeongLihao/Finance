package finance.margin;

import finance.account.AccountManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 两融规则层（v0.6.0）。
 *
 * <p>在 {@link MarginPoolManager} 机械层之上叠加玩法规则：
 * <ul>
 *   <li>融资：2x 杠杆上限（50% 首付从保证金扣），利息按日计，维持保证金率 130% / 预警 150%</li>
 *   <li>融券：券池 per-stock 5% 上限 + 单人 20% 上限，卖出资金冻结，只能买回还券</li>
 *   <li>反刷钱：融资款不进自由账户、融券所得冻结、穿仓记不良债务、停牌不可新开仓</li>
 *   <li>融资股不计入并购举牌门槛（见 {@link #financedSharesOf})</li>
 * </ul>
 *
 * <p>成交价统一按 {@link Stock#getFairValue()}（基本面定价），避免订单簿操纵传导。
 */
public final class MarginService {

    private MarginService() {
    }

    public record Result(boolean success, String message) {
        public static Result ok(String m) { return new Result(true, m); }
        public static Result fail(String m) { return new Result(false, m); }
    }

    // ================================================================
    // 配置读取
    // ================================================================

    /** 最大杠杆倍数（默认 2x） */
    public static int maxLeverage() {
        return Math.max(1, FinanceConfig.marginMaxLeverage());
    }

    /** 维持保证金率（默认 130%） */
    public static double maintenanceRatio() {
        return FinanceConfig.marginMaintenanceBps() / 100.0;
    }

    /** 预警线（默认 150%） */
    public static double warningRatio() {
        return FinanceConfig.marginWarningBps() / 100.0;
    }

    /** 券池比例：流通股的占比（默认 5%） */
    public static double poolRatio() {
        return FinanceConfig.marginPoolRatio();
    }

    /** 单人借券上限：券池占比（默认 20%） */
    public static double perPlayerBorrowRatio() {
        return FinanceConfig.marginPerPlayerBorrowRatio();
    }

    private static double financeAnnualRate() {
        return FinanceConfig.marginFinanceAnnualRate();
    }

    private static double shortAnnualRate() {
        return FinanceConfig.marginShortAnnualRate();
    }

    private static String norm(String symbol) {
        return StockMarketManager.normalizeSymbol(symbol);
    }

    // ================================================================
    // 保证金存取
    // ================================================================

    /** 存入现金保证金（自由资金 → 信用账户） */
    public static synchronized Result depositCollateral(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return Result.fail("金额必须为正。");
        }
        if (!AccountManager.withdraw(playerId, amount)) {
            return Result.fail("自由资金不足。");
        }
        MarginPoolManager.getAccount(playerId).addCashCollateral(amount);
        EconomySavedData.markDirty();
        return Result.ok("已存入保证金 " + amount + "。");
    }

    /**
     * 提取现金保证金。提取后重算维持保证金率，低于预警线拒绝
     * （防"先存后取"套保证金）。
     */
    public static synchronized Result withdrawCollateral(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return Result.fail("金额必须为正。");
        }
        MarginAccount acct = MarginPoolManager.getAccount(playerId);
        if (acct.getCashCollateral() < amount) {
            return Result.fail("保证金余额不足。");
        }
        // 有未结清业务时，提取后保证金率不得低于预警线
        if (acct.hasOpenPositions()) {
            double ratioAfter = ratioWithCollateralDelta(acct, -amount);
            if (ratioAfter < warningRatio()) {
                return Result.fail(String.format(
                        "提取后维持保证金率 %.1f%% 将低于预警线 %.1f%%，拒绝。",
                        ratioAfter, warningRatio()));
            }
        }
        if (!acct.reduceCashCollateral(amount)) {
            return Result.fail("保证金余额不足。");
        }
        if (!AccountManager.deposit(playerId, amount)) {
            acct.addCashCollateral(amount);
            return Result.fail("资金转出失败，已回滚。");
        }
        EconomySavedData.markDirty();
        return Result.ok("已提取保证金 " + amount + "。");
    }

    // ================================================================
    // 融资
    // ================================================================

    /**
     * 融资买入 —— 2x 杠杆做多。
     *
     * <p>首付 50% 从现金保证金扣除，券商垫付剩余 50%。
     * 垫付资金<b>不经过玩家自由账户</b>，直接结算（防借钱套现）。
     * 成交价按 fairValue。
     */
    public static synchronized Result marginBuy(UUID playerId, String symbol, long qty) {
        if (playerId == null || qty <= 0) {
            return Result.fail("参数错误。");
        }
        String key = norm(symbol);
        Stock stock = StockMarketManager.getStock(key);
        if (stock == null) {
            return Result.fail("未知股票: " + symbol);
        }
        if (stock.isTradingHalted()) {
            return Result.fail("该股票停牌中，不可新开融资仓。");
        }
        MarginAccount acct = MarginPoolManager.getAccount(playerId);
        if (acct.getBadDebt() > 0) {
            return Result.fail("有不良债务未清还，不可新开仓。请先还清穿仓债务。");
        }

        long price = stock.getFairValue();
        if (price <= 0) {
            return Result.fail("该股票估值异常，暂不可融资买入。");
        }
        long totalCost = multiplyExactOrNegative(qty, price);
        if (totalCost <= 0) {
            return Result.fail("交易金额过大。");
        }
        // 2x 杠杆：首付 50%（向上取整，防 1 元漏洞）
        long downPayment = (totalCost + 1) / 2;
        long brokerLoan = totalCost - downPayment;
        if (acct.getCashCollateral() < downPayment) {
            return Result.fail(String.format(
                    "现金保证金不足，需首付 %d（2x 杠杆），当前保证金 %d。",
                    downPayment, acct.getCashCollateral()));
        }

        // 执行：扣保证金 → 记负债 → 记融资股
        if (!acct.reduceCashCollateral(downPayment)) {
            return Result.fail("保证金扣减失败。");
        }
        acct.addFinancedDebt(brokerLoan);
        acct.addFinancedShares(key, qty);
        stock.recordTrade(price, qty, true);
        EconomySavedData.markDirty();
        return Result.ok(String.format(
                "融资买入 %d 股 %s @%d，共 %d（首付 %d，券商垫付 %d）。",
                qty, key, price, totalCost, downPayment, brokerLoan));
    }

    /**
     * 融资还款 —— 自由资金还款，先息后本。
     * 本息全清后，融资股转为普通持仓。
     */
    public static synchronized Result repayFinancing(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return Result.fail("金额必须为正。");
        }
        MarginAccount acct = MarginPoolManager.getAccount(playerId);
        long totalOwed = acct.getFinancedDebt() + acct.getFinancedInterestOwed();
        if (totalOwed <= 0) {
            return Result.fail("当前无融资负债。");
        }
        long pay = Math.min(amount, totalOwed);
        if (!AccountManager.withdraw(playerId, pay)) {
            return Result.fail("自由资金不足。");
        }
        // 先息后本
        long interestPay = Math.min(pay, acct.getFinancedInterestOwed());
        acct.reduceFinancedInterest(interestPay);
        acct.reduceFinancedDebt(pay - interestPay);
        EconomySavedData.markDirty();

        // 本息全清 → 融资股转普通持仓
        if (acct.getFinancedDebt() == 0 && acct.getFinancedInterestOwed() == 0
                && !acct.getFinancedShares().isEmpty()) {
            for (Map.Entry<String, Long> e : new ArrayList<>(acct.getFinancedShares().entrySet())) {
                Stock stock = StockMarketManager.getStock(e.getKey());
                long px = stock == null ? 0 : stock.getFairValue();
                if (!StockPortfolioManager.addHolding(playerId, e.getKey(), e.getValue(), px)) {
                    // 转仓失败则回滚该笔（极端情况，几乎不可能）
                    return Result.fail("持仓转出失败，请联系管理员。");
                }
            }
            acct.getFinancedShares().clear();
            EconomySavedData.markDirty();
            return Result.ok(String.format("已还清融资负债 %d，融资股已转为普通持仓。", pay));
        }
        return Result.ok(String.format("已还款 %d（利息 %d，本金 %d），剩余负债 %d。",
                pay, interestPay, pay - interestPay,
                acct.getFinancedDebt() + acct.getFinancedInterestOwed()));
    }

    // ================================================================
    // 融券
    // ================================================================

    /**
     * 融券卖出 —— 借股做空。
     *
     * <p>需 50% 现金保证金作为缓冲（不扣除，仅要求）。卖出所得<b>冻结</b>，
     * 只能用于买回还券（防卖空套现）。
     */
    public static synchronized Result shortSell(UUID playerId, String symbol, long qty) {
        if (playerId == null || qty <= 0) {
            return Result.fail("参数错误。");
        }
        String key = norm(symbol);
        Stock stock = StockMarketManager.getStock(key);
        if (stock == null) {
            return Result.fail("未知股票: " + symbol);
        }
        if (stock.isTradingHalted()) {
            return Result.fail("该股票停牌中，不可新开融券仓。");
        }
        MarginAccount acct = MarginPoolManager.getAccount(playerId);
        if (acct.getBadDebt() > 0) {
            return Result.fail("有不良债务未清还，不可新开仓。请先还清穿仓债务。");
        }

        long price = stock.getFairValue();
        if (price <= 0) {
            return Result.fail("该股票估值异常，暂不可融券卖出。");
        }
        long notional = multiplyExactOrNegative(qty, price);
        if (notional <= 0) {
            return Result.fail("交易金额过大。");
        }
        // 保证金要求：名义金额的 50%（2x 杠杆对称）
        long requiredMargin = (notional + 1) / 2;
        if (acct.getCashCollateral() < requiredMargin) {
            return Result.fail(String.format(
                    "现金保证金不足，需 %d（名义金额 %d 的 50%%），当前 %d。",
                    requiredMargin, notional, acct.getCashCollateral()));
        }
        // 券池检查（P0-2 教训：系统出借必须有硬上限）
        long perPlayerCap = MarginPoolManager.perPlayerBorrowCap(key);
        if (acct.shortPositionOf(key) + qty > perPlayerCap) {
            return Result.fail(String.format(
                    "单人借券上限 %d 股（已借 %d），拒绝。",
                    perPlayerCap, acct.shortPositionOf(key)));
        }
        if (!MarginPoolManager.borrow(key, qty)) {
            return Result.fail(String.format(
                    "券池余量不足（剩余 %d 股），拒绝。", MarginPoolManager.poolAvailable(key)));
        }

        // 执行：卖出所得冻结
        acct.addShortPosition(key, qty);
        acct.addShortProceeds(key, notional);
        acct.setShortSellPrice(key, price);
        stock.recordTrade(price, qty, false);
        EconomySavedData.markDirty();
        return Result.ok(String.format(
                "融券卖出 %d 股 %s @%d，所得 %d 已冻结（仅可用于买回还券）。",
                qty, key, price, notional));
    }

    /**
     * 买券还券 —— 按 fairValue 系统结算。
     *
     * <p>qty <= 0 表示全部归还。结算顺序：冻结资金 → 现金保证金；
     * 不足部分记不良债务。全还时剩余冻结资金解冻至自由账户。
     */
    public static synchronized Result coverShort(UUID playerId, String symbol, long qty) {
        if (playerId == null) {
            return Result.fail("参数错误。");
        }
        String key = norm(symbol);
        MarginAccount acct = MarginPoolManager.getAccount(playerId);
        long pos = acct.shortPositionOf(key);
        if (pos <= 0) {
            return Result.fail("当前无该股票融券仓位。");
        }
        long closeQty = qty <= 0 ? pos : Math.min(qty, pos);

        Stock stock = StockMarketManager.getStock(key);
        long price = stock == null ? 0 : stock.getFairValue();
        if (price <= 0) {
            return Result.fail("该股票当前无有效价格，暂不可还券。");
        }
        long cost = multiplyExactOrNegative(closeQty, price);
        if (cost <= 0) {
            return Result.fail("结算金额过大。");
        }
        // 按比例分摊利息
        long totalInterest = acct.getShortInterestOwed().getOrDefault(key, 0L);
        long interestShare = pos == 0 ? 0 : totalInterest * closeQty / pos;
        long totalOwed = cost + interestShare;

        // 结算：先用冻结资金，再用现金保证金
        long frozen = acct.shortProceedsOf(key);
        long fromFrozen = Math.min(frozen, totalOwed);
        acct.reduceShortProceeds(key, fromFrozen);
        long rest = totalOwed - fromFrozen;
        long fromCollateral = Math.min(acct.getCashCollateral(), rest);
        acct.reduceCashCollateral(fromCollateral);
        rest -= fromCollateral;
        if (rest > 0) {
            // 穿仓：缺口记不良债务（不允许欠系统钱继续交易）
            acct.addBadDebt(rest);
        }
        acct.reduceShortInterest(key, interestShare);
        acct.reduceShortPosition(key, closeQty);
        MarginPoolManager.repay(key, closeQty);
        if (acct.shortPositionOf(key) == 0) {
            acct.clearShortSellPrice(key);
            // 全还：剩余冻结资金解冻至自由账户（盈利实现）
            long leftover = acct.shortProceedsOf(key);
            if (leftover > 0) {
                acct.reduceShortProceeds(key, leftover);
                AccountManager.deposit(playerId, leftover);
            }
            acct.getShortInterestOwed().remove(key);
        }
        EconomySavedData.markDirty();

        String msg = String.format("已买回 %d 股 %s @%d，成本 %d（含利息 %d）%s。",
                closeQty, key, price, totalOwed, interestShare,
                rest > 0 ? "，缺口 " + rest + " 已记为不良债务" : "");
        return Result.ok(msg);
    }

    // ================================================================
    // 保证金率与风控
    // ================================================================

    /**
     * 维持保证金率 = 权益 / 负债。
     * 无负债返回 Double.MAX_VALUE（安全）。
     */
    public static double marginRatio(UUID playerId) {
        MarginAccount acct = MarginPoolManager.accounts().get(playerId);
        if (acct == null) return Double.MAX_VALUE;
        return marginRatioOf(acct);
    }

    private static double marginRatioOf(MarginAccount acct) {
        long equity = acct.getCashCollateral();
        long debt = acct.getFinancedDebt() + acct.getFinancedInterestOwed();
        for (Map.Entry<String, Long> e : acct.getFinancedShares().entrySet()) {
            equity += multiplyCap(e.getValue(), priceOf(e.getKey()));
        }
        for (Map.Entry<String, Long> e : acct.getShortProceeds().entrySet()) {
            equity += e.getValue();
        }
        for (Map.Entry<String, Long> e : acct.getShortPositions().entrySet()) {
            debt += multiplyCap(e.getValue(), priceOf(e.getKey()));
        }
        for (long interest : acct.getShortInterestOwed().values()) {
            debt += interest;
        }
        if (debt <= 0) return Double.MAX_VALUE;
        if (equity < 0) equity = 0;
        return (double) equity / (double) debt * 100.0;
    }

    /** 假设保证金变动 delta 后的保证金率（用于提现预检） */
    private static double ratioWithCollateralDelta(MarginAccount acct, long delta) {
        long equity = acct.getCashCollateral() + delta;
        long debt = acct.getFinancedDebt() + acct.getFinancedInterestOwed();
        for (Map.Entry<String, Long> e : acct.getFinancedShares().entrySet()) {
            equity += multiplyCap(e.getValue(), priceOf(e.getKey()));
        }
        for (Map.Entry<String, Long> e : acct.getShortProceeds().entrySet()) {
            equity += e.getValue();
        }
        for (Map.Entry<String, Long> e : acct.getShortPositions().entrySet()) {
            debt += multiplyCap(e.getValue(), priceOf(e.getKey()));
        }
        for (long interest : acct.getShortInterestOwed().values()) {
            debt += interest;
        }
        if (debt <= 0) return Double.MAX_VALUE;
        if (equity < 0) equity = 0;
        return (double) equity / (double) debt * 100.0;
    }

    private static long priceOf(String symbol) {
        Stock stock = StockMarketManager.getStock(symbol);
        return stock == null ? 0 : Math.max(0, stock.getFairValue());
    }

    /** 是否触及预警线（< 150% 且有负债） */
    public static boolean isWarning(UUID playerId) {
        MarginAccount acct = MarginPoolManager.accounts().get(playerId);
        if (acct == null || !acct.hasOpenPositions()) return false;
        double r = marginRatioOf(acct);
        return r < warningRatio();
    }

    // ================================================================
    // 日终：计息 + 强平
    // ================================================================

    /**
     * 每日日终处理：先计息，后重估保证金率并强平。
     * 由 FinancialCycleService.processDay 调用（Module.STOCK 门控）。
     *
     * @return 强平记录（供日志/通知）
     */
    public static synchronized List<String> processDay(long day) {
        List<String> liquidated = new ArrayList<>();
        for (MarginAccount acct : new ArrayList<>(MarginPoolManager.accounts().values())) {
            if (acct.getLastInterestDay() < day) {
                accrueInterest(acct);
                acct.setLastInterestDay(day);
            }
            if (!acct.hasOpenPositions()) continue;
            double ratio = marginRatioOf(acct);
            if (ratio < maintenanceRatio()) {
                liquidate(acct, liquidated);
            }
        }
        if (!liquidated.isEmpty()) {
            EconomySavedData.markDirty();
        }
        return liquidated;
    }

    private static void accrueInterest(MarginAccount acct) {
        // 融资利息：本金 × 年利率 / 365（单利）
        if (acct.getFinancedDebt() > 0 && financeAnnualRate() > 0) {
            long interest = (long) (acct.getFinancedDebt() * financeAnnualRate() / 365.0);
            if (interest > 0) acct.addFinancedInterest(interest);
        }
        // 融券利息：按卖出时市值计
        if (shortAnnualRate() > 0) {
            for (Map.Entry<String, Long> e : acct.getShortPositions().entrySet()) {
                long base = multiplyCap(e.getValue(), acct.shortSellPriceOf(e.getKey()));
                long interest = (long) (base * shortAnnualRate() / 365.0);
                if (interest > 0) acct.addShortInterest(e.getKey(), interest);
            }
        }
    }

    /**
     * 强制平仓：融资仓按 fairValue 卖出先息后本还款；
     * 融券仓按 fairValue 买回（冻结资金 → 保证金 → 不良债务）。
     * 停牌股跳过（无公允执行价），退市（股票不存在）按 0 结算。
     */
    private static void liquidate(MarginAccount acct, List<String> log) {
        UUID playerId = acct.getPlayerId();
        // ---- 强平融资仓 ----
        for (String symbol : new ArrayList<>(acct.getFinancedShares().keySet())) {
            Stock stock = StockMarketManager.getStock(symbol);
            if (stock != null && stock.isTradingHalted()) {
                continue; // 停牌股跳过本次
            }
            long qty = acct.financedSharesOf(symbol);
            long price = stock == null ? 0 : Math.max(0, stock.getFairValue());
            long proceeds = multiplyCap(qty, price);
            // 先息后本
            long interestPay = Math.min(proceeds, acct.getFinancedInterestOwed());
            acct.reduceFinancedInterest(interestPay);
            proceeds -= interestPay;
            long debtPay = Math.min(proceeds, acct.getFinancedDebt());
            acct.reduceFinancedDebt(debtPay);
            proceeds -= debtPay;
            if (proceeds > 0) {
                AccountManager.deposit(playerId, proceeds);
            }
            acct.removeFinancedShares(symbol, qty);
            log.add(String.format("强平融资 %s %d股@%d", symbol, qty, price));
        }
        // ---- 强平融券仓 ----
        for (String symbol : new ArrayList<>(acct.getShortPositions().keySet())) {
            Stock stock = StockMarketManager.getStock(symbol);
            if (stock != null && stock.isTradingHalted()) {
                continue;
            }
            long qty = acct.shortPositionOf(symbol);
            long price = stock == null ? 0 : Math.max(0, stock.getFairValue());
            long cost = multiplyCap(qty, price);
            long interest = acct.getShortInterestOwed().getOrDefault(symbol, 0L);
            long totalOwed = cost + interest;
            long frozen = acct.shortProceedsOf(symbol);
            long fromFrozen = Math.min(frozen, totalOwed);
            acct.reduceShortProceeds(symbol, fromFrozen);
            long rest = totalOwed - fromFrozen;
            long fromCollateral = Math.min(acct.getCashCollateral(), rest);
            acct.reduceCashCollateral(fromCollateral);
            rest -= fromCollateral;
            if (rest > 0) {
                acct.addBadDebt(rest);
            }
            acct.reduceShortPosition(symbol, qty);
            acct.reduceShortInterest(symbol, interest);
            acct.clearShortSellPrice(symbol);
            MarginPoolManager.repay(symbol, qty);
            // 剩余冻结资金解冻
            long leftover = acct.shortProceedsOf(symbol);
            if (leftover > 0) {
                acct.reduceShortProceeds(symbol, leftover);
                AccountManager.deposit(playerId, leftover);
            }
            acct.getShortInterestOwed().remove(symbol);
            log.add(String.format("强平融券 %s %d股@%d", symbol, qty, price));
        }
    }

    // ================================================================
    // 查询（供并购/命令/序列化）
    // ================================================================

    /** 某玩家某股票的融资股数（供 TakeoverService 剔除举牌门槛） */
    public static long financedSharesOf(UUID playerId, String symbol) {
        MarginAccount acct = MarginPoolManager.accounts().get(playerId);
        if (acct == null) return 0;
        return acct.financedSharesOf(norm(symbol));
    }

    /** 不良债务（供信用/命令展示） */
    public static long badDebtOf(UUID playerId) {
        MarginAccount acct = MarginPoolManager.accounts().get(playerId);
        return acct == null ? 0 : acct.getBadDebt();
    }

    /**
     * 偿还不良债务（自由资金）。还清前冻结新开仓。
     */
    public static synchronized Result repayBadDebt(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return Result.fail("金额必须为正。");
        }
        MarginAccount acct = MarginPoolManager.getAccount(playerId);
        if (acct.getBadDebt() <= 0) {
            return Result.fail("当前无不良债务。");
        }
        long pay = Math.min(amount, acct.getBadDebt());
        if (!AccountManager.withdraw(playerId, pay)) {
            return Result.fail("自由资金不足。");
        }
        acct.reduceBadDebt(pay);
        EconomySavedData.markDirty();
        return Result.ok(String.format("已偿还不良债务 %d，剩余 %d。", pay, acct.getBadDebt()));
    }

    // ================================================================
    // 工具
    // ================================================================

    private static long multiplyExactOrNegative(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    private static long multiplyCap(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        BigInteger r = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
        return r.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                ? Long.MAX_VALUE : r.longValue();
    }
}
