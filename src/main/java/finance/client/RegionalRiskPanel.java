package finance.client;

import finance.gameplay.FinanceScreenMode;
import finance.network.*;
import finance.util.MoneyFormat;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 金融服务 · 区域风险：库存质押、套期保值、区域债。由 {@link ServiceTabs} 分发渲染和点击。 */
final class RegionalRiskPanel {
    private final FinanceScreen screen;

    RegionalRiskPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g) {
        var entry = RegionalRiskClientCache.get();
        if (entry.state() == RegionalRiskClientCache.State.NOT_REQUESTED) {
            request();
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在请求区域风险数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (entry.state() == RegionalRiskClientCache.State.LOADING
                || entry.state() == RegionalRiskClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    entry.state() == RegionalRiskClientCache.State.SLOW ? "区域风险服务响应较慢" : "正在加载区域风险数据…",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    350,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        var data = entry.data();
        int y = FinanceGui.CONTENT_Y + 20;
        FinanceGui.drawClippedString(screen.font(), g, "区域商品情报（不含玩家、仓库坐标与路线）", 12, y, 370, FinanceGui.COL_ACCENT);
        y += 14;
        int shown = 0;
        for (var row : data.regions()) {
            if (shown++ >= 2) break;
            String trend = row.trend().isEmpty()
                    ? "-"
                    : row.trend().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("/"));
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.region() + " " + FinanceFormat.commodityDisplayName(row.commodity()) + " "
                            + FinanceFormat.label(row.pressure()) + " 缺" + row.shortageBps() / 100 + "% 溢"
                            + (row.premiumBps() - 10000) / 100 + "% " + (row.reliable() ? "可信" : "仅数量") + " [" + trend
                            + "]",
                    14,
                    y,
                    374,
                    row.pressure() == finance.regional.RegionalSupplyPressure.SHORTAGE
                            ? FinanceGui.COL_BAD
                            : FinanceGui.COL_TEXT);
            y += 14;
        }
        FinanceGui.drawClippedString(screen.font(), g, "库存质押", 12, y, 70, FinanceGui.COL_TEXT_DIM);
        y += 13;
        shown = 0;
        for (var row : data.collateral()) {
            if (shown++ >= 1) break;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.commodityDisplayName(row.commodity()) + " x" + row.quantity() + "  抵押率 "
                            + MoneyFormat.percentBps(row.ltvBps()) + "  " + FinanceFormat.label(row.status()) + "  回收"
                            + row.recovered(),
                    14,
                    y,
                    374,
                    row.status() == finance.collateral.InventoryCollateralStatus.MARGIN_CALL
                            ? FinanceGui.COL_BAD
                            : FinanceGui.COL_TEXT);
            y += 14;
        }
        FinanceGui.drawClippedString(screen.font(), g, "经营对冲（个人保证金账户，仅作公司风险说明）", 12, y, 320, FinanceGui.COL_TEXT_DIM);
        y += 13;
        shown = 0;
        for (var row : data.hedges()) {
            if (shown++ >= 1) break;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.contractCode() + " " + FinanceFormat.label(row.type()) + " 目标" + row.target() + " 覆盖"
                            + MoneyFormat.percentBps(row.coverageBps()) + " " + FinanceFormat.label(row.status()) + " "
                            + FinanceFormat.label(row.marginRisk()),
                    14,
                    y,
                    374,
                    row.marginRisk() == finance.futures.MarginRiskStatus.NORMAL
                            ? FinanceGui.COL_TEXT
                            : FinanceGui.COL_WARN);
            y += 14;
        }
        g.fill(8, 185, screen.imageWidth() - 8, 232, FinanceGui.COL_ROW_EVEN);
        FinanceGui.drawClippedString(screen.font(), g, "金额", 14, 190, 34, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "数量", 126, 190, 34, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "截止天", 238, 190, 45, FinanceGui.COL_TEXT_DIM);
        if (screen.menu().getInitialMode() == FinanceScreenMode.BANK) {
            screen.button(g, "质押", 196, 210, 48, false, () -> act(RegionalRiskActionPacket.Action.APPLY_COLLATERAL));
            screen.button(
                    g, "补押", 248, 210, 48, false, () -> act(RegionalRiskActionPacket.Action.SUPPLEMENT_COLLATERAL));
            screen.button(g, "还款", 300, 210, 48, false, () -> act(RegionalRiskActionPacket.Action.REPAY_COLLATERAL));
        } else {
            screen.button(g, "采购险", 184, 210, 54, false, () -> act(RegionalRiskActionPacket.Action.CREATE_INPUT_HEDGE));
            screen.button(
                    g, "售价险", 242, 210, 54, false, () -> act(RegionalRiskActionPacket.Action.CREATE_OUTPUT_HEDGE));
            screen.button(g, "取消", 300, 210, 48, false, () -> act(RegionalRiskActionPacket.Action.CANCEL_HEDGE));
        }
    }

    private void act(RegionalRiskActionPacket.Action action) {
        var data = RegionalRiskClientCache.get().data();
        if (data == null) return;
        java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.stockPriceBox, "金额"),
                quantity = () -> screen.requireAmount(screen.stockQuantityBox, "数量");
        java.util.function.IntSupplier deadline = () -> screen.requireInt(screen.companyAmountBox, "截止天", 1, 3650);
        String key = UUID.randomUUID().toString();
        RegionalRiskActionPacket packet = null;
        switch (action) {
            case APPLY_COLLATERAL, SUPPLEMENT_COLLATERAL, REPAY_COLLATERAL -> {
                var agreement = data.collateral().stream().findFirst().orElse(null);
                var call = data.collateral().stream()
                        .filter(v -> v.status() == finance.collateral.InventoryCollateralStatus.MARGIN_CALL)
                        .findFirst()
                        .orElse(agreement);
                var bank = data.banks().stream().findFirst().orElse(null);
                if (action == RegionalRiskActionPacket.Action.APPLY_COLLATERAL && bank != null)
                    packet = new RegionalRiskActionPacket(
                            action, bank.id(), screen.selectedCommodity, quantity.getAsLong(), 0, 0, key);
                else if (action == RegionalRiskActionPacket.Action.SUPPLEMENT_COLLATERAL && call != null)
                    packet = new RegionalRiskActionPacket(action, call.id(), "", quantity.getAsLong(), 0, 0, key);
                else if (action == RegionalRiskActionPacket.Action.REPAY_COLLATERAL && agreement != null)
                    packet = new RegionalRiskActionPacket(action, agreement.id(), "", 0, amount.getAsLong(), 0, key);
            }
            default -> {
                var contract = data.contracts().stream()
                        .filter(v -> v.commodity().equals(screen.selectedCommodity))
                        .findFirst()
                        .orElse(data.contracts().stream().findFirst().orElse(null));
                var hedge = data.hedges().stream().findFirst().orElse(null);
                if (action == RegionalRiskActionPacket.Action.CANCEL_HEDGE) {
                    if (hedge != null) packet = new RegionalRiskActionPacket(action, hedge.id(), "", 0, 0, 0, key);
                } else if (contract != null)
                    packet = new RegionalRiskActionPacket(
                            action, contract.id(), "", quantity.getAsLong(), 0, deadline.getAsInt(), key);
            }
        }
        if (packet == null) return;
        FinancePacketHandler.CHANNEL.sendToServer(packet);
        request();
    }

    void request() {
        long requestId = RegionalRiskClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new RegionalRiskRequestPacket(requestId));
    }
}
