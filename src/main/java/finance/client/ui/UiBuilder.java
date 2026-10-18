package finance.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 按真实文字宽度排版：按钮宽度跟着文字走，放不下的文字截断成“...”并在悬停时显示全文，长说明自动换行。
 * 只产出 {@link UiElement}，不碰绘制，所以测试里也能跑。
 */
public final class UiBuilder {

    public static final int TEXT_H = 9, LINE = 11, BUTTON_H = 13, BUTTON_PAD = 10;
    static final String ELLIPSIS = "...";

    /** 一个按钮：blocked 不为 null 时灰显，点击后在状态栏说明原因。 */
    public record Btn(String label, Runnable action, String blocked) {}

    private final TextMeasure measure;
    private final List<UiElement> out = new ArrayList<>();
    /** 灰色按钮被点击时调用，参数是原因。 */
    private final Consumer<String> explain;

    public UiBuilder(TextMeasure measure, Consumer<String> explain) {
        this.measure = measure;
        this.explain = explain;
    }

    public List<UiElement> elements() { return out; }

    public int width(String text) { return measure.width(text); }

    // ---------------------------------------------------------------- 背景

    public void fill(int x, int y, int w, int h, int color) {
        out.add(new UiElement(UiElement.Kind.FILL, x, y, w, h, null, color, 1, null, null, null, 0));
    }

    public void card(int x, int y, int w, int h, int fill, int border) {
        fill(x, y, w, h, fill);
        out.add(new UiElement(UiElement.Kind.BORDER, x, y, w, h, null, border, 1, null, null, null, 0));
    }

    // ---------------------------------------------------------------- 文字

