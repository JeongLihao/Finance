package finance.simple;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyFinancialReport;
import finance.company.CompanyManager;
import finance.debt.BondStatus;
import finance.debt.CorporateBondManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.settlement.DemandStatus;
import finance.settlement.LocalDemand;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.util.InventoryUtil;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * 机会卡：看玩家眼下的处境，提醒“现在该做什么、做了有什么好处”，点一下就办。
 * 进阶玩法不再摆一列按钮，而是在用得上的时候出现在首页和“当老板”里。
 */
public final class SimpleOpportunityService {

    private SimpleOpportunityService() {}

    /**
     * 一张卡。action 非空时按钮直接发这个简单操作（target / amount 是参数）；
     * 否则按钮跳到 goTo 指的页面：TRADE、INVEST:小标签序号、COMPANY:小标签序号、STOCK:股票代码。
     * button 为空表示只是提示，没有按钮。boss = 这张卡也显示在“当老板”里。
     */
    public record Card(
            String text, String button, String action, String target, long amount, String goTo, boolean boss) {
        static Card act(String text, String button, String action, String target, long amount, boolean boss) {
            return new Card(text, button, action, target, amount, "", boss);
        }

        static Card go(String text, String button, String goTo) {
            return new Card(text, button, "", "", 0, goTo, false);
        }
    }

    public static final int MAX_CARDS = 8;

    /** 便宜到多少（市价 ÷ 每股价值）算“被低估”。 */
    static final double CHEAP = 0.8;
    /** 新股认购机会卡一次认多少股（钱不够就少认）。 */
    static final long IPO_CARD_SHARES = 100;

