package finance.client;

import finance.item.NewspaperData;
import net.minecraft.client.Minecraft;

/** Client-only entry; the common item never loads rendering classes on a server. */
public final class NewspaperClientHandler {
    private NewspaperClientHandler() {}

    public static void open(NewspaperData.Paper paper) {
        Minecraft.getInstance().setScreen(new NewspaperScreen(paper));
    }
}
