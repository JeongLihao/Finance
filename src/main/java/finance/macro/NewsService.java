package finance.macro;

import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 《金融周报》报社：一周里发生的事先进新闻池，每 {@code newsIntervalDays} 天出一期周刊；
 * 大事（罢工、饥荒、法案、紧缩）立刻出号外，号外之间至少隔 {@code newsExtraGapDays} 天，
 * 赶上冷却时只在聊天栏简短提一句，详情等周刊。报纸是实体书，到贸易站右键、金融中心“更多操作”或 {@code /macro news} 领取，每期每人一份。
 */
public final class NewsService {
    public static final int MAX_POOL = 64, MAX_ARCHIVE = 8, MAX_CLAIMS = 512, MAX_PERSONAL_PLAYERS = 512, MAX_PERSONAL_LINES = 8;
    public static final int MAX_RADAR_PLAYERS = 1_024;

    /** 新闻池里的一条新闻。 */
    public record Item(long day, int score, String headline, String body) { }

    /** 已出版的一期。personal 是出版时记下的各玩家个人新闻。 */
    public record Issue(int number, long day, boolean extra, String headline, List<String> pages,
                        Map<UUID, List<String>> personal) { }

    private static final List<Item> POOL = new ArrayList<>();
    private static final Map<UUID, List<String>> PERSONAL = new LinkedHashMap<>();
    private static final ArrayDeque<Issue> ARCHIVE = new ArrayDeque<>();
    private static final Map<Integer, Set<UUID>> CLAIMED = new LinkedHashMap<>();
    private static final Map<UUID, Long> RADAR_DAY = new LinkedHashMap<>();
    private static Item pendingExtra;
    private static int counter;
    private static long lastWeeklyDay = Long.MIN_VALUE;
    private static long lastExtraDay = Long.MIN_VALUE;

    private NewsService() { }

    public static boolean enabled() {
        return FinanceConfig.macroNewsEnabled() && MacroEconomyService.active();
    }

    // ================================================================ 采集

    /**
     * 记一条新闻。major 为 true 的是大事，当天会考虑出号外。
     */
    public static void report(int score, String headline, String body, boolean major) {
        if (headline == null || headline.isBlank()) return;
        Item item = new Item(currentDay(), score, headline, body == null ? "" : body);
        POOL.add(item);
        POOL.sort(Comparator.comparingInt(Item::score).reversed());
        while (POOL.size() > MAX_POOL) POOL.remove(POOL.size() - 1);
        if (major && (pendingExtra == null || score > pendingExtra.score())) pendingExtra = item;
    }

    /** 记一条只有某玩家会在“你的生意”版看到的新闻。 */
    public static void recordPersonal(UUID player, String line) {
        if (player == null || line == null || line.isBlank()) return;
        List<String> lines = PERSONAL.get(player);
        if (lines == null) {
            if (PERSONAL.size() >= MAX_PERSONAL_PLAYERS) return;
            lines = new ArrayList<>();
            PERSONAL.put(player, lines);
        }
        lines.add(line);
        while (lines.size() > MAX_PERSONAL_LINES) lines.remove(0);
    }

    private static long observedDay = 0;

    static long currentDay() { return observedDay; }

    /** 宏观日结开始时记下今天是第几天，供 report 打日期。 */
    static void observeDay(long day) { observedDay = Math.max(0, day); }

    // ================================================================ 出版

    /** 每天日结末尾调用。 */
    public static void processDay(MinecraftServer server, long day) {
        if (!enabled()) {
            pendingExtra = null;
            return;
        }
        observeDay(day);
        if (lastWeeklyDay == Long.MIN_VALUE) lastWeeklyDay = day;
        Item extra = pendingExtra;
        pendingExtra = null;
        if (day - lastWeeklyDay >= Math.max(1, FinanceConfig.macroNewsIntervalDays())) {
            lastWeeklyDay = day;
            Issue issue = NewsWriter.weekly(++counter, day, List.copyOf(POOL), snapshotPersonal());
            publish(server, issue);
            POOL.clear();
            PERSONAL.clear();
            return;
        }
        if (extra == null) return;
        boolean allowed = lastExtraDay == Long.MIN_VALUE || day - lastExtraDay >= Math.max(1, FinanceConfig.macroNewsExtraGapDays());
        if (allowed) {
            lastExtraDay = day;
            publish(server, NewsWriter.extra(++counter, day, extra));
        } else {
            MacroWorldFeedback.announce("finance.macro.announce.brief", MacroWorldFeedback.Tone.BAD, extra.headline());
        }
    }

    private static void publish(MinecraftServer server, Issue issue) {
        ARCHIVE.addLast(issue);
        while (ARCHIVE.size() > MAX_ARCHIVE) {
            Issue old = ARCHIVE.removeFirst();
            CLAIMED.remove(old.number());
        }
        String key = issue.extra() ? "finance.macro.announce.extra" : "finance.macro.announce.weekly";
        MacroWorldFeedback.announce(key, issue.extra() ? MacroWorldFeedback.Tone.BAD : MacroWorldFeedback.Tone.GOOD,
                String.valueOf(issue.number()), issue.headline());
        EconomySavedData.markDirty();
    }

