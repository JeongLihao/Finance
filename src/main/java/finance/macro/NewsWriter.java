package finance.macro;

import finance.caravan.CaravanManager;
import finance.caravan.CaravanRoute;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 报纸排版：把新闻池和经济状态写成报纸的若干原文页（按“【版名】”分版）。只读经济状态。
 *
 * <p>原文页沿用旧成书的尺寸（约 12 行），{@link #paginate} 按行数和字数分页；报纸界面会按版面重新拼接排版。</p>
 */
public final class NewsWriter {
    static final int PAGE_LINES = 12;
    static final int PAGE_CHARS = 200;

    private NewsWriter() {}

    // ================================================================ 周刊

    static NewsService.Issue weekly(
            int number, long day, List<NewsService.Item> pool, Map<UUID, List<String>> personal) {
        int interval = Math.max(1, FinanceConfig.macroNewsIntervalDays());
        List<Mover> movers = movers(interval);
        NewsService.Item lead = pool.isEmpty() ? null : pool.get(0);
        String headline;
        String leadBody;
        if (lead != null) {
            headline = lead.headline();
            leadBody = lead.body();
        } else if (!movers.isEmpty()) {
            Mover m = movers.get(0);
            headline = m.settlement + m.commodity + (m.deltaBps > 0 ? "价格飙升" : "价格跳水");
            leadBody = m.describe();
        } else {
            headline = "本周风平浪静";
            leadBody = "各地市场平稳，没有大事发生。";
        }
        List<String> pages = new ArrayList<>();
        pages.addAll(paginate("《金融周报》第 " + number + " 期\n第 " + day + " 天\n\n【头版】" + headline + "\n\n" + leadBody));
        pages.addAll(paginate(marketPage(movers, interval)));
        pages.addAll(paginate(societyPage(interval)));
        pages.addAll(paginate(centralBankPage(interval)));
        if (pool.size() > 1) {
            StringBuilder more = new StringBuilder("【本周其他新闻】\n");
            for (int i = 1; i < Math.min(pool.size(), 6); i++)
                more.append("· ").append(pool.get(i).headline()).append('\n');
            pages.addAll(paginate(more.toString()));
        }
        pages.addAll(paginate(opportunityPage(day)));
        return new NewsService.Issue(number, day, false, headline, List.copyOf(pages), personal);
    }

    // ================================================================ 号外

    static NewsService.Issue extra(int number, long day, NewsService.Item item) {
        List<String> pages = new ArrayList<>(
                paginate("【号外】第 " + number + " 期\n第 " + day + " 天\n\n" + item.headline() + "\n\n" + item.body()));
        pages.addAll(paginate(societyPage(Math.max(1, FinanceConfig.macroNewsIntervalDays()))));
        pages.addAll(paginate(centralBankPage(Math.max(1, FinanceConfig.macroNewsIntervalDays()))));
        return new NewsService.Issue(number, day, true, item.headline(), List.copyOf(pages), Map.of());
    }

    // ================================================================ 版面

    record Mover(String settlement, String commodity, int deltaBps, boolean shortage, long localPrice) {
        String describe() {
            return String.format(
                    "%s的%s本地价 %+d%%，现价 %d（%s）。",
                    settlement, commodity, deltaBps / 100, localPrice, shortage ? "供不应求" : "供过于求");
        }
    }

    /** 一段时间里本地价格系数变化最大的三种商品。 */
    static List<Mover> movers(int days) {
        List<Mover> out = new ArrayList<>();
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            SettlementRecord s = SettlementManager.get(econ.settlementId);
            if (s == null) continue;
            for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
                List<GoodBook.Point> h = e.getValue().history();
                if (h.size() < 2) continue;
                GoodBook.Point now = h.get(h.size() - 1);
                GoodBook.Point then = h.get(Math.max(0, h.size() - 1 - days));
                int delta = now.factorBps() - then.factorBps();
                if (Math.abs(delta) < 500) continue;
                out.add(new Mover(
                        s.displayName(),
                        MacroRadar.name(e.getKey()),
                        delta,
                        now.supply() < now.demand(),
                        MacroEconomyService.localPrice(econ, e.getKey())));
            }
        }
        out.sort(Comparator.comparingInt((Mover m) -> -Math.abs(m.deltaBps())).thenComparing(Mover::settlement));
        return out.subList(0, Math.min(3, out.size()));
    }

    static String marketPage(List<Mover> movers, int days) {
        StringBuilder b = new StringBuilder("【市场】\n");
        List<MacroEconomyManager.Snapshot> snaps = MacroEconomyManager.snapshots();
        if (snaps.size() >= 2) {
            MacroEconomyManager.Snapshot now = snaps.get(snaps.size() - 1);
            MacroEconomyManager.Snapshot then = snaps.get(Math.max(0, snaps.size() - 1 - days));
            if (now.cpiX100() > 0 && then.cpiX100() > 0) {
                double change = (now.cpiX100() - then.cpiX100()) * 100.0 / then.cpiX100();
                b.append(String.format("物价指数 %.1f，本周 %+.1f%%\n\n", now.cpiX100() / 100.0, change));
            }
        }
        if (movers.isEmpty()) b.append("各地价格平稳。\n");
        for (Mover m : movers) b.append("· ").append(m.describe()).append('\n');
        return b.toString();
    }

    static String societyPage(int days) {
        StringBuilder b = new StringBuilder("【民生】\n");
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            SettlementRecord s = SettlementManager.get(econ.settlementId);
            VillageRecord v = VillageManager.get(econ.settlementId);
            if (s == null || v == null) continue;
            List<SettlementEconomy.Point> h = econ.history();
            int before = h.isEmpty()
                    ? v.population()
                    : h.get(Math.max(0, h.size() - 1 - days)).population();
            int delta = v.population() - before;
            b.append(String.format(
                    "· %s %d 人（%+d），生活 %d%%\n", s.displayName(), v.population(), delta, econ.solBps / 100));
        }
        return b.toString();
    }

    /** 【央行动态】：利率与物价目标、准备金率、最近的议息决议、商品平准、下次议息前瞻。 */
    static String centralBankPage(int days) {
        StringBuilder b = new StringBuilder("【央行动态】\n");
        int rate = finance.policy.MonetaryPolicyService.benchmarkRateBasisPoints();
        int inflation = finance.centralbank.CentralBankService.inflationBps();
        int target = finance.centralbank.CentralBankService.inflationTargetBps();
        b.append(String.format("基准利率 %.2f%%，通胀 %.1f%%（目标 %.1f%%）\n", rate / 100.0, inflation / 100.0, target / 100.0));
        b.append(String.format("活期准备金率 %.1f%%\n", finance.centralbank.CentralBankService.demandReserveBps() / 100.0));
        int gap = RateTransmission.realRateGapBps();
        if (gap > 100) b.append("扣掉通胀后利率偏紧：村民少买、商人少囤货，物价承压\n");
        else if (gap < -100) b.append("扣掉通胀后利率偏松：村民多买、商人愿意囤货，物价有上行压力\n");
        List<finance.centralbank.CentralBankService.Decision> decisions =
                finance.centralbank.CentralBankService.decisions();
        if (!decisions.isEmpty()) {
            var d = decisions.get(decisions.size() - 1);
            long today = MacroEconomyManager.lastProcessedDay();
            if (today == Long.MIN_VALUE || today - d.day() <= days) {
                b.append(String.format(
                        "第 %d 天议息：利率 %.2f%%→%.2f%%", d.day(), d.rateBefore() / 100.0, d.rateAfter() / 100.0));
                if (d.reserveAfter() != d.reserveBefore()) {
                    b.append(String.format(
                            "，准备金率%s到 %.1f%%",
                            d.reserveAfter() > d.reserveBefore() ? "上调" : "下调", d.reserveAfter() / 100.0));
                }
                b.append('\n');
            }
        }
        var actions = finance.centralbank.PriceStabilizationService.lastActions();
        for (var a : actions.subList(0, Math.min(3, actions.size()))) {
            b.append("· 平准")
                    .append(a.sold() ? "抛售" : "收储")
                    .append(MacroRadar.name(a.commodityId()))
                    .append(' ')
                    .append(a.quantity())
                    .append('\n');
        }
        var outlook = finance.policy.PolicyMeetingService.outlook();
        if (outlook.nextMeetingDay() >= 0) {
            b.append("下次议息：第 ")
                    .append(outlook.nextMeetingDay())
                    .append(" 天，倾向")
                    .append(outlook.guidanceDirection())
                    .append('\n');
        }
        return b.toString();
    }

    static String opportunityPage(long day) {
        List<MacroRadar.Opportunity> top = MacroRadar.scan(1);
        StringBuilder b = new StringBuilder("【商机】\n");
        if (top.isEmpty()) b.append("暂时没有明显的商机。\n");
        else
            b.append(top.get(0).text())
                    .append("\n\n（调查于第 ")
                    .append(day)
                    .append(" 天，消息可能已经过时。想要最新情报：金融中心“更多操作 → 财报商情”）");
        return b.toString();
    }

    /** “你的生意”版：领报时按当时的数据生成，附上出版时记下的个人新闻。 */
    static String personalPage(UUID player, NewsService.Issue issue) {
        StringBuilder b = new StringBuilder("【你的生意】\n");
        Company company = CompanyManager.getCompanyByOwner(player);
        if (company != null) {
            List<Long> profits = company.getRecentProfits();
            int days = Math.max(1, FinanceConfig.macroNewsIntervalDays());
            long sum = 0;
            for (int i = Math.max(0, profits.size() - days); i < profits.size(); i++) sum += profits.get(i);
            long workers = 0;
            for (CompanyFacilityRecord f : CompanyFacilityManager.forCompany(company.getCompanyId())) {
                workers += MacroEconomyManager.employment(f.facilityId());
            }
            b.append(String.format(
                    "%s：近 %d 天利润 %+d\n雇工 %d 人，日工资 %d\n",
                    company.getName(), days, sum, workers, MacroLaborService.wageOf(company.getCompanyId())));
        } else {
            b.append("你还没有公司。\n");
        }
        long caravanProfit = 0;
        int routes = 0;
        for (CaravanRoute r : CaravanManager.routesForOwner(player)) {
            caravanProfit += r.totalProfit();
            routes++;
        }
        if (routes > 0) b.append(String.format("商队路线 %d 条，累计盈亏 %+d\n", routes, caravanProfit));
        List<String> lines = issue.personal().get(player);
        if (lines != null && !lines.isEmpty()) {
            b.append('\n');
            for (String l : lines) b.append("· ").append(l).append('\n');
        }
        return b.toString();
    }

    /** 按行数和字数把一段文字切成若干原文页。 */
    static List<String> paginate(String text) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.isBlank()) return pages;
        StringBuilder page = new StringBuilder();
        int lines = 0;
        for (String raw : text.split("\n", -1)) {
            // 长行按约 14 个字折成多行计数
            int wrapped = Math.max(1, (raw.length() + 13) / 14);
            if (page.length() > 0 && (lines + wrapped > PAGE_LINES || page.length() + raw.length() > PAGE_CHARS)) {
                pages.add(page.toString().stripTrailing());
                page.setLength(0);
                lines = 0;
            }
            page.append(raw).append('\n');
            lines += wrapped;
        }
        if (!page.toString().isBlank()) pages.add(page.toString().stripTrailing());
        return pages;
    }
}
