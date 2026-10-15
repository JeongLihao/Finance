package finance.earnings;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 季度财报 —— 只读聚合自公司密封的日度经营流水，不许手填编造。
 *
 * <p>无经营数据的公司照发零财报（hasData=false），不参与预期差定价，
 * 不伪造任何数字。
 */
public record QuarterlyEarningsReport(
        UUID companyId,
        String symbol,
        String companyName,
        int quarterIndex,
        long mcDay,
        /** 本期覆盖的经营天数（季度中上市的公司如实标注） */
        int tradingDays,
        long revenue,
        long expenses,
        long netProfit,
        /** 每股收益 = netProfit / totalShares（long 除法） */
        long eps,
        /** 营收环比 bps；上一期为 0 时记 0 */
        long revenueQoQBps,
        /** 利润环比 bps；上一期为 0 时记 0 */
        long profitQoQBps,
        /** 本期每股分红（读 dividendHistory 汇总） */
        long dividendPerShare,
        /** 是否有真实经营数据 */
        boolean hasData,
        /** 市场预期净利润（发布时计算，供展示） */
        long expectedProfit,
        /** 预期差 bps（发布时计算） */
        long surpriseBps,
        LocalDateTime createdAt) {
}
