package finance.event;

import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.market.NpcMarketMaker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 事件管理器 —— 事件压力系统 + 调度 + 激活/过期管理。
 *
 * <h3>事件机制（三个独立计时器）</h3>
 * <ul>
 *   <li>每个 MC 天三个计时器各 +1</li>
 *   <li>一级阈值 9 MC天 ≈ 3小时（2~4h 区间）</li>
 *   <li>二级阈值 45 MC天 ≈ 15小时（10~20h 区间）</li>
 *   <li>黑天鹅阈值 225 MC天 ≈ 75小时（50~100h 区间）</li>
 *   <li>各自独立触发和重置，互不干扰</li>
 * </ul>
 */
public class EventManager {

    /** 一级事件触发间隔（MC天）：9天 ≈ 3小时 */
    private static final int TIMER_MINOR = 9;
    /** 二级事件触发间隔（MC天）：45天 ≈ 15小时 */
    private static final int TIMER_MAJOR = 45;
    /** 黑天鹅触发间隔（MC天）：225天 ≈ 75小时 */
    private static final int TIMER_BLACK_SWAN = 225;

    private static final Random RANDOM = new Random();

    private static int timerMinor = 0;
    private static int timerMajor = 0;
    private static int timerBlackSwan = 0;

    private static final List<MarketEvent> activeEvents = new ArrayList<>();

    // ================================================================
    // Tick
    // ================================================================

    /**
     * 每个 MC 天调用一次（约 24000 ticks）。
     */
    public static void onDayTick(MinecraftServer server) {
        timerMinor++;
        timerMajor++;
        timerBlackSwan++;

        // ---- 衰减已激活事件 ----
        Iterator<MarketEvent> iter = activeEvents.iterator();
        while (iter.hasNext()) {
            MarketEvent ev = iter.next();
            ev.tickDay();
            if (ev.isExpired()) {
                if (ev.affectsAll()) {
                    NpcMarketMaker.removeEventFromAll(ev);
                } else {
                    NpcMarketMaker.removeEvent(ev.getCommodityId(), ev);
                }

                String endMsg;
                if (ev.affectsAll()) {
                    endMsg = "【财经快讯】" + ev.getName() + "影响消退，所有商品价格恢复正常。";
                } else {
                    endMsg = "【财经快讯】" + ev.getName() + "影响消退，"
                            + ev.getCommodityId() + "价格恢复正常。";
                }
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal(endMsg), false);

                iter.remove();
            }
        }

        // ---- 独立计时器触发（互不干扰，可在同一天同时触发） ----
        List<String> commodityIds = CommodityRegistry.getAllCommodities()
                .stream().map(c -> c.getId()).toList();

        if (!commodityIds.isEmpty()) {
            if (timerBlackSwan >= TIMER_BLACK_SWAN) {
                timerBlackSwan = 0;
                fireEvent(EventTier.BLACK_SWAN, commodityIds, server);
            }
            if (timerMajor >= TIMER_MAJOR) {
                timerMajor = 0;
                fireEvent(EventTier.MAJOR, commodityIds, server);
            }
            if (timerMinor >= TIMER_MINOR) {
                timerMinor = 0;
                fireEvent(EventTier.MINOR, commodityIds, server);
            }
        }

        EconomySavedData.markDirty();
    }

    private static void fireEvent(EventTier tier, List<String> commodityIds,
                                   MinecraftServer server) {
        MarketEvent newEvent = EventTemplates.roll(tier, commodityIds);
        if (newEvent == null) return;

        activeEvents.add(newEvent);

        if (newEvent.affectsAll()) {
            NpcMarketMaker.applyEventToAll(newEvent);
        } else {
            NpcMarketMaker.applyEvent(newEvent.getCommodityId(), newEvent);
        }

        broadcastEvent(server, newEvent);
    }

    // ================================================================
    // 广播
    // ================================================================

    private static void broadcastEvent(MinecraftServer server, MarketEvent event) {
        String tierLabel = switch (event.getTier()) {
            case MINOR -> "";
            case MAJOR -> "[重大] ";
            case BLACK_SWAN -> "[黑天鹅] ";
        };

        String scope;
        if (event.affectsAll()) {
            scope = "全部商品";
        } else {
            scope = event.getCommodityId();
        }

        String msg = "【财经快讯】" + tierLabel + event.getDescription()
                + "，" + scope + "价格" + event.getChangePct()
                + "！预计持续" + event.getDurationDesc() + "。";

        server.getPlayerList().broadcastSystemMessage(
                Component.literal(msg), false);
    }

    // ================================================================
    // 查询
    // ================================================================

    public static int getTimerMinor() { return timerMinor; }
    public static int getTimerMajor() { return timerMajor; }
    public static int getTimerBlackSwan() { return timerBlackSwan; }

    public static String getTimerSummary() {
        return "minor:" + timerMinor + "/" + TIMER_MINOR
                + " major:" + timerMajor + "/" + TIMER_MAJOR
                + " swan:" + timerBlackSwan + "/" + TIMER_BLACK_SWAN;
    }

    /** 测试命令：直接触发指定等级的事件 */
    public static void fireTestEvent(EventTier tier, MinecraftServer server) {
        List<String> commodityIds = CommodityRegistry.getAllCommodities()
                .stream().map(c -> c.getId()).toList();
        if (commodityIds.isEmpty()) return;

        fireEvent(tier, commodityIds, server);
        EconomySavedData.markDirty();
    }

    public static List<MarketEvent> getActiveEvents() { return activeEvents; }

    /** 获取影响指定商品的活跃事件，无则返回 null */
    public static MarketEvent getActiveEvent(String commodityId) {
        for (MarketEvent ev : activeEvents) {
            if (ev.affectsAll() || commodityId.equals(ev.getCommodityId())) {
                return ev;
            }
        }
        return null;
    }

    // ================================================================
    // 持久化支持
    // ================================================================

    public static void setTimers(int minor, int major, int blackSwan) {
        timerMinor = minor;
        timerMajor = major;
        timerBlackSwan = blackSwan;
    }

    public static void clearActiveEvents() {
        activeEvents.clear();
    }

    public static void addActiveEventDirect(MarketEvent event) {
        activeEvents.add(event);
    }
}
