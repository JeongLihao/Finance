package finance.client.simple;

import finance.simple.SimpleCenterService;
import finance.simple.SimpleUnlocks;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** 简单模式金融中心在客户端的状态：服务端推来的首页数据、飘字、玩家是否显示高级功能。 */
public final class SimpleClientState {

    private SimpleClientState() {}

    private static SimpleCenterService.Status status;

    /** 一条飘字：“+8 磅 32 便士”，显示约 1.6 秒。 */
    public record Floater(long delta, long createdAt) {}
    private static final List<Floater> FLOATERS = new ArrayList<>();
    public static final long FLOAT_MILLIS = 1_600;

    /** “上次打开以来”的小结：收到后在首页弹出，点“知道了”关掉。 */
    private static List<String> pendingSummary = List.of();

    public static void accept(SimpleCenterService.Status s) {
        status = s;
        if (s == null) return;
        // 刚解锁的玩法排在最前面，用“解锁了「”开头，首页小结卡据此换标题
        List<String> lines = new ArrayList<>();
        for (String id : s.unlockedNow()) {
            SimpleUnlocks.Group g = SimpleUnlocks.Group.of(id);
            if (g != null) lines.add("解锁了「" + g.label + "」：" + g.intro);
        }
        if (lines.isEmpty() && s.summary().isEmpty()) return;
        lines.addAll(s.summary());
        pendingSummary = List.copyOf(lines.subList(0, Math.min(6, lines.size())));
    }

    public static List<String> pendingSummary() { return pendingSummary; }

    public static void dismissSummary() { pendingSummary = List.of(); }

    /** 还没收到时返回 null。 */
    public static SimpleCenterService.Status status() { return status; }

    public static void clear() { status = null; FLOATERS.clear(); pendingSummary = List.of(); }

    public static void pushMoney(long delta) {
        if (delta == 0) return;
        FLOATERS.add(new Floater(delta, System.currentTimeMillis()));
        if (FLOATERS.size() > 6) FLOATERS.remove(0);
    }

    public static List<Floater> floaters() {
        long now = System.currentTimeMillis();
        FLOATERS.removeIf(f -> now - f.createdAt() > FLOAT_MILLIS);
        return FLOATERS;
    }

    // ---- 高级功能入口：服务器 advancedMode = player 时由玩家自己决定，存在本机 config/finance-client.properties

    private static Boolean showAdvanced;

    /** 金融中心是否显示“高级”按钮。 */
    public static boolean advancedAllowed() {
        return status != null && "always".equals(status.advancedMode());
    }

    /** 服务器是否让玩家自己选择。 */
    public static boolean playerChooses() {
        return status == null || "player".equals(status.advancedMode());
    }

    public static boolean playerWantsAdvanced() {
        if (showAdvanced == null) showAdvanced = Boolean.parseBoolean(load().getProperty("showAdvanced", "false"));
        return showAdvanced;
    }

    public static void setPlayerWantsAdvanced(boolean value) {
        showAdvanced = value;
        Properties props = load();
        props.setProperty("showAdvanced", Boolean.toString(value));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            props.store(w, "Finance client preferences");
        } catch (IOException | RuntimeException ignored) {
            // 写不了就只在本次游戏内生效
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("finance-client.properties");
    }

    private static Properties load() {
        Properties props = new Properties();
        try {
            Path f = file();
            if (Files.exists(f)) try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { props.load(r); }
        } catch (IOException | RuntimeException ignored) {
        }
        return props;
    }
}
