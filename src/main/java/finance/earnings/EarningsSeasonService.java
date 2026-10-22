package finance.earnings;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.feedback.FeedbackAudience;
import finance.feedback.FeedbackSeverity;
import finance.feedback.WorldEconomyEvent;
import finance.feedback.WorldEconomyFeedbackService;
import finance.feedback.WorldFeedbackType;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.util.MathUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * 财报季服务 —— 每 MC 天检查上市公司财报日，发布季报并按预期差定价。
 *
 * <p>挂在 {@code EconomyCycleService.tickDay}，在 {@code CompanyManager.settleDailyProfits}
 * 之后执行（当日流水已密封）。
 */
public final class EarningsSeasonService {

    private EarningsSeasonService() {}

    public static void processDay(MinecraftServer server, long mcDay) {
        int quarterDays = FinanceConfig.earningsQuarterDays();
        if (quarterDays <= 0) {
            return;
        }
        boolean dirty = false;
        for (Company company : CompanyManager.getCompanies()) {
            if (!company.isPublic()) {
                continue;
            }
            Stock stock = StockMarketManager.getStockByCompanyId(company.getCompanyId());
            if (stock == null || stock.getTotalShares() <= 0) {
                continue;
            }
            if (!EarningsExpectation.isEarningsDay(company.getCompanyId(), mcDay, quarterDays)) {
                continue;
            }
            publishEarnings(server, company, stock, mcDay);
            dirty = true;
        }
        if (dirty) {
            EconomySavedData.markDirty();
        }
    }

    private static void publishEarnings(MinecraftServer server, Company company, Stock stock, long mcDay) {
        Company.QuarterAccumulator acc = company.drainQuarter();
        List<QuarterlyEarningsReport> history = company.getEarningsHistory();
        int quarterIndex =
                history.isEmpty() ? 1 : history.get(history.size() - 1).quarterIndex() + 1;

        boolean hasData = acc.revenue() != 0 || acc.expenses() != 0;
        long netProfit = acc.revenue() - acc.expenses();
        long totalShares = Math.max(1, stock.getTotalShares());
        long eps = netProfit / totalShares;
        QuarterlyEarningsReport previous = history.isEmpty() ? null : history.get(history.size() - 1);
        long revenueQoQ = previous == null ? 0 : EarningsExpectation.qoqBps(acc.revenue(), previous.revenue());
        long profitQoQ = previous == null ? 0 : EarningsExpectation.qoqBps(netProfit, previous.netProfit());
        long dividendPerShare = quarterlyDividendPerShare(company, mcDay);

        long expected = EarningsExpectation.expectedProfit(history);
        long surprise = hasData ? EarningsExpectation.surpriseBps(netProfit, expected) : 0;

        QuarterlyEarningsReport report = new QuarterlyEarningsReport(
                company.getCompanyId(),
                stock.getSymbol(),
                company.getName(),
                quarterIndex,
                mcDay,
                acc.days(),
                acc.revenue(),
                acc.expenses(),
                netProfit,
                eps,
                revenueQoQ,
                profitQoQ,
                dividendPerShare,
                hasData,
                expected,
                surprise,
                LocalDateTime.now());
        company.addEarningsReportDirect(report, Math.max(1, FinanceConfig.earningsHistoryKept()));

        // 预期差定价：只在"有数据且有预期"时冲击
        if (hasData && expected != Long.MIN_VALUE && surprise != 0) {
            double shockBps = (double) surprise * FinanceConfig.earningsReactionBps() / 10000.0;
            long maxShock = Math.max(1, FinanceConfig.earningsMaxShockBps());
            shockBps = Math.max(-maxShock, Math.min(maxShock, shockBps));
            double fvPass = Math.max(0.0, Math.min(1.0, FinanceConfig.earningsFvPassThroughBps() / 10000.0));
            stock.applyEarningsShock(shockBps, fvPass);
        }

        announce(server, company, stock, report, expected);
    }

    /** 本季度每股分红：汇总 dividendHistory 中本季度内的每股分红（perShare），不是总额。 */
    private static long quarterlyDividendPerShare(Company company, long mcDay) {
        int quarterDays = Math.max(1, FinanceConfig.earningsQuarterDays());
        long quarterStart = mcDay - quarterDays;
        long total = 0;
        for (Company.DividendRecord record : company.getDividendHistory()) {
            if (record.mcDay() > quarterStart && record.mcDay() <= mcDay) {
                total = MathUtil.saturatedAddNonNegative(total, record.perShare());
            }
        }
        return total;
    }

    private static void announce(
            MinecraftServer server, Company company, Stock stock, QuarterlyEarningsReport report, long expected) {
        String key;
        FeedbackSeverity severity;
        List<String> args = new ArrayList<>();
        args.add(company.getName());
        args.add("Q" + report.quarterIndex());
        args.add(formatMoney(report.revenue()));
        args.add(formatMoney(report.netProfit()));
        args.add(formatMoney(report.eps()));

        if (!report.hasData()) {
            key = "finance.feedback.earnings_nodata";
            severity = FeedbackSeverity.INFO;
        } else if (expected == Long.MIN_VALUE) {
            key = "finance.feedback.earnings_first";
            severity = FeedbackSeverity.INFO;
        } else if (report.surpriseBps() >= 1000) {
            key = "finance.feedback.earnings_beat";
            severity = FeedbackSeverity.WARNING;
            args.add(formatPct(report.surpriseBps()));
        } else if (report.surpriseBps() <= -1000) {
            key = "finance.feedback.earnings_miss";
            severity = FeedbackSeverity.WARNING;
            args.add(formatPct(report.surpriseBps()));
        } else {
            key = "finance.feedback.earnings_inline";
            severity = FeedbackSeverity.INFO;
        }

        WorldEconomyFeedbackService.publish(
                server,
                new WorldEconomyEvent(
                        "earnings:Q" + report.quarterIndex(),
                        WorldFeedbackType.EARNINGS_REPORT,
                        severity,
                        company.getCompanyId().toString(),
                        report.mcDay(),
                        "",
                        null,
                        key,
                        Collections.unmodifiableList(args),
                        FeedbackAudience.GLOBAL,
                        Collections.emptySet()));
        // 审计留痕：WorldEconomyFeedbackService.HISTORY 已持久化（record() 内 markDirty），不再另记流水。
    }

    private static String formatMoney(long amount) {
        if (amount == Long.MIN_VALUE) {
            // Math.abs(Long.MIN_VALUE) 会回绕成自身，特殊处理避免输出 "--9223372036854775808"
            return "-9223372036854775808";
        }
        return (amount < 0 ? "-" : "") + Math.abs(amount);
    }

    private static String formatPct(long bps) {
        long sign = bps < 0 ? -1 : 1;
        long abs = Math.abs(bps);
        return (sign < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100) + "%";
    }
}
