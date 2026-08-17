package finance.chart;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovingAverageTest {

    @Test
    void calculatesStandardMaFiveAndMaTen() {
        List<Candlestick> bars = closes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        double[] ma5 = MovingAverage.simple(bars, 5);
        double[] ma10 = MovingAverage.simple(bars, 10);

        assertTrue(Double.isNaN(ma5[3]));
        assertEquals(3.0, ma5[4]);
        assertEquals(8.0, ma5[9]);
        assertTrue(Double.isNaN(ma10[8]));
        assertEquals(5.5, ma10[9]);
    }

    @Test
    void handlesEmptyInsufficientAndExtremeCloses() {
        assertEquals(0, MovingAverage.simple(List.of(), 5).length);
        double[] insufficient = MovingAverage.simple(closes(1, 2), 5);
        assertTrue(Double.isNaN(insufficient[1]));
        double[] extreme = MovingAverage.simple(List.of(
                Candlestick.carry(0, Long.MAX_VALUE), Candlestick.carry(1, Long.MAX_VALUE)), 2);
        assertTrue(Double.isFinite(extreme[1]));
        assertEquals((double) Long.MAX_VALUE, extreme[1]);
    }

    private static List<Candlestick> closes(long... closes) {
        List<Candlestick> bars = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) bars.add(Candlestick.carry(index, closes[index]));
        return bars;
    }
}
