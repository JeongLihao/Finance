package finance.cash;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.diagnostic.ModuleHealthRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 实体现金：账户余额 ⇄ 背包里的便士硬币和英镑纸币（1:1，账户单位就是便士），现金 ⇄ 绿宝石（浮动汇率）。
 * <p>
 * 现金是不记名的：取出时从账户扣钱、发出物品；存入时收回物品、给账户入账，谁拿着就是谁的。
 * 绿宝石兑换由“现金发行处”（{@link #ISSUER}）做对手方：收绿宝石付现金是新发行货币，
 * 卖绿宝石收现金是回收货币。
 * <p>
 * 汇率浮动，不设每日额度：全服玩家卖给央行的绿宝石越多，收购价越低；买走的越多，卖价越高（每颗按
 * {@code cash.emeraldImpactBps} 复利移动），偏离每个 MC 日向基准价回落 {@code cash.emeraldRecoveryPercent}%。
 * 这样刷绿宝石印钱、或“低价买绿宝石 → 找村民换货 → 卖到市场”的套利，做得越多越不划算，很快自己收敛。
 * 卖 1 颗再买回 1 颗一定亏钱（买价按卖完后的价位算，且基准卖价 ≥ 基准收购价 × 一颗的冲击），来回倒手赚不到钱。
 * 管理员把绿宝石登记成市场商品时，改为直接和市场成交，价格由市场库存决定。
 */
public final class CashService {
    /** 现金发行处：交易记录里绿宝石兑换和存取现金的对方。不是真实账户，不持有余额。 */
    public static final UUID ISSUER =
            UUID.nameUUIDFromBytes("finance:cash-issuer".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** 单次最多取出的金额：200 张 1 磅纸币，避免一次掉落几十组物品。 */
    public static final long MAX_WITHDRAW = 20_000;
    /** 单次最多兑换的绿宝石数（一组）。 */
    public static final long MAX_EMERALDS_PER_ACTION = 64;

    public record Result(boolean success, String message) {
        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /**
     * 当前汇率。baseBid / baseAsk 是不受流量影响时的基准价（跟市场时等于当前价）；
     * recoverPercent 是偏离每天回落的比例，impactBps 是每颗绿宝石移动价格的基点（跟市场时为 0）。
     */
    public record Quote(
            boolean enabled,
            long emeraldToCash,
            long cashToEmerald,
            long baseBid,
            long baseAsk,
            long recoverPercent,
            long impactBps) {
        /** 当前收购价相对基准的偏离，百分比（负数 = 绿宝石卖多了、变便宜）。 */
        public long deviationPercent() {
            return baseBid <= 0 ? 0 : Math.round((emeraldToCash - baseBid) * 100.0 / baseBid);
        }

        /** 给玩家看的一句汇率说明。 */
        public String trendText() {
            if (impactBps == 0) return recoverPercent == 0 ? "汇率跟随市场" : "固定汇率";
            long d = deviationPercent();
            String now = d == 0 ? "现在是基准价" : "现在比基准" + (d > 0 ? "高 " : "低 ") + Math.abs(d) + "%";
            return now + "：大家卖得越多越便宜、买得越多越贵，每天往基准回落 " + recoverPercent + "%";
        }
    }

    private CashService() {}

    /** 市场上的绿宝石商品 ID：注册了就按市场价浮动，没注册（自定义配置）就按配置的固定价。 */
    public static final String EMERALD = "emerald";

    /** 央行发出去、还在玩家手里的钞票总面值（取现、卖绿宝石发出；存入、买绿宝石收回）。物品被销毁时不会减少。 */
    private static long notesOutstanding;

    public static long notesOutstanding() {
        return notesOutstanding;
    }

    public static void restoreNotesOutstanding(long value) {
        notesOutstanding = Math.max(0, value);
    }

    private static void notesIssued(long amount) {
        if (amount > 0) notesOutstanding = finance.util.MathUtil.saturatedAddNonNegative(notesOutstanding, amount);
    }

    private static void notesRetired(long amount) {
        if (amount > 0) notesOutstanding = Math.max(0, notesOutstanding - amount);
    }

    static finance.market.MarketPrice emeraldMarket() {
        if (finance.commodity.CommodityRegistry.getCommodity(EMERALD) == null) return null;
        return finance.market.NpcMarketMaker.getMarketPrice(EMERALD);
    }

    // ---------------------------------------------------------------- 报价

    /** 价格偏离的上下限：最低跌到基准的 1/5，最高涨到 5 倍。 */
    private static final double MIN_FACTOR = 0.2, MAX_FACTOR = 5.0;

    /** 全服绿宝石净流入（卖给央行为正，从央行买走为负），决定固定价模式下的偏离。 */
    private static double pressure;

    /** pressure 最后一次按天回落的 MC 日；MIN_VALUE 表示还没结算过。 */
    private static long pressureDay = Long.MIN_VALUE;

    public static double emeraldPressure() {
        return pressure;
    }

    public static long emeraldPressureDay() {
        return pressureDay;
    }

    public static void restoreEmeraldPressure(double value, long day) {
        pressure = Double.isFinite(value) ? value : 0;
        pressureDay = day;
    }

    private static double impact() {
        return Math.max(0, FinanceConfig.cashEmeraldImpactBps()) / 10_000.0;
    }

    /** 把 pressure 按经过的 MC 日回落到今天。 */
    static void settle(long day) {
        if (pressureDay == Long.MIN_VALUE || day < pressureDay) {
            pressureDay = day;
            return;
        }
        long days = Math.min(365, day - pressureDay);
        if (days > 0) {
            double keep = 1 - Math.max(0, Math.min(100, FinanceConfig.cashEmeraldRecoveryPercent())) / 100.0;
            pressure *= Math.pow(keep, days);
            if (Math.abs(pressure) < 0.01) pressure = 0;
            pressureDay = day;
        }
    }

    /**
     * 卖压不超过价格到顶 / 到底时的值：再往外累积价格也不会变，只会让回落拖得更久。
     * 冲击为 0（固定汇率）时卖压不影响价格，归零。
     */
    static double clampPressure(double p) {
        double impact = impact();
        if (impact <= 0) return 0;
        double max = Math.log(1 / MIN_FACTOR) / impact, min = -Math.log(MAX_FACTOR) / impact;
        return Math.max(min, Math.min(max, p));
    }

    private static double factor(double p) {
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, Math.exp(-impact() * p)));
    }

    /** 固定价模式的基准收购价。 */
    static long baseBid() {
        return Math.max(1, FinanceConfig.cashEmeraldToCash());
    }

    /** 固定价模式的基准卖价：不低于“收购价 × 一颗的冲击”，保证卖 1 颗再买回 1 颗一定亏。 */
    static long baseAsk() {
        long bid = baseBid();
        long minAsk = (long) Math.ceil(bid * Math.exp(impact())) + (impact() == 0 ? 0 : 1);
        return Math.max(Math.max(bid, minAsk), FinanceConfig.cashCashToEmerald());
    }

    private static double bidAt(double p) {
        return baseBid() * factor(p);
    }

    private static double askAt(double p) {
        return baseAsk() * factor(p);
    }

    /** 卖出 n 颗一共能拿多少：逐颗按下跌后的价格累加。 */
    static long sellProceeds(long n) {
        double sum = 0;
        for (long i = 0; i < n; i++) sum += bidAt(pressure + i);
        return Math.max(n, (long) Math.floor(sum));
    }

    /** 买入 n 颗一共要付多少：逐颗按上涨后的价格累加。 */
    static long buyCost(long n) {
        double sum = 0;
        for (long i = 0; i < n; i++) sum += askAt(pressure - i);
        return (long) Math.ceil(sum);
    }

    /** 现在卖 1 颗绿宝石能拿的便士（不推进时间）。 */
    public static long emeraldToCash() {
        finance.market.MarketPrice mp = emeraldMarket();
        if (mp != null) return Math.max(1, mp.getBidPrice());
        return Math.max(1, (long) Math.floor(bidAt(pressure)));
    }

    /** 现在买 1 颗绿宝石要付的便士；总高于收购价。 */
    public static long cashToEmerald() {
        finance.market.MarketPrice mp = emeraldMarket();
        if (mp != null) return Math.max(emeraldToCash() + 1, mp.getAskPrice());
        return Math.max(emeraldToCash() + 1, (long) Math.ceil(askAt(pressure)));
    }

    public static Quote quote(CashPurse purse, long day) {
        settle(day);
        boolean market = emeraldMarket() != null;
        long bid = emeraldToCash(), ask = cashToEmerald();
        return new Quote(
                FinanceConfig.cashEnabled(),
                bid,
                ask,
                market ? bid : baseBid(),
                market ? ask : baseAsk(),
                market ? 0 : Math.max(0, Math.min(100, FinanceConfig.cashEmeraldRecoveryPercent())),
                market ? 0 : Math.max(0, FinanceConfig.cashEmeraldImpactBps()));
    }

    // ---------------------------------------------------------------- 账户 ⇄ 现金

    /** 把随身现金存入账户。amount ≤ 0 表示全部存入；面额凑不齐时拆一张大面额并找零。 */
    public static Result deposit(UUID player, CashPurse purse, long amount) {
        Result blocked = blocked(player, purse);
        if (blocked != null) return blocked;
        long total = purse.cashTotal();
        if (total <= 0) return Result.fail("身上没有现金。");
        long want = amount <= 0 ? total : amount;
        if (want > total) return Result.fail("身上现金只有 " + total + " 便士，不够存入 " + want + "。");
        if (!AccountManager.canDeposit(player, want)) return Result.fail("账户余额已达上限，无法存入。");

        long change = takeExact(purse, want);
        if (!AccountManager.deposit(player, want)) {
            // 理论上不会发生（上面已检查）；把收走的现金按面值还回去，不让玩家吃亏。
            giveAmount(purse, want);
            return Result.fail("入账失败，现金已退回。");
        }
        notesRetired(want);
        AccountManager.addTransactionRecord(
                new TransactionRecord(ISSUER, player, want, TransactionType.CASH_DEPOSIT, player, "现金", want));
        return Result.ok("已存入 " + want + " 便士" + (change > 0 ? "（找零 " + change + "）" : "") + "，账户余额 "
                + AccountManager.getBalance(player) + "。");
    }

    /** 从账户取出现金，按大面额优先发放。 */
    public static Result withdraw(UUID player, CashPurse purse, long amount) {
        Result blocked = blocked(player, purse);
        if (blocked != null) return blocked;
        if (amount <= 0) return Result.fail("取出金额必须大于 0。");
        if (amount > MAX_WITHDRAW) return Result.fail("单次最多取出 " + MAX_WITHDRAW + " 便士。");
        long balance = AccountManager.getBalance(player);
        if (balance < amount) return Result.fail("账户可用余额只有 " + balance + " 便士。");
        if (!AccountManager.withdraw(player, amount)) return Result.fail("扣款失败，请稍后重试。");
        giveAmount(purse, amount);
        notesIssued(amount);
        AccountManager.addTransactionRecord(
                new TransactionRecord(player, ISSUER, amount, TransactionType.CASH_WITHDRAW, player, "现金", amount));
        return Result.ok("已取出 " + amount + " 便士现金，账户余额 " + AccountManager.getBalance(player) + "。");
    }

    // ---------------------------------------------------------------- 现金 ⇄ 绿宝石

    /** 交出绿宝石，换回现金。卖得越多单价越低。 */
    public static Result sellEmeralds(UUID player, CashPurse purse, long count, long day) {
        Result blocked = blocked(player, purse);
        if (blocked != null) return blocked;
        if (count <= 0 || count > MAX_EMERALDS_PER_ACTION)
            return Result.fail("每次可兑换 1～" + MAX_EMERALDS_PER_ACTION + " 颗绿宝石。");
        if (purse.emeralds() < count) return Result.fail("身上只有 " + purse.emeralds() + " 颗绿宝石。");
        settle(day);
        finance.market.MarketPrice market = emeraldMarket();
        long cash = market != null ? Math.multiplyExact(count, emeraldToCash()) : sellProceeds(count);
        if (market != null && AccountManager.getBalance(finance.market.NpcMarketMaker.NPC_UUID) < cash) {
            return Result.fail("市场今天收绿宝石的钱不够了，明天再来。");
        }
        if (!purse.removeEmeralds(count)) return Result.fail("扣除绿宝石失败。");
        if (market != null) {
            // 跟市场：市场出钱、收下绿宝石，库存多了价格就跌
            AccountManager.withdraw(finance.market.NpcMarketMaker.NPC_UUID, cash);
            finance.commodity.CommodityInventoryManager.addCommodity(
                    finance.market.NpcMarketMaker.NPC_UUID, EMERALD, (int) count);
            finance.market.NpcMarketMaker.recordNpcTrade(EMERALD, true, (int) count, cash / count);
            finance.centralbank.ReserveService.recordTrade(cash);
        } else {
            pressure = clampPressure(pressure + count);
        }
        giveAmount(purse, cash);
        notesIssued(cash);
        AccountManager.addTransactionRecord(
                new TransactionRecord(ISSUER, player, cash, TransactionType.EMERALD_SELL, player, "绿宝石", count));
        return Result.ok(
                count + " 颗绿宝石换得 " + cash + " 便士现金（均价 " + cash / count + "）。现在卖 1 颗得 " + emeraldToCash() + "。");
    }

    /** 用现金买绿宝石。买得越多单价越高。 */
    public static Result buyEmeralds(UUID player, CashPurse purse, long count, long day) {
        Result blocked = blocked(player, purse);
        if (blocked != null) return blocked;
        if (count <= 0 || count > MAX_EMERALDS_PER_ACTION)
            return Result.fail("每次可兑换 1～" + MAX_EMERALDS_PER_ACTION + " 颗绿宝石。");
        settle(day);
        finance.market.MarketPrice market = emeraldMarket();
        long cost = market != null ? Math.multiplyExact(count, cashToEmerald()) : buyCost(count);
        long total = purse.cashTotal();
        if (total < cost) return Result.fail("需要 " + cost + " 便士现金，身上只有 " + total + "。可以先从账户取现。");
        if (market != null
                && finance.commodity.CommodityInventoryManager.getCommodityAmount(
                                finance.market.NpcMarketMaker.NPC_UUID, EMERALD)
                        < count) {
            return Result.fail("市场上没有这么多绿宝石了，等别人卖了再来。");
        }
        long change = takeExact(purse, cost);
        if (market != null) {
            finance.commodity.CommodityInventoryManager.removeCommodity(
                    finance.market.NpcMarketMaker.NPC_UUID, EMERALD, (int) count);
            AccountManager.deposit(finance.market.NpcMarketMaker.NPC_UUID, cost);
            finance.market.NpcMarketMaker.recordNpcTrade(EMERALD, false, (int) count, cost / count);
            finance.centralbank.ReserveService.recordTrade(cost);
        } else {
            pressure = clampPressure(pressure - count);
        }
        notesRetired(cost);
        purse.giveEmeralds(count);
        AccountManager.addTransactionRecord(
                new TransactionRecord(player, ISSUER, cost, TransactionType.EMERALD_BUY, player, "绿宝石", count));
        return Result.ok("花 " + cost + " 便士现金买入 " + count + " 颗绿宝石（均价 " + cost / count + "）"
                + (change > 0 ? "，找零 " + change : "") + "。现在买 1 颗付 " + cashToEmerald() + "。");
    }

    // ---------------------------------------------------------------- 等值兑换钞

    /** 一张兑换钞最多能写的金额：1 万磅。 */
    public static final long MAX_EXCHANGE_NOTE = 1_000_000;

    /** 已签发、还没兑现的兑换钞：编号 → 金额。兑现时核销，防止复制钞票重复兑现。 */
    private static final Map<UUID, Long> EXCHANGE_NOTES = new java.util.LinkedHashMap<>();

    public static Map<UUID, Long> exchangeNotes() {
        return java.util.Collections.unmodifiableMap(EXCHANGE_NOTES);
    }

    public static void restoreExchangeNotes(Map<UUID, Long> notes) {
        EXCHANGE_NOTES.clear();
        if (notes == null) return;
        notes.forEach((serial, amount) -> {
            if (serial != null && amount != null && amount > 0 && amount <= MAX_EXCHANGE_NOTE)
                EXCHANGE_NOTES.put(serial, amount);
        });
    }

    /**
     * 签发兑换钞：从账户扣 amount 便士，由调用方把一张空白兑换钞换成写好金额的兑换钞。
     * 兑换钞和现金一样不记名，计入流通中的钞票。
     */
    public static Result issueExchangeNote(UUID player, long amount, UUID serial) {
        Result blocked = blocked(player);
        if (blocked != null) return blocked;
        if (amount <= 0 || amount > MAX_EXCHANGE_NOTE) {
            return Result.fail("金额要在 1～" + MAX_EXCHANGE_NOTE + " 便士之间（最多 " + pounds(MAX_EXCHANGE_NOTE) + "）。");
        }
        if (serial == null || EXCHANGE_NOTES.containsKey(serial)) return Result.fail("兑换钞编号无效，请重试。");
        long balance = AccountManager.getBalance(player);
        if (balance < amount) return Result.fail("账户可用余额只有 " + balance + " 便士。");
        if (!AccountManager.withdraw(player, amount)) return Result.fail("扣款失败，请稍后重试。");
        EXCHANGE_NOTES.put(serial, amount);
        notesIssued(amount);
        AccountManager.addTransactionRecord(
                new TransactionRecord(player, ISSUER, amount, TransactionType.EXCHANGE_NOTE_ISSUE, player, "兑换钞", 1));
        return Result.ok(
                "已签发 " + amount + " 便士（" + pounds(amount) + "）的兑换钞，账户余额 " + AccountManager.getBalance(player) + "。");
    }

    /**
     * 兑现兑换钞：按编号在登记表里核销，把登记的金额存进持有人的账户，由调用方收走这张兑换钞。
     * 没登记过或已经兑过的编号（复制出来的钞票、旧版没有编号的钞票）一律不认。
     */
    public static Result redeemExchangeNote(UUID player, UUID serial) {
        Result blocked = blocked(player);
        if (blocked != null) return blocked;
        Long registered = serial == null ? null : EXCHANGE_NOTES.get(serial);
        if (registered == null) return Result.fail("这张兑换钞没有登记或已经兑现过，不能兑现。");
        long amount = registered;
        if (!AccountManager.canDeposit(player, amount)) return Result.fail("账户余额已达上限，无法兑现。");
        if (!AccountManager.deposit(player, amount)) return Result.fail("入账失败，请稍后重试。");
        EXCHANGE_NOTES.remove(serial);
        notesRetired(amount);
        AccountManager.addTransactionRecord(
                new TransactionRecord(ISSUER, player, amount, TransactionType.EXCHANGE_NOTE_REDEEM, player, "兑换钞", 1));
        return Result.ok(
                "兑换钞已兑现，存入 " + amount + " 便士（" + pounds(amount) + "），账户余额 " + AccountManager.getBalance(player) + "。");
    }

    /** 便士写成英镑：1234 → £12.34。 */
    public static String pounds(long pence) {
        long abs = Math.abs(pence);
        return (pence < 0 ? "-" : "") + "£" + abs / 100 + "." + String.format(java.util.Locale.ROOT, "%02d", abs % 100);
    }

    // ---------------------------------------------------------------- 面额计算

    /** 按大面额优先拆分金额。 */
    public static Map<Denomination, Long> split(long amount) {
        Map<Denomination, Long> out = new EnumMap<>(Denomination.class);
        long rest = Math.max(0, amount);
        for (Denomination d : Denomination.values()) {
            long n = rest / d.value();
            if (n > 0) out.put(d, n);
            rest -= n * d.value();
        }
        return out;
    }

    static void giveAmount(CashPurse purse, long amount) {
        split(amount).forEach(purse::give);
    }

    /**
     * 从随身现金里正好收走 amount（调用方保证 cashTotal ≥ amount）：先按大面额贪心收，
     * 凑不整时再收一张比余数大的最小面额，把差额作为找零发回。返回找零金额。
     */
    static long takeExact(CashPurse purse, long amount) {
        long rest = amount;
        for (Denomination d : Denomination.values()) {
            long n = Math.min(purse.count(d), rest / d.value());
            if (n > 0 && purse.remove(d, n)) rest -= n * d.value();
        }
        if (rest == 0) return 0;
        Denomination[] all = Denomination.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Denomination d = all[i];
            if (d.value() > rest && purse.count(d) > 0 && purse.remove(d, 1)) {
                long change = d.value() - rest;
                giveAmount(purse, change);
                return change;
            }
        }
        throw new IllegalStateException("cash total changed while taking " + amount);
    }

    private static Result blocked(UUID player, CashPurse purse) {
        if (purse == null) return Result.fail("只有玩家可以使用现金。");
        return blocked(player);
    }

    private static Result blocked(UUID player) {
        if (player == null) return Result.fail("只有玩家可以使用现金。");
        if (!FinanceConfig.cashEnabled()) return Result.fail("实体现金未启用（配置 cash.enabled）。");
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.ACCOUNT)) return Result.fail("账户模块已因一致性问题暂停。");
        return null;
    }
}
