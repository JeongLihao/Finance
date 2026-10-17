package finance.macro;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.cycle.EconomyCycleService;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageRecord;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /macro 命令：宏观经济的查看与操作入口。查看对所有玩家开放；设定工资仅限公司所有者。
 */
public final class MacroCommand {
    private MacroCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("macro")
                .executes(ctx -> overview(ctx.getSource()))
                .then(Commands.literal("overview").executes(ctx -> overview(ctx.getSource())))
                .then(Commands.literal("pops").executes(ctx -> pops(ctx.getSource())))
                .then(Commands.literal("market")
                        .then(Commands.argument("settlement", StringArgumentType.greedyString())
                                .executes(ctx -> market(ctx.getSource(), StringArgumentType.getString(ctx, "settlement")))))
                .then(Commands.literal("wage")
                        .then(Commands.argument("wage", LongArgumentType.longArg(0, 1_000_000))
                                .executes(ctx -> wage(ctx.getSource(), LongArgumentType.getLong(ctx, "wage")))))
                .then(Commands.literal("jobs").executes(ctx -> jobs(ctx.getSource())))
                .then(Commands.literal("cb").executes(ctx -> centralBank(ctx.getSource())))
                .then(Commands.literal("news").executes(ctx -> news(ctx.getSource())))
                .then(Commands.literal("radar").executes(ctx -> radar(ctx.getSource()))));
    }

    // ================================================================ 查看

    private static int overview(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        if (!MacroEconomyService.active()) {
            lines.add("宏观经济未启用（配置 macroEconomy.enabled 或模块熔断）。");
            send(source, lines);
            return 0;
        }
        MacroEconomyManager.Snapshot s = MacroEconomyManager.latestSnapshot();
        double cpi = MacroIndicators.cpiOrNegative();
        lines.add("====== 宏观经济 ======");
        lines.add(String.format(Locale.ROOT, "CPI %s  年化通胀 %.1f%%", cpi < 0 ? "—" : String.format(Locale.ROOT, "%.1f", cpi),
                MacroIndicators.annualizedInflationPct()));
        if (s != null) {
            lines.add(String.format(Locale.ROOT, "人口 %,d  平均生活水平 %s  就业 %,d  工资 %,d/天  消费 %,d/天",
                    s.population(), pct(s.avgSol()), s.employed(), s.wages(), s.consumptionValue()));
        }
        lines.addAll(centralBankLines());
        send(source, lines);
        return 1;
    }

    private static int pops(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        lines.add("====== 聚落人口 ======");
        for (SettlementRecord settlement : SettlementManager.settlements().values()) {
            VillageRecord village = VillageManager.get(settlement.id());
            SettlementEconomy econ = MacroEconomyManager.settlement(settlement.id());
            if (village == null) continue;
            int pop = village.population();
            long income = econ == null ? 0 : econ.lastIncome;
            lines.add(String.format(Locale.ROOT, "%s：人口 %d  生活水平 %s  就业 %d  人均收入 %.1f",
                    settlement.displayName(), pop, econ == null ? "—" : pct(econ.solBps),
                    econ == null ? 0 : econ.employed, pop <= 0 ? 0.0 : (double) income / pop));
        }
        if (lines.size() == 1) lines.add("还没有登记的聚落（放置聚落贸易站并使用后才会出现）。");
        send(source, lines);
        return 1;
    }

    private static int market(CommandSourceStack source, String name) {
        SettlementRecord settlement = findSettlement(name);
        if (settlement == null) {
            source.sendFailure(Component.literal("找不到聚落：" + name));
            return 0;
        }
        SettlementEconomy econ = MacroEconomyManager.settlement(settlement.id());
        List<String> lines = new ArrayList<>();
        lines.add("====== " + settlement.displayName() + " 市场 ======");
        if (econ == null || econ.goods.isEmpty()) {
            lines.add("还没有市场数据（每日集市结算后出现）。");
            send(source, lines);
            return 1;
        }
        lines.add("商品  需求/供给  消费  进口/出口  价格系数  本地价");
        for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
            GoodBook b = e.getValue();
            lines.add(String.format(Locale.ROOT, "%s  %d/%d  %d  %d/%d  %s  %d", e.getKey(), b.lastDemand, b.lastSupply,
                    b.lastConsumed, b.lastImports, b.lastExports, pct(b.factorBps),
                    MacroEconomyService.localPrice(econ, e.getKey())));
        }
        StringBuilder f = new StringBuilder("满足率：");
        for (NeedCategory c : NeedCategory.values()) {
            Double v = econ.lastFulfilment.get(c);
            f.append(c.displayName()).append(' ').append(v == null ? "—" : Math.round(v * 100) + "%").append("  ");
        }
        lines.add(f.toString().trim());
        lines.add("生活水平 " + pct(econ.solBps) + "  就业 " + econ.employed + "  昨日收入 " + econ.lastIncome);
        send(source, lines);
        return 1;
    }

    private static int jobs(CommandSourceStack source) {
        Company company = ownCompany(source);
        if (company == null) return 0;
        List<String> lines = new ArrayList<>();
        lines.add("====== " + company.getName() + " 雇工 ======");
        lines.add("日工资 " + MacroLaborService.wageOf(company.getCompanyId()) + "（保留工资 "
                + MacroLaborService.reservationWage() + "）" + (MacroLaborService.active() ? "" : "  §e雇工未启用，设施按满员生产"));
        for (CompanyFacilityRecord f : CompanyFacilityManager.forCompany(company.getCompanyId())) {
            var src = SettlementManager.get(MacroEconomyManager.laborSource(f.facilityId()));
            lines.add(String.format(Locale.ROOT, "设施 %s  等级 %d  在岗 %d/%d  产量 %s  劳动力来源 %s  状态 %s",
                    f.facilityId().toString().substring(0, 8), f.productionLevel(),
                    MacroEconomyManager.employment(f.facilityId()), MacroLaborService.jobs(f),
                    pct(MacroLaborService.staffingBps(f)), src == null ? "无（附近没有聚落）" : src.displayName(), f.status()));
        }
        send(source, lines);
        return 1;
    }

    private static int news(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只有玩家可以领报纸。"));
            return 0;
        }
        if (!NewsService.enabled()) {
            source.sendFailure(Component.literal("《金融周报》未启用（配置 macroEconomy.newsEnabled）。"));
            return 0;
        }
        int n = NewsService.claim(player);
        if (n > 0) {
            source.sendSuccess(() -> Component.literal("领到 " + n + " 份报纸，已放进背包。"), false);
            return 1;
        }
        List<NewsService.Issue> archive = NewsService.archive();
        String next = "下一期周报约在第 " + (NewsService.lastWeeklyDay() == Long.MIN_VALUE ? "?"
                : String.valueOf(NewsService.lastWeeklyDay() + finance.config.FinanceConfig.macroNewsIntervalDays())) + " 天出版。";
        source.sendSuccess(() -> Component.literal((archive.isEmpty() ? "还没有出过报纸。" : "最近的报纸你都领过了。") + next), false);
        return 1;
    }

    private static int radar(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只有玩家可以做商情调查。"));
            return 0;
        }
        MacroRadar.Purchase r = MacroRadar.purchase(player.getUUID(), EconomyCycleService.currentMcDay(source.getServer()));
        if (!r.success()) {
            source.sendFailure(Component.literal(r.message()));
            return 0;
        }
        List<String> lines = new ArrayList<>();
        lines.add("====== " + r.message() + " ======");
        if (r.opportunities().isEmpty()) lines.add("调查员转了一圈，暂时没发现明显的商机。");
        for (MacroRadar.Opportunity o : r.opportunities()) lines.add("· " + o.text());
        lines.add("§7情报会过时：价格会被商队和村民拉平，两三天后最好重新调查。");
        send(source, lines);
        return 1;
    }

    // ================================================================ 操作

    private static int wage(CommandSourceStack source, long wage) {
        Company company = ownCompany(source);
        if (company == null) return 0;
        ServerPlayer player = source.getPlayer();
        if (!MacroLaborService.setWage(player.getUUID(), company.getCompanyId(), wage)) {
            source.sendFailure(Component.literal("设定失败：只有公司所有者可以设定工资。"));
            return 0;
        }
        String hint = wage < MacroLaborService.reservationWage() ? "（低于保留工资 " + MacroLaborService.reservationWage() + "，将招不到人）" : "";
        source.sendSuccess(() -> Component.literal(company.getName() + " 日工资设为 " + wage + hint), false);
        return 1;
    }

    // ================================================================ 央行

    private static int centralBank(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        lines.add("====== 中央银行 ======");
        lines.addAll(centralBankLines());
        for (finance.centralbank.CentralBankService.Decision d : finance.centralbank.CentralBankService.decisions()) {
            lines.add(String.format(Locale.ROOT, "第 %d 天：利率 %.2f%%→%.2f%%  准备金率 %.1f%%→%.1f%%  通胀 %.1f%%",
                    d.day(), d.rateBefore() / 100.0, d.rateAfter() / 100.0, d.reserveBefore() / 100.0,
                    d.reserveAfter() / 100.0, d.inflationBps() / 100.0));
        }
        send(source, lines);
        return 1;
    }

    /** 央行概况几行：利率与物价目标、准备金率、议息前瞻、公开市场操作和商品平准。 */
    static List<String> centralBankLines() {
        var cb = MacroSnapshotBuilder.centralBankRow();
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "央行基准利率 %.2f%%  通胀 %.1f%%（目标 %.1f%%）  活期准备金率 %.1f%%",
                cb.rateBps() / 100.0, cb.inflationBps() / 100.0, cb.targetBps() / 100.0, cb.reserveBps() / 100.0));
        if (cb.nextMeetingDay() >= 0) lines.add("下次议息第 " + cb.nextMeetingDay() + " 天，倾向" + cb.guidance());
        lines.add("公开市场操作：银行持有央行票据 " + cb.absorbed() + "，回购投放 " + cb.injected());
        lines.add(cb.stabilization().isEmpty() ? "商品平准：昨日无操作" : "商品平准：" + String.join("、", cb.stabilization()));
        return lines;
    }

    // ================================================================ 工具

    private static Company ownCompany(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只有玩家可以使用。"));
            return null;
        }
        Company company = CompanyManager.getCompanyByOwner(player.getUUID());
        if (company == null) source.sendFailure(Component.literal("你还没有公司。"));
        return company;
    }

    private static SettlementRecord findSettlement(String name) {
        SettlementRecord found = null;
        for (SettlementRecord s : SettlementManager.settlements().values()) {
            if (s.displayName().equals(name)) {
                if (found != null) return null;
                found = s;
            }
        }
        return found;
    }

    static String pct(int bps) {
        return String.format(Locale.ROOT, "%.1f%%", bps / 100.0);
    }

    private static void send(CommandSourceStack source, List<String> lines) {
        for (String line : lines) source.sendSuccess(() -> Component.literal(line), false);
    }
}
