package finance.client;

import finance.chart.MarketInstrumentType;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 金融服务 · 期货：合约列表、K 线、下单和保证金。由 {@link ServiceTabs} 分发渲染和点击。 */
final class FuturesPanel {
    private final FinanceScreen screen;

    FuturesPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        FuturesClientCache.Entry cache = FuturesClientCache.get();
        if (cache.state() == FuturesClientCache.State.NOT_REQUESTED) {
            request();
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在请求期货数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (cache.state() == FuturesClientCache.State.LOADING || cache.state() == FuturesClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    cache.state() == FuturesClientCache.State.SLOW ? "期货服务响应较慢" : "正在加载期货数据…",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    350,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        var data = cache.data();
        var chartContract = data.contracts().stream().findFirst().orElse(null);
        if (screen.futuresChartVisible && chartContract != null) {
            screen.candlestickView.renderCandlestickView(
                    g,
                    MarketInstrumentType.FUTURES,
                    chartContract.id().toString(),
                    chartContract.code(),
                    mouseX,
                    mouseY);
            return;
        }
        int y = FinanceGui.CONTENT_Y + 22;
        if (chartContract != null) {
            // 点合约表格任意位置打开第一份合约的 K 线
            screen.contentClicks.add(0, y, screen.imageWidth(), 188 - y, () -> {
                screen.futuresChartVisible = true;
                screen.candlestickView.requestCandlesticks(
                        MarketInstrumentType.FUTURES, chartContract.id().toString());
                screen.updateInputVisibility();
            });
            double change = chartContract.settlementPrice() > 0
                    ? (chartContract.lastPrice() - chartContract.settlementPrice())
                            * 100.0
                            / chartContract.settlementPrice()
                    : 0;
            long basis;
            try {
                basis = Math.subtractExact(chartContract.lastPrice(), chartContract.spotPrice());
            } catch (ArithmeticException ex) {
                basis = chartContract.lastPrice() >= chartContract.spotPrice() ? Long.MAX_VALUE : Long.MIN_VALUE;
            }
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    String.format(
                            Locale.ROOT,
                            "今日涨跌 %.2f%%  比现货贵 %d  今日成交 %d  未平仓 %d",
                            change,
                            basis,
                            chartContract.dailyVolume(),
                            chartContract.openInterest()),
                    14,
                    y,
                    370,
                    change >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            y += 14;
        }
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "保证金 " + data.marginCash() + " 冻结 " + data.frozen() + " 权益 " + data.equity() + " 可提 "
                        + data.withdrawable() + " " + data.riskStatus(),
                14,
                y,
                370,
                data.riskStatus() == finance.futures.MarginRiskStatus.NORMAL
                        ? FinanceGui.COL_GOOD
                        : FinanceGui.COL_WARN);
        y += 16;
        FinanceGui.drawClippedString(screen.font(), g, "代码/标的", 14, y, 105, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "期/现/结", 126, y, 100, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "到期/OI", 238, y, 90, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "持仓/PnL", 330, y, 62, FinanceGui.COL_TEXT_DIM);
        y += 14;
        for (var row : data.contracts()) {
            if (y > 174) break;
            var pos = data.positions().stream()
                    .filter(p -> p.contractId().equals(row.id()))
                    .findFirst()
                    .orElse(null);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.code() + "/" + FinanceFormat.commodityDisplayName(row.commodity()),
                    14,
                    y,
                    105,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.lastPrice() + "/" + row.spotPrice() + "/" + row.settlementPrice(),
                    126,
                    y,
                    105,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "第" + row.maturityDay() + "天/" + row.openInterest(),
                    238,
                    y,
                    85,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    pos == null ? "0/0" : pos.signedQuantity() + "/" + pos.unrealized(),
                    330,
                    y,
                    62,
                    pos == null || pos.unrealized() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            y += 16;
        }
        int ay = 190;
        g.fill(8, ay - 5, screen.imageWidth() - 8, 232, FinanceGui.COL_ROW_EVEN);
        FinanceGui.drawClippedString(screen.font(), g, "金额/限价", 14, ay, 60, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "数量", 126, ay, 34, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "期限", 238, ay, 34, FinanceGui.COL_TEXT_DIM);
        screen.filledButton(
                g, "买", 365, ay - 1, 28, FinanceGui.COL_ACCENT, () -> act(FuturesActionPacket.Action.PLACE_BUY));
        screen.button(g, "卖", 244, ay + 20, 28, false, () -> act(FuturesActionPacket.Action.PLACE_SELL));
        screen.button(g, "入", 274, ay + 20, 28, false, () -> act(FuturesActionPacket.Action.DEPOSIT_MARGIN));
        screen.button(g, "出", 304, ay + 20, 28, false, () -> act(FuturesActionPacket.Action.WITHDRAW_MARGIN));
        screen.button(g, "撤", 334, ay + 20, 28, false, () -> act(FuturesActionPacket.Action.CANCEL_ORDER));
        if (screen.isAdmin)
            screen.button(g, "建", 364, ay + 20, 28, false, () -> act(FuturesActionPacket.Action.CREATE_CONTRACT));
    }

    private void act(FuturesActionPacket.Action action) {
        var data = FuturesClientCache.get().data();
        if (data == null) return;
        var contract = data.contracts().stream()
                .filter(c -> c.status() == finance.futures.FuturesContractStatus.TRADING
                        || c.status() == finance.futures.FuturesContractStatus.LAST_TRADING_DAY)
                .findFirst()
                .orElse(null);
        FuturesActionPacket packet;
        switch (action) {
            case PLACE_BUY, PLACE_SELL -> {
                if (contract == null) return;
                long price = screen.requireAmount(screen.stockPriceBox, "金额/限价");
                long quantity = screen.requireAmount(screen.stockQuantityBox, "数量");
                packet = new FuturesActionPacket(action, contract.id(), "", 0, price, quantity, 0);
            }
            case DEPOSIT_MARGIN, WITHDRAW_MARGIN -> packet = new FuturesActionPacket(
                    action, null, "", screen.requireAmount(screen.stockPriceBox, "金额/限价"), 0, 0, 0);
            case CANCEL_ORDER -> {
                var own = data.orders().stream()
                        .filter(o -> o.owned())
                        .findFirst()
                        .orElse(null);
                if (own == null) return;
                packet = new FuturesActionPacket(action, own.orderId(), "", 0, 0, 0, 0);
            }
            case CREATE_CONTRACT -> packet = new FuturesActionPacket(
                    action,
                    null,
                    screen.selectedCommodity,
                    0,
                    0,
                    0,
                    screen.requireInt(screen.companyAmountBox, "期限", 2, 3650));
            default -> {
                return;
            }
        }
        FinancePacketHandler.CHANNEL.sendToServer(packet);
        request();
    }

    void request() {
        long requestId = FuturesClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new FuturesRequestPacket(requestId));
    }
}
