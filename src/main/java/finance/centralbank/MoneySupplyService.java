package finance.centralbank;

import finance.metrics.EconomyMetricsService;

import java.util.List;

/**
 * 货币总量：所有的钱都算央行发行的。“流通中的钱” = 玩家和银行账户 + 公司账上 + 央行发出的钞票；
 * 央行和市场手里的钱不算流通。最近 7 天的变化取自每日经济快照。
 */
public final class MoneySupplyService {

    /** accounts：玩家和银行账户；companies：公司账上；notes：钞票；change7d：账户加公司最近 7 天的变化。 */
    public record Summary(long accounts, long companies, long notes, long circulating, long centralBankAndMarket,
                          long change7d, boolean hasHistory) {}

    private MoneySupplyService() {}

    public static Summary summary() {
        EconomyMetricsService.CurrentMetrics m = EconomyMetricsService.getCurrentMetrics();
        long accounts = add(m.playerCash(), m.playerFrozenFunds());
        long companies = Math.max(0, m.companyCash());
        long notes = finance.cash.CashService.notesOutstanding();
        long circulating = add(add(accounts, companies), notes);
        long system = add(m.npcCash(), m.centralBankReserve());
        List<EconomyMetricsService.DailySnapshot> days = EconomyMetricsService.getDailySnapshots();
        if (days.isEmpty()) return new Summary(accounts, companies, notes, circulating, system, 0, false);
        EconomyMetricsService.DailySnapshot past = days.get(Math.max(0, days.size() - 7));
        long before = add(add(past.playerCash(), past.playerFrozenFunds()), past.companyCash());
        return new Summary(accounts, companies, notes, circulating, system, add(accounts, companies) - before, true);
    }

    private static long add(long a, long b) {
        long r = Math.max(0, a) + Math.max(0, b);
        return r < 0 ? Long.MAX_VALUE : r;
    }
}
