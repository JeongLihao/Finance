package finance.simple;

import com.mojang.authlib.GameProfile;
import finance.account.AccountManager;
import finance.bank.BankAccountStatus;
import finance.bank.BankAccountType;
import finance.bank.BankCustomerAccount;
import finance.bank.BankingManager;
import finance.bank.CustomerType;
import finance.bank.TimeDeposit;
import finance.bank.TimeDepositStatus;
import finance.config.FinanceConfig;
import finance.fixedincome.TreasuryBond;
import finance.fixedincome.TreasuryBondManager;
import finance.fixedincome.TreasuryBondStatus;
import finance.fund.FundManager;
import finance.fund.FundState;
import finance.fund.PlayerFundPosition;
import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import finance.stock.Stock;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.util.MoneyFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 身家：钱包、存款、债券、基金、股票、押注加起来值多少。
 * 用在首页的 7 天走势（每天记一次，存在玩家身上）和全服排行榜（每分钟最多算一次，每周播报）。
 */
public final class SimpleWealthService {

    private SimpleWealthService() {}

    // ------------------------------------------------------------ 身家

    /** 一个玩家现在的身家（便士），离线玩家也能算。公司的钱不算，公司股票按市价算。 */
    public static long netWorth(UUID id) {
        long total = AccountManager.getBalance(id);
        var account = AccountManager.getAccounts().get(id);
        if (account != null) total = add(total, account.getFrozenBalance());
        for (BankCustomerAccount a : BankingManager.accounts().values()) {
            if (id.equals(a.ownerId()) && a.ownerType() == CustomerType.PLAYER && a.type() == BankAccountType.DEMAND_DEPOSIT
                    && a.status() != BankAccountStatus.CLOSED) total = add(total, a.balance());
        }
        for (TimeDeposit t : BankingManager.timeDeposits().values()) {
            if (id.equals(t.ownerId()) && (t.status() == TimeDepositStatus.ACTIVE || t.status() == TimeDepositStatus.MATURED)) {
                total = add(total, t.principal());
            }
        }
        for (TreasuryBond b : TreasuryBondManager.bonds().values()) {
            if (b.status() == TreasuryBondStatus.ACTIVE) total = add(total, b.principalByPlayer().getOrDefault(id, 0L));
        }
        Map<String, PlayerFundPosition> funds = FundManager.positions().get(id);
        if (funds != null) {
            Map<String, FundState> states = FundManager.states();
            for (Map.Entry<String, PlayerFundPosition> e : funds.entrySet()) {
                FundState s = states.get(e.getKey());
                if (s != null) total = add(total, multiply(e.getValue().shareUnits(), s.currentNav()) / FundManager.SHARE_SCALE);
            }
        }
        for (Map.Entry<String, StockHolding> e : StockPortfolioManager.getPortfolio(id).entrySet()) {
            Stock stock = StockMarketManager.getStock(e.getKey());
            if (stock != null) total = add(total, multiply(e.getValue().getQuantity(), stock.getAnchorPrice()));
        }
        MarginAccount m = MarginPoolManager.accounts().get(id);
        if (m != null) {
            long equity = m.getCashCollateral() - m.getFinancedDebt() - m.getFinancedInterestOwed() - m.getBadDebt();
            for (Map.Entry<String, Long> e : m.getFinancedShares().entrySet()) equity += multiply(e.getValue(), price(e.getKey()));
            for (long proceeds : m.getShortProceeds().values()) equity += proceeds;
            for (Map.Entry<String, Long> e : m.getShortPositions().entrySet()) equity -= multiply(e.getValue(), price(e.getKey()));
            for (long interest : m.getShortInterestOwed().values()) equity -= interest;
            total = Math.max(0, total + equity);
        }
        return add(total, Math.max(0, finance.futures.FuturesRiskService.equity(id)));
    }

    // ------------------------------------------------------------ 7 天走势

    static final String HISTORY_TAG = "finance_worth";
    static final int HISTORY_DAYS = 8;

