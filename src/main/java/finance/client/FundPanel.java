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
        FinanceGui.drawFilledButton(screen.font(), g, "申购", 275, 189, 40, FinanceGui.COL_ACCENT);
        FinanceGui.drawFilledButton(screen.font(), g, "赎回", 320, 189, 40, FinanceGui.COL_ACCENT);
        FinanceGui.drawFilledButton(screen.font(), g, "定投", 365, 189, 28, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(
                screen.font(), g, "申购费、赎回费及净值均由服务端确认；赎回可能因流动性延迟。", 14, 215, 236, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "定投间隔", 284, 215, 26, FinanceGui.COL_TEXT_DIM);
    }

    boolean click(int mx, int my) {
        var data = FundClientCache.get();
        if (data.state() != FundClientCache.State.READY || data.funds().isEmpty()) return false;
        var fund = data.funds().get(0);
        java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.stockPriceBox, "金额"),
                shares = () -> screen.requireLong(screen.stockQuantityBox, "份额", 100, FinanceScreen.MAX_INPUT_AMOUNT);
        java.util.function.IntSupplier interval = () -> screen.requireInt(screen.companyAmountBox, "定投间隔", 1, 3650);
        if (my >= 185 && my <= 210) {
            FundActionPacket packet;
            if (mx >= 275 && mx < 315)
                packet = new FundActionPacket(
                        FundActionPacket.Action.SUBSCRIBE,
                        fund.id(),
                        null,
                        amount.getAsLong(),
                        0,
                        0,
                        UUID.randomUUID().toString());
            else if (mx >= 320 && mx < 360)
                packet = new FundActionPacket(
                        FundActionPacket.Action.REDEEM,
                        fund.id(),
                        null,
                        0,
                        shares.getAsLong(),
                        0,
                        UUID.randomUUID().toString());
            else if (mx >= 365)
                packet = new FundActionPacket(
                        FundActionPacket.Action.CREATE_PLAN,
                        fund.id(),
                        null,
                        amount.getAsLong(),
                        0,
                        interval.getAsInt(),
                        UUID.randomUUID().toString());
            else return false;
            FinancePacketHandler.CHANNEL.sendToServer(packet);
            request();
            return true;
        }
        if (my >= 210 && my <= 232 && mx < 270) {
            FinancePacketHandler.CHANNEL.sendToServer(
                    new FundActionPacket(FundActionPacket.Action.ACKNOWLEDGE_RISK, fund.id(), null, 0, 0, 0, ""));
            return true;
        }
        return false;
    }

    void request() {
        long requestId = FundClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new FundRequestPacket(requestId));
    }
}
