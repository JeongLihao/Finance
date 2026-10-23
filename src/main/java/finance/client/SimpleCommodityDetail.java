package finance.client;

import static finance.client.SimplePages.*;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.gui.MarketRows;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleBetService;
import finance.util.MoneyFormat;
import java.util.List;

/** 简单模式的商品详情（“买卖”页点进某种商品）：价格图、买卖、商品押注和来源说明。从 {@link SimpleInvestPage} 拆出。 */
final class SimpleCommodityDetail {

    private SimpleCommodityDetail() {}

    /** 商品押注要用的合约：这种商品最近到期、正在交易、有报价的那份；没有返回 null。 */
    static finance.network.FuturesResponsePacket.ContractRow betContract(View v, String commodity) {
        finance.network.FuturesResponsePacket data = v.futures();
        if (data == null) return null;
        finance.network.FuturesResponsePacket.ContractRow best = null;
        for (var c : data.contracts()) {
            if (!c.commodity().equals(commodity) || quote(v, c) == null) continue;
            if (c.status() != finance.futures.FuturesContractStatus.TRADING
                    && c.status() != finance.futures.FuturesContractStatus.LAST_TRADING_DAY) continue;
            if (best == null || c.maturityDay() < best.maturityDay()) best = c;
        }
        return best;
    }

    static finance.network.FuturesResponsePacket.MmQuoteRow quote(
            View v, finance.network.FuturesResponsePacket.ContractRow c) {
        return v.futures().mmQuotes().stream()
                .filter(q -> q.contractId().equals(c.id()))
                .findFirst()
                .orElse(null);
    }

