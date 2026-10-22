package finance.simple;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.futures.FuturesContract;
import finance.futures.FuturesMarketMaker;
import finance.futures.FuturesMarketManager;
import finance.futures.FuturesMath;
import finance.futures.FuturesPosition;
import finance.futures.MarginManager;
import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import finance.margin.MarginService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 押注：把加倍买、借股卖出（股票）和锁价合同（商品期货）合成“看涨 / 看跌”两个按钮。
 * 玩家只选投入多少钱；股数、保证金、合约份数都在这里算好，结束时押金自动退回钱包。
 * 真正的成交、计息和强制平仓仍在原来的服务里。
 */
public final class SimpleBetService {

    private SimpleBetService() {}

    /**
     * 一笔押注。key：s:代码:up / s:代码:down / f:合约ID；stake 是投入，value 是现在结束大约能拿回多少。
     * tracked = false 表示不是从押注按钮开的（旧界面或命令），没有投入记录，只显示现值。
     */
    public record Bet(String key, String name, boolean up, long stake, long value, String note, boolean tracked) {
        public long pnl() {
            return tracked ? value - stake : 0;
        }
    }

    /** 下单前的预估：数量（股或份）、对应的货值、涨跌 10% 的盈亏、强制结束线（跌/涨百分之几，-1 表示没有）。 */
    public record Preview(long quantity, long notional, long swing10, int stopPct) {
        public static final Preview NONE = new Preview(0, 0, 0, -1);
    }

    // ------------------------------------------------------------ 预估（纯计算，客户端也用）

    /**
     * 股票押注：投入 stake，看涨时自己出一半、券商借一半，看跌时押一半借股卖出——两边都是 2 倍货值。
     * 强制结束线按维持保证金率算：看涨跌到 维持率/2 就会被强卖，看跌涨到 1.5/维持率 就会被强制买回。
     */
    public static Preview stockPreview(long stake, long price, boolean up, int maintenancePct) {
        if (stake <= 0 || price <= 0) return Preview.NONE;
        long qty = Math.min(stake, Long.MAX_VALUE / 4) * 2 / price;
        if (qty <= 0) return Preview.NONE;
        long notional = qty * price;
        int m = Math.max(101, maintenancePct);
        int stop = up ? 100 - m / 2 : (int) Math.round((150.0 / m - 1) * 100);
        return new Preview(qty, notional, notional / 10, Math.max(1, stop));
    }

    /** 商品押注：每份合约要交一份初始保证金，投入够几份就签几份。到期按价格结算，不会提前强制结束。 */
    public static Preview futuresPreview(long stake, long price, long contractSize, int marginBps) {
        long perContract = FuturesMath.margin(price, contractSize, 1, marginBps);
        if (stake <= 0 || perContract <= 0) return Preview.NONE;
        long qty = stake / perContract;
        if (qty <= 0) return Preview.NONE;
        long notional = FuturesMath.notional(price, contractSize, qty);
        return new Preview(qty, notional, notional / 10, -1);
    }

    /** 商品押注最少要投入多少（一份合约的保证金）。 */
    public static long futuresMinStake(long price, long contractSize, int marginBps) {
        return Math.max(1, FuturesMath.margin(price, contractSize, 1, marginBps));
    }

    // ------------------------------------------------------------ 投入记录（存在玩家身上）

    static final String TAG = "finance_bets";

