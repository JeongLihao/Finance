package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.cycle.EconomyCycleService;
import finance.earnings.EarningsExpectation;
import finance.earnings.QuarterlyEarningsReport;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /earnings —— 财报季查询。
 * <ul>
 *   <li>/earnings calendar [days] —— 未来 N 天（默认 30）上市公司财报日历</li>
 *   <li>/earnings report &lt;symbol&gt; —— 某股票最近一期季报 + 市场预期 + 历史预期差</li>
 * </ul>
 * 只读已发布数据，无提前泄露通道。
 */
public class EarningsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("earnings")
                        // 只读公开数据，全员可查；显式声明 OP-0，与其他命令的 requires 惯例对齐
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.literal("calendar")
                                .executes(context -> showCalendar(context.getSource(), 30))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                        .executes(context -> showCalendar(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "days")))))
                        .then(Commands.literal("report")
                                .then(Commands.argument("symbol", StringArgumentType.word())
                                        .executes(context -> showReport(context.getSource(),
                                                StringArgumentType.getString(context, "symbol")))))
        );
    }

    private static int showCalendar(CommandSourceStack source, int days) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("该命令需由玩家执行。"));
            return 0;
        }
        int quarterDays = Math.max(1, FinanceConfig.earningsQuarterDays());
        long today = EconomyCycleService.currentMcDay(player.getServer());

        List<CalendarEntry> entries = new ArrayList<>();
        for (Company company : CompanyManager.getCompanies()) {
            if (!company.isPublic()) {
                continue;
            }
            Stock stock = StockMarketManager.getStockByCompanyId(company.getCompanyId());
            if (stock == null) {
                continue;
            }
            int offset = EarningsExpectation.earningsDayOffset(company.getCompanyId(), quarterDays);
            for (int d = 0; d < days; d++) {
                long day = today + d;
                if (Math.floorMod(day, quarterDays) == offset) {
                    entries.add(new CalendarEntry(day, "D+" + d + "（第 " + day + " 天） " + company.getName()
                            + " [" + stock.getSymbol() + "] 现价 " + stock.getLastPrice()));
                }
            }
        }
        if (entries.isEmpty()) {
            player.sendSystemMessage(Component.literal("未来 " + days + " 天没有财报日。"));
            return 1;
        }
        // 按绝对天数排序：字符串排序会把 "D+10" 排到 "D+5" 前面
        entries.sort(Comparator.comparingLong(CalendarEntry::day));
        player.sendSystemMessage(Component.literal("=== 财报日历（未来 " + days + " 天）==="));
        for (CalendarEntry entry : entries) {
            player.sendSystemMessage(Component.literal(entry.text()));
        }
        return 1;
    }

    private static int showReport(CommandSourceStack source, String symbol) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("该命令需由玩家执行。"));
            return 0;
        }
        Stock stock = StockMarketManager.getStock(StockMarketManager.normalizeSymbol(symbol));
        if (stock == null) {
            player.sendSystemMessage(Component.literal("找不到股票：" + symbol));
            return 0;
        }
        Company company = CompanyManager.getCompanies().stream()
                .filter(c -> c.getCompanyId().equals(stock.getCompanyId()))
                .findFirst().orElse(null);
        if (company == null) {
            player.sendSystemMessage(Component.literal("找不到该股票对应的公司。"));
            return 0;
        }
        List<QuarterlyEarningsReport> history = company.getEarningsHistory();
        if (history.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    company.getName() + " [" + stock.getSymbol() + "] 尚未发布过季报。"));
            return 1;
        }
        QuarterlyEarningsReport latest = history.get(history.size() - 1);
        player.sendSystemMessage(Component.literal(
                "=== " + company.getName() + " [" + stock.getSymbol() + "] Q" + latest.quarterIndex()
                        + " 季报（第 " + latest.mcDay() + " 天发布）==="));
        player.sendSystemMessage(Component.literal(
                "营收 " + latest.revenue() + " | 成本 " + latest.expenses()
                        + " | 净利润 " + latest.netProfit() + " | EPS " + latest.eps()));
        player.sendSystemMessage(Component.literal(
                "营收环比 " + fmtBps(latest.revenueQoQBps()) + " | 利润环比 " + fmtBps(latest.profitQoQBps())
                        + " | 每股分红 " + latest.dividendPerShare()
                        + " | 覆盖 " + latest.tradingDays() + " 天"
                        + (latest.hasData() ? "" : "（本期无经营数据）")));
        long expected = latest.expectedProfit();
        long surprise = latest.surpriseBps();
        if (expected == Long.MIN_VALUE) {
            player.sendSystemMessage(Component.literal("市场预期：暂无（首份季报）"));
        } else {
            player.sendSystemMessage(Component.literal(
                    "市场预期净利润 " + expected + " | 实际 " + latest.netProfit()
                            + " | 预期差 " + fmtBps(surprise)));
        }
        if (history.size() > 1) {
            StringBuilder sb = new StringBuilder("历史预期差：");
            for (int i = Math.max(0, history.size() - 6); i < history.size(); i++) {
                QuarterlyEarningsReport r = history.get(i);
                sb.append("Q").append(r.quarterIndex()).append(" ")
                        .append(fmtBps(r.surpriseBps())).append("；");
            }
            player.sendSystemMessage(Component.literal(sb.toString()));
        }
        return 1;
    }

    private static String fmtBps(long bps) {
        long abs = Math.abs(bps);
        return (bps < 0 ? "-" : "+") + (abs / 100) + "." + String.format("%02d", abs % 100) + "%";
    }

    /** 财报日历条目：携带绝对天数用于数字排序，避免 "D+10" 排在 "D+5" 前。 */
    private record CalendarEntry(long day, String text) {
    }
}
