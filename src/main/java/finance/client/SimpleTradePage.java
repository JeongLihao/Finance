package finance.client;

import static finance.client.SimplePages.*;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.gui.MarketRows;
import finance.network.SimpleActionPacket;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 简单模式“买卖”页：商品列表、搜索收藏、买卖按钮、挂单。排版规则见 {@link SimplePages}。 */
final class SimpleTradePage {

    private SimpleTradePage() {}

    /**
     * 仓库里有的排前面（按能卖的钱从多到少），其余按名字。没有对应物品的商品不列；
     * 市场里没货、仓库和背包里也没有的不列——有人卖了才会出现。
     */
    static List<MarketRows.MarketRow> tradeRows(View v) {
        Map<String, Integer> held = v.storedGoods();
        Map<String, Integer> bag = v.backpack();
        List<MarketRows.MarketRow> rows = new ArrayList<>();
        for (MarketRows.MarketRow row : v.market()) {
            Commodity c = CommodityRegistry.getCommodity(row.commodityId());
            if (row.marketStock() <= 0
                    && held.getOrDefault(row.commodityId(), 0) <= 0
                    && bag.getOrDefault(row.commodityId(), 0) <= 0) continue;
            if (c == null || c.getItemId() != null && !c.getItemId().isEmpty()) rows.add(row);
        }
        rows.sort(Comparator.<MarketRows.MarketRow>comparingLong(
                        r -> -(long) held.getOrDefault(r.commodityId(), 0) * r.bidPrice())
                .thenComparing(r -> FinanceFormat.commodityDisplayName(r.commodityId())));
        return rows;
    }

    /** 买卖列表：按搜索词过滤（名字或 ID 包含就算），收藏的排最前面。 */
    static List<MarketRows.MarketRow> tradeRows(View v, State s) {
        String q = s.search == null ? "" : s.search.trim().toLowerCase(java.util.Locale.ROOT);
        List<MarketRows.MarketRow> out = new ArrayList<>();
        for (MarketRows.MarketRow row : tradeRows(v)) {
            if (q.isEmpty()
                    || row.commodityId().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || FinanceFormat.commodityDisplayName(row.commodityId())
                            .toLowerCase(java.util.Locale.ROOT)
                            .contains(q)) out.add(row);
        }
        out.sort(Comparator.comparing(r -> !s.favorites.contains(r.commodityId())));
        return out;
    }

    static void tradePage(UiBuilder ui, View v, State s, Actions a) {
        List<String> labels = List.of("马上买卖", "挂单");
        if (s.tradeTab >= labels.size()) s.tradeTab = 0;
        int y = subTabs(ui, TOP, labels, s.tradeTab, i -> {
            s.tradeTab = i;
            s.selectedCommodity = null;
        });
        switch (s.tradeTab) {
            case 1 -> orders(ui, v, s, a, y);
            default -> {
                MarketRows.MarketRow selected = s.selectedCommodity == null
                        ? null
                        : v.market().stream()
                                .filter(r -> r.commodityId().equals(s.selectedCommodity))
                                .findFirst()
                                .orElse(null);
                if (selected != null) SimpleCommodityDetail.commodityDetail(ui, v, s, a, selected, y);
                else trade(ui, v, s, a, y);
            }
        }
    }

    // ---------------------------------------------------------------- 挂单

