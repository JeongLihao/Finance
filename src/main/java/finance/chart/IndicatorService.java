package finance.chart;

import java.util.List;

public final class IndicatorService {
    private IndicatorService() {}

    public static double[] closes(List<Candlestick> bars) {
        if (bars == null) return new double[0];
        return bars.stream().mapToDouble(Candlestick::close).toArray();
    }
}
