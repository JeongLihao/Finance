package finance.chart;

import java.util.Arrays;
import java.util.List;

public final class MovingAverage {

    private MovingAverage() {
    }

    /** Returns NaN until enough closes exist for the requested simple moving average. */
    public static double[] simple(List<Candlestick> bars, int period) {
        if (bars == null || bars.isEmpty()) return new double[0];
        double[] values = new double[bars.size()];
        Arrays.fill(values, Double.NaN);
        if (period <= 0) return values;
        double sum = 0;
        for (int index = 0; index < bars.size(); index++) {
            sum += bars.get(index).close();
            if (index >= period) sum -= bars.get(index - period).close();
            if (index >= period - 1) values[index] = sum / period;
        }
        return values;
    }
}
