package finance.client;

import finance.FinanceMod;
import finance.tutorial.TutorialStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, value = Dist.CLIENT)
public final class TutorialHud {
    private TutorialHud() {}

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        TutorialStage stage = TutorialClientState.stage();
        if (!TutorialClientState.visible() || stage == null || minecraft.options.hideGui
                || minecraft.player == null || minecraft.screen != null) return;

        String translationBase = TutorialClientState.objectiveTranslationBase();
        if (translationBase == null) return;
        Component title = Component.translatable(translationBase + ".title");
        Component hint = Component.translatable(translationBase + ".hint");
        Component controls = Component.translatable("finance.tutorial.controls");
        Font font = minecraft.font;
        int width = Math.min(220, event.getWindow().getGuiScaledWidth() - 16);
        List<FormattedCharSequence> hintLines = font.split(hint, width - 18);
        int height = 38 + hintLines.size() * 10;
        int x = event.getWindow().getGuiScaledWidth() - width - 8;
        int y = 8;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(x, y, x + width, y + height, 0xC0101010);
        graphics.fill(x, y, x + 3, y + height,
                TutorialClientState.highlighted() ? 0xFFFFCC45 : 0xFF55AA55);
        graphics.drawString(font, title, x + 9, y + 7, 0xFFFFFFFF, false);
        for (int line = 0; line < hintLines.size(); line++) {
            graphics.drawString(font, hintLines.get(line), x + 9, y + 20 + line * 10, 0xFFE0E0E0, false);
        }
        graphics.drawString(font, controls, x + 9, y + height - 12, 0xFFAAAAAA, false);
    }
}
