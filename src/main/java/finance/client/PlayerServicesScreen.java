package finance.client;

import finance.client.PlayerServicesForm.InvalidInput;
import finance.client.PlayerServicesForm.Page;
import finance.network.FinancePacketHandler;
import finance.network.PlayerServicesActionPacket;
import finance.network.PlayerServicesRequestPacket;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService.Action;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 更多操作：把原来只能用命令完成的玩家功能做成按钮——融资融券、我的银行、要约收购（接受/撤回/发起/强制私有化）、
 * 中央银行（央行概况、央行债券认购/赎回、定存提前支取）、宏观行动（工资、领报纸）、财报日历与季报、商情调查，以及实体现金的存取和绿宝石兑换。
 * 沿用金融中心的米色面板风格，关闭后回到上一个界面。
 */
public final class PlayerServicesScreen extends Screen {
    /** 高 240：原版“自动”界面尺寸下可用高度常为 240，超出部分会被裁掉。 */
    static final int PANEL_WIDTH = 384, PANEL_HEIGHT = 240;

    static final int TITLE_H = 18, PAGE_Y = 20, CONTENT_Y = 40, LINE_H = 11;
    static final int LABEL_Y = 162, BOX_Y = 172, BUTTON_Y1 = 192, BUTTON_Y2 = 208, STATUS_Y = 226;
    private static final int BOX_W = 88, BOX_GAP = 6;

    private static Page lastPage = Page.MARGIN;

    record Hit(int x, int y, int w, int h, Runnable run) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    record Btn(String label, Runnable run) {}

    private final Screen parent;
    private Page page;
    private final EditBox[] boxes = new EditBox[PlayerServicesForm.BOXES];
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Page, ServicesPage> pages = new EnumMap<>(Page.class);
    private String status = "";
    private int left, top;

    public PlayerServicesScreen(Screen parent, Page page) {
        super(Component.literal("更多操作"));
        this.parent = parent;
        this.page = page == null ? lastPage : page;
        pages.put(Page.MARGIN, new ServicesMarginPage(this));
        pages.put(Page.BANK, new ServicesBankPage(this));
        pages.put(Page.TENDER, new ServicesTenderPage(this));
        pages.put(Page.CENTRAL_BANK, new ServicesCentralBankPage(this));
        pages.put(Page.MACRO, new ServicesMacroPage(this));
        pages.put(Page.RESEARCH, new ServicesResearchPage(this));
        pages.put(Page.CASH, new ServicesCashPage(this));
    }

    public static void open() {
        open(null);
    }

