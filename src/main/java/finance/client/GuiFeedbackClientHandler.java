package finance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GuiFeedbackClientHandler {

    private GuiFeedbackClientHandler() {
    }

    public static void handle(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FinanceScreen screen) {
            screen.setGuiStatus(message);
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
