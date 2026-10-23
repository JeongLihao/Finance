package finance.client;

import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 金融服务 · 保险：投保、续保、理赔。由 {@link ServiceTabs} 分发渲染和点击。 */
final class InsurancePanel {
    private final FinanceScreen screen;

    InsurancePanel(FinanceScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g) {
        var entry = InsuranceClientCache.get();
        if (entry.state() == InsuranceClientCache.State.NOT_REQUESTED) {
            request();
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在请求保险数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (entry.state() == InsuranceClientCache.State.LOADING || entry.state() == InsuranceClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    entry.state() == InsuranceClientCache.State.SLOW ? "保险服务响应较慢" : "正在加载保险数据…",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    350,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        var data = entry.data();
        int y = FinanceGui.CONTENT_Y + 22;
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "风险 " + FinanceFormat.label(data.risk().level()) + " | "
                        + data.risk().reasons(),
                12,
                y,
                370,
                FinanceGui.COL_WARN);
        y += 15;
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "库存 " + data.risk().inventoryValue() + "  已保 " + data.risk().insuredInventory() + "  待赔 "
                        + data.risk().pendingClaims(),
                12,
                y,
                370,
                FinanceGui.COL_TEXT_DIM);
        y += 17;
        FinanceGui.drawClippedString(screen.font(), g, "产品/状态", 12, y, 120, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "保额/剩余", 142, y, 120, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "生效/到期", 275, y, 110, FinanceGui.COL_TEXT_DIM);
        y += 14;
        for (var row : data.policies()) {
            if (y > 150) break;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.label(row.product()) + "/" + FinanceFormat.label(row.status()),
                    12,
                    y,
                    120,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, row.coverage() + "/" + row.remaining(), 142, y, 120, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "第" + row.effective() + "-" + row.expiry() + "天",
                    275,
                    y,
                    110,
                    FinanceGui.COL_TEXT_DIM);
            y += 15;
        }
        for (var row : data.claims()) {
            if (y > 174) break;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "理赔 " + FinanceFormat.label(row.status()),
                    12,
                    y,
                    100,
                    row.status() == finance.insurance.ClaimStatus.PAID ? FinanceGui.COL_GOOD : FinanceGui.COL_WARN);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.paid() + "/" + row.approved() + " 损失 " + row.loss(),
                    118,
                    y,
                    170,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, row.reason(), 294, y, 92, FinanceGui.COL_TEXT_DIM);
            y += 15;
        }
        g.fill(8, 185, screen.imageWidth() - 8, 232, FinanceGui.COL_ROW_EVEN);
        FinanceGui.drawClippedString(screen.font(), g, "保额", 14, 190, 55, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "期限(天)", 126, 190, 70, FinanceGui.COL_TEXT_DIM);
        screen.button(g, "取消", 218, 210, 48, false, this::cancelFirst);
        screen.filledButton(
                g,
                "库存险",
                270,
                210,
                56,
                FinanceGui.COL_ACCENT,
                () -> purchase(finance.insurance.InsuranceProduct.INVENTORY_DISASTER));
        screen.filledButton(
                g,
                "中断险",
                330,
                210,
                62,
                FinanceGui.COL_ACCENT,
                () -> purchase(finance.insurance.InsuranceProduct.BUSINESS_INTERRUPTION));
        if (data.admin())
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "资金池 " + data.poolCash() + " 敞口 " + data.exposure() + " 未付 " + data.unpaid(),
                    14,
                    215,
                    194,
                    FinanceGui.COL_TEXT_DIM);
    }

    private void cancelFirst() {
        var entry = InsuranceClientCache.get();
        if (entry.data() == null || entry.data().policies().isEmpty()) return;
        var policy = entry.data().policies().get(0);
        FinancePacketHandler.CHANNEL.sendToServer(new InsuranceActionPacket(
                InsuranceActionPacket.Action.CANCEL,
                policy.product(),
                policy.id(),
                0,
                0,
                UUID.randomUUID().toString()));
        request();
    }

    private void purchase(finance.insurance.InsuranceProduct product) {
        var company = screen.menu().getPlayerCompany();
        if (company == null) return;
        long coverage = screen.requireAmount(screen.stockPriceBox, "保额");
        int term = screen.requireInt(screen.stockQuantityBox, "期限(天)", 2, 365);
        FinancePacketHandler.CHANNEL.sendToServer(new InsuranceActionPacket(
                InsuranceActionPacket.Action.PURCHASE,
                product,
                company.companyId(),
                coverage,
                term,
                UUID.randomUUID().toString()));
        request();
    }

    void request() {
        long requestId = InsuranceClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new InsuranceRequestPacket(requestId));
    }
}
