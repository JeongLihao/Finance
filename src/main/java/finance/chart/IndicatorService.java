package finance.chart;

import java.util.List;

public final class IndicatorService {
    private IndicatorService() {}

    public static double[] closes(List<Candlestick> bars) {
        if (bars == null) return new double[0];
        return bars.stream().mapToDouble(Candlestick::close).toArray();
    }

    public static double[] ma(List<Candlestick> bars, int period) {
        double[] values = closes(bars);
        double[] result = new double[values.length];
        java.util.Arrays.fill(result, Double.NaN);
        if (period <= 0) return result;
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) sum -= values[i - period];
            if (i >= period - 1) result[i] = sum / period;
        }
        return result;
    }
}