    public static void open(Page page) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new PlayerServicesScreen(minecraft.screen, page));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        for (int i = 0; i < boxes.length; i++) {
            String old = boxes[i] == null ? "" : boxes[i].getValue();
            EditBox box =
                    new EditBox(font, left + 8 + i * (BOX_W + BOX_GAP), top + BOX_Y, BOX_W, 14, Component.empty());
            box.setMaxLength(32);
            box.setValue(old);
            boxes[i] = addRenderableWidget(box);
        }
        updateBoxes();
        request();
    }

    private void request() {
        FinancePacketHandler.CHANNEL.sendToServer(new PlayerServicesRequestPacket(PlayerServicesClientCache.begin()));
    }

    public void setStatus(String message) {
        status = message == null ? "" : message;
    }

    private PlayerServicesResponsePacket data() {
        return PlayerServicesClientCache.get().data();
    }

    private void updateBoxes() {
        String[] labels = PlayerServicesForm.labels(page, data());
        for (int i = 0; i < boxes.length; i++) {
            boolean visible = labels[i] != null;
            boxes[i].visible = visible;
            boxes[i].active = visible;
            if (!visible && boxes[i].isFocused()) setFocused(null);
        }
    }

    private void switchPage(Page next) {
        if (next == page) return;
        page = next;
        lastPage = next;
        for (EditBox box : boxes) box.setValue("");
        setFocused(null);
        status = "";
        updateBoxes();
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        updateBoxes();
        g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, FinanceGui.COL_BG);
        FinanceGui.drawSimpleBorder(g, left, top, PANEL_WIDTH, PANEL_HEIGHT, FinanceGui.COL_PANEL_BORDER);
        g.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + TITLE_H - 1, 0xFFF4F0E2);
        FinanceGui.drawClippedString(font, g, "更多操作", left + 10, top + 5, 80, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(
                font, g, "原来要用命令的功能，都可以在这里点按钮完成", left + 70, top + 5, 250, FinanceGui.COL_TEXT_DIM);
        button(g, "返回", left + PANEL_WIDTH - 46, top + 2, 38, false, this::onClose);

        int x = left + 8;
        int pageW = (PANEL_WIDTH - 16 - (Page.values().length - 1) * 4) / Page.values().length;
        for (Page p : Page.values()) {
            button(g, p.label(), x, top + PAGE_Y, pageW, p == page, () -> switchPage(p));
            x += pageW + 4;
        }
        FinanceGui.drawSimpleSeparator(g, left + 6, top + PAGE_Y + 16, PANEL_WIDTH - 12);

        var entry = PlayerServicesClientCache.get();
        PlayerServicesResponsePacket data = entry.data();
        if (data == null) {
            String msg = entry.state() == PlayerServicesClientCache.State.SLOW ? "服务器响应较慢，可点“刷新”重试" : "正在加载…";
            line(g, msg, 0, FinanceGui.COL_TEXT_DIM);
        } else {
            pages.get(page).render(g, data);
        }

        String[] labels = PlayerServicesForm.labels(page, data);
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] != null) {
                FinanceGui.drawClippedString(
                        font,
                        g,
                        labels[i],
                        left + 8 + i * (BOX_W + BOX_GAP),
                        top + LABEL_Y,
                        BOX_W,
                        FinanceGui.COL_TEXT_DIM);
            }
        }
        layoutButtons(g, data == null ? List.of() : pages.get(page).buttons(data));
        int color = status.isEmpty() ? FinanceGui.COL_TEXT_DIM : FinanceGui.COL_ACCENT;
        FinanceGui.drawClippedString(
                font,
                g,
                status.isEmpty() ? "提示：先选中列表里的一项，再填写数字并点按钮" : status,
                left + 8,
                top + STATUS_Y,
                PANEL_WIDTH - 16,
                color);
        super.render(g, mouseX, mouseY, partialTick);
    }

    void line(GuiGraphics g, String text, int row, int color) {
        FinanceGui.drawClippedString(font, g, text, left + 10, top + CONTENT_Y + row * LINE_H, PANEL_WIDTH - 20, color);
    }

    /** 自动换行，返回占用的行数。 */
    int wrapped(GuiGraphics g, String text, int x, int row, int width, int maxRows, int color) {
        List<FormattedCharSequence> parts = font.split(Component.literal(text), width);
        int n = Math.min(parts.size(), maxRows);
        for (int i = 0; i < n; i++)
            g.drawString(font, parts.get(i), x, top + CONTENT_Y + (row + i) * LINE_H, color, false);
        return Math.max(1, n);
    }

    private void button(GuiGraphics g, String label, int x, int y, int w, boolean active, Runnable run) {
        FinanceGui.drawButton(font, g, label, x, y, w, active);
        hits.add(new Hit(x, y, w, 13, run));
    }

    private void layoutButtons(GuiGraphics g, List<Btn> buttons) {
        int perRow = 5;
        for (int i = 0; i < buttons.size(); i++) {
            int row = i / perRow, col = i % perRow;
            int count = Math.min(perRow, buttons.size() - row * perRow);
            int w = (PANEL_WIDTH - 16 - (count - 1) * 4) / count;
            Btn b = buttons.get(i);
            button(g, b.label(), left + 8 + col * (w + 4), top + (row == 0 ? BUTTON_Y1 : BUTTON_Y2), w, false, () -> {
                try {
                    b.run().run();
                } catch (InvalidInput invalid) {
                    status = invalid.getMessage();
                }
            });
        }
    }

    // ---------------------------------------------------------------- 按钮

    String box(int i) {
        return boxes[i].getValue();
    }

    void send(Action action, String text, long a, long b, long c, long d) {
        FinancePacketHandler.CHANNEL.sendToServer(new PlayerServicesActionPacket(action, text, a, b, c, d));
        status = "已提交，等待服务器处理…";
    }

    void refresh() {
        request();
        status = "正在刷新…";
    }

    // ---------------------------------------------------------------- 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        setFocused(null);
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(mouseX, mouseY)) {
                hit.run().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ---- 给各页（ServicesPage）用 ----

    int left() {
        return left;
    }

    int top() {
        return top;
    }

    net.minecraft.client.gui.Font font() {
        return font;
    }

    List<Hit> hits() {
        return hits;
    }
}
