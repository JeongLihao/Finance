package finance.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 画按钮时登记的点击区域：边界、叠放顺序、清空。 */
class ClickRegionsTest {

    @Test
    void hitsInsideAndMissesOnRightAndBottomEdge() {
        ClickRegions regions = new ClickRegions();
        List<String> hits = new ArrayList<>();
        regions.add(10, 20, 30, 13, () -> hits.add("a"));
        assertTrue(regions.click(10, 20), "左上角算点中");
        assertTrue(regions.click(39, 32), "右下角最后一个像素算点中");
        assertFalse(regions.click(40, 25), "右边界外");
        assertFalse(regions.click(15, 33), "下边界外");
        assertFalse(regions.click(9, 25), "左边界外");
        assertEquals(List.of("a", "a"), hits);
    }

    @Test
    void laterRegionWinsWhenOverlapping() {
        ClickRegions regions = new ClickRegions();
        List<String> hits = new ArrayList<>();
        regions.add(0, 0, 50, 50, () -> hits.add("under"));
        regions.add(10, 10, 10, 10, () -> hits.add("over"));
        assertTrue(regions.click(15, 15));
        assertTrue(regions.click(40, 40));
        assertEquals(List.of("over", "under"), hits, "后画的在上面，先查");
    }

    @Test
    void clearForgetsLastFrame() {
        ClickRegions regions = new ClickRegions();
        regions.add(0, 0, 10, 10, () -> fail("清空后不该再触发"));
        regions.clear();
        assertFalse(regions.click(5, 5));
    }

    @Test
    void actionExceptionPropagates() {
        ClickRegions regions = new ClickRegions();
        regions.add(0, 0, 10, 10, () -> {
            throw new IllegalStateException("bad input");
        });
        assertThrows(IllegalStateException.class, () -> regions.click(1, 1), "输入框校验失败要能传回界面，由界面统一提示");
    }
}
