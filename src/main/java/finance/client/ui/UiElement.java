package finance.client.ui;

/**
 * 界面上的一个元素（面板内坐标）。页面先排出全部元素，再统一绘制、判断点击和悬停；
 * 测试可以只排版不绘制，检查文字和按钮有没有重叠或超出面板。
 */
public record UiElement(Kind kind, int x, int y, int w, int h, String text, int color, float scale,
                        Runnable action, String tooltip, String key, double value) {

    public enum Kind {
        /** 背景色块、卡片边框：可以和别的东西叠在一起。 */
        FILL, BORDER,
        /** 以下几种是“实体”，彼此不许重叠。 */
        TEXT, BUTTON, BUTTON_DISABLED, ICON, PROGRESS, INPUT,
        /** 只负责点击的透明区域。 */
        HOTSPOT
    }

    public boolean solid() {
        return switch (kind) {
            case TEXT, BUTTON, BUTTON_DISABLED, ICON, PROGRESS, INPUT -> true;
            default -> false;
        };
    }

    public boolean contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public boolean overlaps(UiElement o) {
        return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
    }

    public int right() { return x + w; }
    public int bottom() { return y + h; }
}
