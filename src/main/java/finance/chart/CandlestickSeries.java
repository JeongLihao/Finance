package finance.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CandlestickSeries {

    public static final int MAX_BARS = 120;
    private final List<Candlestick> bars = new ArrayList<>();

    public boolean recordTrade(long mcDay, long price, long quantity) {
        if (mcDay < 0 || price <= 0 || quantity <= 0) return false;
        Candlestick latest = latest();
        if (latest != null && mcDay < latest.mcDay()) return false;
        if (latest != null && mcDay > latest.mcDay()) fillThrough(mcDay - 1);
        latest = latest();
        if (latest != null && latest.mcDay() == mcDay) {
            bars.set(bars.size() - 1, latest.withTrade(price, quantity));
        } else {
            bars.add(Candlestick.trade(mcDay, price, quantity));
        }
        trim();
        return true;
    }

    public void fillThrough(long mcDay) {
        Candlestick latest = latest();
        if (latest == null || mcDay <= latest.mcDay()) return;
        long firstMissing = latest.mcDay() == Long.MAX_VALUE ? Long.MAX_VALUE : latest.mcDay() + 1;
        long retainedStart = Math.max(0, mcDay - MAX_BARS + 1);
        for (long day = Math.max(firstMissing, retainedStart); day <= mcDay; day++) {
            bars.add(Candlestick.carry(day, latest().close()));
            trim();
            if (day == Long.MAX_VALUE) break;
        }
    }

    public boolean addLoaded(Candlestick bar) {
        if (bar == null) return false;
        Candlestick latest = latest();
        if (latest != null && bar.mcDay() <= latest.mcDay()) return false;
        bars.add(bar);
        trim();
        return true;
    }

    public Candlestick latest() {
        return bars.isEmpty() ? null : bars.get(bars.size() - 1);
    }

    public List<Candlestick> getBars(int limit) {
        int safeLimit = Math.max(0, Math.min(MAX_BARS, limit));
        if (safeLimit == 0 || bars.isEmpty()) return List.of();
        int start = Math.max(0, bars.size() - safeLimit);
        return Collections.unmodifiableList(new ArrayList<>(bars.subList(start, bars.size())));
    }

    private void trim() {
        if (bars.size() > MAX_BARS) bars.subList(0, bars.size() - MAX_BARS).clear();
    }
}