    static void orders(UiBuilder ui, View v, State s, Actions a, int y) {
        List<MarketRows.MarketRow> rows = tradeRows(v);
        if (rows.isEmpty()) {
            ui.text(LEFT + PAD, y + 2, WIDTH, "市场里还没有货，仓库和背包里也没有能卖的东西。", DIM);
            return;
        }
        MarketRows.MarketRow row = rows.stream()
                .filter(r -> r.commodityId().equals(s.orderCommodity))
                .findFirst()
                .orElse(rows.get(0));
        if (!row.commodityId().equals(s.orderCommodity)) {
            s.orderCommodity = row.commodityId();
            s.orderPrice = 0;
        }
        String id = row.commodityId();
        String name = FinanceFormat.commodityDisplayName(id);
        long price = s.orderPrice > 0 ? s.orderPrice : Math.max(1, row.midPrice());
        long step = Math.max(1, price / 20);
        int have = v.storedGoods().getOrDefault(id, 0);
        int index = rows.indexOf(row);

        // 第一行：选商品 + 市场价
        int x = LEFT + 2;
        ui.text(x, y + 3, 30, "商品", DIM);
        x += 32;
        x += ui.button(
                        x,
                        y,
                        "<",
                        () -> {
                            s.orderCommodity = rows.get((index - 1 + rows.size()) % rows.size())
                                    .commodityId();
                            s.orderPrice = 0;
                        },
                        null)
                + 4;
        ui.icon(x, y + 2, id);
        x += ui.text(x + 13, y + 3, 70, name, TEXT) + 17;
        x += ui.button(
                        x,
                        y,
                        ">",
                        () -> {
                            s.orderCommodity =
                                    rows.get((index + 1) % rows.size()).commodityId();
                            s.orderPrice = 0;
                        },
                        null)
                + 8;
        ui.textRight(
                RIGHT - 2,
                y + 3,
                RIGHT - 2 - x,
                "市场收 " + MoneyFormat.compact(row.bidPrice()) + "  卖 " + MoneyFormat.compact(row.askPrice()),
                DIM);

        // 第二行：单价、数量
        y += 18;
        x = LEFT + 2;
        ui.text(x, y + 3, 30, "单价", DIM);
        x += 32;
        long finalPrice = price;
        x += ui.button(
                        x,
                        y,
                        "-",
                        () -> s.orderPrice = Math.max(1, finalPrice - step),
                        finalPrice <= 1 ? "最低 1 便士" : null)
                + 4;
        x += ui.text(x, y + 3, 60, MoneyFormat.compact(price), TEXT) + 4;
        x = Math.max(x, LEFT + 34 + 18 + 64);
        x += ui.button(x, y, "+", () -> s.orderPrice = finalPrice + step, null) + 14;
        ui.text(x, y + 3, 60, "数量 " + s.orderQty, TEXT);
        int qx = x + ui.width("数量 9999") + 6;
        ui.buttonFlow(
                qx,
                y,
                RIGHT - qx,
                2,
                List.of(
                        new Btn("1", () -> s.orderQty = 1, null),
                        new Btn("16", () -> s.orderQty = 16, null),
                        new Btn("64", () -> s.orderQty = 64, null),
                        new Btn(
                                "仓库全部",
                                () -> s.orderQty = Math.max(1, have),
                                have > 0 ? null : storeHint(v, id, name))));

        // 第三行：挂卖单 / 挂买单
        y += 18;
        int qty = s.orderQty;
        long total = finalPrice * qty;
        x = LEFT + 2;
        x += ui.button(
                        x,
                        y,
                        "挂卖单",
                        () -> a.simple(SimpleActionPacket.Action.PLACE_SELL_ORDER, id, qty, finalPrice),
                        have >= qty ? null : have > 0 ? "仓库里只有 " + have + " 个" + name : storeHint(v, id, name))
                + 4;
        x += ui.button(
                        x,
                        y,
                        "挂买单",
                        () -> a.simple(SimpleActionPacket.Action.PLACE_BUY_ORDER, id, qty, finalPrice),
                        v.balance() >= total ? null : "钱不够，要冻结 " + MoneyFormat.pounds(total))
                + 8;
        ui.text(
                x,
                y + 3,
                RIGHT - x,
                qty + " 个，每个 " + MoneyFormat.compact(price) + "，共 " + MoneyFormat.compact(total) + "（买单先冻结这笔钱）",
                DIM);
        y += 17;
        y += ui.wrapped(LEFT + 2, y, WIDTH - 4, "卖单：市场收购价涨到你定的价就自动卖出仓库里的货。买单：市场出售价跌到你定的价就自动买入，买到的放进仓库。", DIM, 2) + 4;

        // 我的挂单
        List<MarketRows.OrderRow> mine =
                v.orders().stream().filter(MarketRows.OrderRow::ownedByPlayer).toList();
        ui.text(LEFT + 2, y, 120, "我的挂单（" + mine.size() + "）", DIM);
        y += 12;
        if (mine.isEmpty()) {
            ui.text(LEFT + PAD, y, WIDTH, "还没有挂单。", DIM);
            y += 13;
        }
        for (int i = 0; i < Math.min(3, mine.size()); i++) {
            MarketRows.OrderRow o = mine.get(i);
            int bx = ui.buttonRight(
                    RIGHT - 2,
                    y - 2,
                    "撤单",
                    () -> a.simple(
                            SimpleActionPacket.Action.CANCEL_ORDER, o.orderId().toString(), 0, 0),
                    null);
            ui.text(
                    LEFT + PAD,
                    y,
                    bx - LEFT - PAD - 6,
                    ("SELL".equals(o.type()) ? "卖 " : "买 ") + o.quantity() + " 个"
                            + FinanceFormat.commodityDisplayName(o.commodityId()) + "，每个 "
                            + MoneyFormat.pounds(o.price()),
                    TEXT);
            y += 14;
        }
        // 仓库里的货取回背包、背包里的货存进仓库：只能在自己的仓库控制器或市场终端旁（服务端会拦）
        List<Map.Entry<String, Integer>> stored = v.storedGoods().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .limit(2)
                .toList();
        for (Map.Entry<String, Integer> e : stored) {
            if (y + 13 > BOTTOM) break;
            String goods = e.getKey();
            int bx = ui.buttonRight(
                    RIGHT - 2, y - 2, "取回背包", () -> a.simple(SimpleActionPacket.Action.TAKE_GOODS, goods, 0, 0), null);
            ui.text(
                    LEFT + PAD,
                    y,
                    bx - LEFT - PAD - 6,
                    "仓库里：" + FinanceFormat.commodityDisplayName(goods) + " " + e.getValue() + " 个",
                    DIM);
            y += 14;
        }
        List<Map.Entry<String, Integer>> bag = v.backpack().entrySet().stream()
                .filter(e -> e.getValue() > 0 && CommodityRegistry.getCommodity(e.getKey()) != null)
                .limit(2)
                .toList();
        for (Map.Entry<String, Integer> e : bag) {
            if (y + 13 > BOTTOM) break;
            String goods = e.getKey();
            int count = e.getValue();
            int bx = ui.buttonRight(
                    RIGHT - 2,
                    y - 2,
                    "存入仓库",
                    () -> a.simple(SimpleActionPacket.Action.STORE_GOODS, goods, count, 0),
                    null);
            ui.text(
                    LEFT + PAD,
                    y,
                    bx - LEFT - PAD - 6,
                    "背包里：" + FinanceFormat.commodityDisplayName(goods) + " " + count + " 个（在仓库或市场终端旁才能存）",
                    DIM);
            y += 14;
        }
    }

