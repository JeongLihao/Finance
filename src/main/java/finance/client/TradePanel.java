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
        FinanceGui.drawButton(screen.font(), g, btnLabel, 82, FinanceGui.CONTENT_Y, 140, screen.dropdownOpen);

        int cardY = FinanceGui.CONTENT_Y + 20;
        int cardH = 66;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFF1ECDD);
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + 1, FinanceGui.COL_PANEL_BORDER);
        g.fill(8, cardY + cardH, screen.imageWidth() - 8, cardY + cardH + 1, FinanceGui.COL_PANEL_BORDER);

        g.drawString(screen.font(), "价格:", 12, cardY + 6, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "数量:", 12, cardY + 30, FinanceGui.COL_TEXT_DIM, false);

        FinanceGui.drawButton(screen.font(), g, "-", 170, cardY + 4, 20, false);
        FinanceGui.drawButton(screen.font(), g, "+", 194, cardY + 4, 20, false);
        FinanceGui.drawButton(screen.font(), g, "-10", 222, cardY + 4, 30, false);
        FinanceGui.drawButton(screen.font(), g, "+10", 256, cardY + 4, 30, false);

        FinanceGui.drawButton(screen.font(), g, "-", 170, cardY + 30, 20, false);
        FinanceGui.drawButton(screen.font(), g, "+", 194, cardY + 30, 20, false);
        FinanceGui.drawButton(screen.font(), g, "-10", 222, cardY + 30, 30, false);
        FinanceGui.drawButton(screen.font(), g, "+10", 256, cardY + 30, 30, false);

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

        FinanceGui.drawFilledButton(screen.font(), g, "提交买单", 10, 186, 84, FinanceGui.COL_ACCENT);
        FinanceGui.drawFilledButton(screen.font(), g, "提交卖单", 102, 186, 84, FinanceGui.COL_BAD);
    }

    public boolean handleTradeClick(int mx, int my) {
        // 下拉按钮
        if (mx >= 82 && mx < 222 && my >= FinanceGui.CONTENT_Y && my < FinanceGui.CONTENT_Y + 13) {
            screen.dropdownOpen = !screen.dropdownOpen;
            screen.dropdownScroll = 0;
            return true;
        }

        int cardY = FinanceGui.CONTENT_Y + 20;

        // 价格加减
        if (my >= cardY + 4 && my < cardY + 18) {
            long price = FinanceFormat.parseLong(screen.priceBox.getValue());
            if (mx >= 170 && mx < 190) {
                screen.priceBox.setValue(Long.toString(Math.max(1, price - 1)));
                return true;
            }
            if (mx >= 194 && mx < 214) {
                screen.priceBox.setValue(Long.toString(price + 1));
                return true;
            }
            if (mx >= 222 && mx < 252) {
                screen.priceBox.setValue(Long.toString(Math.max(1, price - 10)));
                return true;
            }
            if (mx >= 256 && mx < 286) {
                screen.priceBox.setValue(Long.toString(price + 10));
                return true;
            }
        }

        // 数量加减
        if (my >= cardY + 30 && my < cardY + 44) {
            int qty = FinanceFormat.parseInt(screen.quantityBox.getValue());
            if (mx >= 170 && mx < 190) {
                screen.quantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 194 && mx < 214) {
                screen.quantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 222 && mx < 252) {
                screen.quantityBox.setValue(Integer.toString(Math.max(1, qty - 10)));
                return true;
            }
            if (mx >= 256 && mx < 286) {
                screen.quantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
        }

        // 提交买单/卖单
        if (my >= 186 && my < 200 && (mx >= 10 && mx < 94 || mx >= 102 && mx < 186)) {
            long price = screen.requireAmount(screen.priceBox, "价格");
            int qty = screen.requireInt(screen.quantityBox, "数量", 1, Integer.MAX_VALUE);
            if (mx >= 10 && mx < 94) {
                screen.setStatus("已提交玩家买单");
                FinancePacketHandler.CHANNEL.sendToServer(new TradeActionPacket(
                        TradeActionPacket.ActionType.P2P_BUY, screen.selectedCommodity, price, qty));
                return true;
            }
            if (mx >= 102 && mx < 186) {
                screen.setStatus("已提交玩家卖单");
                FinancePacketHandler.CHANNEL.sendToServer(new TradeActionPacket(
                        TradeActionPacket.ActionType.P2P_SELL, screen.selectedCommodity, price, qty));
                return true;
            }
        }
        return false;
    }
}
