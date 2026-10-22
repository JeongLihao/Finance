package finance.client;

import net.minecraft.client.Minecraft;

/** Client-only entry; the common item never loads rendering classes on a server. */
public final class FinanceGuideClientHandler {
    private FinanceGuideClientHandler() {}
    /** 打开金融手册；从其他界面（如教程中心）打开时，关闭后回到原界面。 */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new FinanceGuideScreen(minecraft.screen));
    }
}
