package finance.client;

import finance.cash.CashService;
import finance.network.ExchangeNoteFillPacket;
import finance.network.FinancePacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

/** 在空白兑换钞上填写金额：输入便士数，下方实时显示折合多少磅，点“签发”或按回车提交。 */
public final class ExchangeNoteScreen extends Screen {
    private static final int W = 220, H = 104;

    private final InteractionHand hand;
    private EditBox amount;
    private String status = "";
    private int left, top;

    private ExchangeNoteScreen(InteractionHand hand) {
        super(Component.translatable("screen.finance.exchange_note.title"));
        this.hand = hand;
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new ExchangeNoteScreen(hand));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        String old = amount == null ? "" : amount.getValue();
        amount = new EditBox(font, left + 10, top + 42, W - 20, 16, Component.empty());
        amount.setMaxLength(9);
        amount.setFilter(s -> s.chars().allMatch(Character::isDigit));
        amount.setValue(old);
        addRenderableWidget(amount);
        setInitialFocus(amount);
    }

    private long parsed() {
        try {
            return amount.getValue().isEmpty() ? 0 : Long.parseLong(amount.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void submit() {
        long v = parsed();
        if (v <= 0) {
            status = Component.translatable("screen.finance.exchange_note.need_amount")
                    .getString();
            return;
        }
        if (v > CashService.MAX_EXCHANGE_NOTE) {
            status = Component.translatable("screen.finance.exchange_note.too_much", CashService.MAX_EXCHANGE_NOTE)
                    .getString();
            return;
        }
        FinancePacketHandler.CHANNEL.sendToServer(new ExchangeNoteFillPacket(hand == InteractionHand.OFF_HAND, v));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xFFF5F1E6);
        FinanceGui.drawSimpleBorder(g, left, top, W, H, 0xFF4A4438);
        g.drawString(font, title, left + 10, top + 9, FinanceGui.COL_TEXT, false);
        g.drawString(
                font,
                Component.translatable("screen.finance.exchange_note.label"),
                left + 10,
                top + 28,
                FinanceGui.COL_TEXT_DIM,
                false);
        long v = parsed();
        String preview = v > 0 ? "= " + CashService.pounds(v) : "";
        g.drawString(font, preview, left + 10, top + 62, FinanceGui.COL_GOOD, false);
        if (!status.isEmpty()) {
            FinanceGui.drawClippedString(font, g, status, left + 10, top + 62, W - 20, FinanceGui.COL_BAD);
        }
        FinanceGui.drawButton(
                font,
                g,
                Component.translatable("screen.finance.exchange_note.sign").getString(),
                left + 10,
                top + 80,
                96,
                true);
        FinanceGui.drawButton(
                font, g, Component.translatable("gui.cancel").getString(), left + W - 106, top + 80, 96, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || my < top + 80 || my >= top + 93) return false;
        if (mx >= left + 10 && mx < left + 106) {
            submit();
            return true;
        }
        if (mx >= left + W - 106 && mx < left + W - 10) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (amount.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            status = "";
            return amount.keyPressed(key, scan, modifiers) || amount.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
