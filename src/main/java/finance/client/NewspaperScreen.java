package finance.client;

import finance.item.NewspaperData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** 《金融周报》阅读界面：报头 + 版面页签 + 双栏正文。
 * ← → 换版，↑ ↓ / 滚轮翻栏；头版先排 2 倍大的头条，再接正文。 */
public final class NewspaperScreen extends Screen {
    static final int PANEL_WIDTH = 320;
    static final int PANEL_HEIGHT = 230;

    private static final int PAPER = 0xFFF4ECD8, INK = 0xFF1E1E1E, INK_DIM = 0xFF5A5246, RULE = 0xFF3A3328;
    private static final int EXTRA_RED = 0xFF9A2F2F, TAB_IDLE = 0xFFE4D9BE;
    private static final int MARGIN = 10;
    private static final int TABS_Y = 40, TAB_H = 12;
    private static final int BODY_Y = TABS_Y + TAB_H + 6;
    private static final int FOOTER_Y = PANEL_HEIGHT - 18;
    private static final int COLUMN_GAP = 10;
    private static final int COLUMN_W = (PANEL_WIDTH - MARGIN * 2 - COLUMN_GAP) / 2;
    private static final int LINE_H = 10;
    /** 头条放大倍数必须是整数，否则像素字体会糊；行高与坐标都取偶数，放大后仍对齐像素。 */
    private static final int HEADLINE_SCALE = 2, HEADLINE_LINE_H = 18;
    private static final int PREV_X = MARGIN, NEXT_X = MARGIN + 56, CLOSE_X = PANEL_WIDTH - MARGIN - 52, BUTTON_W = 52;

    private final NewspaperData.Paper paper;
    private final List<NewspaperData.Section> sections = new ArrayList<>();
    private final List<int[]> tabBounds = new ArrayList<>();
    private int section;
    private int columnPage;
    private List<FormattedCharSequence> headlineLines = List.of();
    private List<FormattedCharSequence> bodyLines = List.of();
    private int left, top;

    public NewspaperScreen(NewspaperData.Paper paper) {
        super(Component.translatable("screen.finance.newspaper.masthead"));
        this.paper = paper;
        sections.addAll(NewspaperData.sections(paper));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        section = FinanceGuideScreen.clamp(section, 0, sections.size() - 1);
        layoutSection();
    }

    private boolean onFront() {
        return !sections.isEmpty() && section == 0 && NewspaperData.FRONT_PAGE.equals(sections.get(0).title());
    }

    private void layoutSection() {
        columnPage = 0;
        bodyLines = sections.isEmpty() ? List.of()
                : font.split(Component.literal(sections.get(section).body()), COLUMN_W);
        // 头条放大 2 倍（整数倍，像素字体不糊），所以按一半宽度折行
        headlineLines = onFront() && paper != null && !paper.headline().isBlank()
                ? font.split(Component.literal(paper.headline()), (PANEL_WIDTH - MARGIN * 2) / HEADLINE_SCALE)
                : List.of();
    }

    /** 头条占掉的高度。 */
    private int headlineHeight() {
        return headlineLines.isEmpty() ? 0 : Math.min(2, headlineLines.size()) * HEADLINE_LINE_H + 6;
    }

    /** 每栏能排几行；头版第一屏要让出头条的位置。 */
    private int rowsPerColumn(int page) {
        int height = FOOTER_Y - 6 - BODY_Y - (page == 0 ? headlineHeight() : 0);
        return Math.max(1, height / LINE_H);
    }

    /** 第 page 屏从第几行开始。 */
    private int pageStart(int page) {
        int start = 0;
        for (int p = 0; p < page; p++) start += rowsPerColumn(p) * 2;
        return start;
    }

    private int columnPages() {
        int pages = 1;
        while (pageStart(pages) < bodyLines.size()) pages++;
        return pages;
    }

    private void goToSection(int target) {
        int next = FinanceGuideScreen.clamp(target, 0, sections.size() - 1);
        if (next == section) return;
        section = next;
        layoutSection();
    }

    private void turnColumns(int step) {
        columnPage = FinanceGuideScreen.clamp(columnPage + step, 0, columnPages() - 1);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int mx = mouseX - left, my = mouseY - top;
        g.pose().pushPose();
        g.pose().translate(left, top, 0);

        g.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, PAPER);
        FinanceGui.drawSimpleBorder(g, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, RULE);
        renderMasthead(g);
        renderTabs(g, mx, my);
        renderBody(g);

