package finance.chart;

import java.util.Arrays;

public final class ExponentialMovingAverage {
    private ExponentialMovingAverage() {}

    public static double[] calculate(double[] values, int period) {
        if (values == null || period <= 0) throw new IllegalArgumentException("Invalid EMA input");
        double[] result = new double[values.length];
        Arrays.fill(result, Double.NaN);
        if (values.length < period) return result;
        double seed = 0;
        for (int i = 0; i < period; i++) {
            if (!Double.isFinite(values[i])) return result;
            seed += values[i];
        }
        result[period - 1] = seed / period;
        double alpha = 2.0 / (period + 1.0);
        for (int i = period; i < values.length; i++) {
            result[i] = Double.isFinite(values[i])
                    ? alpha * values[i] + (1.0 - alpha) * result[i - 1] : result[i - 1];
        }
        return result;
    }
}