    /** 记下今天的身家（同一天反复记只留最新的），返回最近 8 天从旧到新，最后一个是现在。 */
    public static List<Long> history(ServerPlayer player, long day, long worth) {
        CompoundTag data = player.getPersistentData();
        ListTag list = data.getList(HISTORY_TAG, Tag.TAG_COMPOUND);
        List<long[]> points = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getLong("d") != day) points.add(new long[]{t.getLong("d"), t.getLong("v")});
        }
        points.add(new long[]{day, worth});
        points.sort(Comparator.comparingLong(p -> p[0]));
        while (points.size() > HISTORY_DAYS) points.remove(0);
        ListTag out = new ListTag();
        List<Long> values = new ArrayList<>();
        for (long[] p : points) {
            CompoundTag t = new CompoundTag();
            t.putLong("d", p[0]);
            t.putLong("v", p[1]);
            out.add(t);
            values.add(p[1]);
        }
        data.put(HISTORY_TAG, out);
        return values;
    }

    // ------------------------------------------------------------ 排行榜

    public record Rank(UUID id, String name, long worth) {}

    private static List<Rank> cached = List.of();
    private static long cachedTick = Long.MIN_VALUE;

    /** 身家前 5 名（只算有正式玩家档案的账户，不含 NPC 和系统账户）；每分钟最多算一次。 */
    public static synchronized List<Rank> ranks(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick - cachedTick < 1_200 && tick >= cachedTick) return cached;
        cachedTick = tick;
        cached = computeRanks(server, 5);
        return cached;
    }

    static List<Rank> computeRanks(MinecraftServer server, int limit) {
        List<Rank> all = new ArrayList<>();
        var profiles = server.getProfileCache();
        for (UUID id : AccountManager.getAccounts().keySet()) {
            Optional<GameProfile> profile = profiles == null ? Optional.empty() : profiles.get(id);
            if (profile.isEmpty() || profile.get().getName() == null) continue;
            all.add(new Rank(id, profile.get().getName(), netWorth(id)));
        }
        all.sort(Comparator.comparingLong(Rank::worth).reversed());
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** 排行榜几行字：“1. Steve 1234.56 磅”。 */
    public static List<String> rankLines(MinecraftServer server, UUID viewer) {
        List<String> out = new ArrayList<>();
        List<Rank> ranks = ranks(server);
        for (int i = 0; i < ranks.size(); i++) {
            Rank r = ranks.get(i);
            out.add((i + 1) + ". " + r.name() + "  " + MoneyFormat.pounds(r.worth()) + (r.id().equals(viewer) ? "（你）" : ""));
        }
        return out;
    }

    /** 上周初每个人的身家，用来算“本周涨得最多”；服务器重启后从下一周重新算。 */
    private static final Map<UUID, Long> WEEK_START = new HashMap<>();
    private static long lastWeekly = -1;

    /** 每 7 个 MC 日在聊天栏播报一次身家榜和本周涨得最多的人（配置 simpleCenter.weeklyLeaderboard）。 */
    public static synchronized void weekly(MinecraftServer server, long day) {
        if (server == null || day <= 0 || day % 7 != 0 || day == lastWeekly) return;
        lastWeekly = day;
        List<Rank> all = computeRanks(server, Integer.MAX_VALUE);
        boolean announce = FinanceConfig.simpleWeeklyLeaderboard() && !all.isEmpty();
        if (announce) {
            List<String> lines = new ArrayList<>();
            lines.add("§6[金融周榜] 第 " + (day / 7) + " 周身家排行：");
            for (int i = 0; i < Math.min(5, all.size()); i++) {
                lines.add("  " + (i + 1) + ". " + all.get(i).name() + "  " + MoneyFormat.pounds(all.get(i).worth()));
            }
            Rank riser = null;
            long best = 0;
            for (Rank r : all) {
                Long start = WEEK_START.get(r.id());
                if (start != null && r.worth() - start > best) {
                    best = r.worth() - start;
                    riser = r;
                }
            }
            if (riser != null) lines.add("  本周涨得最多：" + riser.name() + " +" + MoneyFormat.pounds(best));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                for (String line : lines) p.sendSystemMessage(Component.literal(line));
            }
        }
        WEEK_START.clear();
        for (Rank r : all) WEEK_START.put(r.id(), r.worth());
    }

    // ------------------------------------------------------------ 小工具

    private static long price(String symbol) {
        Stock s = StockMarketManager.getStock(symbol);
        return s == null ? 0 : s.getAnchorPrice();
    }

    private static long add(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? (b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE) : r;
    }

    private static long multiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
}
