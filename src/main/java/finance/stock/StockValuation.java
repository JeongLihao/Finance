package finance.stock;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.gameplay.company.CompanyFundamentalBridge;
import finance.util.MathUtil;

import java.util.Map;

/**
 * 公司估值和每股价值：只看三样——家底、最近赚多少、央行利率。
 *
 * <pre>
 *   家底   = 现金 + 库存（按央行底价）+ 厂房 − 欠款
 *   估值   = 家底 + 近 7 天平均日利润 × 倍数        （亏损时不低于家底的一半）
 *   倍数   = 10000 ÷ (利率基点 + 800)，夹在 4 到 12 倍之间：利率 4% 约 8 倍，10% 约 5.6 倍
 *   每股价值 = 估值 ÷ 总股数
 * </pre>
 *
 * 库存按底价而不是现价算：抢购把商品价格抬高，不会顺带把自己公司的股价抬上去。
 */
public final class StockValuation {

    /** 股票详情里“股价怎么来的”那一行要的数。multipleX10 = 倍数 × 10。 */
    public record Breakdown(long netAssets, long dailyProfit, int multipleX10, long companyValue) {}

    private StockValuation() {}

    /** 央行利率决定的利润倍数（× 10）。 */
    public static int multipleX10(int rateBps) {
        long m = 100_000L / (Math.max(0, rateBps) + 800);
        return (int) Math.max(40, Math.min(120, m));
    }

    public static int currentMultipleX10() {
        return multipleX10(finance.policy.MonetaryPolicyService.benchmarkRateBasisPoints());
    }

    /** 库存按底价估：有底价用底价，没有（旧规则商品）按基准价一半。 */
    public static long inventoryAtFloor(Company company) {
        long total = 0;
        for (Map.Entry<String, Integer> e : company.getInventory().entrySet()) {
            int qty = e.getValue() == null ? 0 : e.getValue();
            if (qty <= 0) continue;
            long unit = finance.centralbank.ReserveService.floorPrice(e.getKey());
            if (unit <= 0) {
                Commodity c = CommodityRegistry.getCommodity(e.getKey());
                unit = c == null ? 0 : Math.max(0, c.getBasePrice() / 2);
            }
            total = MathUtil.saturatedAddNonNegative(total, MathUtil.saturatedMultiplyNonNegative(unit, qty));
        }
        return total;
    }

    public static Breakdown of(Company company) {
        if (company == null) return new Breakdown(0, 0, currentMultipleX10(), 0);
        var inputs = CompanyFundamentalBridge.inputs(company, finance.chart.CandlestickService.currentMcDay());
        long gross = MathUtil.saturatedAddNonNegative(Math.max(0, company.getCash()), inventoryAtFloor(company));
        gross = MathUtil.saturatedAddNonNegative(gross, Math.max(0, inputs.physicalFacilityValue()));
        long net = Math.max(0, gross - Math.max(0, inputs.debtPrincipal()));
        long profit = company.getSmoothedDailyProfit();
        int multiple = currentMultipleX10();
        long earnings;
        try {
            earnings = Math.multiplyExact(profit, (long) multiple) / 10;
        } catch (ArithmeticException e) {
            earnings = profit > 0 ? Long.MAX_VALUE / 4 : -(Long.MAX_VALUE / 4);
        }
        long value = earnings >= 0 ? MathUtil.saturatedAddNonNegative(net, earnings) : Math.max(net / 2, net + earnings);
        return new Breakdown(net, profit, multiple, Math.max(0, value));
    }

    public static long companyValue(Company company) {
        return of(company).companyValue();
    }

    public static long perShare(Company company, long totalShares) {
        return totalShares <= 0 ? 0 : Math.max(1, companyValue(company) / totalShares);
    }
}
