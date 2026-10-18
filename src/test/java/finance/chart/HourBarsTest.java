package finance.chart;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 游戏小时 K 线：同一小时内的变动合成一根，没有变动的小时不画。 */
class HourBarsTest {

    @Test void pointsInTheSameHourBecomeOneBar() {
        long h = HourBars.BUCKET_MILLIS;
        List<Long> bars = HourBars.build(List.of(0L, 10L, 20L, h * 5, h * 5 + 1), List.of(10L, 15L, 8L, 20L, 18L), 32);
        assertEquals(List.of(10L, 15L, 8L, 8L, 20L, 20L, 18L, 18L), bars, "两根：第 0 小时和第 5 小时，中间空着的小时不画");
    }

    @Test void keepsOnlyTheLatestBars() {
        long h = HourBars.BUCKET_MILLIS;
        List<Long> times = new java.util.ArrayList<>(), prices = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) { times.add(h * i); prices.add(100L + i); }
        List<Long> bars = HourBars.build(times, prices, 10);
        assertEquals(40, bars.size());
        assertEquals(140L, bars.get(0));
    }

    @Test void emptyInputGivesNoBars() {
        assertTrue(HourBars.build(List.of(), List.of(), 32).isEmpty());
    }
}
