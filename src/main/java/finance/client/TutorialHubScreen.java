package finance.client;

import finance.tutorial.TutorialStage;
import finance.tutorial.TutorialOptionalGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.fml.ModList;

import java.util.List;

/** Minecraft-sized overview of the main tutorial route and its current step. */
public final class TutorialHubScreen extends Screen {
    static final int PANEL_WIDTH = 310;
    static final int PANEL_HEIGHT = 236;
    private static final int ROW_HEIGHT = 15;
    private static final int OPTIONAL_ROUTE_Y = 45;
    private static final int OPTIONAL_ROW_HEIGHT = 45;
    private static final int OPTIONAL_CARD_WIDTH = 145;
    private static final int OPTIONAL_COLUMN_GAP = 4;

    private Button visibilityButton;
    private Button pageButton;
    private boolean optionalPage;

    public TutorialHubScreen() {
        super(Component.translatable("screen.finance.tutorial.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new TutorialHubScreen());
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int buttonY = top + PANEL_HEIGHT - 27;
        pageButton = addRenderableWidget(Button.builder(pageLabel(), button -> {
            optionalPage = !optionalPage;
            pageButton.setMessage(pageLabel());
        }).bounds(left + 10, buttonY, 72, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.finance.tutorial.handbook"),
                button -> FinanceGuideClientHandler.open()).bounds(left + 87, buttonY, 70, 18).build());
        visibilityButton = addRenderableWidget(Button.builder(visibilityLabel(), button -> {
            TutorialClientState.toggleVisible();
            visibilityButton.setMessage(visibilityLabel());
        }).bounds(left + 162, buttonY, 78, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + 245, buttonY, 55, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF18211C);
        graphics.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFFF0EAD8);
        graphics.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + 22, 0xFF315A48);
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFFFFF);

        if (optionalPage) renderOptionalRoutes(graphics, mouseX, mouseY, left, top);
        else renderMainRoute(graphics, mouseX, mouseY, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainRoute(GuiGraphics graphics, int mouseX, int mouseY, int left, int top) {
        TutorialStage current = TutorialClientState.stage();
        int currentOrdinal = current == null ? 0 : current.ordinal();
        graphics.drawString(font, Component.translatable("screen.finance.tutorial.summary",
                Math.min(currentOrdinal, 9), 9), left + 10, top + 29, 0xFF315A48, false);

        int routeY = top + 46;
        TutorialStage[] stages = TutorialStage.values();
        for (int index = 0; index < TutorialStage.COMPLETE.ordinal(); index++) {
            TutorialStage stage = stages[index];
            boolean complete = currentOrdinal > index;
            boolean active = currentOrdinal == index;
            int rowY = routeY + index * ROW_HEIGHT;
            if (active) graphics.fill(left + 7, rowY - 2, left + PANEL_WIDTH - 7, rowY + 11, 0xFFD9C98B);
            Component state = Component.translatable(complete
                    ? "screen.finance.tutorial.done"
                    : active ? "screen.finance.tutorial.current" : "screen.finance.tutorial.pending");
            Component label = Component.translatable("finance.tutorial.stage." + stage.translationId() + ".title");
            int color = complete ? 0xFF36743C : active ? 0xFF6C5200 : 0xFF77736A;
            graphics.drawString(font, Component.literal((index + 1) + ". ").append(state).append(" ").append(label),
                    left + 11, rowY, color, false);

            if (mouseX >= left + 7 && mouseX < left + PANEL_WIDTH - 7
                    && mouseY >= rowY - 2 && mouseY < rowY + 12) {
                Component hint = Component.translatable("finance.tutorial.stage." + stage.translationId() + ".hint");
                graphics.renderTooltip(font, hint, mouseX, mouseY);
            }
        }

        int footerY = routeY + 9 * ROW_HEIGHT + 4;
        Component footer = Component.translatable(ModList.get().isLoaded("ponder")
                ? "screen.finance.tutorial.ponder_ready" : "screen.finance.tutorial.ponder_optional");
        List<FormattedCharSequence> lines = font.split(footer, PANEL_WIDTH - 20);
        for (int line = 0; line < Math.min(2, lines.size()); line++) {
            graphics.drawString(font, lines.get(line), left + 10, footerY + line * 10, 0xFF625D52, false);
        }
    }

    private void renderOptionalRoutes(GuiGraphics graphics, int mouseX, int mouseY, int left, int top) {
        int completed = Integer.bitCount(TutorialClientState.optionalMask());
        graphics.drawString(font, Component.translatable("screen.finance.tutorial.optional_summary",
                completed, TutorialOptionalGoal.values().length), left + 10, top + 29, 0xFF315A48, false);

        int routeY = top + OPTIONAL_ROUTE_Y;
        TutorialOptionalGoal activeGoal = TutorialClientState.nextOptionalGoal();
        for (int index = 0; index < TutorialOptionalGoal.values().length; index++) {
            TutorialOptionalGoal goal = TutorialOptionalGoal.values()[index];
            boolean complete = TutorialClientState.optionalComplete(goal);
            boolean active = goal == activeGoal;
            int column = index % 2;
            int row = index / 2;
            int cardX = left + 8 + column * (OPTIONAL_CARD_WIDTH + OPTIONAL_COLUMN_GAP);
            int rowY = routeY + row * OPTIONAL_ROW_HEIGHT;
            graphics.fill(cardX, rowY - 3, cardX + OPTIONAL_CARD_WIDTH, rowY + 35,
                    active ? 0xFFD9C98B : complete ? 0xFFD9E8D4 : 0xFFE1DCCF);
            Component state = Component.translatable(complete
                    ? "screen.finance.tutorial.done"
                    : active ? "screen.finance.tutorial.current" : "screen.finance.tutorial.pending");
            String base = "finance.tutorial.optional." + goal.translationId();
            Component title = Component.translatable(base + ".title");
            int color = complete ? 0xFF36743C : active ? 0xFF6C5200 : 0xFF77736A;
            List<FormattedCharSequence> heading = font.split(state.copy().append(" ").append(title), OPTIONAL_CARD_WIDTH - 10);
            if (!heading.isEmpty()) graphics.drawString(font, heading.get(0), cardX + 5, rowY, color, false);
            List<FormattedCharSequence> hint = font.split(Component.translatable(base + ".hint"), OPTIONAL_CARD_WIDTH - 10);
            for (int line = 0; line < Math.min(2, hint.size()); line++)
                graphics.drawString(font, hint.get(line), cardX + 5, rowY + 11 + line * 10, 0xFF625D52, false);
            if (mouseX >= cardX && mouseX < cardX + OPTIONAL_CARD_WIDTH
                    && mouseY >= rowY - 3 && mouseY < rowY + 36) {
                graphics.renderTooltip(font, Component.translatable(base + ".hint"), mouseX, mouseY);
            }
        }

        Component footer = Component.translatable("screen.finance.tutorial.optional_footer");
        graphics.drawString(font, footer, left + 10, top + 191, 0xFF625D52, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && optionalPage) {
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            TutorialOptionalGoal goal = optionalGoalAt((int) mouseX - left, (int) mouseY - top);
            if (goal != null && !TutorialClientState.optionalComplete(goal)) {
                TutorialClientState.trackOptionalGoal(goal);
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    static TutorialOptionalGoal optionalGoalAt(int relativeX, int relativeY) {
        if (relativeX < 8 || relativeX >= PANEL_WIDTH - 8) return null;
        int firstRowTop = OPTIONAL_ROUTE_Y - 3;
        int rowOffset = relativeY - firstRowTop;
        if (rowOffset < 0) return null;
        int column;
        if (relativeX >= 8 && relativeX < 8 + OPTIONAL_CARD_WIDTH) column = 0;
        else if (relativeX >= 8 + OPTIONAL_CARD_WIDTH + OPTIONAL_COLUMN_GAP
                && relativeX < 8 + OPTIONAL_CARD_WIDTH * 2 + OPTIONAL_COLUMN_GAP) column = 1;
        else return null;
        int row = rowOffset / OPTIONAL_ROW_HEIGHT;
        if (rowOffset % OPTIONAL_ROW_HEIGHT >= 39) return null;
        int index = row * 2 + column;
        if (index >= TutorialOptionalGoal.values().length) return null;
        return TutorialOptionalGoal.values()[index];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component visibilityLabel() {
        return Component.translatable(TutorialClientState.visible()
                ? "screen.finance.tutorial.hide_hud" : "screen.finance.tutorial.show_hud");
    }

    private Component pageLabel() {
        return Component.translatable(optionalPage
                ? "screen.finance.tutorial.main_page" : "screen.finance.tutorial.optional_page");
    }
}
