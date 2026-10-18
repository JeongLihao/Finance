package finance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GuiFeedbackClientHandler {

    private GuiFeedbackClientHandler() {
    }

    public static void handle(String message) {
        handle(message, 0);
    }

    /** moneyDelta 非 0：钱包有变化，飘字并播放音效（卖出、领钱是经验球声，花钱是物品拾取声）。 */
    public static void handle(String message, long moneyDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (moneyDelta != 0) {
            finance.client.simple.SimpleClientState.pushMoney(moneyDelta);
            if (minecraft.player != null) {
                minecraft.player.playSound(moneyDelta > 0 ? net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP
                        : net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.6F, moneyDelta > 0 ? 1.2F : 1.0F);
            }
        }
        if (minecraft.screen instanceof FinanceScreen screen) {
            screen.setGuiStatus(message);
        } else if (minecraft.screen instanceof PlayerServicesScreen screen) {
            screen.setStatus(message);
        } else if (minecraft.screen instanceof MarketOverviewScreen screen) {
            screen.setGuiStatus(message);
        } else if (minecraft.player != null && message != null && !message.isBlank()) {
            // Compact physical-entry menus refresh their domain status from the
            // server. Any generic response that has no status slot still needs a
            // visible, non-chat fallback instead of being silently discarded.
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }
}
