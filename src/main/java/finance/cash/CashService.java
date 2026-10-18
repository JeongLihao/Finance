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
 * 实体现金：账户余额 ⇄ 背包里的便士硬币和英镑纸币（1:1，账户单位就是便士），现金 ⇄ 绿宝石（按配置的买卖价，带每日额度）。
 * <p>
 * 现金是不记名的：取出时从账户扣钱、发出物品；存入时收回物品、给账户入账，谁拿着就是谁的。
 * 绿宝石兑换由“现金发行处”（{@link #ISSUER}）做对手方：收绿宝石付现金是新发行货币，
 * 卖绿宝石收现金是回收货币。买入价总是不低于卖出价，所以来回兑换只会亏点差，不会凭空赚钱；
 * 每日额度防止刷绿宝石（袭击塔、村民交易）无限印钱。
 */
public final class CashService {
    /** 现金发行处：交易记录里绿宝石兑换和存取现金的对方。不是真实账户，不持有余额。 */
    public static final UUID ISSUER = UUID.nameUUIDFromBytes("finance:cash-issuer".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** 单次最多取出的金额：200 张 1 磅纸币，避免一次掉落几十组物品。 */
    public static final long MAX_WITHDRAW = 20_000;
    /** 单次最多兑换的绿宝石数（一组）。 */
    public static final long MAX_EMERALDS_PER_ACTION = 64;

    static final String QUOTA_SELL = "emerald_sell";
    static final String QUOTA_BUY = "emerald_buy";

    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    /** 当前汇率和玩家今天剩余的额度。 */
    public record Quote(boolean enabled, long emeraldToCash, long cashToEmerald,
                        long sellLeft, long buyLeft, long sellLimit, long buyLimit) { }

    private CashService() { }

    /** 市场上的绿宝石商品 ID：注册了就按市场价浮动，没注册（自定义配置）就按配置的固定价。 */
    public static final String EMERALD = "emerald";

    /** 央行发出去、还在玩家手里的钞票总面值（取现、卖绿宝石发出；存入、买绿宝石收回）。物品被销毁时不会减少。 */
    private static long notesOutstanding;

    public static long notesOutstanding() { return notesOutstanding; }

    public static void restoreNotesOutstanding(long value) { notesOutstanding = Math.max(0, value); }

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

    /** 每颗绿宝石换得的便士：市场收购价（浮动）；没有绿宝石市场时用配置的固定价。 */
    public static long emeraldToCash() {
        finance.market.MarketPrice mp = emeraldMarket();
        if (mp != null) return Math.max(1, mp.getBidPrice());
        return Math.max(1, FinanceConfig.cashEmeraldToCash());
    }

    /** 买一颗绿宝石要付的便士；不低于 {@link #emeraldToCash()}，保证没有来回套利。 */
    public static long cashToEmerald() {
        finance.market.MarketPrice mp = emeraldMarket();
        if (mp != null) return Math.max(emeraldToCash() + 1, mp.getAskPrice());
        return Math.max(emeraldToCash(), FinanceConfig.cashCashToEmerald());
    }

    public static Quote quote(CashPurse purse, long day) {
        long sellLimit = Math.max(0, FinanceConfig.cashDailyEmeraldSellLimit());
        long buyLimit = Math.max(0, FinanceConfig.cashDailyEmeraldBuyLimit());
        long sold = purse == null ? 0 : purse.quotaUsed(QUOTA_SELL, day);
        long bought = purse == null ? 0 : purse.quotaUsed(QUOTA_BUY, day);
        return new Quote(FinanceConfig.cashEnabled(), emeraldToCash(), cashToEmerald(),
                Math.max(0, sellLimit - sold), Math.max(0, buyLimit - bought), sellLimit, buyLimit);
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
        AccountManager.addTransactionRecord(new TransactionRecord(ISSUER, player, want, TransactionType.CASH_DEPOSIT,
                player, "现金", want));
        return Result.ok("已存入 " + want + " 便士" + (change > 0 ? "（找零 " + change + "）" : "")
                + "，账户余额 " + AccountManager.getBalance(player) + "。");
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
        AccountManager.addTransactionRecord(new TransactionRecord(player, ISSUER, amount, TransactionType.CASH_WITHDRAW,
                player, "现金", amount));
        return Result.ok("已取出 " + amount + " 便士现金，账户余额 " + AccountManager.getBalance(player) + "。");
    }

    // ---------------------------------------------------------------- 现金 ⇄ 绿宝石

    /** 交出绿宝石，换回现金。 */
    public static Result sellEmeralds(UUID player, CashPurse purse, long count, long day) {
        Result blocked = blocked(player, purse);
        if (blocked != null) return blocked;
        if (count <= 0 || count > MAX_EMERALDS_PER_ACTION) return Result.fail("每次可兑换 1～" + MAX_EMERALDS_PER_ACTION + " 颗绿宝石。");
        Quote q = quote(purse, day);
        if (count > q.sellLeft()) return Result.fail("今天还能卖出 " + q.sellLeft() + " 颗绿宝石（每日 " + q.sellLimit() + " 颗）。");
        if (purse.emeralds() < count) return Result.fail("身上只有 " + purse.emeralds() + " 颗绿宝石。");
        long cash = Math.multiplyExact(count, q.emeraldToCash());
        finance.market.MarketPrice market = emeraldMarket();
        if (market != null && AccountManager.getBalance(finance.market.NpcMarketMaker.NPC_UUID) < cash) {
            return Result.fail("市场今天收绿宝石的钱不够了，明天再来。");
        }
        if (!purse.removeEmeralds(count)) return Result.fail("扣除绿宝石失败。");
        if (market != null) {
            // 浮动价：市场出钱、收下绿宝石，库存多了价格就跌
            AccountManager.withdraw(finance.market.NpcMarketMaker.NPC_UUID, cash);
            finance.commodity.CommodityInventoryManager.addCommodity(finance.market.NpcMarketMaker.NPC_UUID, EMERALD, (int) count);
            finance.market.NpcMarketMaker.recordNpcTrade(EMERALD, true, (int) count, q.emeraldToCash());
            finance.centralbank.ReserveService.recordTrade(cash);
        }
        giveAmount(purse, cash);
        notesIssued(cash);
        purse.setQuotaUsed(QUOTA_SELL, day, purse.quotaUsed(QUOTA_SELL, day) + count);
        AccountManager.addTransactionRecord(new TransactionRecord(ISSUER, player, cash, TransactionType.EMERALD_SELL,
                player, "绿宝石", count));
        return Result.ok(count + " 颗绿宝石换得 " + cash + " 便士现金。今天还能卖 " + (q.sellLeft() - count) + " 颗。");
    }

    /** 用现金买绿宝石。 */
    public static Result buyEmeralds(UUID player, CashPurse purse, long count, long day) {
        Result blocked = blocked(player, purse);
        if (blocked != null) return blocked;
        if (count <= 0 || count > MAX_EMERALDS_PER_ACTION) return Result.fail("每次可兑换 1～" + MAX_EMERALDS_PER_ACTION + " 颗绿宝石。");
        Quote q = quote(purse, day);
        if (count > q.buyLeft()) return Result.fail("今天还能买入 " + q.buyLeft() + " 颗绿宝石（每日 " + q.buyLimit() + " 颗）。");
        long cost = Math.multiplyExact(count, q.cashToEmerald());
        long total = purse.cashTotal();
        if (total < cost) return Result.fail("需要 " + cost + " 便士现金，身上只有 " + total + "。可以先从账户取现。");
        finance.market.MarketPrice market = emeraldMarket();
        if (market != null && finance.commodity.CommodityInventoryManager.getCommodityAmount(finance.market.NpcMarketMaker.NPC_UUID, EMERALD) < count) {
            return Result.fail("市场上没有这么多绿宝石了，等别人卖了再来。");
        }
        long change = takeExact(purse, cost);
        if (market != null) {
            finance.commodity.CommodityInventoryManager.removeCommodity(finance.market.NpcMarketMaker.NPC_UUID, EMERALD, (int) count);
            AccountManager.deposit(finance.market.NpcMarketMaker.NPC_UUID, cost);
            finance.market.NpcMarketMaker.recordNpcTrade(EMERALD, false, (int) count, q.cashToEmerald());
            finance.centralbank.ReserveService.recordTrade(cost);
        }
        notesRetired(cost);
        purse.giveEmeralds(count);
        purse.setQuotaUsed(QUOTA_BUY, day, purse.quotaUsed(QUOTA_BUY, day) + count);
        AccountManager.addTransactionRecord(new TransactionRecord(player, ISSUER, cost, TransactionType.EMERALD_BUY,
                player, "绿宝石", count));
        return Result.ok("花 " + cost + " 便士现金买入 " + count + " 颗绿宝石" + (change > 0 ? "（找零 " + change + "）" : "")
                + "。今天还能买 " + (q.buyLeft() - count) + " 颗。");
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
        if (player == null || purse == null) return Result.fail("只有玩家可以使用现金。");
        if (!FinanceConfig.cashEnabled()) return Result.fail("实体现金未启用（配置 cash.enabled）。");
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.ACCOUNT)) return Result.fail("账户模块已因一致性问题暂停。");
        return null;
    }
}
