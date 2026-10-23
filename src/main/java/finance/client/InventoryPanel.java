package finance.client;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 库存页：账户可用和冻结资金、商品库存和背包数量，存入取出。原在 RecordTabs，由 {@link FinanceScreen} 直接调用。 */
final class InventoryPanel {
    private final FinanceScreen screen;

    InventoryPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderInventoryTab(GuiGraphics g) {
        int cardY = FinanceGui.CONTENT_Y;
        int cardH = 38;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);

        FinanceGui.drawSectionTitle(screen.font(), g, "账户", 10, cardY + 2);
        g.drawString(screen.font(), "可用: ", 12, cardY + 18, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(screen.menu().getBalance()), 46, cardY + 18, 120, FinanceGui.COL_GOOD);
        g.drawString(screen.font(), "冻结: ", 190, cardY + 18, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                Long.toString(screen.menu().getFrozenBalance()),
                224,
                cardY + 18,
                120,
                FinanceGui.COL_WARN);

        int invY = cardY + cardH + 6;
        int invH = screen.imageHeight() - invY - 4;
        g.fill(8, invY, screen.imageWidth() - 8, invY + invH, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, 8, invY, screen.imageWidth() - 16, invH, FinanceGui.COL_PANEL_BORDER);

        FinanceGui.drawSectionTitle(screen.font(), g, "商品库存（点击选择）", 10, invY + 2);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "当前: " + FinanceFormat.commodityDisplayName(screen.selectedCommodity),
                122,
                invY + 3,
                90,
                FinanceGui.COL_ACCENT);
        g.drawString(screen.font(), "数量:", 12, invY + 22, FinanceGui.COL_TEXT_DIM, false);
        screen.button(g, "-64", 116, invY + 20, 32, false, () -> {
            int amount = Math.max(0, FinanceFormat.parseInt(screen.inventoryQuantityBox.getValue()));
            screen.inventoryQuantityBox.setValue(Integer.toString(Math.max(1, amount - 64)));
        });
        screen.button(g, "+64", 152, invY + 20, 32, false, () -> {
            int amount = Math.max(0, FinanceFormat.parseInt(screen.inventoryQuantityBox.getValue()));
            screen.inventoryQuantityBox.setValue(Integer.toString(amount + 64));
        });
        screen.filledButton(g, "存入", 246, invY + 20, 44, FinanceGui.COL_GOOD, () -> {
            Commodity c = CommodityRegistry.getCommodity(screen.selectedCommodity);
            if (c != null && c.getItemId() != null) {
                screen.setStatus("已提交存入请求");
                FinancePacketHandler.CHANNEL.sendToServer(new InventoryActionPacket(
                        InventoryActionPacket.ActionType.DEPOSIT,
                        screen.selectedCommodity,
                        screen.requireInt(screen.inventoryQuantityBox, "数量", 1, Integer.MAX_VALUE)));
            } else {
                screen.setStatus("该商品没有绑定物品");
            }
        });
        screen.filledButton(g, "取出", 296, invY + 20, 44, FinanceGui.COL_ACCENT, () -> {
            screen.setStatus("已提交取出请求");
            FinancePacketHandler.CHANNEL.sendToServer(new InventoryActionPacket(
                    InventoryActionPacket.ActionType.WITHDRAW,
                    screen.selectedCommodity,
                    screen.requireInt(screen.inventoryQuantityBox, "数量", 1, Integer.MAX_VALUE)));
        });

        int headerLineY = invY + 42;
        FinanceGui.drawHeader(screen.font(), g, "商品", 14, headerLineY);
        FinanceGui.drawHeader(screen.font(), g, "库存", 140, headerLineY);
        FinanceGui.drawHeader(screen.font(), g, "背包", 210, headerLineY);
        g.fill(8, headerLineY + 11, screen.imageWidth() - 8, headerLineY + 12, FinanceGui.COL_PANEL_BORDER);

        Map<String, Integer> inv = screen.menu().getPlayerInventory();
        Map<String, Integer> mcInv = screen.menu().getMcInventory();
        Set<String> allCommodityIds = new LinkedHashSet<>();
        allCommodityIds.addAll(inv.keySet());
        allCommodityIds.addAll(mcInv.keySet());

        if (allCommodityIds.isEmpty()) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "暂无商品（可通过管理标签添加常用物品）",
                    12,
                    headerLineY + 18,
                    screen.imageWidth() - 24,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }

        int y = headerLineY + 14;
        int maxY = invY + invH - FinanceGui.ROW_HEIGHT;
        for (String commodityId : allCommodityIds) {
            if (y > maxY) break;
            boolean selected = commodityId.equals(screen.selectedCommodity);
            screen.contentClicks.add(8, y, screen.imageWidth() - 16, FinanceGui.ROW_HEIGHT, () -> {
                screen.selectedCommodity = commodityId;
                // 更新数量输入框为该商品的库存数量
                int stock = inv.getOrDefault(commodityId, 0);
                screen.inventoryQuantityBox.setValue(Integer.toString(Math.max(64, stock)));
                screen.rememberUiState();
            });
            int bg = selected
                    ? FinanceGui.COL_ROW_SELECT
                    : ((y / FinanceGui.ROW_HEIGHT) % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            g.fill(9, y, screen.imageWidth() - 9, y + FinanceGui.ROW_HEIGHT, bg);
            if (selected) {
                g.fill(9, y, 11, y + FinanceGui.ROW_HEIGHT, FinanceGui.COL_ACCENT);
            }
            Commodity c = CommodityRegistry.getCommodity(commodityId);
            FinanceGui.renderItemIcon(g, c, 14, y + 1);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.commodityDisplayName(commodityId),
                    28,
                    y + 3,
                    106,
                    selected ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    String.valueOf(inv.getOrDefault(commodityId, 0)),
                    140,
                    y + 3,
                    60,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    String.valueOf(mcInv.getOrDefault(commodityId, 0)),
                    210,
                    y + 3,
                    60,
                    FinanceGui.COL_TEXT_DIM);
            y += FinanceGui.ROW_HEIGHT;
        }
    }
}
