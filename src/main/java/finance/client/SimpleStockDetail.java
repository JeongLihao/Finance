package finance.client;

import static finance.client.SimplePages.*;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.gui.StockRows;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleBetService;
import finance.simple.SimpleCenterService;
import finance.util.MoneyFormat;
import java.util.List;

/** 简单模式“投资”页的股票详情：价格图、买卖、自动卖出、押注和“股价怎么来的”。从 {@link SimpleInvestPage} 拆出。 */
final class SimpleStockDetail {

    private SimpleStockDetail() {}

    /** 股票押注的说明：投入多少相当于多少股、涨跌 10% 赚亏多少、跌（涨）到哪会被强制结束。 */
    static String stockBetText(View v, long price, long stake) {
        int m = v.status() == null || v.status().margin().liquidationPct() <= 0
                ? 130
                : v.status().margin().liquidationPct();
        SimpleBetService.Preview up = SimpleBetService.stockPreview(stake, price, true, m);
        SimpleBetService.Preview down = SimpleBetService.stockPreview(stake, price, false, m);
        if (up.quantity() <= 0) return "押注至少要投入 " + MoneyFormat.pounds((price + 1) / 2) + "。";
        return "投入 " + MoneyFormat.pounds(stake) + " ≈ 押 " + up.quantity() + " 股的涨跌（一半是借的）：涨 10% 看涨赚 "
                + MoneyFormat.pounds(up.swing10()) + "，看跌亏同样多。看涨跌超 " + up.stopPct() + "%、看跌涨超 "
                + down.stopPct() + "% 会被强制结束，押金自动退回。";
    }

    static void stockDetail(UiBuilder ui, View v, State s, Actions a, StockRows.StockRow row, int y) {
        String symbol = row.symbol();
        long have = holding(v, symbol);
        long price = Math.max(1, row.lastPrice());
        int bw = ui.button(LEFT + 2, y - 1, "< 返回", () -> s.selectedStock = null, null);
        String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
        int hx = ui.textRight(RIGHT - 2, y + 2, 140, "我持有 " + have + " 股", TEXT);
        ui.text(
                LEFT + bw + 10,
                y + 2,
                hx - LEFT - bw - 18,
                row.name() + "  " + MoneyFormat.compact(price) + arrow
                        + String.format(java.util.Locale.ROOT, "（今天 %+.1f%%）", row.dayChange()),
                FinanceGui.changeColor(row.dayChange()));
        y += 16;
        SimpleInvestPage.priceChart(
                ui,
                s,
                y,
                42,
                row.priceHistory(),
                row.hourBars(),
                FinanceGui.changeColor(row.dayChange()),
                () -> a.simple(SimpleActionPacket.Action.PRICE_ALERT, "s:" + symbol, 0, 0));
        y += 46;
        ui.text(
                LEFT + 2,
                y,
                WIDTH - 4,
                "每股分红约 " + MoneyFormat.pounds(row.expectedDividendPerShare()) + "（上次 "
                        + MoneyFormat.pounds(row.lastDividendPerShare()) + "），每周发一次",
                DIM);
        y += 13;

        // 买卖
        boolean locked = SimpleInvestPage.stocksLocked(v);
        String lockReason = row.mmHalted()
                ? SimpleInvestPage.HALTED
                : locked ? "钱包满 " + MoneyFormat.pounds(SimpleInvestPage.unlock(v)) + " 后解锁" : null;
        // 按钮上写明大约花多少钱；按住 Shift 股数 ×10
        int small = 10 * mult(s), large = 100 * mult(s);
        long smallCost = price * small, largeCost = price * large;
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 40, "买卖", DIM) + 8;
        y += ui.buttonFlow(
                        lx,
                        y,
                        RIGHT - lx,
                        3,
                        List.of(
                                confirm(
                                        s,
                                        a,
                                        "stockbuy:" + symbol,
                                        "买" + small + "股≈" + MoneyFormat.compact(smallCost),
                                        () -> a.stock(true, symbol, small),
                                        lockReason != null ? lockReason : v.balance() < smallCost ? "钱不够" : null,
                                        smallCost >= BIG),
                                confirm(
                                        s,
                                        a,
                                        "stockbuy100:" + symbol,
                                        "买" + large + "股≈" + MoneyFormat.compact(largeCost),
                                        () -> a.stock(true, symbol, large),
                                        lockReason != null ? lockReason : v.balance() < largeCost ? "钱不够" : null,
                                        largeCost >= BIG),
                                confirm(
                                        s,
                                        a,
                                        "stocksell:" + symbol,
                                        "全卖",
                                        () -> a.stock(false, symbol, (int) Math.min(Integer.MAX_VALUE, have)),
                                        row.mmHalted() ? SimpleInvestPage.HALTED : have > 0 ? null : "你没有这只股票",
                                        have * price >= BIG)))
                + 4;

