package finance.client;

import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 交易页：选商品，填价格和数量，挂玩家之间的买单或卖单。原在 MarketTabs。 */
final class TradePanel {
    private final FinanceScreen screen;

    TradePanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderTradeTab(GuiGraphics g) {
        if (screen.cacheDirty) screen.refreshCache();
        screen.refreshSelectedRow();

        // ---- 商品选择下拉按钮 ----
        FinanceGui.drawSectionTitle(screen.font(), g, "商品选择", 10, FinanceGui.CONTENT_Y);
        String btnLabel = FinanceFormat.commodityDisplayName(screen.selectedCommodity) + " ▾";
        screen.button(g, btnLabel, 82, FinanceGui.CONTENT_Y, 140, screen.dropdownOpen, () -> {
            screen.dropdownOpen = !screen.dropdownOpen;
            screen.dropdownScroll = 0;
        });

        int cardY = FinanceGui.CONTENT_Y + 20;
        int cardH = 66;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFF1ECDD);
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + 1, FinanceGui.COL_PANEL_BORDER);
        g.fill(8, cardY + cardH, screen.imageWidth() - 8, cardY + cardH + 1, FinanceGui.COL_PANEL_BORDER);

        g.drawString(screen.font(), "价格:", 12, cardY + 6, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "数量:", 12, cardY + 30, FinanceGui.COL_TEXT_DIM, false);

        screen.button(g, "-", 170, cardY + 4, 20, false, () -> addPrice(-1));
        screen.button(g, "+", 194, cardY + 4, 20, false, () -> addPrice(1));
        screen.button(g, "-10", 222, cardY + 4, 30, false, () -> addPrice(-10));
        screen.button(g, "+10", 256, cardY + 4, 30, false, () -> addPrice(10));

        screen.button(g, "-", 170, cardY + 30, 20, false, () -> addQuantity(-1));
        screen.button(g, "+", 194, cardY + 30, 20, false, () -> addQuantity(1));
        screen.button(g, "-10", 222, cardY + 30, 30, false, () -> addQuantity(-10));
        screen.button(g, "+10", 256, cardY + 30, 30, false, () -> addQuantity(10));

        int infoY = cardY + cardH + 6;
        if (screen.cachedSelectedRow != null) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "市场收购: " + screen.cachedSelectedRow.bidPrice(),
                    10,
                    infoY,
                    126,
                    FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "市场出售: " + screen.cachedSelectedRow.askPrice(),
                    146,
                    infoY,
                    126,
                    FinanceGui.COL_WARN);
        } else {
            FinanceGui.drawClippedString(screen.font(), g, "该商品未接入国际市场，仅支持玩家间交易", 10, infoY, 260, FinanceGui.COL_WARN);
        }
        FinanceGui.drawClippedString(
                screen.font(), g, "余额: " + screen.menu().getBalance(), 10, infoY + 16, 180, FinanceGui.COL_TEXT);
        int owned = screen.menu().getPlayerInventory().getOrDefault(screen.selectedCommodity, 0);
        FinanceGui.drawClippedString(screen.font(), g, "持有: " + owned, 210, infoY + 16, 150, FinanceGui.COL_TEXT_DIM);
        long orderTotal = FinanceFormat.safeMultiply(
                FinanceFormat.parseLong(screen.priceBox.getValue()),
                Math.max(1, FinanceFormat.parseInt(screen.quantityBox.getValue())));
        FinanceGui.drawClippedString(
                screen.font(), g, "订单总价: " + orderTotal, 210, infoY + 30, 150, FinanceGui.COL_ACCENT);

        screen.filledButton(g, "提交买单", 10, 186, 84, FinanceGui.COL_ACCENT, () -> submit(true));
        screen.filledButton(g, "提交卖单", 102, 186, 84, FinanceGui.COL_BAD, () -> submit(false));
    }

    private void addPrice(int delta) {
        long price = FinanceFormat.parseLong(screen.priceBox.getValue());
        screen.priceBox.setValue(Long.toString(delta < 0 ? Math.max(1, price + delta) : price + delta));
    }

    private void addQuantity(int delta) {
        int qty = FinanceFormat.parseInt(screen.quantityBox.getValue());
        screen.quantityBox.setValue(Integer.toString(delta < 0 ? Math.max(1, qty + delta) : qty + delta));
    }

    private void submit(boolean buy) {
        long price = screen.requireAmount(screen.priceBox, "价格");
        int qty = screen.requireInt(screen.quantityBox, "数量", 1, Integer.MAX_VALUE);
        screen.setStatus(buy ? "已提交玩家买单" : "已提交玩家卖单");
        FinancePacketHandler.CHANNEL.sendToServer(new TradeActionPacket(
                buy ? TradeActionPacket.ActionType.P2P_BUY : TradeActionPacket.ActionType.P2P_SELL,
                screen.selectedCommodity,
                price,
                qty));
    }
}
