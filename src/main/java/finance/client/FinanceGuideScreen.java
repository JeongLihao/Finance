package finance.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** 金融手册：替代原版书本界面，沿用金融中心的米色面板风格。
 * 左侧目录、右侧正文（超长时滚轮滚动），底部翻页；从教程中心打开时关闭后回到教程中心。 */
public final class FinanceGuideScreen extends Screen {
    static final int PANEL_WIDTH = 320;
    static final int PANEL_HEIGHT = 220;
    static final String PAGE_KEY = "finance.guide.page.";
    /** 手册页数上限，防止翻译文件异常时无限探测。 */
    static final int MAX_PAGES = 64;

    private static final int TITLE_H = 22;
    private static final int TOC_X = 6, TOC_W = 98, TOC_ROW = 12;
    private static final int BODY_X = 112, BODY_W = 200;
    private static final int CONTENT_Y = TITLE_H + 6;
    private static final int FOOTER_Y = PANEL_HEIGHT - 20;
    private static final int LINE_H = 10;
    private static final int PREV_X = 112, NEXT_X = 168, CLOSE_X = 258, BUTTON_W = 52;

    private final Screen parent;
    private final List<String[]> pages = new ArrayList<>();
    private int page;
    private int bodyScroll;
    private int tocScroll;
    private List<FormattedCharSequence> bodyLines = List.of();
    private int left, top;

