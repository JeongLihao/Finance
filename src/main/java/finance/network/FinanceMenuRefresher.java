package finance.network;

import finance.FinanceMod;
import finance.gui.FinanceGuiOpener;
import finance.gui.FinanceMenu;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 让开着的金融中心保持最新：玩家操作后的下一 tick 立即刷新，平时每 {@value #PERIOD_TICKS} tick 刷新一次。
 */
@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID)
public final class FinanceMenuRefresher {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FinanceMenuRefresher.class);
    static final int PERIOD_TICKS = 60;
    private static final Set<UUID> DIRTY = new HashSet<>();

    private FinanceMenuRefresher() {}

    /** 玩家的操作已经处理完（服务端已回复结果），下一 tick 推送新数字。 */
    public static synchronized void markDirty(ServerPlayer player) {
        if (player != null && player.containerMenu instanceof FinanceMenu) DIRTY.add(player.getUUID());
    }

    static synchronized boolean takeDirty(UUID playerId) {
        return DIRTY.remove(playerId);
    }

    /** 本 tick 是否轮到该玩家的定时刷新；按玩家错开，避免所有人挤在同一 tick。 */
    static boolean periodicDue(UUID playerId, long tick) {
        return Math.floorMod(tick + playerId.hashCode(), PERIOD_TICKS) == 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        long tick = event.getServer().getTickCount();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof FinanceMenu)) continue;
            boolean dirty = takeDirty(player.getUUID());
            if (dirty || periodicDue(player.getUUID(), tick)) {
                try {
                    FinanceGuiOpener.refresh(player);
                } catch (RuntimeException e) {
                    LOGGER.warn(
                            "Finance center refresh failed for {}",
                            player.getGameProfile().getName(),
                            e);
                }
            }
        }
        synchronized (FinanceMenuRefresher.class) {
            if (DIRTY.size() > 1024) DIRTY.clear();
        }
    }

    public static synchronized void clear() {
        DIRTY.clear();
    }
}
