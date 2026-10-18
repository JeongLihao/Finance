package finance.client;

import finance.gui.FinanceMenu;
import net.minecraft.client.Minecraft;

/** 收到服务端推来的新快照：换掉开着的金融中心里的数据，界面停在原来的页签。 */
public final class FinanceRefreshClientHandler {

    private FinanceRefreshClientHandler() {}

    public static void handle(int containerId, FinanceMenu snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.player == null
                || !(minecraft.player.containerMenu instanceof FinanceMenu menu)
                || menu.containerId != containerId) {
            return;
        }
        menu.replaceData(snapshot);
        // “更多操作”等子界面打开时 FinanceScreen 不是当前界面，回来时 init 会重新读菜单数据
        if (minecraft.screen instanceof FinanceScreen screen) screen.onDataRefreshed();
    }
}