    private static Map<UUID, List<String>> snapshotPersonal() {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        PERSONAL.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return out;
    }

    // ================================================================ 领取

    /** 还没领的期号（旧 → 新）。 */
    public static List<Issue> unclaimed(UUID player) {
        List<Issue> out = new ArrayList<>();
        if (player == null) return out;
        for (Issue issue : ARCHIVE) {
            Set<UUID> claimed = CLAIMED.get(issue.number());
            if (claimed == null || !claimed.contains(player)) out.add(issue);
        }
        return out;
    }

    /** 领取所有没领过的报纸，放进背包（放不下就掉在脚下）。返回领了几份。 */
    public static int claim(ServerPlayer player) {
        if (player == null) return 0;
        int n = 0;
        for (Issue issue : unclaimed(player.getUUID())) {
            Set<UUID> claimed = CLAIMED.computeIfAbsent(issue.number(), k -> new LinkedHashSet<>());
            if (claimed.size() >= MAX_CLAIMS) continue;
            claimed.add(player.getUUID());
            ItemStack book = book(issue, NewsWriter.personalPage(player.getUUID(), issue));
            if (!player.getInventory().add(book)) player.drop(book, false);
            n++;
        }
        if (n > 0) EconomySavedData.markDirty();
        return n;
    }

    /** 到贸易站时顺手领报。 */
    public static void claimOnVisit(ServerPlayer player) {
        if (!enabled()) return;
        int n = claim(player);
        if (n > 0) player.displayClientMessage(Component.translatable("finance.macro.news.claimed", n), true);
    }

    /** 上线提醒有新报纸。 */
    public static void notifyOnLogin(ServerPlayer player) {
        if (player == null || !enabled()) return;
        int n = unclaimed(player.getUUID()).size();
        if (n > 0) player.sendSystemMessage(Component.translatable("finance.macro.news.waiting", n));
    }

    static ItemStack book(Issue issue, String personalPage) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", (issue.extra() ? "号外 第" : "金融周报 第") + issue.number() + "期");
        tag.putString("author", "金融周报社");
        tag.putBoolean("resolved", true);
        ListTag pages = new ListTag();
        List<String> all = new ArrayList<>(issue.pages());
        if (personalPage != null && !personalPage.isBlank()) all.addAll(NewsWriter.paginate(personalPage));
        for (String page : all) pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        tag.put("pages", pages);
        return stack;
    }

    // ================================================================ 商情调查

    /** 上次做商情调查的日子；没做过返回 Long.MIN_VALUE。 */
    public static long lastRadarDay(UUID player) {
        Long d = player == null ? null : RADAR_DAY.get(player);
        return d == null ? Long.MIN_VALUE : d;
    }

    static void markRadar(UUID player, long day) {
        if (player == null) return;
        RADAR_DAY.remove(player);
        while (RADAR_DAY.size() >= MAX_RADAR_PLAYERS) RADAR_DAY.remove(RADAR_DAY.keySet().iterator().next());
        RADAR_DAY.put(player, day);
    }

    // ================================================================ 存档与隔离

    public static List<Issue> archive() { return new ArrayList<>(ARCHIVE); }
    public static List<Item> pool() { return new ArrayList<>(POOL); }
    public static int counter() { return counter; }
    public static long lastWeeklyDay() { return lastWeeklyDay; }
    public static long lastExtraDay() { return lastExtraDay; }
    public static Map<Integer, Set<UUID>> claimedDirect() { return CLAIMED; }
    public static Map<UUID, List<String>> personalDirect() { return PERSONAL; }
    public static Map<UUID, Long> radarDirect() { return RADAR_DAY; }

    static void restore(int count, long weekly, long extra, List<Item> pool, List<Issue> issues,
                        Map<Integer, Set<UUID>> claimed, Map<UUID, List<String>> personal, Map<UUID, Long> radar) {
        clearDirect();
        counter = Math.max(0, count);
        lastWeeklyDay = weekly;
        lastExtraDay = extra;
        for (Item i : pool) if (POOL.size() < MAX_POOL) POOL.add(i);
        for (Issue i : issues) {
            ARCHIVE.addLast(i);
            while (ARCHIVE.size() > MAX_ARCHIVE) ARCHIVE.removeFirst();
        }
        claimed.forEach((k, v) -> { if (ARCHIVE.stream().anyMatch(i -> i.number() == k)) CLAIMED.put(k, new LinkedHashSet<>(v)); });
        personal.forEach((k, v) -> { if (PERSONAL.size() < MAX_PERSONAL_PLAYERS) PERSONAL.put(k, new ArrayList<>(v)); });
        radar.forEach((k, v) -> { if (RADAR_DAY.size() < MAX_RADAR_PLAYERS) RADAR_DAY.put(k, v); });
    }

    public static void clearDirect() {
        POOL.clear();
        PERSONAL.clear();
        ARCHIVE.clear();
        CLAIMED.clear();
        RADAR_DAY.clear();
        pendingExtra = null;
        counter = 0;
        lastWeeklyDay = Long.MIN_VALUE;
        lastExtraDay = Long.MIN_VALUE;
        observedDay = 0;
    }
}
