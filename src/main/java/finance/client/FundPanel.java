package finance.client;

import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 金融服务 · 基金：申购、赎回、风险确认。由 {@link ServiceTabs} 分发渲染和点击。 */
final class FundPanel {
    private final FinanceScreen screen;

    FundPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g) {
        var data = FundClientCache.get();
        if (data.state() == FundClientCache.State.NOT_REQUESTED) {
            request();
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在请求基金数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (data.state() == FundClientCache.State.LOADING || data.state() == FundClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    data.state() == FundClientCache.State.SLOW ? "基金数据响应较慢" : "正在加载…",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    350,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        int y = FinanceGui.CONTENT_Y + 24;
        FinanceGui.drawClippedString(screen.font(), g, "基金/状态", 14, y, 135, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "净值/涨跌", 154, y, 90, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "我的份额", 250, y, 75, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "风险", 330, y, 60, FinanceGui.COL_TEXT_DIM);
        y += 15;
        for (var row : data.funds()) {
            if (y > 172) break;
            double change = row.previousNav() > 0 ? (row.nav() / (double) row.previousNav() - 1) * 100 : 0;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.name() + " " + FinanceFormat.label(row.status()),
                    14,
                    y,
                    135,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.nav() + " / " + String.format(Locale.ROOT, "%+.2f%%", change),
                    154,
                    y,
                    90,
                    change >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(
                    screen.font(), g, row.shares() + " (冻" + row.frozenShares() + ")", 250, y, 75, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.sufficientHistory() ? String.format(Locale.ROOT, "波动%.1f%%", row.volatility()) : "数据不足",
                    330,
                    y,
                    60,
                    FinanceGui.COL_WARN);
            y += 16;
        }
        g.fill(8, 185, screen.imageWidth() - 8, 232, FinanceGui.COL_ROW_EVEN);
        FinanceGui.drawClippedString(screen.font(), g, "金额", 14, 190, 35, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "份额", 126, 190, 40, FinanceGui.COL_TEXT_DIM);
        screen.filledButton(g, "申购", 275, 189, 40, FinanceGui.COL_ACCENT, () -> act(FundActionPacket.Action.SUBSCRIBE));
        screen.filledButton(g, "赎回", 320, 189, 40, FinanceGui.COL_ACCENT, () -> act(FundActionPacket.Action.REDEEM));
        screen.filledButton(
                g, "定投", 365, 189, 28, FinanceGui.COL_ACCENT, () -> act(FundActionPacket.Action.CREATE_PLAN));
        // 点下面这行费用说明就是确认风险提示（没有单独的按钮）
        screen.contentClicks.add(0, 210, 270, 23, () -> act(FundActionPacket.Action.ACKNOWLEDGE_RISK));
        FinanceGui.drawClippedString(
                screen.font(), g, "申购费、赎回费及净值均由服务端确认；赎回可能因流动性延迟。", 14, 215, 236, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "定投间隔", 284, 215, 26, FinanceGui.COL_TEXT_DIM);
    }

    private void act(FundActionPacket.Action action) {
        var data = FundClientCache.get();
        if (data.state() != FundClientCache.State.READY || data.funds().isEmpty()) return;
        var fund = data.funds().get(0);
        if (action == FundActionPacket.Action.ACKNOWLEDGE_RISK) {
            FinancePacketHandler.CHANNEL.sendToServer(new FundActionPacket(action, fund.id(), null, 0, 0, 0, ""));
            return;
        }
        long amount = action == FundActionPacket.Action.REDEEM ? 0 : screen.requireAmount(screen.stockPriceBox, "金额");
        long shares = action == FundActionPacket.Action.REDEEM
                ? screen.requireLong(screen.stockQuantityBox, "份额", 100, FinanceScreen.MAX_INPUT_AMOUNT)
                : 0;
        int interval = action == FundActionPacket.Action.CREATE_PLAN
                ? screen.requireInt(screen.companyAmountBox, "定投间隔", 1, 3650)
                : 0;
        FinancePacketHandler.CHANNEL.sendToServer(new FundActionPacket(
                action,
                fund.id(),
                null,
                amount,
                shares,
                interval,
                UUID.randomUUID().toString()));
        request();
    }

    void request() {
        long requestId = FundClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new FundRequestPacket(requestId));
    }
}