    private static CompoundTag book(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG)) data.put(TAG, new CompoundTag());
        return data.getCompound(TAG);
    }

    private static void addToBook(ServerPlayer player, String key, long stake, long borrowed) {
        CompoundTag book = book(player);
        CompoundTag entry = book.getCompound(key);
        entry.putLong("stake", Math.max(0, entry.getLong("stake")) + Math.max(0, stake));
        entry.putLong("borrowed", Math.max(0, entry.getLong("borrowed")) + Math.max(0, borrowed));
        book.put(key, entry);
    }

    // ------------------------------------------------------------ 列表

    public static List<Bet> bets(ServerPlayer player) {
        UUID id = player.getUUID();
        CompoundTag book = book(player);
        List<Bet> out = new ArrayList<>();
        List<String> live = new ArrayList<>();

        MarginAccount acct = MarginPoolManager.accounts().get(id);
        if (acct != null) {
            // 借款和利息是整个账户一笔：按每笔借了多少分摊，没有投入记录时按货值分摊
            long totalBorrowed = 0, totalGross = 0;
            Map<String, Long> gross = new java.util.HashMap<>();
            for (Map.Entry<String, Long> e : acct.getFinancedShares().entrySet()) {
                Stock stock = StockMarketManager.getStock(e.getKey());
                long g = stock == null ? 0 : multiply(e.getValue(), stock.impactedPrice(e.getValue(), false));
                gross.put(e.getKey(), g);
                totalGross += g;
                totalBorrowed += book.getCompound("s:" + e.getKey() + ":up").getLong("borrowed");
            }
            long owed = acct.getFinancedDebt() + acct.getFinancedInterestOwed();
            for (Map.Entry<String, Long> e : acct.getFinancedShares().entrySet()) {
                String key = "s:" + e.getKey() + ":up";
                live.add(key);
                Stock stock = StockMarketManager.getStock(e.getKey());
                CompoundTag rec = book.getCompound(key);
                boolean tracked = rec.contains("stake") && totalBorrowed > 0;
                double weight = tracked
                        ? rec.getLong("borrowed") / (double) totalBorrowed
                        : totalGross > 0 ? gross.get(e.getKey()) / (double) totalGross : 0;
                long value = Math.max(0, gross.get(e.getKey()) - Math.round(owed * weight));
                out.add(new Bet(
                        key,
                        stock == null ? e.getKey() : stock.getName(),
                        true,
                        tracked ? rec.getLong("stake") : value,
                        value,
                        e.getValue() + " 股（一半是借的）",
                        tracked));
            }
            for (Map.Entry<String, Long> e : acct.getShortPositions().entrySet()) {
                String key = "s:" + e.getKey() + ":down";
                live.add(key);
                Stock stock = StockMarketManager.getStock(e.getKey());
                long qty = e.getValue();
                long cover = stock == null ? 0 : multiply(qty, stock.impactedPrice(qty, true));
                long pnl = acct.shortProceedsOf(e.getKey())
                        - cover
                        - acct.getShortInterestOwed().getOrDefault(e.getKey(), 0L);
                CompoundTag rec = book.getCompound(key);
                boolean tracked = rec.contains("stake");
                long stake = tracked ? rec.getLong("stake") : (acct.shortProceedsOf(e.getKey()) + 1) / 2;
                out.add(new Bet(
                        key,
                        stock == null ? e.getKey() : stock.getName(),
                        false,
                        stake,
                        Math.max(0, stake + pnl),
                        "借了 " + qty + " 股卖掉",
                        tracked));
            }
        }

        int bps = FinanceConfig.futuresInitialMarginBps();
        for (FuturesPosition p : MarginManager.positions().values()) {
            if (!p.ownerId().equals(id) || p.signedQuantity() == 0) continue;
            FuturesContract c = FuturesMarketManager.contract(p.contractId());
            if (c == null) continue;
            String key = "f:" + c.id();
            live.add(key);
            long price = FuturesMarketManager.riskPrice(c.id());
            long pnl = price <= 0
                    ? 0
                    : FuturesMath.signedPnl(p.averageEntryPrice(), price, c.contractSize(), p.signedQuantity());
            CompoundTag rec = book.getCompound(key);
            boolean tracked = rec.contains("stake");
            long stake = tracked
                    ? rec.getLong("stake")
                    : FuturesMath.margin(p.averageEntryPrice(), c.contractSize(), p.quantity(), bps);
            out.add(new Bet(
                    key,
                    commodityName(c.commodityId()),
                    p.signedQuantity() > 0,
                    stake,
                    Math.max(0, stake + pnl),
                    p.quantity() + " 份 × " + c.contractSize() + " 个，第 " + c.maturityDay() + " 天自动结算",
                    tracked));
        }

        // 已经结束（到期结算、被强制结束）的记录清掉
        for (String key : new ArrayList<>(book.getAllKeys())) if (!live.contains(key)) book.remove(key);
        out.sort(Comparator.comparing(Bet::name));
        return out;
    }

    // ------------------------------------------------------------ 开始押注

    /** target：s:股票代码 或 f:商品ID；stake：投入多少钱。 */
    public static SimpleInvestService.Result open(ServerPlayer player, String target, boolean up, long stake) {
        if (target == null || stake <= 0) return fail("先选好投入多少钱。");
        if (target.startsWith("s:")) return openStock(player, target.substring(2), up, stake);
        if (target.startsWith("f:")) return openFutures(player, target.substring(2), up, stake);
        return fail("没有这种押注。");
    }

    private static SimpleInvestService.Result openStock(ServerPlayer player, String symbol, boolean up, long stake) {
        Opened o = openStockFor(player.getUUID(), symbol, up, stake);
        if (o.result().success()) addToBook(player, o.key(), o.own(), o.borrowed());
        return o.result();
    }

    /** 开了一笔股票押注：结果、押注 key、自己出的钱、借的钱（看涨才有）。 */
    record Opened(SimpleInvestService.Result result, String key, long own, long borrowed) {}

    /** 只动账户和仓位的部分（不记投入），测试直接调用。 */
    static Opened openStockFor(UUID id, String symbol, boolean up, long stake) {
        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) return failed("没有这只股票。");
        if (stock.isTradingPaused()) return failed("这只股票熔断或停牌中，稍后再押。");
        long price = stock.getAnchorPrice();
        Preview pv = stockPreview(stake, price, up, (int) Math.round(MarginService.maintenanceRatio()));
        if (pv.quantity() <= 0) return failed("投入太少：至少要 " + MoneyFormat.pounds((price + 1) / 2) + "。");
        long own = (pv.notional() + 1) / 2;
        MarginAccount acct = MarginPoolManager.getAccount(id);
        // 押金要够：看涨的首付会直接花掉，已有的借股卖出各要押一半；不然一开就低于强制结束线
        long shortsNeed = 0;
        for (long proceeds : acct.getShortProceeds().values()) shortsNeed += (proceeds + 1) / 2;
        long topUp = own + shortsNeed - acct.getCashCollateral();
        long before = AccountManager.getBalance(id);
        if (topUp > 0) {
            if (before < topUp) return failed("钱包不够：要 " + MoneyFormat.pounds(topUp) + "。");
            MarginService.Result deposit = MarginService.depositCollateral(id, topUp);
            if (!deposit.success()) return failed(deposit.message());
        }
        MarginService.Result r = up
                ? MarginService.marginBuy(id, stock.getSymbol(), pv.quantity())
                : MarginService.shortSell(id, stock.getSymbol(), pv.quantity());
        if (!r.success()) {
            sweepIdle(id);
            return failed(r.message());
        }
        long paid = before - AccountManager.getBalance(id);
        int stop = pv.stopPct();
        String msg = up
                ? "看涨「" + stock.getName() + "」：投入 " + MoneyFormat.pounds(own) + "，相当于买了 " + pv.quantity()
                        + " 股（一半是借的）。涨 10% 约赚 " + MoneyFormat.pounds(pv.swing10()) + "，跌超 " + stop + "% 会被强制结束。"
                : "看跌「" + stock.getName() + "」：押了 " + MoneyFormat.pounds(own) + "，借 " + pv.quantity() + " 股卖掉。跌 10% 约赚 "
                        + MoneyFormat.pounds(pv.swing10()) + "，涨超 " + stop + "% 会被强制结束。";
        return new Opened(
                new SimpleInvestService.Result(true, msg, -paid),
                "s:" + stock.getSymbol() + (up ? ":up" : ":down"),
                own,
                up ? pv.notional() - own : 0);
    }

    private static Opened failed(String message) {
        return new Opened(fail(message), "", 0, 0);
    }

    /** 结束一笔股票押注（s:代码:up / s:代码:down）并退回闲置押金；成功返回 null，失败返回原因。测试直接调用。 */
    static String closeStockFor(UUID id, String key) {
        String[] parts = key.split(":");
        if (parts.length != 3 || !parts[0].equals("s")) return "请求无效。";
        MarginService.Result r = "up".equals(parts[2])
                ? MarginService.closeFinanced(id, parts[1])
                : MarginService.coverShort(id, parts[1], 0);
        if (!r.success()) return r.message();
        sweepIdle(id);
        return null;
    }

    private static SimpleInvestService.Result openFutures(
            ServerPlayer player, String commodityId, boolean up, long stake) {
        FuturesContract c = liveContract(commodityId);
        if (c == null) return fail("这种东西现在没有可押的合约，明天再来。");
        long[] quote = FuturesMarketMaker.getQuote(c.id());
        if (quote == null) return fail("市场暂时不报价，稍后再试。");
        long price = up ? quote[1] : quote[0];
        int bps = FinanceConfig.futuresInitialMarginBps();
        Preview pv = futuresPreview(stake, price, c.contractSize(), bps);
        if (pv.quantity() <= 0)
            return fail("投入太少：至少要 " + MoneyFormat.pounds(futuresMinStake(price, c.contractSize(), bps)) + "。");
        long before = AccountManager.getBalance(player.getUUID());
        SimpleCenterService.Result r = SimpleCenterService.lockPrice(player, c.id(), !up, pv.quantity());
        if (!r.success()) return fail(r.message());
        long margin = FuturesMath.margin(price, c.contractSize(), pv.quantity(), bps);
        addToBook(player, "f:" + c.id(), margin, 0);
        String name = commodityName(commodityId);
        return new SimpleInvestService.Result(
                true,
                (up ? "看涨" : "看跌") + name + "：投入 " + MoneyFormat.pounds(margin) + "，签了 "
                        + pv.quantity() + " 份（共 " + multiply(pv.quantity(), c.contractSize()) + " 个）。价格"
                        + (up ? "涨" : "跌") + " 10% 约赚 " + MoneyFormat.pounds(pv.swing10()) + "，第 " + c.maturityDay()
                        + " 天自动结算。",
                AccountManager.getBalance(player.getUUID()) - before);
    }

    /** 这种商品最近到期、正在交易、有报价的合约。 */
    public static FuturesContract liveContract(String commodityId) {
        FuturesContract best = null;
        for (FuturesContract c : FuturesMarketManager.contracts().values()) {
            if (!c.commodityId().equals(commodityId) || !c.canTrade() || FuturesMarketMaker.getQuote(c.id()) == null)
                continue;
            if (best == null || c.maturityDay() < best.maturityDay()) best = c;
        }
        return best;
    }

    // ------------------------------------------------------------ 结束押注

    public static SimpleInvestService.Result close(ServerPlayer player, String key) {
        UUID id = player.getUUID();
        if (key == null) return fail("请求无效。");
        Bet bet = bets(player).stream()
                .filter(b -> b.key().equals(key))
                .findFirst()
                .orElse(null);
        if (bet == null) return fail("这笔押注已经结束了。");
        long before = AccountManager.getBalance(id);
        String failure = null;
        if (key.startsWith("s:")) {
            failure = closeStockFor(id, key);
        } else if (key.startsWith("f:")) {
            UUID contract;
            try {
                contract = UUID.fromString(key.substring(2));
            } catch (IllegalArgumentException e) {
                return fail("请求无效。");
            }
            SimpleCenterService.Result r = SimpleCenterService.closeLock(player, contract);
            if (!r.success()) failure = r.message();
        } else {
            return fail("请求无效。");
        }
        if (failure != null) return fail(failure);
        sweepIdle(id);
        long got = AccountManager.getBalance(id) - before;
        Long pnl = settleReturned(player, key, got);
        String result =
                pnl == null ? "" : pnl >= 0 ? WIN_MARK + MoneyFormat.pounds(pnl) : LOSS_MARK + MoneyFormat.pounds(-pnl);
        return new SimpleInvestService.Result(
                true, "结束押注「" + bet.name() + "」：拿回 " + MoneyFormat.pounds(Math.max(0, got)) + result + "。", got);
    }

    // ------------------------------------------------------------ 结算与战绩

    /** 结束押注的提示里用这两个词说明赚亏，客户端据此换提示音。 */
    public static final String WIN_MARK = "，赚了 ", LOSS_MARK = "，亏了 ";

    /** 押注战绩：结束了几笔、赚了几笔、累计赚亏、最赚和最亏的一笔。 */
    public record Stats(int count, int wins, long total, long best, long worst) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0);

        Stats plus(long pnl) {
            return new Stats(
                    count + 1,
                    wins + (pnl > 0 ? 1 : 0),
                    total + pnl,
                    count == 0 ? pnl : Math.max(best, pnl),
                    count == 0 ? pnl : Math.min(worst, pnl));
        }

        /** “战绩：12 笔，赚了 7 笔，累计 +3 磅，最赚一笔 +5 磅”；一笔都没有返回空串。 */
        public String line() {
            if (count == 0) return "";
            return "战绩：" + count + " 笔，赚了 " + wins + " 笔，累计 " + MoneyFormat.signed(total) + "，最赚一笔 "
                    + MoneyFormat.signed(best) + "，最亏一笔 " + MoneyFormat.signed(worst);
        }
    }

    static final String STATS_TAG = "finance_bet_stats";

    public static Stats stats(ServerPlayer player) {
        CompoundTag t = player.getPersistentData().getCompound(STATS_TAG);
        return new Stats(
                t.getInt("count"), t.getInt("wins"), t.getLong("total"), t.getLong("best"), t.getLong("worst"));
    }

    private static void record(ServerPlayer player, long pnl) {
        Stats s = stats(player).plus(pnl);
        CompoundTag t = new CompoundTag();
        t.putInt("count", s.count());
        t.putInt("wins", s.wins());
        t.putLong("total", s.total());
        t.putLong("best", s.best());
        t.putLong("worst", s.worst());
        player.getPersistentData().put(STATS_TAG, t);
    }

    /** 押注结束、退回 returned：按投入记录算赚亏、记战绩、删记录；没有投入记录（旧仓位）返回 null。 */
    public static Long settleReturned(ServerPlayer player, String key, long returned) {
        CompoundTag book = book(player);
        if (!book.contains(key)) return null;
        long pnl = returned - book.getCompound(key).getLong("stake");
        book.remove(key);
        record(player, pnl);
        return pnl;
    }

    /** 押注结束、赚亏已知（期货按结算价算）：记战绩、删记录；没有投入记录返回 null。 */
    public static Long settlePnl(ServerPlayer player, String key, long pnl) {
        CompoundTag book = book(player);
        if (!book.contains(key)) return null;
        book.remove(key);
        record(player, pnl);
        return pnl;
    }

    /** 玩家离线时结束的押注：先记下来，上线或打开金融中心时补记战绩。服务器重启会丢，丢了只是少记一笔战绩。 */
    record Pending(String key, long amount, boolean isPnl) {}

    private static final Map<UUID, List<Pending>> PENDING = new java.util.HashMap<>();

    public static synchronized void defer(UUID player, String key, long amount, boolean isPnl) {
        List<Pending> list = PENDING.computeIfAbsent(player, k -> new ArrayList<>());
        if (list.size() < 64) list.add(new Pending(key, amount, isPnl));
    }

    public static synchronized void applyPending(ServerPlayer player) {
        List<Pending> list = PENDING.remove(player.getUUID());
        if (list == null) return;
        for (Pending p : list) {
            if (p.isPnl()) settlePnl(player, p.key(), p.amount());
            else settleReturned(player, p.key(), p.amount());
        }
    }

    /** 没有押注在身上时，把闲着的押金退回钱包；返回退回多少。 */
    public static long sweepIdle(UUID id) {
        long returned = 0;
        MarginAccount acct = MarginPoolManager.accounts().get(id);
        if (acct != null && !acct.hasOpenPositions() && acct.getCashCollateral() > 0) {
            long cash = acct.getCashCollateral();
            if (MarginService.withdrawCollateral(id, cash).success()) returned += cash;
        }
        boolean futuresOpen = MarginManager.positions().values().stream()
                .anyMatch(p -> p.ownerId().equals(id) && p.signedQuantity() != 0);
        if (!futuresOpen) {
            long free = MarginManager.availableToWithdraw(id);
            if (free > 0 && MarginManager.withdraw(id, free)) returned += free;
        }
        return returned;
    }

    // ------------------------------------------------------------ 小工具

    static String commodityName(String id) {
        Commodity c = CommodityRegistry.getCommodity(id);
        return c == null ? id : c.getDisplayName();
    }

    private static long multiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static SimpleInvestService.Result fail(String message) {
        return new SimpleInvestService.Result(false, message, 0);
    }
}