        g.fill(MARGIN, FOOTER_Y - 4, PANEL_WIDTH - MARGIN, FOOTER_Y - 3, RULE);
        FinanceGui.drawButton(font, g, Component.translatable("screen.finance.newspaper.prev").getString(),
                PREV_X, FOOTER_Y, BUTTON_W, section > 0 && hit(mx, my, PREV_X));
        FinanceGui.drawButton(font, g, Component.translatable("screen.finance.newspaper.next").getString(),
                NEXT_X, FOOTER_Y, BUTTON_W, section < sections.size() - 1 && hit(mx, my, NEXT_X));
        FinanceGui.drawButton(font, g, Component.translatable("screen.finance.guide.close").getString(),
                CLOSE_X, FOOTER_Y, BUTTON_W, hit(mx, my, CLOSE_X));
        int pages = columnPages();
        String hint = pages > 1
                ? Component.translatable("screen.finance.newspaper.columns", columnPage + 1, pages).getString()
                : Component.translatable("screen.finance.newspaper.keys").getString();
        FinanceGui.drawCenteredClippedString(font, g, hint, NEXT_X + BUTTON_W + 4, FOOTER_Y + 3,
                CLOSE_X - NEXT_X - BUTTON_W - 8, INK_DIM);

        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderMasthead(GuiGraphics g) {
        boolean extra = paper != null && paper.extra();
        String name = Component.translatable(extra ? "screen.finance.newspaper.extra_masthead"
                : "screen.finance.newspaper.masthead").getString();
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, name, (PANEL_WIDTH / 2 - font.width(name)) / 2, 2, extra ? EXTRA_RED : INK, false);
        g.pose().popPose();
        // 报头下的粗细双线
        g.fill(MARGIN, 24, PANEL_WIDTH - MARGIN, 26, RULE);
        g.fill(MARGIN, 27, PANEL_WIDTH - MARGIN, 28, RULE);
        if (paper != null) {
            String issue = Component.translatable("screen.finance.newspaper.issue", paper.number()).getString();
            String day = Component.translatable("screen.finance.newspaper.day", paper.day()).getString();
            g.drawString(font, issue, MARGIN, 30, INK_DIM, false);
            g.drawString(font, day, PANEL_WIDTH - MARGIN - font.width(day), 30, INK_DIM, false);
        }
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        tabBounds.clear();
        if (sections.isEmpty()) return;
        int available = PANEL_WIDTH - MARGIN * 2;
        int natural = 0;
        for (NewspaperData.Section s : sections) natural += font.width(s.title()) + 10;
        int x = MARGIN;
        for (int i = 0; i < sections.size(); i++) {
            String label = sections.get(i).title();
            int w = natural <= available ? font.width(label) + 10
                    : Math.max(18, (available - (sections.size() - 1) * 2) / sections.size());
            boolean active = i == section;
            boolean hovered = mx >= x && mx < x + w && my >= TABS_Y && my < TABS_Y + TAB_H;
            g.fill(x, TABS_Y, x + w, TABS_Y + TAB_H, active ? RULE : hovered ? 0xFFD8CBAA : TAB_IDLE);
            FinanceGui.drawCenteredClippedString(font, g, label, x, TABS_Y + 2, w, active ? PAPER : INK);
            tabBounds.add(new int[]{x, w});
            x += w + 2;
        }
        g.fill(MARGIN, TABS_Y + TAB_H, PANEL_WIDTH - MARGIN, TABS_Y + TAB_H + 1, RULE);
    }

    private void renderBody(GuiGraphics g) {
        if (sections.isEmpty()) {
            FinanceGui.drawCenteredClippedString(font, g, Component.translatable("screen.finance.newspaper.empty").getString(),
                    0, BODY_Y + 30, PANEL_WIDTH, INK_DIM);
            return;
        }
        int y = BODY_Y;
        if (columnPage == 0 && !headlineLines.isEmpty()) {
            g.pose().pushPose();
            g.pose().scale(HEADLINE_SCALE, HEADLINE_SCALE, 1.0F);
            for (int i = 0; i < Math.min(2, headlineLines.size()); i++) {
                g.drawString(font, headlineLines.get(i), MARGIN / HEADLINE_SCALE, (y + i * HEADLINE_LINE_H) / HEADLINE_SCALE,
                        paper.extra() ? EXTRA_RED : INK, false);
            }
            g.pose().popPose();
            y += headlineHeight();
            g.fill(MARGIN, y - 4, PANEL_WIDTH - MARGIN, y - 3, 0x66555555);
        }
        int rows = rowsPerColumn(columnPage);
        int start = pageStart(columnPage);
        for (int column = 0; column < 2; column++) {
            int x = MARGIN + column * (COLUMN_W + COLUMN_GAP);
            for (int row = 0; row < rows; row++) {
                int index = start + column * rows + row;
                if (index >= bodyLines.size()) break;
                g.drawString(font, bodyLines.get(index), x, y + row * LINE_H, INK, false);
            }
        }
        // 栏间竖线
        int ruleX = MARGIN + COLUMN_W + COLUMN_GAP / 2;
        if (bodyLines.size() > start + rows) g.fill(ruleX, y, ruleX + 1, y + rows * LINE_H, 0x66555555);
    }

    private static boolean hit(int mx, int my, int x) {
        return mx >= x && mx < x + BUTTON_W && my >= FOOTER_Y && my < FOOTER_Y + 13;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - left, my = (int) mouseY - top;
        if (button == 0) {
            if (hit(mx, my, PREV_X)) { goToSection(section - 1); return true; }
            if (hit(mx, my, NEXT_X)) { goToSection(section + 1); return true; }
            if (hit(mx, my, CLOSE_X)) { onClose(); return true; }
            if (my >= TABS_Y && my < TABS_Y + TAB_H) {
                for (int i = 0; i < tabBounds.size(); i++) {
                    int[] b = tabBounds.get(i);
                    if (mx >= b[0] && mx < b[0] + b[1]) { goToSection(i); return true; }
                }
            }
            // 点正文左半栏往回翻、右半栏往后翻
            if (my >= BODY_Y && my < FOOTER_Y - 4 && mx >= MARGIN && mx < PANEL_WIDTH - MARGIN) {
                turnColumns(mx < PANEL_WIDTH / 2 ? -1 : 1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) turnColumns(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> { goToSection(section - 1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { goToSection(section + 1); return true; }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> { turnColumns(-1); return true; }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> { turnColumns(1); return true; }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