    /** 截断到 maxWidth 以内；返回实际宽度。被截断时悬停显示全文。 */
    public int text(int x, int y, int maxWidth, String text, int color) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return 0;
        String shown = clip(text, maxWidth);
        if (shown.isEmpty()) return 0;
        int w = measure.width(shown);
        out.add(new UiElement(UiElement.Kind.TEXT, x, y, w, TEXT_H, shown, color, 1, null,
                shown.equals(text) ? null : text, null, 0));
        return w;
    }

    /** 右对齐到 rightX；返回左边界。 */
    public int textRight(int rightX, int y, int maxWidth, String text, int color) {
        int w = measure.width(clip(text, maxWidth));
        text(rightX - w, y, maxWidth, text, color);
        return rightX - w;
    }

    /** 放大的文字（例如钱包余额）。 */
    public void bigText(int x, int y, int maxWidth, String text, int color, float scale) {
        String shown = clip(text, (int) (maxWidth / scale));
        int w = (int) Math.ceil(measure.width(shown) * scale);
        out.add(new UiElement(UiElement.Kind.TEXT, x, y, w, (int) Math.ceil(TEXT_H * scale), shown, color, scale, null,
                shown.equals(text) ? null : text, null, 0));
    }

    /** 自动换行，最多 maxLines 行（放不下的部分截断，悬停看全文）；返回占用的高度。 */
    public int wrapped(int x, int y, int width, String text, int color, int maxLines) {
        List<String> lines = wrap(text, width);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) {
            if (i == n - 1 && lines.size() > n) {
                String shown = clip(String.join("", lines.subList(i, lines.size())), width);
                out.add(new UiElement(UiElement.Kind.TEXT, x, y + i * LINE, measure.width(shown), TEXT_H, shown, color, 1,
                        null, text, null, 0));
            } else {
                text(x, y + i * LINE, width, lines.get(i), color);
            }
        }
        return n * LINE;
    }

    public List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            line.append(c);
            if (measure.width(line.toString()) <= width) continue;
            line.setLength(line.length() - 1);
            int space = line.lastIndexOf(" ");
            if (c < 128 && c != ' ' && space > 0 && line.length() - space < 12) {
                // 英文单词尽量不拆开
                String tail = line.substring(space + 1);
                lines.add(line.substring(0, space));
                line.setLength(0);
                line.append(tail);
            } else {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (c != ' ') line.append(c);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    public String clip(String text, int maxWidth) {
        if (text == null) return "";
        if (measure.width(text) <= maxWidth) return text;
        int ell = measure.width(ELLIPSIS);
        if (maxWidth <= ell) return "";
        int end = text.length();
        while (end > 0 && measure.width(text.substring(0, end)) + ell > maxWidth) end--;
        return text.substring(0, end) + ELLIPSIS;
    }

    // ---------------------------------------------------------------- 按钮

    public int buttonWidth(String label) {
        return measure.width(label) + BUTTON_PAD;
    }

    /** 左上角在 (x, y)；返回宽度。 */
    public int button(int x, int y, String label, Runnable action, String blocked) {
        int w = buttonWidth(label);
        if (blocked == null) {
            out.add(new UiElement(UiElement.Kind.BUTTON, x, y, w, BUTTON_H, label, 0, 1, action, null, null, 0));
        } else {
            String reason = blocked;
            out.add(new UiElement(UiElement.Kind.BUTTON_DISABLED, x, y, w, BUTTON_H, label, 0, 1,
                    () -> explain.accept(reason), reason, null, 0));
        }
        return w;
    }

    public int button(int x, int y, Btn b) {
        return button(x, y, b.label(), b.action(), b.blocked());
    }

    /** 右边缘对齐到 rightX；返回按钮左边界。 */
    public int buttonRight(int rightX, int y, String label, Runnable action, String blocked) {
        int w = buttonWidth(label);
        button(rightX - w, y, label, action, blocked);
        return rightX - w;
    }

    /** 一排按钮从右往左排，右边缘对齐 rightX；返回最左边按钮的左边界。 */
    public int buttonsRight(int rightX, int y, int gap, List<Btn> buttons) {
        int x = rightX;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Btn b = buttons.get(i);
            x -= buttonWidth(b.label());
            button(x, y, b);
            if (i > 0) x -= gap;
        }
        return x;
    }

    /** 这排按钮从右往左排时最左边会到哪里（只算不画）。 */
    public int buttonsRightStart(int rightX, int gap, List<String> labels) {
        int x = rightX;
        for (int i = 0; i < labels.size(); i++) x -= buttonWidth(labels.get(i)) + (i > 0 ? gap : 0);
        return x;
    }

    /** 一排按钮从左往右排，放不下就换行；返回占用的高度。 */
    public int buttonFlow(int x, int y, int maxWidth, int gap, List<Btn> buttons) {
        int cx = x, cy = y;
        for (Btn b : buttons) {
            int w = buttonWidth(b.label());
            if (cx > x && cx + w > x + maxWidth) {
                cx = x;
                cy += BUTTON_H + 3;
            }
            button(cx, cy, b);
            cx += w + gap;
        }
        return buttons.isEmpty() ? 0 : cy - y + BUTTON_H;
    }

    // ---------------------------------------------------------------- 其他

    public void progress(int x, int y, int w, double ratio) {
        out.add(new UiElement(UiElement.Kind.PROGRESS, x, y, w, 5, null, 0, 1, null, null, null,
                Math.max(0, Math.min(1, Double.isFinite(ratio) ? ratio : 0))));
    }

    /** 走势图（价格历史）。 */
    public void chart(int x, int y, int w, int h, List<Long> series, int color) {
        out.add(new UiElement(UiElement.Kind.CHART, x, y, w, h, null, color, 1, null, null, null, 0,
                series == null ? List.of() : List.copyOf(series)));
    }

    /** K 线：bars 为开、高、低、收四个数一组。 */
    public void candles(int x, int y, int w, int h, List<Long> bars) {
        out.add(new UiElement(UiElement.Kind.CANDLES, x, y, w, h, null, 0, 1, null, null, null, 0,
                bars == null ? List.of() : List.copyOf(bars)));
    }

    public void icon(int x, int y, String commodityId) {
        out.add(new UiElement(UiElement.Kind.ICON, x, y, 10, 10, null, 0, 1, null, null, commodityId, 0));
    }

    /** 输入框占位：真正的 EditBox 由界面按 key 放到这里。 */
    public void input(String key, int x, int y, int w) {
        out.add(new UiElement(UiElement.Kind.INPUT, x, y, w, 16, null, 0, 1, null, null, key, 0));
    }

    public void hotspot(int x, int y, int w, int h, Runnable action) {
        out.add(new UiElement(UiElement.Kind.HOTSPOT, x, y, w, h, null, 0, 1, action, null, null, 0));
    }
}
