package finance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Client-only translated pages; the common item never loads rendering classes on a server. */
public final class FinanceGuideClientHandler {
    private FinanceGuideClientHandler(){}
    public static void open(){Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(){public int getPageCount(){return 8;}public FormattedText getPageRaw(int page){return Component.translatable("finance.guide.page."+(page+1));}}));}
}