    public static List<Card> cards(
            ServerPlayer player,
            Predicate<String> allowed,
            SimpleBossService.BossInfo boss,
            SimpleInvestService.MarginInfo margin,
            boolean hasSavings,
            long balance) {
        UUID id = player.getUUID();
        Company company = CompanyManager.getCompanyByOwner(id);
        List<Card> out = new ArrayList<>();

        // 1. 押注快被强制结束
        if (margin.safetyPct() >= 0 && margin.safetyPct() < margin.liquidationPct() + 20) {
            out.add(Card.go(
                    "押注快被强制结束了：安全度 " + margin.safetyPct() + "%，低于 " + margin.liquidationPct() + "% 会被强卖。可以先结束亏得最多的那笔。",
                    "去看看",
                    "INVEST:2"));
        }
        // 2. 收购快完成
        if (allowed.test("takeover")) {
            for (SimpleBossService.Target t : boss.targets()) {
                if (t.squeezable()) {
                    out.add(Card.act(
                            "你已持有「" + t.name() + "」90% 以上的股份：买下剩下的，这家公司就归你了。",
                            "买下",
                            "SQUEEZE",
                            t.companyId(),
                            0,
                            true));
                    break;
                }
            }
        }
        // 3. 公司缺钱：上市公司进了破产风险，或者（没上市也算）昨天的日常开销没付上
        if (company != null && (company.isBankruptcyRisk() || company.getLastCostShortfall() > 0)) {
            if (company.isPublic() && allowed.test("shareholder")) {
                out.add(Card.act("公司快撑不住了：开股东大会增发 10% 新股，把钱融进来。", "提议增发", "PROPOSE", "issue", 0, true));
            } else {
                out.add(Card.act("公司快撑不住了：先向银行借 50 磅周转，30 天内分期还。", "借 50 磅", "COMPANY_LOAN", "", 5_000, true));
            }
            boolean bondOpen = CorporateBondManager.bonds().values().stream()
                    .anyMatch(
                            b -> b.companyId().equals(company.getCompanyId()) && b.status() == BondStatus.SUBSCRIPTION);
            if (!bondOpen)
                out.add(Card.act(
                        "也可以发 100 磅公司债：NPC 投资者 3 天内认购，钱到账后 90 天还本，期间按月付息。", "发债", "ISSUE_BOND", "", 10_000, true));
            if (pledgeValue(company) >= 5_000)
                out.add(Card.act("仓库里的货能抵押：押给银行先借一笔，还清后货自动解押。", "抵押借钱", "PLEDGE", "", 0, true));
        }
        // 3b. 别家公司在上市认购：认一笔（每股按估值定价，钱先进托管，没上市原路退回）
        for (finance.company.IpoSubscription ipo : finance.company.CompanyIPOService.open()) {
            if (company != null && company.getCompanyId().equals(ipo.companyId())) continue;
            Company target = CompanyManager.getCompany(ipo.companyId());
            long shares =
                    Math.min(ipo.remainingShares(), Math.min(IPO_CARD_SHARES, balance / Math.max(1, ipo.issuePrice())));
            if (target == null || shares <= 0) continue;
            out.add(Card.act(
                    "「" + target.getName() + "」正在上市认购：每股 " + MoneyFormat.pounds(ipo.issuePrice()) + "，还剩 "
                            + ipo.remainingShares() + " 股，第 " + ipo.endDay() + " 天结算。没认够就全额退款。",
                    "认 " + shares + " 股",
                    "IPO_SUBSCRIBE",
                    ipo.id().toString(),
                    shares,
                    false));
            break;
        }
        // 4. 招不到工人
        if (allowed.test("wage") && boss.wageActive() && boss.wage() < boss.reservationWage()) {
            out.add(Card.act(
                    "工人日工资低于 " + MoneyFormat.pounds(boss.reservationWage()) + " 就招不到人，工厂会停工。",
                    "提到 " + MoneyFormat.compact(boss.reservationWage()),
                    "SET_WAGE",
                    "",
                    boss.reservationWage(),
                    true));
        }
        // 5. 库存没上保险
        if (company != null && allowed.test("insurance")) {
            for (SimpleBossService.Insurance i : boss.insurance()) {
                if (!i.active() && i.premium() > 0 && i.coverage() >= 5_000) {
                    out.add(Card.act(
                            i.name() + "：最多赔 " + MoneyFormat.pounds(i.coverage()) + "，保 7 天只要 "
                                    + MoneyFormat.pounds(i.premium()) + "。",
                            "投保",
                            "INSURE",
                            i.product(),
                            0,
                            true));
                    break;
                }
            }
        }
        // 6. 别家公司被低估：大老板去收购，其他人买入等它涨回来
        Stock cheapest = null;
        for (Stock s : StockMarketManager.getListedStocks()) {
            if (company != null && s.getCompanyId().equals(company.getCompanyId())) continue;
            if (s.getFairValue() <= 0 || s.isTradingPaused()) continue;
            if (ratio(s) < CHEAP && (cheapest == null || ratio(s) < ratio(cheapest))) cheapest = s;
        }
        if (cheapest != null) {
            int tenths = (int) Math.round(ratio(cheapest) * 10);
            if (company != null && allowed.test("takeover")) {
                out.add(Card.act(
                        "「" + cheapest.getName() + "」股价只有每股价值的 " + tenths + " 成：发起收购，买到 51% 就归你管。",
                        "出价收购",
                        "TAKEOVER",
                        cheapest.getCompanyId().toString(),
                        0,
                        true));
            } else {
                out.add(Card.go(
                        "「" + cheapest.getName() + "」现价比每股价值便宜 " + (100 - tenths * 10) + "%，买入等它涨回来。",
                        "去看看",
                        "STOCK:" + cheapest.getSymbol()));
            }
        }
        // 7. 自己公司股价偏低、账上有钱：回购
        Stock own = company == null || !company.isPublic()
                ? null
                : StockMarketManager.getStockByCompanyId(company.getCompanyId());
        if (own != null
                && allowed.test("shareholder")
                && ratio(own) < 0.9
                && company.getCash() > own.getTotalShares() / 20 * Math.max(1, own.getFairValue())) {
            out.add(Card.act(
                    "你公司的股价比每股价值低 " + (100 - (int) Math.round(ratio(own) * 100)) + "%，账上又有闲钱：回购 5% 的股份能把股价托起来。",
                    "提议回购",
                    "PROPOSE",
                    "buyback",
                    0,
                    true));
        }
        // 8. 在赚钱但分红很少
        CompanyFinancialReport report = company == null ? null : company.getLatestFinancialReport();
        if (own != null && boss.dividendPct() < 20 && report != null && report.netProfit() > 0) {
            int next = Math.min(100, boss.dividendPct() + 20);
            out.add(Card.act(
                    "公司在赚钱，利润只分 " + boss.dividendPct() + "% 给股东：多分一点，大家更愿意买你的股票，股价会涨。",
                    "分 " + next + "%",
                    "SET_DIVIDEND",
                    "",
                    next,
                    true));
        }
        // 9. 钱多到可以开银行
        if (allowed.test("bank")
                && !boss.ownsBank()
                && boss.bankMinCapital() > 0
                && balance >= 2 * (boss.bankMinCapital() + boss.bankCharterFee())) {
            out.add(Card.act(
                    "钱包有 " + MoneyFormat.pounds(balance) + "：开一家自己的银行，别人存钱进来，你放贷赚利差。",
                    "开银行",
                    "CHARTER_BANK",
                    "",
                    boss.bankMinCapital(),
                    true));
        }
        // 10. 闲钱没放理财
        if (!hasSavings && balance >= 20_000) {
            out.add(Card.go("钱包里闲着 " + MoneyFormat.pounds(balance) + "：放进理财，每天都有利息。", "去理财", "INVEST:1"));
        }
        // 11. 村庄需求、背包里值钱的东西
        SettlementManager.demands().values().stream()
                .filter(d -> d.status() == DemandStatus.OPEN)
                .max(Comparator.comparingLong(LocalDemand::reward))
                .ifPresent(d -> {
                    SettlementRecord village = SettlementManager.get(d.settlementId());
                    out.add(Card.go(
                            (village == null ? "有个村庄" : village.displayName()) + "需要 " + d.quantity() + " 个"
                                    + SimpleBetService.commodityName(d.commodityId()) + "，送到村里的贸易站能拿 "
                                    + MoneyFormat.pounds(d.reward()) + "。",
                            "",
                            ""));
                });
        record Held(String name, int count, long value) {}
        List<Held> held = new ArrayList<>();
        for (Commodity c : CommodityRegistry.getAllCommodities()) {
            if (c.getItemId() == null || c.getItemId().isEmpty()) continue;
            int count = InventoryUtil.countItemInInventory(player, c.getItemId());
            MarketPrice price = NpcMarketMaker.getMarketPrice(c.getId());
            if (count <= 0 || price == null || price.getBidPrice() <= 0) continue;
            held.add(new Held(c.getDisplayName(), count, price.getBidPrice() * count));
        }
        held.sort(Comparator.comparingLong(Held::value).reversed());
        for (Held h : held.subList(0, Math.min(2, held.size()))) {
            out.add(Card.go(
                    "背包里的 " + h.count() + " 个" + h.name() + "现在能卖 " + MoneyFormat.pounds(h.value())
                            + "：到自己的仓库或市场终端旁存进去，之后人在哪里都能卖。",
                    "去看看",
                    "TRADE"));
        }
        if (out.isEmpty()) out.add(Card.go("去挖矿、种地或砍树，拿到东西后存进仓库，在“买卖”页卖掉。", "", ""));
        return out.subList(0, Math.min(MAX_CARDS, out.size()));
    }

    /** 公司仓库里没押出去的货按市价值多少。 */
    static long pledgeValue(Company company) {
        long value = 0;
        for (Commodity c : CommodityRegistry.getAllCommodities()) {
            int available =
                    finance.gameplay.company.CompanyInventoryFacade.availableInsurableInventory(company, c.getId());
            MarketPrice price = NpcMarketMaker.getMarketPrice(c.getId());
            if (available > 0 && price != null) value += price.getMidPrice() * available;
        }
        return value;
    }

    /** 市价 ÷ 每股价值。 */
    static double ratio(Stock s) {
        return s.getAnchorPrice() / (double) Math.max(1, s.getFairValue());
    }
}
