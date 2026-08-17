package finance.chart;

import java.util.List;

public record MarketSummary(long latestPrice, long previousClose, long change,
                            double changePercent, long high, long low, long volume,
                            double averageFiveDayVolume, boolean volumeSpike) {

    public static MarketSummary from(List<Candlestick> bars) {
        long currentDay = bars == null || bars.isEmpty() ? 0 : bars.get(bars.size() - 1).mcDay();
        return from(bars, currentDay);
    }

    public static MarketSummary from(List<Candlestick> bars, long currentMcDay) {
        if (bars == null || bars.isEmpty()) return null;
        Candlestick latest = bars.get(bars.size() - 1);
        boolean currentInProgress = latest.mcDay() >= currentMcDay;
        long previous = currentInProgress && bars.size() > 1
                ? bars.get(bars.size() - 2).close()
                : bars.size() > 1 ? bars.get(bars.size() - 2).close() : latest.open();
        java.math.BigInteger exactChange = java.math.BigInteger.valueOf(latest.close())
                .subtract(java.math.BigInteger.valueOf(previous));
        long change = exactChange.max(java.math.BigInteger.valueOf(Long.MIN_VALUE))
                .min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        double percent = previous <= 0 ? 0 : exactChange.doubleValue() / previous * 100.0;
        if (!Double.isFinite(percent)) percent = Math.copySign(Double.MAX_VALUE, percent);
        int completeEnd = currentInProgress ? Math.max(0, bars.size() - 1) : bars.size();
        int start = Math.max(0, completeEnd - 5);
        double average = 0;
        if (completeEnd > start) {
            for (int index = start; index < completeEnd; index++) average += bars.get(index).volume();
            average /= completeEnd - start;
        }
        long windowHigh = 0;
        long windowLow = Long.MAX_VALUE;
        long windowVolume = 0;
        for (Candlestick bar : bars) {
            windowHigh = Math.max(windowHigh, bar.high());
            windowLow = Math.min(windowLow, bar.low());
            windowVolume = finance.util.MathUtil.saturatedAddNonNegative(windowVolume, bar.volume());
        }
        return new MarketSummary(latest.close(), previous, change, percent, windowHigh, windowLow,
                windowVolume, average, currentInProgress && average > 0 && latest.volume() > average * 2.0);
    }
}