        // 自动卖出
        long up = price + Math.max(1, price / 10), down = Math.max(1, price - Math.max(1, price / 10));
        lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 50, "自动卖出", DIM) + 8;
        String noHold = have > 0 ? null : "先买入才能设自动卖出";
        ui.buttonFlow(
                lx,
                y,
                RIGHT - lx,
                3,
                List.of(
                        new Btn(
                                "涨到 " + MoneyFormat.compact(up) + " 全卖",
                                () -> a.simple(SimpleActionPacket.Action.AUTO_SELL_UP, symbol, have, up),
                                noHold),
                        new Btn(
                                "跌到 " + MoneyFormat.compact(down) + " 全卖",
                                () -> a.simple(SimpleActionPacket.Action.AUTO_SELL_DOWN, symbol, have, down),
                                noHold)));
        y += 16;
        List<StockRows.ConditionalStockOrderRow> autos =
                v.autoOrders().stream().filter(o -> o.symbol().equals(symbol)).toList();
        if (!autos.isEmpty()) {
            StockRows.ConditionalStockOrderRow o = autos.get(0);
            int cx = ui.buttonRight(
                    RIGHT - 2,
                    y - 2,
                    "取消",
                    () -> a.simple(
                            SimpleActionPacket.Action.CANCEL_AUTO, o.orderId().toString(), 0, 0),
                    null);
            ui.text(
                    LEFT + 12,
                    y,
                    cx - LEFT - 18,
                    ("TAKE_PROFIT".equals(o.type()) ? "涨到 " : "跌到 ") + MoneyFormat.pounds(o.triggerPrice()) + " 时卖出 "
                            + o.quantity() + " 股" + (autos.size() > 1 ? "（还有 " + (autos.size() - 1) + " 条）" : ""),
                    TEXT);
            y += 13;
        }
        y += 3;

        // 押注：解锁了就给看涨 / 看跌，还没解锁就说怎么解锁，整个关掉就讲股价怎么来的
        if (extra(v, "margin")) {
            y += SimpleInvestPage.betRow(
                    ui, v, s, a, y, "s:" + symbol, row.mmHalted() ? SimpleInvestPage.HALTED : null);
            ui.wrapped(
                    LEFT + 2,
                    y,
                    WIDTH - 4,
                    stockBetText(v, price, s.betStake),
                    DIM,
                    Math.max(1, Math.min(2, (BOTTOM - y) / UiBuilder.LINE)));
            return;
        }
        String text = locked(v, "bet")
                ? "押注（" + unlockHint("bet") + "）：押股价涨还是跌，押对了赚翻倍。" + valuationText(v, row)
                : valuationText(v, row);
        ui.wrapped(LEFT + 2, y, WIDTH - 4, text, DIM, Math.max(1, (BOTTOM - y) / UiBuilder.LINE));
    }

    /** “股价怎么来的”：家底 + 日利润 × 倍数，÷ 股数；现价比每股价值贵还是便宜。 */
    static String valuationText(View v, StockRows.StockRow row) {
        SimpleCenterService.Valuation val = v.status() == null
                ? null
                : v.status().valuations().stream()
                        .filter(x -> x.symbol().equals(row.symbol()))
                        .findFirst()
                        .orElse(null);
        if (val == null || val.shares() <= 0) {
            return "股价怎么来的：公司的家底加上最近赚的钱，再除以股数。央行加息时股价通常会跌。";
        }
        long perShare = Math.max(1, row.fairValue());
        long price = Math.max(1, row.lastPrice());
        String verdict = price > perShare * 11 / 10 ? "现价偏贵" : price < perShare * 9 / 10 ? "现价偏便宜" : "现价合理";
        return "股价怎么来的：家底 " + MoneyFormat.compact(val.netAssets()) + " + 日利润 " + MoneyFormat.signed(val.dailyProfit())
                + " × " + (val.multipleX10() / 10) + " 天，÷ " + val.shares()
                + " 股 ≈ 每股 " + MoneyFormat.compact(perShare) + "，" + verdict + "。倍数看央行利率，加息时变小，股价跟着跌。";
    }
}
