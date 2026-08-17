package finance.chart;

import java.util.Arrays;

public final class MacdIndicator {
    public record Result(double[] dif, double[] dea, double[] histogram) {}
    private MacdIndicator() {}

    public static Result calculate(double[] closes) { return calculate(closes, 12, 26, 9); }

    public static Result calculate(double[] closes, int fast, int slow, int signal) {
        if (fast <= 0 || slow <= fast || signal <= 0) throw new IllegalArgumentException("Invalid MACD periods");
        double[] fastEma = ExponentialMovingAverage.calculate(closes, fast);
        double[] slowEma = ExponentialMovingAverage.calculate(closes, slow);
        double[] dif = new double[closes.length];
        double[] dea = new double[closes.length];
        double[] histogram = new double[closes.length];
        Arrays.fill(dif, Double.NaN);
        Arrays.fill(dea, Double.NaN);
        Arrays.fill(histogram, Double.NaN);
        int firstDif = slow - 1;
        for (int i = firstDif; i < closes.length; i++) dif[i] = fastEma[i] - slowEma[i];
        if (closes.length < firstDif + signal) return new Result(dif, dea, histogram);
        double seed = 0;
        for (int i = firstDif; i < firstDif + signal; i++) seed += dif[i];
        int firstSignal = firstDif + signal - 1;
        dea[firstSignal] = seed / signal;
        histogram[firstSignal] = dif[firstSignal] - dea[firstSignal];
        double alpha = 2.0 / (signal + 1.0);
        for (int i = firstSignal + 1; i < closes.length; i++) {
            dea[i] = alpha * dif[i] + (1.0 - alpha) * dea[i - 1];
            histogram[i] = dif[i] - dea[i];
        }
        return new Result(dif, dea, histogram);
    }
}
