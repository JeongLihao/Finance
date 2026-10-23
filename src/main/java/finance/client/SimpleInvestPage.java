package finance.client;

import static finance.client.SimplePages.*;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.gui.FinanceMenu;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleBetService;
import finance.simple.SimpleCenterService;
import finance.simple.SimpleInvestService;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.List;

/** 简单模式“投资”页：股票、商品详情、押注、理财产品。排版规则见 {@link SimplePages}。 */
final class SimpleInvestPage {

    private SimpleInvestPage() {}

    // ---------------------------------------------------------------- 押注（股票详情、商品详情共用）

    static final long[] BET_AMOUNTS = {1_000, 5_000, 20_000};

    /** 押注一行：投入金额 + 三个金额小按钮 + 看涨 / 看跌。返回占用高度。 */
    static int betRow(UiBuilder ui, View v, State s, Actions a, int y, String target, String blocked) {
        String poor = v.balance() < s.betStake ? "钱包不到 " + MoneyFormat.pounds(s.betStake) : null;
        String block = blocked != null ? blocked : poor;
        long stake = s.betStake;
        boolean big = stake >= BIG;
        int rx = ui.buttonsRight(
                RIGHT - 2,
                y,
                2,
                List.of(
                        confirm(
                                s,
                                a,
                                "bet-up:" + target,
                                "看涨",
                                () -> a.simple(SimpleActionPacket.Action.BET_UP, target, stake, 0),
                                block,
                                big),
                        confirm(
                                s,
                                a,
                                "bet-down:" + target,
                                "看跌",
                                () -> a.simple(SimpleActionPacket.Action.BET_DOWN, target, stake, 0),
                                block,
                                big)));
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 30, "押注", DIM) + 6;
        lx += ui.text(lx, y + 3, 70, "投入 " + MoneyFormat.compact(s.betStake), TEXT) + 6;
        List<Btn> chips = new ArrayList<>();
        for (long amount : BET_AMOUNTS)
            chips.add(new Btn(MoneyFormat.compact(amount), () -> s.betStake = amount, null));
        int chipsW = -ui.buttonsRightStart(0, 2, chips.stream().map(Btn::label).toList());
        if (lx + chipsW <= rx - 6) ui.buttonsRight(lx + chipsW, y, 2, chips);
        return UiBuilder.BUTTON_H + 4;
    }

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

    static void invest(UiBuilder ui, View v, State s, Actions a) {
        if (s.investTab > 3) s.investTab = 0;
        int y = subTabs(ui, TOP, List.of("股票", "理财", "押注", "现金"), s.investTab, i -> {
            s.investTab = i;
            s.selectedStock = null;
        });
        switch (s.investTab) {
            case 1 -> products(ui, v, s, a, y);
            case 2 -> betsTab(ui, v, a, y);
            case 3 -> SimpleCompanyPage.cashTab(ui, v, a, y);
            default -> {
                FinanceMenu.StockRow selected = s.selectedStock == null
                        ? null
                        : v.stocks().stream()
                                .filter(r -> r.symbol().equals(s.selectedStock))
                                .findFirst()
                                .orElse(null);
                if (selected != null) stockDetail(ui, v, s, a, selected, y);
                else stockList(ui, v, s, a, y);
            }
        }
    }

    static long unlock(View v) {
        return v.status() == null ? 3_000 : v.status().stockUnlockBalance();
    }

    static boolean stocksLocked(View v) {
        return v.balance() < unlock(v) && v.holdings().isEmpty();
    }

    static void stockList(UiBuilder ui, View v, State s, Actions a, int y) {
        boolean locked = stocksLocked(v);
        int gap = 2;
        int lot = 10 * mult(s);
        String buyLabel = "买" + lot + "股";
        int buttonsX = ui.buttonsRightStart(RIGHT, gap, List.of(buyLabel, "全卖"));
        int holdX = buttonsX - 6 - 56;
        int divX = holdX - 6 - 60;
        int priceX = divX - 6 - 48;
        int nameX = LEFT + 4;
        ui.text(nameX, y, priceX - nameX - 4, "公司（点名字看详情）", DIM);
        ui.text(priceX, y, 48, "股价", DIM);
        ui.text(divX, y, 60, "每百股分红", DIM);
        ui.text(holdX, y, 56, "我持有", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.StockRow> stocks = v.stocks();
        s.investScroll = clamp(s.investScroll, 0, Math.max(0, stocks.size() - INVEST_ROWS));
        y += 15;
        if (stocks.isEmpty()) ui.text(nameX, y + 2, 200, "还没有公司上市。", DIM);
        for (int i = s.investScroll; i < Math.min(stocks.size(), s.investScroll + INVEST_ROWS); i++) {
            FinanceMenu.StockRow row = stocks.get(i);
            long have = holding(v, row.symbol());
            if ((i - s.investScroll) % 2 == 0) ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            int nw = ui.text(nameX, y + 2, priceX - nameX - 4, row.name(), FinanceGui.COL_ACCENT);
            String symbol = row.symbol();
            ui.hotspot(nameX, y - 1, Math.max(nw, 30), ROW - 1, () -> s.selectedStock = symbol);
            String arrow =
                    row.mmHalted() ? " 熔断" : row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            ui.text(
                    priceX,
                    y + 2,
                    48,
                    MoneyFormat.compact(row.lastPrice()) + arrow,
                    row.mmHalted() ? FinanceGui.COL_BAD : FinanceGui.changeColor(row.dayChange()));
            ui.text(divX, y + 2, 60, MoneyFormat.compact(row.expectedDividendPerShare() * 100), TEXT);
            ui.text(holdX, y + 2, 56, have > 0 ? have + " 股" : "-", have > 0 ? TEXT : DIM);
            long cost = row.lastPrice() * lot;
            String buyBlock = row.mmHalted()
                    ? HALTED
                    : locked
                            ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后解锁"
                            : v.balance() < cost ? "钱不够，" + lot + " 股约 " + MoneyFormat.pounds(cost) : null;
            ui.buttonsRight(
                    RIGHT,
                    y,
                    gap,
                    List.of(
                            confirm(
                                    s,
                                    a,
                                    "stockbuy:" + symbol,
                                    buyLabel,
                                    () -> a.stock(true, symbol, lot),
                                    buyBlock,
                                    cost >= BIG),
                            confirm(
                                    s,
                                    a,
                                    "stocksell:" + symbol,
                                    "全卖",
                                    () -> a.stock(false, symbol, (int) Math.min(Integer.MAX_VALUE, have)),
                                    have > 0 ? null : "你没有这只股票",
                                    have * row.lastPrice() >= BIG)));
            y += ROW;
        }
        if (s.investScroll + INVEST_ROWS < stocks.size()) ui.textRight(RIGHT, BOTTOM - 40, 100, "滚轮看更多", DIM);
        marginFooter(ui, v, a, locked);
    }

    /** 股票页底部的说明：还不能买时说差多少，能买时说怎么玩；押注没解锁时顺带说怎么解锁。 */
    static void marginFooter(UiBuilder ui, View v, Actions a, boolean locked) {
        int cardY = BOTTOM - 28;
        ui.card(LEFT, cardY, WIDTH, 28, CARD, CARD_BORDER);
        String text = locked
                ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后才能买股票（还差 " + MoneyFormat.pounds(unlock(v) - v.balance())
                        + "）。理财随时可以买。"
                : extra(v, "margin")
                        ? "买股票：涨了卖掉赚差价，拿着每周有分红。点公司名字看走势、设自动卖出，或者押注涨跌。"
                        : locked(v, "bet")
                                ? "买股票：涨了卖掉赚差价，拿着每周有分红。买过一次股票后解锁「押注」：押涨跌赚翻倍。"
                                : "买股票：涨了卖掉赚差价，拿着每周有分红。点公司名字看走势、设自动卖出。";
        ui.wrapped(LEFT + PAD, cardY + 4, WIDTH - 2 * PAD, text, DIM, 2);
    }

    static void stockDetail(UiBuilder ui, View v, State s, Actions a, FinanceMenu.StockRow row, int y) {
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
        priceChart(
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
        boolean locked = stocksLocked(v);
        String lockReason = row.mmHalted() ? HALTED : locked ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后解锁" : null;
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
                                        row.mmHalted() ? HALTED : have > 0 ? null : "你没有这只股票",
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
        List<FinanceMenu.ConditionalStockOrderRow> autos =
                v.autoOrders().stream().filter(o -> o.symbol().equals(symbol)).toList();
        if (!autos.isEmpty()) {
            FinanceMenu.ConditionalStockOrderRow o = autos.get(0);
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
            y += betRow(ui, v, s, a, y, "s:" + symbol, row.mmHalted() ? HALTED : null);
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
    static String valuationText(View v, FinanceMenu.StockRow row) {
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

    /**
     * 价格图：右边小按钮切换“走势”（每次价格变动一个点）和“K线”（每游戏小时一根），
     * 第三个“提醒我”在价格涨或跌 10% 时提醒（alert 为 null 时不显示）。
     */
    static void priceChart(
            UiBuilder ui, State s, int y, int h, List<Long> line, List<Long> bars, int color, Runnable alert) {
        int bw = Math.max(Math.max(ui.buttonWidth("走势"), ui.buttonWidth("K线")), ui.buttonWidth("提醒我"));
        int chartW = WIDTH - 4 - bw - 4;
        if (s.candles) ui.candles(LEFT + 2, y, chartW, h, bars);
        else ui.chart(LEFT + 2, y, chartW, h, line, color);
        int bx = LEFT + 2 + chartW + 4;
        ui.button(bx, y, "走势", () -> s.candles = false, s.candles ? null : "正在看走势：每次价格变动一个点");
        ui.button(bx, y + UiBuilder.BUTTON_H + 1, "K线", () -> s.candles = true, s.candles ? "正在看 K 线：每游戏小时一根" : null);
        if (alert != null) ui.button(bx, y + 2 * (UiBuilder.BUTTON_H + 1), "提醒我", alert, null);
    }

    static void commodityDetail(UiBuilder ui, View v, State s, Actions a, FinanceMenu.MarketRow row, int y) {
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
        priceChart(
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
            y += betRow(ui, v, s, a, y, "f:" + id, betBlock);
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

    /** 熔断或停牌时按钮上的说明。 */
    static final String HALTED = "熔断或停牌中：股价 5 分钟内跌超 15% 会暂停交易 1 分钟";

    static final int PRODUCT_ROWS = 8;

    static final long[] AMOUNTS = {1_000, 5_000, 10_000};

    static void products(UiBuilder ui, View v, State s, Actions a, int y) {
        SimpleCenterService.Status st = v.status();
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 30, "金额", DIM) + 6;
        lx += ui.text(lx, y + 3, 80, MoneyFormat.pounds(s.productAmount), TEXT) + 8;
        List<Btn> amounts = new ArrayList<>();
        for (long amount : AMOUNTS)
            amounts.add(new Btn(MoneyFormat.compact(amount), () -> s.productAmount = amount, null));
        amounts.add(
                new Btn("钱包全部", () -> s.productAmount = Math.max(1, v.balance()), v.balance() > 0 ? null : "钱包是空的"));
        ui.buttonFlow(lx, y, RIGHT - lx, 2, amounts);
        y += 17;

        // 三档：稳健 / 均衡 / 进取，每档一行
        int buyX = RIGHT - ui.buttonWidth("存入");
        int riskX = buyX - 6 - 24, rateX = riskX - 6 - 50, termX = rateX - 6 - 50, nameX = LEFT + 2;
        List<SimpleInvestService.Product> products = st == null ? List.of() : st.products();
        if (products.isEmpty()) {
            ui.text(nameX, y + 2, 200, st == null ? "正在读取……" : "现在没有银行营业。", DIM);
            y += ROW;
        }
        long amount = s.productAmount;
        for (SimpleInvestService.Product p : products) {
            if (y + ROW > BOTTOM - 2 * ROW) break;
            ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            ui.text(nameX, y + 2, termX - nameX - 4, p.name(), TEXT);
            ui.text(termX, y + 2, 50, p.term(), DIM);
            ui.text(
                    rateX,
                    y + 2,
                    50,
                    p.rateBps() < 0 ? "随行情" : String.format(java.util.Locale.ROOT, "年 %.2f%%", p.rateBps() / 100.0),
                    TEXT);
            int riskColor = "低".equals(p.risk())
                    ? FinanceGui.COL_GOOD
                    : "中".equals(p.risk()) ? FinanceGui.COL_WARN : FinanceGui.COL_BAD;
            ui.text(riskX, y + 2, 24, "险" + p.risk(), riskColor);
            String block = amount < p.minAmount()
                    ? "最少 " + MoneyFormat.pounds(p.minAmount())
                    : v.balance() < amount ? "钱包不到 " + MoneyFormat.pounds(amount) : null;
            String key = p.key();
            ui.button(buyX, y, "存入", () -> a.simple(SimpleActionPacket.Action.BUY_PRODUCT, key, amount, 0), block);
            y += ROW;
        }
        if (locked(v, "growth")) {
            ui.text(nameX, y + 1, WIDTH - 4, "进取 · 基金（" + unlockHint("growth") + "）：跟着股市涨跌，收益高也可能亏", DIM);
            y += 12;
        }

        // 我的理财
        y += 4;
        if (y + 13 + ROW > BOTTOM) return;
        List<SimpleInvestService.Holding> holdings = st == null ? List.of() : st.holdings();
        int labelW = ui.text(LEFT + 2, y, 60, "我的理财", DIM);
        ui.text(
                LEFT + 2 + labelW + 8,
                y,
                WIDTH - labelW - 12,
                holdings.isEmpty() ? "还没有，挑一档存进去，每天都有利息。" : "存款保本；基金随行情涨跌。",
                DIM);
        y += 13;
        int rows = Math.max(1, (BOTTOM - y) / ROW);
        s.productScroll = clamp(s.productScroll, 0, Math.max(0, holdings.size() - rows));
        for (int i = s.productScroll; i < Math.min(holdings.size(), s.productScroll + rows); i++) {
            SimpleInvestService.Holding h = holdings.get(i);
            String key = h.key();
            int bx = ui.buttonRight(
                    RIGHT - 2,
                    y,
                    h.redeemLabel(),
                    () -> a.simple(SimpleActionPacket.Action.REDEEM_PRODUCT, key, 0, 0),
                    null);
            int nw = ui.text(
                    LEFT + PAD,
                    y + 3,
                    Math.min(150, bx - LEFT - PAD - 8),
                    h.name() + " " + MoneyFormat.compact(h.amount()),
                    TEXT);
            ui.text(LEFT + PAD + nw + 6, y + 3, bx - LEFT - PAD - nw - 14, h.status(), DIM);
            y += ROW;
        }
    }

    // ---------------------------------------------------------------- 押注

    /** 我的押注：每笔一行，右边是赚了还是亏了、和“结束”按钮。押金在结束后自动退回钱包。 */
    static void betsTab(UiBuilder ui, View v, Actions a, int y) {
        if (!extra(v, "margin")
                && !extra(v, "futures")
                && (v.status() == null || v.status().bets().isEmpty())) {
            if (locked(v, "bet")) lockedCard(ui, y, "bet");
            else ui.text(LEFT + PAD, y + 2, WIDTH, "服务器没有开启押注。", DIM);
            return;
        }
        List<SimpleBetService.Bet> bets =
                v.status() == null ? List.of() : v.status().bets();
        SimpleInvestService.MarginInfo m = v.status() == null
                ? SimpleInvestService.MarginInfo.EMPTY
                : v.status().margin();
        int footer = 24;
        if (bets.isEmpty()) {
            ui.wrapped(
                    LEFT + PAD,
                    y + 2,
                    WIDTH - 2 * PAD,
                    "还没有押注。在股票详情（投资 → 点公司名字）或商品详情（买卖 → 点东西名字）里选好投入，" + "点「看涨」或「看跌」就开始。押对了赚翻倍，押错会亏，亏太多会被强制结束。",
                    DIM,
                    4);
            String stats = v.status() == null ? "" : v.status().betStats();
            if (!stats.isEmpty()) ui.text(LEFT + PAD, BOTTOM - 11, WIDTH - 2 * PAD, stats, TEXT);
            return;
        }
        int rowH = 26;
        int fit = Math.max(1, (BOTTOM - footer - y) / rowH);
        int shown = bets.size() <= fit ? bets.size() : fit - 1;
        for (SimpleBetService.Bet bet : bets.subList(0, shown)) {
            ui.card(LEFT, y, WIDTH, rowH - 2, CARD, CARD_BORDER);
            String key = bet.key();
            int bx = ui.buttonRight(
                            RIGHT - PAD,
                            y + 5,
                            "结束",
                            () -> a.simple(SimpleActionPacket.Action.BET_END, key, 0, 0),
                            null)
                    - 8;
            String result = !bet.tracked()
                    ? "现值 " + MoneyFormat.compact(bet.value())
                    : (bet.pnl() >= 0 ? "赚 " : "亏 ") + MoneyFormat.compact(Math.abs(bet.pnl()));
            int rx = ui.textRight(bx, y + 4, 110, result, bet.tracked() ? FinanceGui.changeColor(bet.pnl()) : TEXT) - 8;
            int sx = bet.tracked()
                    ? ui.textRight(bx, y + 14, 110, "投入 " + MoneyFormat.compact(bet.stake()), DIM) - 8
                    : bx;
            int tw = ui.text(
                    LEFT + PAD, y + 4, 40, bet.up() ? "看涨" : "看跌", bet.up() ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            ui.text(LEFT + PAD + tw + 6, y + 4, rx - LEFT - PAD - tw - 6, bet.name(), TEXT);
            ui.text(LEFT + PAD, y + 14, sx - LEFT - PAD, bet.note(), DIM);
            y += rowH;
        }
        if (shown < bets.size())
            ui.text(LEFT + PAD, y + 4, WIDTH - PAD, "还有 " + (bets.size() - shown) + " 笔，先结束几笔再看。", DIM);
        int fy = BOTTOM - footer + 2;
        boolean stockBets = bets.stream().anyMatch(b -> b.key().startsWith("s:"));
        String line = stockBets && m.safetyPct() >= 0
                ? "股票押注安全度 " + m.safetyPct() + "%：低于 " + m.liquidationPct() + "% 会被强制结束。结束后押金自动退回钱包。"
                : "结束后押金自动退回钱包；商品押注到期自动结算。";
        ui.text(
                LEFT + 2,
                fy,
                WIDTH - 4,
                line,
                stockBets && m.safetyPct() >= 0 && m.safetyPct() < m.liquidationPct() + 20 ? FinanceGui.COL_BAD : DIM);
        String stats = v.status() == null ? "" : v.status().betStats();
        ui.text(
                LEFT + 2,
                fy + UiBuilder.LINE,
                WIDTH - 4,
                stats.isEmpty() ? "被强制结束、到期结算时会在聊天栏提醒你，离线时上线补发。" : stats,
                DIM);
    }
}