    public FinanceGuideScreen(Screen parent) {
        super(Component.translatable("item.finance.finance_guide"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        pages.clear();
        int count = pageCount(key -> I18n.exists(key));
        for (int i = 1; i <= count; i++) pages.add(splitPage(I18n.get(PAGE_KEY + i)));
        page = clamp(page, 0, pages.size() - 1);
        layoutBody();
    }

    /** 从 1 开始连续探测翻译键，遇到第一个缺失的页停止。 */
    static int pageCount(Predicate<String> exists) {
        int count = 0;
        while (count < MAX_PAGES && exists.test(PAGE_KEY + (count + 1))) count++;
        return count;
    }

    /** 页面文字约定为“标题\n\n正文”；没有空行时整段作为正文，标题取首行。 */
    static String[] splitPage(String raw) {
        String text = raw == null ? "" : raw.replace("\r", "").strip();
        int newline = text.indexOf('\n');
        if (newline < 0) return new String[] {text, ""};
        return new String[] {
            text.substring(0, newline).strip(), text.substring(newline + 1).strip()
        };
    }

    static int clamp(int value, int min, int max) {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }

    private int tocRows() {
        return (FOOTER_Y - 4 - CONTENT_Y) / TOC_ROW;
    }

    private int bodyRows() {
        return (FOOTER_Y - 4 - (CONTENT_Y + 16)) / LINE_H;
    }

    private void layoutBody() {
        bodyScroll = 0;
        bodyLines = pages.isEmpty() ? List.of() : font.split(Component.literal(pages.get(page)[1]), BODY_W);
        // 目录跟随当前页
        int rows = tocRows();
        if (page < tocScroll) tocScroll = page;
        if (page >= tocScroll + rows) tocScroll = page - rows + 1;
        tocScroll = clamp(tocScroll, 0, Math.max(0, pages.size() - rows));
    }

    private void goTo(int target) {
        int next = clamp(target, 0, pages.size() - 1);
        if (next == page) return;
        page = next;
        layoutBody();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int mx = mouseX - left, my = mouseY - top;
        g.pose().pushPose();
        g.pose().translate(left, top, 0);

        g.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, FinanceGui.COL_BG);
        FinanceGui.drawSimpleBorder(g, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, FinanceGui.COL_PANEL_BORDER);
        g.fill(1, 1, PANEL_WIDTH - 1, TITLE_H - 1, 0xFFF4F0E2);
        g.fill(1, TITLE_H - 1, PANEL_WIDTH - 1, TITLE_H, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawClippedString(font, g, title.getString(), 10, 7, 200, FinanceGui.COL_TEXT);
        if (!pages.isEmpty()) {
            String indicator = Component.translatable("screen.finance.guide.page", page + 1, pages.size())
                    .getString();
            g.drawString(font, indicator, PANEL_WIDTH - 10 - font.width(indicator), 7, FinanceGui.COL_TEXT_DIM, false);
        }

        // 目录
        g.fill(TOC_X - 2, CONTENT_Y - 2, TOC_X + TOC_W + 2, FOOTER_Y - 4, FinanceGui.COL_ROW_EVEN);
        FinanceGui.drawSimpleBorder(g, TOC_X - 2, CONTENT_Y - 2, TOC_W + 4, FOOTER_Y - 2 - CONTENT_Y, 0x66555555);
        int rows = tocRows();
        for (int i = 0; i < rows && tocScroll + i < pages.size(); i++) {
            int index = tocScroll + i;
            int y = CONTENT_Y + i * TOC_ROW;
            boolean current = index == page;
            boolean hovered = mx >= TOC_X && mx < TOC_X + TOC_W && my >= y && my < y + TOC_ROW;
            if (current) g.fill(TOC_X - 1, y, TOC_X + TOC_W + 1, y + TOC_ROW, FinanceGui.COL_ROW_SELECT);
            else if (hovered) g.fill(TOC_X - 1, y, TOC_X + TOC_W + 1, y + TOC_ROW, FinanceGui.COL_ROW_ODD);
            if (current) g.fill(TOC_X - 1, y, TOC_X + 1, y + TOC_ROW, FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(
                    font,
                    g,
                    (index + 1) + ". " + pages.get(index)[0],
                    TOC_X + 3,
                    y + 2,
                    TOC_W - 4,
                    current ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
        }

        // 正文
        if (pages.isEmpty()) {
            FinanceGui.drawClippedString(
                    font,
                    g,
                    Component.translatable("screen.finance.guide.empty").getString(),
                    BODY_X,
                    CONTENT_Y,
                    BODY_W,
                    FinanceGui.COL_TEXT_DIM);
        } else {
            FinanceGui.drawClippedString(font, g, pages.get(page)[0], BODY_X, CONTENT_Y, BODY_W, FinanceGui.COL_TEXT);
            FinanceGui.drawSimpleSeparator(g, BODY_X, CONTENT_Y + 11, BODY_W);
            int bodyTop = CONTENT_Y + 16;
            int visible = bodyRows();
            for (int i = 0; i < visible && bodyScroll + i < bodyLines.size(); i++) {
                g.drawString(
                        font, bodyLines.get(bodyScroll + i), BODY_X, bodyTop + i * LINE_H, FinanceGui.COL_TEXT, false);
            }
            if (bodyLines.size() > visible) {
                String hint = (bodyScroll > 0 ? "▲" : "") + (bodyScroll + visible < bodyLines.size() ? "▼" : "");
                g.drawString(font, hint, BODY_X + BODY_W - font.width(hint), CONTENT_Y, FinanceGui.COL_TEXT_DIM, false);
            }
        }

        // 底部按钮
        FinanceGui.drawSimpleSeparator(g, 1, FOOTER_Y - 4, PANEL_WIDTH - 2);
        FinanceGui.drawButton(
                font,
                g,
                Component.translatable("screen.finance.guide.prev").getString(),
                PREV_X,
                FOOTER_Y,
                BUTTON_W,
                page > 0 && hit(mx, my, PREV_X));
        FinanceGui.drawButton(
                font,
                g,
                Component.translatable("screen.finance.guide.next").getString(),
                NEXT_X,
                FOOTER_Y,
                BUTTON_W,
                page < pages.size() - 1 && hit(mx, my, NEXT_X));
        FinanceGui.drawButton(
                font,
                g,
                Component.translatable(parent == null ? "screen.finance.guide.close" : "screen.finance.guide.back")
                        .getString(),
                CLOSE_X,
                FOOTER_Y,
                BUTTON_W,
                hit(mx, my, CLOSE_X));
        FinanceGui.drawClippedString(
                font,
                g,
                Component.translatable("screen.finance.guide.keys").getString(),
                TOC_X,
                FOOTER_Y + 3,
                TOC_W,
                FinanceGui.COL_TEXT_DIM);

        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static boolean hit(int mx, int my, int x) {
        return mx >= x && mx < x + BUTTON_W && my >= FOOTER_Y && my < FOOTER_Y + 13;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - left, my = (int) mouseY - top;
        if (button == 0) {
            if (hit(mx, my, PREV_X)) {
                goTo(page - 1);
                return true;
            }
            if (hit(mx, my, NEXT_X)) {
                goTo(page + 1);
                return true;
            }
            if (hit(mx, my, CLOSE_X)) {
                onClose();
                return true;
            }
            if (mx >= TOC_X && mx < TOC_X + TOC_W && my >= CONTENT_Y && my < CONTENT_Y + tocRows() * TOC_ROW) {
                int index = tocScroll + (my - CONTENT_Y) / TOC_ROW;
                if (index < pages.size()) goTo(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX - left;
        int step = delta > 0 ? -1 : delta < 0 ? 1 : 0;
        if (step == 0) return false;
        if (mx < BODY_X - 4) {
            tocScroll = clamp(tocScroll + step, 0, Math.max(0, pages.size() - tocRows()));
        } else {
            bodyScroll = clamp(bodyScroll + step, 0, Math.max(0, bodyLines.size() - bodyRows()));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> {
                goTo(page - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> {
                goTo(page + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                goTo(0);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                goTo(pages.size() - 1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                bodyScroll = clamp(bodyScroll - 1, 0, Math.max(0, bodyLines.size() - bodyRows()));
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                bodyScroll = clamp(bodyScroll + 1, 0, Math.max(0, bodyLines.size() - bodyRows()));
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
