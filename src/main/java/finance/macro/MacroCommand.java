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
                .then(Commands.literal("laws").executes(ctx -> laws(ctx.getSource())))
                .then(Commands.literal("law").then(Commands.literal("propose")
                        .then(Commands.argument("group", StringArgumentType.word())
                                .then(Commands.argument("level", StringArgumentType.word())
                                        .executes(ctx -> propose(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "group"),
                                                StringArgumentType.getString(ctx, "level")))))))
                .then(Commands.literal("ig").executes(ctx -> interestGroups(ctx.getSource())))
                .then(Commands.literal("budget").executes(ctx -> budget(ctx.getSource())))
                .then(Commands.literal("news").executes(ctx -> news(ctx.getSource())))
                .then(Commands.literal("radar").executes(ctx -> radar(ctx.getSource())))
                .then(Commands.literal("lobby")
                        .then(Commands.argument("group", StringArgumentType.word())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> lobby(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "group"),
                                                LongArgumentType.getLong(ctx, "amount")))))));
    }

    // ================================================================ 查看

    private static int overview(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        if (!MacroEconomyService.active()) {
            lines.add("宏观经济未启用（配置 macroEconomy.enabled 或模块熔断）。");
            send(source, lines);
            return 0;
        }
        PoliticsService.recompute();
        MacroEconomyManager.Snapshot s = MacroEconomyManager.latestSnapshot();
        double cpi = MacroIndicators.cpiOrNegative();
        lines.add("====== 宏观经济 ======");
        lines.add(String.format(Locale.ROOT, "CPI %s  年化通胀 %.1f%%", cpi < 0 ? "—" : String.format(Locale.ROOT, "%.1f", cpi),
                MacroIndicators.annualizedInflationPct()));
        if (s != null) {
            lines.add(String.format(Locale.ROOT, "人口 %,d  平均生活水平 %s  就业 %,d  工资 %,d/天  消费 %,d/天",
                    s.population(), pct(s.avgSol()), s.employed(), s.wages(), s.consumptionValue()));
        }
        lines.add("法律：" + lawLine());
        if (MacroFiscalService.active()) {
            long[] t = MacroFiscalService.lastDayTotals();
            lines.add(String.format(Locale.ROOT, "国库 %,d  债务 %,d  昨日收入 %,d  支出 %,d%s", MacroFiscalService.balance(),
                    Treasury.debt(), t[0], t[1], Treasury.austerity() ? "  §c紧缩中" : ""));
        }
        if (MacroEconomyManager.strike()) lines.add("§c工会罢工中：雇工产量减半");
        if (MacroEconomyManager.unrest()) lines.add("§c民众骚乱：人口增长减半，迁入意愿下降");
        MacroEconomyManager.Proposal p = MacroEconomyManager.proposal();
        if (p != null) lines.add(proposalLine(p));
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
            lines.add(String.format(Locale.ROOT, "%s：人口 %d  生活水平 %s  就业 %d  人均收入 %.1f%s",
                    settlement.displayName(), pop, econ == null ? "—" : pct(econ.solBps),
                    econ == null ? 0 : econ.employed, pop <= 0 ? 0.0 : (double) income / pop,
                    econ != null && econ.unrest ? "  §c骚乱" : ""));
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

    private static int laws(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        lines.add("====== 法律 ======");
        for (LawGroup g : LawGroup.values()) {
            StringBuilder b = new StringBuilder(g.displayName() + "（" + g.name().toLowerCase(Locale.ROOT) + "）：");
            for (LawLevel l : LawLevel.levelsOf(g)) {
                boolean on = MacroEconomyManager.law(g) == l;
                b.append(on ? "§a[" : "").append(l.displayName()).append('(').append(l.key()).append(')').append(on ? "]§r" : "").append(' ');
            }
            lines.add(b.toString().trim());
        }
        MacroEconomyManager.Proposal p = MacroEconomyManager.proposal();
        if (p != null) lines.add(proposalLine(p));
        for (MacroEconomyManager.LawResult r : MacroEconomyManager.lawHistory()) {
            lines.add(String.format(Locale.ROOT, "第 %d 天 %s → %s：%s（支持 %d / 反对 %d）", r.day(),
                    r.level().group().displayName(), r.level().displayName(), r.passed() ? "通过" : "未通过", r.support(), r.opposition()));
        }
        lines.add("发起提案：/macro law propose <组> <级别>");
        send(source, lines);
        return 1;
    }

    private static int interestGroups(CommandSourceStack source) {
        PoliticsService.recompute();
        List<String> lines = new ArrayList<>();
        lines.add("====== 利益集团 ======");
        for (InterestGroup ig : InterestGroup.values()) {
            lines.add(String.format(Locale.ROOT, "%s（%s）  影响力 %s  满意度 %+d  游说 %+d", ig.displayName(),
                    ig.name().toLowerCase(Locale.ROOT), pct(MacroEconomyManager.cloutBps(ig)),
                    MacroEconomyManager.approval(ig), MacroEconomyManager.lobby(ig)));
        }
        lines.add("游说：/macro lobby <集团> <金额>");
        send(source, lines);
        return 1;
    }

    private static int budget(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        lines.add("====== 国家财政 ======");
        if (!MacroFiscalService.active()) {
            lines.add("国家财政未启用（配置 macroEconomy.fiscalEnabled）。");
            send(source, lines);
            return 0;
        }
        lines.add(String.format(Locale.ROOT, "国库 %,d  债务 %,d / 上限 %,d（%s）  税制 %s", MacroFiscalService.balance(),
                Treasury.debt(), finance.config.FinanceConfig.macroDebtCeiling(), pct(MacroFiscalService.debtRatioBps()),
                MacroEconomyManager.law(LawGroup.TAXATION).displayName()));
        lines.add("明日利息 " + MacroFiscalService.dailyInterest(Treasury.debt()) + "  累计央行增发 " + Treasury.cumulativeIssuance()
                + (Treasury.austerity() ? "  §c紧缩中：福利停发" : ""));
        Treasury.Day d = Treasury.lastDay();
        if (d == null) {
            lines.add("还没有完整的一天账目。");
        } else {
            StringBuilder in = new StringBuilder("第 " + d.day() + " 天收入：");
            StringBuilder out = new StringBuilder("第 " + d.day() + " 天支出：");
            for (Treasury.Item item : Treasury.Item.values()) {
                long v = d.flows().getOrDefault(item, 0L);
                if (v <= 0) continue;
                (item.inflow() ? in : out).append(item.displayName()).append(' ').append(v).append("  ");
            }
            lines.add(in.toString().trim());
            lines.add(out.toString().trim());
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

    private static int propose(CommandSourceStack source, String groupName, String levelName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只有玩家可以发起提案。"));
            return 0;
        }
        LawGroup group = parseGroup(groupName);
        LawLevel level = LawLevel.parse(group, levelName);
        if (group == null || level == null) {
            source.sendFailure(Component.literal("未知法律。用 /macro laws 查看可选的组和级别。"));
            return 0;
        }
        PoliticsService.recompute();
        long[] t = PoliticsService.tally(level);
        PoliticsService.Result r = PoliticsService.propose(player.getUUID(), level,
                EconomyCycleService.currentMcDay(source.getServer()));
        if (!r.success()) {
            source.sendFailure(Component.literal(r.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(r.message() + " 当前预估：支持 " + t[0] + " / 反对 " + t[1]), false);
        return 1;
    }

    private static int lobby(CommandSourceStack source, String groupName, long amount) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只有玩家可以游说。"));
            return 0;
        }
        InterestGroup ig = null;
        for (InterestGroup g : InterestGroup.values()) {
            if (g.name().equalsIgnoreCase(groupName) || g.displayName().equals(groupName)) ig = g;
        }
        if (ig == null) {
            source.sendFailure(Component.literal("未知利益集团。用 /macro ig 查看。"));
            return 0;
        }
        PoliticsService.recompute();
        PoliticsService.Result r = PoliticsService.lobby(player.getUUID(), ig, amount);
        if (!r.success()) {
            source.sendFailure(Component.literal(r.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(r.message()), false);
        return 1;
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

    private static LawGroup parseGroup(String name) {
        for (LawGroup g : LawGroup.values()) {
            if (g.name().equalsIgnoreCase(name) || g.displayName().equals(name)) return g;
        }
        return null;
    }

    private static String lawLine() {
        StringBuilder b = new StringBuilder();
        for (LawGroup g : LawGroup.values()) b.append(g.displayName()).append('·').append(MacroEconomyManager.law(g).displayName()).append("  ");
        return b.toString().trim();
    }

    private static String proposalLine(MacroEconomyManager.Proposal p) {
        long[] t = PoliticsService.tally(p.level());
        return "进行中提案：" + p.level().group().displayName() + " → " + p.level().displayName()
                + "，第 " + p.endDay() + " 天表决（当前支持 " + t[0] + " / 反对 " + t[1] + "）";
    }

    static String pct(int bps) {
        return String.format(Locale.ROOT, "%.1f%%", bps / 100.0);
    }

    private static void send(CommandSourceStack source, List<String> lines) {
        for (String line : lines) source.sendSuccess(() -> Component.literal(line), false);
    }
}
