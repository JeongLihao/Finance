package finance.client.ui;

import finance.client.FinanceGui;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 把排好的 {@link UiElement} 画出来，并负责点击和悬停提示。坐标都是面板内坐标。 */
public final class UiPainter {

    private UiPainter() {}

    public static void paint(GuiGraphics g, Font font, List<UiElement> elements, int mouseX, int mouseY) {
        for (UiElement e : elements) {
            switch (e.kind()) {
                case FILL -> g.fill(e.x(), e.y(), e.right(), e.bottom(), e.color());
                case BORDER -> FinanceGui.drawSimpleBorder(g, e.x(), e.y(), e.w(), e.h(), e.color());
                case TEXT -> {
                    if (e.scale() == 1) {
                        g.drawString(font, e.text(), e.x(), e.y(), e.color(), false);
                    } else {
                        g.pose().pushPose();
                        g.pose().translate(e.x(), e.y(), 0);
                        g.pose().scale(e.scale(), e.scale(), 1);
                        g.drawString(font, e.text(), 0, 0, e.color(), false);
                        g.pose().popPose();
                    }
                }
                case BUTTON -> {
                    boolean hover = e.contains(mouseX, mouseY);
                    g.fill(e.x(), e.y(), e.right(), e.bottom(), hover ? 0xFF3E9443 : FinanceGui.COL_GOOD);
                    FinanceGui.drawSimpleBorder(g, e.x(), e.y(), e.w(), e.h(), FinanceGui.COL_PANEL_BORDER);
                    centered(g, font, e, 0xFFFFFFFF);
                }
                case BUTTON_DISABLED -> {
                    g.fill(e.x(), e.y(), e.right(), e.bottom(), FinanceGui.COL_BUTTON_BG);
                    FinanceGui.drawSimpleBorder(g, e.x(), e.y(), e.w(), e.h(), 0xFF8C8676);
                    centered(g, font, e, 0xFF7A7568);
                }
                case ICON -> {
                    Commodity c = CommodityRegistry.getCommodity(e.key());
                    if (c != null) FinanceGui.renderItemIcon(g, c, e.x(), e.y());
                }
                case PROGRESS -> {
                    g.fill(e.x(), e.y(), e.right(), e.bottom(), 0xFFCFC8B4);
                    g.fill(e.x(), e.y(), e.x() + (int) Math.round(e.w() * e.value()), e.bottom(), FinanceGui.COL_GOOD);
                }
                case CHART -> FinanceGui.drawTrendGraph(g, e.series(), e.x(), e.y(), e.w(), e.h(), e.color());
                case CANDLES -> FinanceGui.drawHourCandles(g, e.series(), e.x(), e.y(), e.w(), e.h());
                case INPUT, HOTSPOT -> {}
            }
        }
    }

    /** 悬停在被截断的文字或灰色按钮上时显示全文或原因。在 renderLabels 之后、面板坐标系里调用。 */
    public static void tooltip(GuiGraphics g, Font font, List<UiElement> elements, int mouseX, int mouseY) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            UiElement e = elements.get(i);
            if (e.tooltip() != null && e.solid() && e.contains(mouseX, mouseY)) {
                g.renderTooltip(font, font.split(Component.literal(e.tooltip()), 220), mouseX, mouseY);
                return;
            }
        }
    }

    /** 点击：从上往下找第一个带动作的元素。 */
    public static boolean click(List<UiElement> elements, int mouseX, int mouseY) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            UiElement e = elements.get(i);
            if (e.action() != null && e.contains(mouseX, mouseY)) {
                e.action().run();
                return true;
            }
        }
        return false;
    }

    private static void centered(GuiGraphics g, Font font, UiElement e, int color) {
        int tx = e.x() + (e.w() - font.width(e.text())) / 2;
        g.drawString(font, e.text(), tx, e.y() + 3, color, false);
    }
}
