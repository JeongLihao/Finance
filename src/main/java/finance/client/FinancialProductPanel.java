package finance.client;

import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 金融服务 · 指数、发行、债市、持仓、票据、贷款（共用金融产品数据）。由 {@link ServiceTabs} 分发渲染和点击。 */
final class FinancialProductPanel {
    private final FinanceScreen screen;

    FinancialProductPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g) {
        FinancialProductClientCache.Entry data = FinancialProductClientCache.get();
        if (data.state() == FinancialProductClientCache.State.NOT_REQUESTED) {
            request();
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在请求服务端数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (data.state() == FinancialProductClientCache.State.LOADING
                || data.state() == FinancialProductClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    data.state() == FinancialProductClientCache.State.SLOW ? "服务端响应较慢，可重新进入此页刷新" : "正在加载…",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    350,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        int y = FinanceGui.CONTENT_Y + 24;
        if (screen.financialSubTab == ServiceTabs.INDEX) {
            FinanceGui.drawClippedString(screen.font(), g, "名称", 14, y, 150, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "点位", 180, y, 70, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "日涨跌", 270, y, 70, FinanceGui.COL_TEXT_DIM);
            y += 15;
            for (var row : data.indices()) {
                if (y > 174) break;
                FinanceGui.drawClippedString(screen.font(), g, row.id(), 14, y, 150, FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        String.format(Locale.ROOT, "%.2f", row.value()),
                        180,
                        y,
                        75,
                        FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        String.format(Locale.ROOT, "%+.2f%%", row.changePercent()),
                        270,
                        y,
                        70,
                        row.changePercent() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
                y += 16;
            }
        } else if (screen.financialSubTab == ServiceTabs.ISSUE) {
            FinanceGui.drawClippedString(screen.font(), g, "代码/状态", 14, y, 115, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "认购进度", 145, y, 100, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "票息/到期", 265, y, 105, FinanceGui.COL_TEXT_DIM);
            y += 15;
            for (var row : data.bonds()) {
                if (y > 174) break;
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        row.code() + " " + FinanceFormat.label(row.status()),
                        14,
                        y,
                        120,
                        FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(), g, row.subscribed() + "/" + row.total(), 145, y, 100, FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        String.format(Locale.ROOT, "%.2f%% / D%d", row.couponBps() / 100.0, row.maturityDay()),
                        265,
                        y,
                        105,
                        FinanceGui.COL_TEXT);
                y += 16;
            }
        } else if (screen.financialSubTab == ServiceTabs.BOND_MARKET) {
            FinanceGui.drawClippedString(screen.font(), g, "代码", 14, y, 55, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "市价/参考", 78, y, 95, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "收益率", 184, y, 60, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "盘口", 254, y, 125, FinanceGui.COL_TEXT_DIM);
            y += 15;
            for (var row : data.bonds()) {
                if (y > 174) break;
                if (row.status() != finance.debt.BondStatus.ACTIVE) continue;
                long bid = data.bondOrders().stream()
                        .filter(o -> o.bondId().equals(row.id()) && o.side() == finance.bondmarket.BondOrderSide.BUY)
                        .mapToLong(o -> o.price())
                        .max()
                        .orElse(0);
                long ask = data.bondOrders().stream()
                        .filter(o -> o.bondId().equals(row.id()) && o.side() == finance.bondmarket.BondOrderSide.SELL)
                        .mapToLong(o -> o.price())
                        .min()
                        .orElse(0);
                FinanceGui.drawClippedString(screen.font(), g, row.code(), 14, y, 55, FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        row.marketPrice() + "/" + row.referencePrice(),
                        78,
                        y,
                        95,
                        FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        String.format(
                                Locale.ROOT,
                                "%.2f/%.2f%%",
                                row.marketYieldBps() / 100.0,
                                row.referenceYieldBps() / 100.0),
                        184,
                        y,
                        60,
                        FinanceGui.COL_ACCENT);
                FinanceGui.drawClippedString(screen.font(), g, bid + " / " + ask, 254, y, 125, FinanceGui.COL_TEXT);
                y += 16;
            }
        } else if (screen.financialSubTab == ServiceTabs.HOLDINGS) {
            FinanceGui.drawClippedString(screen.font(), g, "代码", 14, y, 55, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "可用/冻结", 78, y, 90, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "成本/市价", 180, y, 95, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "浮盈/应计", 285, y, 100, FinanceGui.COL_TEXT_DIM);
            y += 15;
            for (var row : data.bonds()) {
                if (y > 174) break;
                if (row.playerQuantity() <= 0) continue;
                FinanceGui.drawClippedString(screen.font(), g, row.code(), 14, y, 55, FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        row.availableQuantity() + "/" + row.frozenQuantity(),
                        78,
                        y,
                        90,
                        FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(), g, row.averageCost() + "/" + row.marketPrice(), 180, y, 95, FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        row.unrealizedProfit() + "/" + row.accruedInterest(),
                        285,
                        y,
                        100,
                        row.unrealizedProfit() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
                y += 16;
            }
        } else if (screen.financialSubTab == ServiceTabs.NOTES) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "收益率曲线 7日 " + String.format(Locale.ROOT, "%.2f%%", data.yield7Bps() / 100.0) + "  30日 "
                            + String.format(Locale.ROOT, "%.2f%%", data.yield30Bps() / 100.0) + "  90日 "
                            + String.format(Locale.ROOT, "%.2f%%", data.yield90Bps() / 100.0),
                    14,
                    y,
                    370,
                    FinanceGui.COL_ACCENT);
            y += 18;
            for (var row : data.bills()) {
                if (y > 174) break;
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        row.termDays() + "日 " + FinanceFormat.label(row.status()) + " 第" + row.maturityDay() + "天到期",
                        14,
                        y,
                        150,
                        FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        "本金 " + row.principal() + " 预计 " + row.expectedValue(),
                        175,
                        y,
                        200,
                        FinanceGui.COL_TEXT);
                y += 16;
            }
        } else {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "基准利率 " + String.format(Locale.ROOT, "%.2f%%", data.benchmarkRateBps() / 100.0),
                    14,
                    y,
                    160,
                    FinanceGui.COL_ACCENT);
            y += 16;
            FinanceGui.drawClippedString(screen.font(), g, data.riskSummary(), 14, y, 360, FinanceGui.COL_WARN);
            y += 20;
            for (var row : data.loans()) {
                if (y > 174) break;
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        FinanceFormat.label(row.status()) + " 余额 " + row.outstanding() + " 利息 " + row.interest(),
                        14,
                        y,
                        230,
                        FinanceGui.COL_TEXT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        String.format(Locale.ROOT, "%.2f%% / D%d", row.rateBps() / 100.0, row.maturityDay()),
                        255,
                        y,
                        115,
                        FinanceGui.COL_TEXT);
                y += 16;
            }
        }
        renderActions(g);
    }

    /** 底部操作区（输入框标签和按钮）。 */
    void renderActions(GuiGraphics g) {
        int y = 190;
        g.fill(8, y - 5, screen.imageWidth() - 8, 232, FinanceGui.COL_ROW_EVEN);
        if (screen.financialSubTab == ServiceTabs.INDEX) {
            FinanceGui.drawClippedString(
                    screen.font(), g, screen.isAdmin ? "管理员利率(基点)" : "基准利率由管理员调整", 14, y, 140, FinanceGui.COL_TEXT_DIM);
            if (screen.isAdmin) screen.filledButton(g, "改", 365, y - 1, 28, FinanceGui.COL_ACCENT, this::primary);
        } else if (screen.financialSubTab == ServiceTabs.ISSUE) {
            FinanceGui.drawClippedString(screen.font(), g, "面值", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "数量", 126, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "票息bp", 238, y, 45, FinanceGui.COL_TEXT_DIM);
            screen.filledButton(g, "发", 365, y - 1, 28, FinanceGui.COL_ACCENT, this::primary);
            screen.button(g, "认购首个开放债券", 250, y + 20, 128, false, this::subscribeFirstBond);
        } else if (screen.financialSubTab == ServiceTabs.BOND_MARKET) {
            FinanceGui.drawClippedString(screen.font(), g, "限价", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "数量", 126, y, 34, FinanceGui.COL_TEXT_DIM);
            screen.filledButton(g, "买", 365, y - 1, 28, FinanceGui.COL_ACCENT, this::primary);
            screen.button(g, "卖首个活跃债", 250, y + 20, 92, false, this::sellFirstBond);
            screen.button(g, "撤我的首单", 344, y + 20, 50, false, this::cancelOwnBondOrder);
        } else if (screen.financialSubTab == ServiceTabs.HOLDINGS) {
            FinanceGui.drawClippedString(screen.font(), g, "持仓成本包含认购与二级买入；票息单独累计", 14, y, 350, FinanceGui.COL_TEXT_DIM);
        } else if (screen.financialSubTab == ServiceTabs.NOTES) {
            FinanceGui.drawClippedString(screen.font(), g, "本金", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "期限(7/30/90)", 238, y, 100, FinanceGui.COL_TEXT_DIM);
            screen.filledButton(g, "购", 365, y - 1, 28, FinanceGui.COL_ACCENT, this::primary);
        } else {
            FinanceGui.drawClippedString(screen.font(), g, "金额", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "期限", 126, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "间隔", 238, y, 34, FinanceGui.COL_TEXT_DIM);
            screen.filledButton(g, "申", 365, y - 1, 28, FinanceGui.COL_ACCENT, this::primary);
            screen.button(g, "偿还首笔贷款", 270, y + 20, 108, false, this::repayFirstLoan);
        }
    }

    /** 右上角主按钮：按子页签改利率、发债、买债、购央票或申请贷款。 */
    private void primary() {
        // 利率上限 20000 基点、期限/间隔上限 3650 天与服务端解码器一致，越界会被直接断开连接
        FinancialProductActionPacket packet;
        if (screen.financialSubTab == ServiceTabs.INDEX && screen.isAdmin) {
            packet = new FinancialProductActionPacket(
                    FinancialProductActionPacket.Action.SET_BENCHMARK_RATE,
                    null,
                    0,
                    0,
                    screen.requireInt(screen.companyAmountBox, "利率(基点)", 0, 20_000),
                    0,
                    0);
        } else if (screen.financialSubTab == ServiceTabs.ISSUE) {
            packet = new FinancialProductActionPacket(
                    FinancialProductActionPacket.Action.ISSUE_BOND,
                    null,
                    screen.requireAmount(screen.stockPriceBox, "面值"),
                    screen.requireAmount(screen.stockQuantityBox, "数量"),
                    screen.requireInt(screen.companyAmountBox, "票息bp", 1, 20_000),
                    30,
                    7);
        } else if (screen.financialSubTab == ServiceTabs.BOND_MARKET) {
            var target = FinancialProductClientCache.get().bonds().stream()
                    .filter(b -> b.status() == finance.debt.BondStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);
            if (target == null) return;
            packet = new FinancialProductActionPacket(
                    FinancialProductActionPacket.Action.PLACE_BOND_BUY,
                    target.id(),
                    screen.requireAmount(screen.stockPriceBox, "限价"),
                    screen.requireAmount(screen.stockQuantityBox, "数量"),
                    0,
                    0,
                    0);
        } else if (screen.financialSubTab == ServiceTabs.NOTES) {
            packet = new FinancialProductActionPacket(
                    FinancialProductActionPacket.Action.SUBSCRIBE_CENTRAL_BANK_BILL,
                    null,
                    screen.requireAmount(screen.stockPriceBox, "本金"),
                    0,
                    0,
                    screen.requireInt(screen.companyAmountBox, "期限", 1, 3650),
                    0);
        } else if (screen.financialSubTab == ServiceTabs.LOANS) {
            packet = new FinancialProductActionPacket(
                    FinancialProductActionPacket.Action.APPLY_LOAN,
                    null,
                    screen.requireAmount(screen.stockPriceBox, "金额"),
                    0,
                    0,
                    screen.requireInt(screen.stockQuantityBox, "期限", 1, 365),
                    screen.requireInt(screen.companyAmountBox, "间隔", 1, 3650));
        } else return;
        FinancePacketHandler.CHANNEL.sendToServer(packet);
    }

    private void subscribeFirstBond() {
        FinancialProductClientCache.Entry data = FinancialProductClientCache.get();
        if (data.bonds().isEmpty()) return;
        FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                FinancialProductActionPacket.Action.SUBSCRIBE_BOND,
                data.bonds().get(0).id(),
                0,
                screen.requireAmount(screen.stockQuantityBox, "数量"),
                0,
                0,
                0));
    }

    private void sellFirstBond() {
        var target = FinancialProductClientCache.get().bonds().stream()
                .filter(b -> b.status() == finance.debt.BondStatus.ACTIVE && b.availableQuantity() > 0)
                .findFirst()
                .orElse(null);
        if (target == null) return;
        FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                FinancialProductActionPacket.Action.PLACE_BOND_SELL,
                target.id(),
                screen.requireAmount(screen.stockPriceBox, "限价"),
                screen.requireAmount(screen.stockQuantityBox, "数量"),
                0,
                0,
                0));
    }

    private void cancelOwnBondOrder() {
        var own = FinancialProductClientCache.get().bondOrders().stream()
                .filter(o -> o.ownedByPlayer())
                .findFirst()
                .orElse(null);
        if (own == null) return;
        FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                FinancialProductActionPacket.Action.CANCEL_BOND_ORDER, own.orderId(), 0, 0, 0, 0, 0));
    }

    private void repayFirstLoan() {
        FinancialProductClientCache.Entry data = FinancialProductClientCache.get();
        if (data.loans().isEmpty()) return;
        FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                FinancialProductActionPacket.Action.REPAY_LOAN,
                data.loans().get(0).id(),
                screen.requireAmount(screen.stockPriceBox, "金额"),
                0,
                0,
                0,
                0));
    }

    void request() {
        long requestId = FinancialProductClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductRequestPacket(requestId));
    }
}
