package finance.chart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalIndicatorTest {
    @Test void emaUsesSmaSeedAndThenStandardSmoothing() {
        double[] ema = ExponentialMovingAverage.calculate(new double[]{1, 2, 3, 4, 5}, 3);
        assertTrue(Double.isNaN(ema[1]));
        assertEquals(2, ema[2], 1e-9);
        assertEquals(3, ema[3], 1e-9);
        assertEquals(4, ema[4], 1e-9);
    }

    @Test void rsiHandlesFlatOnlyGainAndOnlyLossSeries() {
        assertEquals(50, RelativeStrengthIndex.calculate(new double[]{5,5,5,5}, 3)[3], 1e-9);
        assertEquals(100, RelativeStrengthIndex.calculate(new double[]{1,2,3,4}, 3)[3], 1e-9);
        assertEquals(0, RelativeStrengthIndex.calculate(new double[]{4,3,2,1}, 3)[3], 1e-9);
    }

    @Test void macdProducesSignalOnlyAfterEnoughSamples() {
        double[] closes = new double[40];
        for (int i = 0; i < closes.length; i++) closes[i] = i + 1;
        MacdIndicator.Result result = MacdIndicator.calculate(closes);
        assertTrue(Double.isNaN(result.dea()[32]));
        assertTrue(Double.isFinite(result.dea()[33]));
        assertTrue(result.dif()[39] > 0);
    }
}
