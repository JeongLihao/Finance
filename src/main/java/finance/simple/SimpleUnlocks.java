package finance.simple;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.margin.MarginPoolManager;
import finance.stock.StockHolding;
import finance.stock.StockPortfolioManager;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 进阶玩法按进度解锁：存过理财 → 进取理财，买过股票 → 押注，公司上市 → 大老板。
 * 解锁过的玩法记在玩家身上，之后卖光股票也不会再锁回去。
 * 配置 simpleCenter.extras 里的玩法不用解锁，simpleCenter.disabled 里的玩法整个服务器关掉。
 */
public final class SimpleUnlocks {

    private SimpleUnlocks() {}

    /** 三组进阶玩法；members 是旧配置里的单项名，服务端校验和客户端按钮都按单项名判断。 */
    public enum Group {
        GROWTH(
                "growth",
                "进取理财",
                "存过一次理财后解锁",
                "理财多了「进取」一档：买基金跟着股市涨跌，「均衡」一档也能买央行债券。收益更高，也可能亏。",
                List.of("bonds", "fund")),
        BET(
                "bet",
                "押注",
                "买过一次股票后解锁",
                "在股票或商品详情里点「看涨」「看跌」：投一点钱押价格方向，押对了赚翻倍，押错会亏，亏太多会被强制结束。",
                List.of("margin", "futures")),
        BOSS(
                "boss",
                "大老板",
                "公司上市后解锁",
                "「我的公司 → 当老板」会提醒你什么时候该收购、回购、投保、开银行，点一下就办。",
                List.of("shareholder", "takeover", "bank", "insurance", "wage"));
        public final String id, label, howTo, intro;
        public final List<String> members;

        Group(String id, String label, String howTo, String intro, List<String> members) {
            this.id = id;
            this.label = label;
            this.howTo = howTo;
            this.intro = intro;
            this.members = members;
        }

        /** 组名或单项名 → 所属的组；不认识返回 null。 */
        public static Group of(String name) {
            if (name == null) return null;
            String n = name.toLowerCase(Locale.ROOT);
            for (Group g : values()) if (g.id.equals(n) || g.members.contains(n)) return g;
            return null;
        }
    }

    // ------------------------------------------------------------ 纯规则（测试直接调用）

    /** 现在达到了哪些进度。 */
    public static EnumSet<Group> reached(boolean hasSavings, boolean holdsStock, boolean ownsPublicCompany) {
        EnumSet<Group> out = EnumSet.noneOf(Group.class);
        if (hasSavings) out.add(Group.GROWTH);
        if (holdsStock) out.add(Group.BET);
        if (ownsPublicCompany) out.add(Group.BOSS);
        return out;
    }

    /** 名字（组名或单项名）是否在配置列表里；all 匹配一切。 */
    static boolean listed(Set<String> list, String name) {
        if (list.contains("all") || list.contains(name)) return true;
        Group g = Group.of(name);
        return g != null && list.contains(g.id);
    }

    /** 某个单项玩法对这个玩家是否可用。 */
    public static boolean allowed(Set<Group> unlocked, Set<String> instant, Set<String> disabled, String name) {
        if (listed(disabled, name)) return false;
        if (listed(instant, name)) return true;
        Group g = Group.of(name);
        return g != null && unlocked.contains(g);
    }

    /** 发给客户端的可用名单：组名和单项名都列上，客户端按单项名判断按钮。 */
    public static List<String> allowedNames(Set<Group> unlocked, Set<String> instant, Set<String> disabled) {
        List<String> out = new ArrayList<>();
        for (Group g : Group.values()) {
            boolean any = false;
            for (String m : g.members) {
                if (allowed(unlocked, instant, disabled, m)) {
                    out.add(m);
                    any = true;
                }
            }
            if (any) out.add(g.id);
        }
        return out;
    }

    /** 还没解锁、但做到进度就能解锁的组（整组被关掉或直接打开的不算）。 */
    public static List<String> lockedIds(Set<Group> unlocked, Set<String> instant, Set<String> disabled) {
        List<String> out = new ArrayList<>();
        for (Group g : Group.values()) {
            if (unlocked.contains(g)) continue;
            boolean anyOpenable = false, anyInstant = false;
            for (String m : g.members) {
                if (!listed(disabled, m)) anyOpenable = true;
                if (listed(instant, m) && !listed(disabled, m)) anyInstant = true;
            }
            if (anyOpenable && !anyInstant) out.add(g.id);
        }
        return out;
    }

    // ------------------------------------------------------------ 服务端：读写玩家进度

    static final String UNLOCKED_TAG = "finance_unlocked", SEEN_TAG = "finance_unlock_seen";

    /** 这个玩家解锁过的组：记录里已有的加上现在达到的，新达到的顺手记下。 */
    public static EnumSet<Group> unlocked(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        EnumSet<Group> out = read(data.getString(UNLOCKED_TAG));
        out.addAll(reached(hasSavings(player), holdsStock(player.getUUID()), ownsPublicCompany(player.getUUID())));
        data.putString(UNLOCKED_TAG, write(out));
        return out;
    }

    /** 刚解锁、还没告诉玩家的组；取出后记为已告诉。整组被关掉或本来就直接打开的不提示。 */
    public static List<Group> takeNew(ServerPlayer player, Set<Group> unlocked) {
        CompoundTag data = player.getPersistentData();
        EnumSet<Group> seen = read(data.getString(SEEN_TAG));
        List<String> locked =
                lockedIds(EnumSet.noneOf(Group.class), FinanceConfig.simpleExtras(), FinanceConfig.simpleDisabled());
        List<Group> fresh = new ArrayList<>();
        for (Group g : unlocked) {
            if (seen.contains(g)) continue;
            seen.add(g);
            if (locked.contains(g.id)) fresh.add(g);
        }
        data.putString(SEEN_TAG, write(seen));
        return fresh;
    }

    public static boolean allowed(ServerPlayer player, String name) {
        return allowed(unlocked(player), FinanceConfig.simpleExtras(), FinanceConfig.simpleDisabled(), name);
    }

    /** 服务器层面这个单项有没有被关掉（不看玩家进度）。 */
    public static boolean disabled(String name) {
        return listed(FinanceConfig.simpleDisabled(), name);
    }

    static boolean hasSavings(ServerPlayer player) {
        return !SimpleInvestService.holdings(player).isEmpty();
    }

    static boolean holdsStock(UUID player) {
        for (StockHolding h : StockPortfolioManager.getPortfolio(player).values()) if (h.getQuantity() > 0) return true;
        var margin = MarginPoolManager.accounts().get(player);
        return margin != null
                && (!margin.getFinancedShares().isEmpty()
                        || !margin.getShortPositions().isEmpty());
    }

    static boolean ownsPublicCompany(UUID player) {
        Company c = CompanyManager.getCompanyByOwner(player);
        return c != null && c.isPublic();
    }

    static EnumSet<Group> read(String csv) {
        EnumSet<Group> out = EnumSet.noneOf(Group.class);
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            Group g = Group.of(part.trim());
            if (g != null) out.add(g);
        }
        return out;
    }

    static String write(Set<Group> groups) {
        List<String> ids = new ArrayList<>();
        for (Group g : groups) ids.add(g.id);
        return String.join(",", ids);
    }
}
