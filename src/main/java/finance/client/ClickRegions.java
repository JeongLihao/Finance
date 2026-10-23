package finance.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 画按钮时顺手登记的点击区域：坐标只在画的地方写一次，点击时按登记顺序倒着查（后画的在上面）。
 * 每帧画之前 {@link #clear()}；点击用的坐标系必须和画的时候一致。
 */
final class ClickRegions {
    private record Region(int x, int y, int w, int h, IntConsumer action) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Region> regions = new ArrayList<>();

    void clear() {
        regions.clear();
    }

    void add(int x, int y, int w, int h, Runnable action) {
        regions.add(new Region(x, y, w, h, button -> action.run()));
    }

    /** 动作要区分鼠标键时用这个（参数是鼠标键：0 左键、1 右键）。 */
    void addWithButton(int x, int y, int w, int h, IntConsumer action) {
        regions.add(new Region(x, y, w, h, action));
    }

    /** 点中了某个区域就执行它并返回 true；动作里抛出的异常照常向上抛。 */
    boolean click(int mx, int my) {
        return click(mx, my, 0);
    }

    boolean click(int mx, int my, int button) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.contains(mx, my)) {
                r.action().accept(button);
                return true;
            }
        }
        return false;
    }
}