    /** 仓库里没有这种货时按钮灰掉的原因：背包里有的话提示先存进去。 */
    static String storeHint(View v, String id, String name) {
        return v.backpack().getOrDefault(id, 0) > 0 ? "仓库里没有" + name + "：背包里的要先在仓库或市场终端旁存进去" : "仓库里没有" + name;
    }

    static void trade(UiBuilder ui, View v, State s, Actions a, int top) {
        // 列从右往左按按钮实际宽度排，左边剩下的给商品名；按住 Shift 时数量 ×10
        int m = mult(s);
        String sellLabel = m == 1 ? "卖1组" : "卖10组", buyOne = m == 1 ? "买1" : "买10", buyStack = m == 1 ? "买1组" : "买10组";
        int gap = 2;
        int buyStart = ui.buttonsRightStart(RIGHT, gap, List.of(buyOne, buyStack));
        int askX = buyStart - 6 - 50;
        int sellStart = ui.buttonsRightStart(askX - 6, gap, List.of(sellLabel, "全卖"));
        int bidX = sellStart - 6 - 50;
        int haveX = bidX - 4 - 34;
        int nameX = LEFT + 26;

        int y = top;
        ui.text(nameX, y, haveX - nameX - 4, "东西（点名字看走势，☆ 收藏）", DIM);
        ui.text(haveX, y, 34, "仓库", DIM);
        ui.text(bidX, y, 50, "你卖得", DIM);
        ui.text(askX, y, 50, "你买付", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);

        List<MarketRows.MarketRow> rows = tradeRows(v, s);
        s.tradeScroll = clamp(s.tradeScroll, 0, Math.max(0, rows.size() - TRADE_ROWS));
        y += 15;
        if (rows.isEmpty()) {
            ui.text(
                    nameX,
                    y + 2,
                    WIDTH - nameX,
                    s.search.isBlank() ? "市场里还没有货。去挖点东西卖给市场吧。" : "没有找到“" + s.search.trim() + "”。",
                    DIM);
        }
        for (int i = s.tradeScroll; i < Math.min(rows.size(), s.tradeScroll + TRADE_ROWS); i++) {
            MarketRows.MarketRow row = rows.get(i);
            String id = row.commodityId();
            String name = FinanceFormat.commodityDisplayName(id);
            int have = v.storedGoods().getOrDefault(id, 0);
            if ((i - s.tradeScroll) % 2 == 0) ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            boolean fav = s.favorites.contains(id);
            int sw = ui.text(LEFT + 2, y + 2, 10, fav ? "★" : "☆", fav ? FinanceGui.COL_WARN : DIM);
            ui.hotspot(LEFT, y - 1, Math.max(sw, 8) + 3, ROW - 1, () -> a.toggleFavorite(id));
            ui.icon(LEFT + 12, y + 1, id);
            int nw = ui.text(nameX, y + 2, haveX - nameX - 4, name, FinanceGui.COL_ACCENT);
            ui.hotspot(nameX, y - 1, Math.max(nw, 20), ROW - 1, () -> s.selectedCommodity = id);
            ui.text(haveX, y + 2, 34, have > 0 ? Integer.toString(have) : "-", have > 0 ? TEXT : DIM);
            String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            int color = FinanceGui.changeColor(row.dayChange());
            ui.text(bidX, y + 2, 50, MoneyFormat.compact(row.bidPrice()) + arrow, color);
            String noItem = have > 0 ? null : storeHint(v, id, name);
            int sellQty = Math.min(64 * m, have);
            ui.buttonsRight(
                    askX - 6,
                    y,
                    gap,
                    List.of(
                            new Btn(
                                    sellLabel,
                                    () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, sellQty),
                                    noItem),
                            confirm(
                                    s,
                                    a,
                                    "sellall:" + id,
                                    "全卖",
                                    () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, have),
                                    noItem,
                                    (long) have * row.bidPrice() >= BIG)));
            ui.text(askX, y + 2, 50, MoneyFormat.compact(row.askPrice()) + arrow, color);
            String poor = v.balance() < row.askPrice() ? "钱不够，一个要 " + MoneyFormat.pounds(row.askPrice()) : null;
            ui.buttonsRight(
                    RIGHT,
                    y,
                    gap,
                    List.of(
                            new Btn(buyOne, () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, m), poor),
                            confirm(
                                    s,
                                    a,
                                    "buystack:" + id,
                                    buyStack,
                                    () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 64 * m),
                                    poor,
                                    64L * m * row.askPrice() >= BIG)));
            y += ROW;
        }
        // 最下面一行：搜索框、翻页、Shift 提示
        int footY = BOTTOM - 14;
        int lx = LEFT + 2 + ui.text(LEFT + 2, footY + 4, 30, "搜索", DIM) + 4;
        ui.input("search", lx, footY, 100);
        int pagerX = RIGHT;
        if (rows.size() > TRADE_ROWS) {
            pagerX = ui.textRight(
                            RIGHT,
                            footY + 4,
                            120,
                            "滚轮翻页 " + (s.tradeScroll + 1) + "-" + Math.min(rows.size(), s.tradeScroll + TRADE_ROWS)
                                    + " / " + rows.size(),
                            DIM)
                    - 8;
        }
        ui.text(lx + 108, footY + 4, pagerX - lx - 108, m == 1 ? "按住 Shift：数量 ×10" : "Shift：数量 ×10", DIM);
    }
}