    static void commodityDetail(UiBuilder ui, View v, State s, Actions a, MarketRows.MarketRow row, int y) {
        String id = row.commodityId();
        String name = FinanceFormat.commodityDisplayName(id);
        int have = v.backpack().getOrDefault(id, 0);
        int bw = ui.button(LEFT + 2, y - 1, "< 返回", () -> s.selectedCommodity = null, null);
        int sx = ui.textRight(
                RIGHT - 2,
                y + 2,
                150,
                "市场还有 " + row.marketStock() + " 个",
                row.marketStock() > 0 ? TEXT : FinanceGui.COL_BAD);
        ui.icon(LEFT + bw + 10, y + 1, id);
        ui.text(
                LEFT + bw + 24,
                y + 2,
                sx - LEFT - bw - 30,
                name + String.format(java.util.Locale.ROOT, "（今天 %+.1f%%）", row.dayChange()),
                FinanceGui.changeColor(row.dayChange()));
        y += 16;
        boolean bet = extra(v, "futures"), betLocked = !bet && locked(v, "bet");
        int chartH = bet ? 40 : betLocked ? 58 : 70;
        SimpleInvestPage.priceChart(
                ui,
                s,
                y,
                chartH,
                row.priceHistory(),
                row.hourBars(),
                FinanceGui.changeColor(row.dayChange()),
                () -> a.simple(SimpleActionPacket.Action.PRICE_ALERT, "c:" + id, 0, 0));
        y += chartH + 4;
        ui.text(
                LEFT + 2,
                y,
                WIDTH - 4,
                "今天最高 " + MoneyFormat.compact(row.dayHigh()) + "  最低 " + MoneyFormat.compact(row.dayLow()) + "  成交 "
                        + row.dayVolume() + " 个",
                DIM);
        y += 14;

        // 按住 Shift 数量 ×10；左边的价格后面写一组多少钱
        int m = mult(s);
        String noItem = have > 0 ? null : "背包里没有" + name;
        int sellQty = Math.min(64 * m, have);
        int lx = LEFT
                + 2
                + ui.text(
                        LEFT + 2,
                        y + 3,
                        160,
                        "你卖得 " + MoneyFormat.compact(row.bidPrice()) + "/个，一组 "
                                + MoneyFormat.compact(row.bidPrice() * 64),
                        TEXT)
                + 8;
        ui.buttonFlow(
                lx,
                y,
                RIGHT - lx,
                3,
                List.of(
                        new Btn(
                                m == 1 ? "卖1组" : "卖10组",
                                () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, sellQty),
                                noItem),
                        confirm(
                                s,
                                a,
                                "sellall:" + id,
                                "全卖（" + have + "）",
                                () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, have),
                                noItem,
                                (long) have * row.bidPrice() >= BIG)));
        y += 17;
        String poor = v.balance() < row.askPrice() ? "钱不够，一个要 " + MoneyFormat.pounds(row.askPrice()) : null;
        String empty = row.marketStock() <= 0 ? "市场上没货了，等别人卖或央行补货" : null;
        String block = empty != null ? empty : poor;
        lx = LEFT
                + 2
                + ui.text(
                        LEFT + 2,
                        y + 3,
                        160,
                        "你买付 " + MoneyFormat.compact(row.askPrice()) + "/个，一组 "
                                + MoneyFormat.compact(row.askPrice() * 64),
                        TEXT)
                + 8;
        ui.buttonFlow(
                lx,
                y,
                RIGHT - lx,
                3,
                List.of(
                        new Btn("买" + m, () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, m), block),
                        new Btn("买" + 16 * m, () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 16 * m), block),
                        confirm(
                                s,
                                a,
                                "buystack:" + id,
                                m == 1 ? "买1组" : "买10组",
                                () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 64 * m),
                                block,
                                64L * m * row.askPrice() >= BIG)));
        y += 19;
        if (bet) {
            var contract = betContract(v, id);
            String betBlock = v.futures() == null ? "正在读取合约……" : contract == null ? "这种东西现在没有可押的合约，明天再来" : null;
            String text;
            if (contract == null) {
                text = "商品押注：押价格涨还是跌，到期按价格结算。" + (betBlock == null ? "" : betBlock + "。");
            } else {
                var q = quote(v, contract);
                long price = Math.max(1, (q.bid() + q.ask()) / 2);
                int bps = v.status() == null ? 1_000 : v.status().futuresMarginBps();
                SimpleBetService.Preview pv = SimpleBetService.futuresPreview(s.betStake, price, contract.size(), bps);
                if (pv.quantity() <= 0) {
                    betBlock =
                            "至少投入 " + MoneyFormat.pounds(SimpleBetService.futuresMinStake(price, contract.size(), bps));
                    text = "商品押注一份 = " + contract.size() + " 个，" + betBlock + "。";
                } else {
                    text = "投入 " + MoneyFormat.pounds(s.betStake) + " 签 " + pv.quantity() + " 份（共 "
                            + pv.quantity() * contract.size()
                            + " 个）：价格涨 10% 看涨赚 " + MoneyFormat.pounds(pv.swing10()) + "，看跌亏同样多。第 "
                            + contract.maturityDay() + " 天自动结算，押金退回钱包。";
                }
            }
            y += SimpleInvestPage.betRow(ui, v, s, a, y, "f:" + id, betBlock);
            ui.wrapped(LEFT + 2, y, WIDTH - 4, text, DIM, Math.max(1, Math.min(2, (BOTTOM - y) / UiBuilder.LINE)));
            return;
        }
        if (betLocked) {
            ui.wrapped(
                    LEFT + 2,
                    y,
                    WIDTH - 4,
                    "押注（" + unlockHint("bet") + "）：押价格涨还是跌，押对了赚翻倍。",
                    DIM,
                    Math.max(1, Math.min(2, (BOTTOM - y) / UiBuilder.LINE)));
            return;
        }
        Commodity c = CommodityRegistry.getCommodity(id);
        int tier = c == null ? -1 : c.getReserveTier();
        String source = tier == 0 ? "来源：玩家卖出。央行不供应这种东西，卖完就没了。" : tier > 0 ? "来源：央行储备第 " + tier + " 档，每天只补少量货。" : "";
        ui.wrapped(
                LEFT + 2,
                y,
                WIDTH - 4,
                source + "价格跟着市场库存走：大家抢着买就涨，卖的人多就跌。",
                DIM,
                Math.max(1, (BOTTOM - y) / UiBuilder.LINE));
    }
}
