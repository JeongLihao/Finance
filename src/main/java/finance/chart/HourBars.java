package finance.chart;

import java.util.ArrayList;
import java.util.List;

/**
 * 把价格变动点合成“游戏小时 K 线”：1 游戏小时 = 1000 tick ≈ 现实 50 秒。
 * 没有变动的时段不画；结果按 开、高、低、收 四个数一组摊平，方便塞进现有的数字列表里发给客户端。
 */
public final class HourBars {

    public static final long BUCKET_MILLIS = 50_000L;
    public static final int MAX_BARS = 32;

    private HourBars() {}

    /** times 与 prices 一一对应、按时间先后；返回最近 maxBars 根。 */
    public static List<Long> build(List<Long> times, List<Long> prices, int maxBars) {
        List<Long> out = new ArrayList<>();
        int n = Math.min(times.size(), prices.size());
        long bucket = Long.MIN_VALUE, open = 0, high = 0, low = 0, close = 0;
        for (int i = 0; i < n; i++) {
            long price = prices.get(i);
            if (price <= 0) continue;
            long b = Math.floorDiv(times.get(i), BUCKET_MILLIS);
            if (b != bucket) {
                if (bucket != Long.MIN_VALUE) add(out, open, high, low, close);
                bucket = b;
                open = high = low = close = price;
            } else {
                high = Math.max(high, price);
                low = Math.min(low, price);
                close = price;
            }
        }
        if (bucket != Long.MIN_VALUE) add(out, open, high, low, close);
        int keep = Math.max(0, maxBars) * 4;
        return out.size() <= keep ? out : new ArrayList<>(out.subList(out.size() - keep, out.size()));
    }

    private static void add(List<Long> out, long open, long high, long low, long close) {
        out.add(open);
        out.add(high);
        out.add(low);
        out.add(close);
    }
}
