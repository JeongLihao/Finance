package finance.client.ui;

import java.util.ArrayList;
import java.util.List;

/** 检查一页排版：实体元素不许互相重叠，也不许超出面板。返回发现的问题（空表示没问题）。 */
public final class UiLayoutCheck {

    private UiLayoutCheck() {}

    public static List<String> problems(List<UiElement> elements, int panelWidth, int panelHeight) {
        List<String> problems = new ArrayList<>();
        List<UiElement> solid = elements.stream().filter(UiElement::solid).toList();
        for (UiElement e : solid) {
            if (e.x() < 0 || e.y() < 0 || e.right() > panelWidth || e.bottom() > panelHeight) {
                problems.add("超出面板: " + describe(e));
            }
        }
        for (int i = 0; i < solid.size(); i++) {
            for (int j = i + 1; j < solid.size(); j++) {
                UiElement a = solid.get(i), b = solid.get(j);
                if (a.overlaps(b)) problems.add("重叠: " + describe(a) + " 和 " + describe(b));
            }
        }
        return problems;
    }

    static String describe(UiElement e) {
        String label = e.text() != null ? "“" + e.text() + "”" : e.key() != null ? e.key() : "";
        return e.kind() + label + "@(" + e.x() + "," + e.y() + "," + e.w() + "x" + e.h() + ")";
    }
}
