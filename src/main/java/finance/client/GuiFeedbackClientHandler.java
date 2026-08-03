package finance.client;

import net.minecraft.client.Minecraft;

public final class GuiFeedbackClientHandler {

    private GuiFeedbackClientHandler() {
    }

    public static void handle(String message) {
        if (Minecraft.getInstance().screen instanceof FinanceScreen screen) {
            screen.setGuiStatus(message);
        }
    }
}
