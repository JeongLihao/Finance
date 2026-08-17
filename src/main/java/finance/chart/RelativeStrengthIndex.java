package finance.chart;

import java.util.Arrays;

public final class RelativeStrengthIndex {
    private RelativeStrengthIndex() {}

    public static double[] calculate(double[] closes, int period) {
        if (closes == null || period <= 0) throw new IllegalArgumentException("Invalid RSI input");
        double[] result = new double[closes.length];
        Arrays.fill(result, Double.NaN);
        if (closes.length <= period) return result;
        double averageGain = 0;
        double averageLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            averageGain += Math.max(0, change);
            averageLoss += Math.max(0, -change);
        }
        averageGain /= period;
        averageLoss /= period;
        result[period] = value(averageGain, averageLoss);
        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            averageGain = (averageGain * (period - 1) + Math.max(0, change)) / period;
            averageLoss = (averageLoss * (period - 1) + Math.max(0, -change)) / period;
            result[i] = value(averageGain, averageLoss);
        }
        return result;
    }

    private static double value(double gain, double loss) {
        if (gain == 0 && loss == 0) return 50;
        if (loss == 0) return 100;
        if (gain == 0) return 0;
        return 100.0 - 100.0 / (1.0 + gain / loss);
    }
}
