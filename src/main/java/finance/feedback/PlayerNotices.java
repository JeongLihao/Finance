package finance.feedback;

import finance.cycle.EconomyCycleService;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** 给某个玩家的提醒：在线马上在聊天栏显示并响提示音，离线的存进反馈队列，上线时补发。 */
public final class PlayerNotices {

    private PlayerNotices() {}

    public static void tell(
            MinecraftServer server,
            UUID playerId,
            FeedbackSeverity severity,
            SoundEvent sound,
            String key,
            String... args) {
        if (server == null || playerId == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(key, (Object[]) args));
            player.playNotifySound(sound, SoundSource.PLAYERS, 0.8F, 1.0F);
            return;
        }
        long day = Math.max(0, EconomyCycleService.currentMcDay(server));
        WorldEconomyFeedbackService.queue(playerId, new FeedbackNotification(day, severity, key, List.of(args)));
    }
}
