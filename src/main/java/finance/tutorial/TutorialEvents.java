package finance.tutorial;

import finance.FinanceMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small event bridge for inventory discovery, login sync, and respawn persistence. */
@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID)
public final class TutorialEvents {
    private TutorialEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TutorialProgressService.sync(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original
                && event.getEntity() instanceof ServerPlayer replacement) {
            TutorialProgressService.copy(original, replacement);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0) {
            TutorialProgressService.observeInventory(player);
        }
    }
}
