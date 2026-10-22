package finance.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 某聚落某商品的本地市场账本：当天累计的需求/供给/消费，收盘后的价格系数和最近 60 天历史。
 */
public final class GoodBook {
    public static final int HISTORY_DAYS = 60;

    /** 一天的收盘记录。 */
    public record Point(long day, int factorBps, long demand, long supply, long consumed) {}

    int factorBps = MacroFormulas.NEUTRAL_BPS;
    long demandToday;
    long supplyToday;
    long consumedToday;
    long exportsToday;
    long importsToday;
    long lastDemand;
    long lastSupply;
    long lastConsumed;
    long lastImports;
    long lastExports;
    final ArrayDeque<Point> history = new ArrayDeque<>();

    public int factorBps() {
        return factorBps;
    }

    public long lastDemand() {
        return lastDemand;
    }

    public long lastSupply() {
        return lastSupply;
    }

    public long lastConsumed() {
        return lastConsumed;
    }

    public long lastImports() {
        return lastImports;
    }

    public long lastExports() {
        return lastExports;
    }

    public List<Point> history() {
        return new ArrayList<>(history);
    }

    void recordProcurement(long demand, long supply, long consumed) {
        demandToday = sat(demandToday, demand);
        supplyToday = sat(supplyToday, supply);
        consumedToday = sat(consumedToday, consumed);
    }

    /** 收盘：出口计入需求，按维多利亚 3 公式移动价格系数，清空当日计数。 */
    void close(long day, int stepBps) {
        long demand = sat(demandToday, exportsToday);
        long supply = supplyToday;
        if (demand > 0 || supply > 0) {
            factorBps = MacroFormulas.stepFactor(factorBps, MacroFormulas.targetFactorBps(demand, supply), stepBps);
        }
        lastDemand = demand;
        lastSupply = supply;
        lastConsumed = consumedToday;
        lastImports = importsToday;
        lastExports = exportsToday;
        history.addLast(new Point(day, factorBps, demand, supply, consumedToday));
        while (history.size() > HISTORY_DAYS) history.removeFirst();
        demandToday = supplyToday = consumedToday = exportsToday = importsToday = 0;
    }

    void restore(int factor, long lastDemand, long lastSupply, long lastConsumed, long lastImports, long lastExports) {
        this.factorBps = MacroFormulas.clampFactor(factor);
        this.lastDemand = Math.max(0, lastDemand);
        this.lastSupply = Math.max(0, lastSupply);
        this.lastConsumed = Math.max(0, lastConsumed);
        this.lastImports = Math.max(0, lastImports);
        this.lastExports = Math.max(0, lastExports);
    }

    static long sat(long a, long b) {
        long x = Math.max(0, a), y = Math.max(0, b);
        return x > Long.MAX_VALUE - y ? Long.MAX_VALUE : x + y;
    }
}
