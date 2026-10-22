package finance.client;

import finance.chart.MarketInstrumentType;
import finance.gameplay.FinanceScreenMode;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 金融服务：指数 / 发行 / 债市 / 持仓 / 票据 / 贷款 / 期货 / 银行 / 基金 / 保险 / 区域：从 FinanceScreen 抽取的页签行为类。
 * 通过 screen 反向引用共享界面状态；未来做跨模组页签注册表时，此类即天然的注册单元。 */
public final class ServiceTabs {
    private final FinanceScreen screen;

    public ServiceTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderFinancialProductsTab(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.menu().getInitialMode() == FinanceScreenMode.BANK) {
            if (screen.financialSubTab != 10) screen.financialSubTab = 7;
            g.fill(
                    10,
                    FinanceGui.CONTENT_Y - 2,
                    72,
                    FinanceGui.CONTENT_Y + 13,
                    screen.financialSubTab == 7 ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_BUTTON_BG);
            g.fill(
                    76,
                    FinanceGui.CONTENT_Y - 2,
                    138,
                    FinanceGui.CONTENT_Y + 13,
                    screen.financialSubTab == 10 ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_BUTTON_BG);
            FinanceGui.drawClippedString(screen.font(), g, "银行", 28, FinanceGui.CONTENT_Y + 1, 30, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, "库存质押", 82, FinanceGui.CONTENT_Y + 1, 52, FinanceGui.COL_TEXT);
            if (screen.financialSubTab == 10) renderRegionalRiskTab(g);
            else renderBankTab(g);
            return;
        }
        FinancialProductClientCache.Entry data = FinancialProductClientCache.get();
        String[] labels = {"指数", "发行", "债市", "持仓", "票据", "贷款", "期货", "银行", "基金", "保险", "区域"};
        for (int i = 0; i < labels.length; i++) {
            int x = 10 + i * 34;
            g.fill(
                    x,
                    FinanceGui.CONTENT_Y - 2,
                    x + 32,
                    FinanceGui.CONTENT_Y + 13,
                    i == screen.financialSubTab ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_BUTTON_BG);
            FinanceGui.drawClippedString(
                    screen.font(), g, labels[i], x + 3, FinanceGui.CONTENT_Y + 1, 26, FinanceGui.COL_TEXT);
        }
        if (screen.financialSubTab == 6) {
            renderFuturesTab(g, mouseX, mouseY);
            return;
        }
        if (screen.financialSubTab == 7) {
            renderBankTab(g);
            return;
        }
        if (screen.financialSubTab == 8) {
            renderFundTab(g);
            return;
        }
        if (screen.financialSubTab == 9) {
            renderInsuranceTab(g);
            return;
        }
        if (screen.financialSubTab == 10) {
            renderRegionalRiskTab(g);
            return;
        }
        if (data.state() == FinancialProductClientCache.State.NOT_REQUESTED) {
            requestFinancialProducts();
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
        if (screen.financialSubTab == 0) {
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
        } else if (screen.financialSubTab == 1) {
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
        } else if (screen.financialSubTab == 2) {
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
        } else if (screen.financialSubTab == 3) {
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
        } else if (screen.financialSubTab == 4) {
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
        renderFinancialActions(g);
    }

    public void renderFinancialActions(GuiGraphics g) {
        int y = 190;
        g.fill(8, y - 5, screen.imageWidth() - 8, 232, FinanceGui.COL_ROW_EVEN);
        if (screen.financialSubTab == 0) {
            FinanceGui.drawClippedString(
                    screen.font(), g, screen.isAdmin ? "管理员利率(基点)" : "基准利率由管理员调整", 14, y, 140, FinanceGui.COL_TEXT_DIM);
            if (screen.isAdmin)
                FinanceGui.drawFilledButton(screen.font(), g, "改", 365, y - 1, 28, FinanceGui.COL_ACCENT);
        } else if (screen.financialSubTab == 1) {
            FinanceGui.drawClippedString(screen.font(), g, "面值", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "数量", 126, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "票息bp", 238, y, 45, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "发", 365, y - 1, 28, FinanceGui.COL_ACCENT);
            FinanceGui.drawButton(screen.font(), g, "认购首个开放债券", 250, y + 20, 128, false);
        } else if (screen.financialSubTab == 2) {
            FinanceGui.drawClippedString(screen.font(), g, "限价", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "数量", 126, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "买", 365, y - 1, 28, FinanceGui.COL_ACCENT);
            FinanceGui.drawButton(screen.font(), g, "卖首个活跃债", 250, y + 20, 92, false);
            FinanceGui.drawButton(screen.font(), g, "撤我的首单", 344, y + 20, 50, false);
        } else if (screen.financialSubTab == 3) {
            FinanceGui.drawClippedString(screen.font(), g, "持仓成本包含认购与二级买入；票息单独累计", 14, y, 350, FinanceGui.COL_TEXT_DIM);
        } else if (screen.financialSubTab == 4) {
            FinanceGui.drawClippedString(screen.font(), g, "本金", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "期限(7/30/90)", 238, y, 100, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "购", 365, y - 1, 28, FinanceGui.COL_ACCENT);
        } else {
            FinanceGui.drawClippedString(screen.font(), g, "金额", 14, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "期限", 126, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "间隔", 238, y, 34, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "申", 365, y - 1, 28, FinanceGui.COL_ACCENT);
            FinanceGui.drawButton(screen.font(), g, "偿还首笔贷款", 270, y + 20, 108, false);
        }
    }

    public void requestFinancialProducts() {
        long requestId = FinancialProductClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductRequestPacket(requestId));
    }

    public void requestFunds() {
        long requestId = FundClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new FundRequestPacket(requestId));
    }

    public void requestInsurance() {
        long requestId = InsuranceClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new InsuranceRequestPacket(requestId));
    }

    public void requestRegionalRisk() {
        long requestId = RegionalRiskClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new RegionalRiskRequestPacket(requestId));
    }

    public void renderRegionalRiskTab(GuiGraphics g) {
        var entry = RegionalRiskClientCache.get();
        if (entry.state() == RegionalRiskClientCache.State.NOT_REQUESTED) {
            requestRegionalRisk();
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
                            + row.ltvBps() / 100.0 + "%  " + FinanceFormat.label(row.status()) + "  回收"
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
                            + row.coverageBps() / 100.0 + "% " + FinanceFormat.label(row.status()) + " "
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
            FinanceGui.drawButton(screen.font(), g, "质押", 196, 210, 48, false);
            FinanceGui.drawButton(screen.font(), g, "补押", 248, 210, 48, false);
            FinanceGui.drawButton(screen.font(), g, "还款", 300, 210, 48, false);
        } else {
            FinanceGui.drawButton(screen.font(), g, "采购险", 184, 210, 54, false);
            FinanceGui.drawButton(screen.font(), g, "售价险", 242, 210, 54, false);
            FinanceGui.drawButton(screen.font(), g, "取消", 300, 210, 48, false);
        }
    }

    public void renderInsuranceTab(GuiGraphics g) {
        var entry = InsuranceClientCache.get();
        if (entry.state() == InsuranceClientCache.State.NOT_REQUESTED) {
            requestInsurance();
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
        FinanceGui.drawButton(screen.font(), g, "取消", 218, 210, 48, false);
        FinanceGui.drawFilledButton(screen.font(), g, "库存险", 270, 210, 56, FinanceGui.COL_ACCENT);
        FinanceGui.drawFilledButton(screen.font(), g, "中断险", 330, 210, 62, FinanceGui.COL_ACCENT);
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

    public void renderFundTab(GuiGraphics g) {
        var data = FundClientCache.get();
        if (data.state() == FundClientCache.State.NOT_REQUESTED) {
            requestFunds();
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

    public void requestFutures() {
        long requestId = FuturesClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new FuturesRequestPacket(requestId));
    }

    public void requestBanks() {
        long requestId = BankClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new BankRequestPacket(requestId));
    }

    public void renderBankTab(GuiGraphics g) {
        var cache = BankClientCache.get();
        if (cache.state() == BankClientCache.State.NOT_REQUESTED) {
            requestBanks();
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在请求银行数据…", 12, FinanceGui.CONTENT_Y + 28, 350, FinanceGui.COL_TEXT_DIM);
            return;
        }
        if (cache.state() == BankClientCache.State.LOADING || cache.state() == BankClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    cache.state() == BankClientCache.State.SLOW ? "银行服务响应较慢" : "正在加载银行数据…",
                    12,
                    FinanceGui.CONTENT_Y + 28,
                    350,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        var data = cache.data();
        int y = FinanceGui.CONTENT_Y + 20;
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "隔夜利率 " + data.overnightRateBps() + "bp  " + (data.admin() ? "保险基金 " : "保障上限 ") + data.insuranceFund(),
                12,
                y,
                370,
                FinanceGui.COL_TEXT_DIM);
        y += 15;
        FinanceGui.drawClippedString(screen.font(), g, "银行/状态", 12, y, 100, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "存款利率", 116, y, 80, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, data.admin() ? "资本/流动" : "公开风险", 202, y, 86, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, data.admin() ? "准备金/存款" : "公开状态", 294, y, 98, FinanceGui.COL_TEXT_DIM);
        y += 14;
        for (var bank : data.banks()) {
            if (y > 156) break;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    bank.code() + "/" + FinanceFormat.label(bank.status()),
                    12,
                    y,
                    100,
                    bank.status() == finance.bank.BankStatus.ACTIVE ? FinanceGui.COL_GOOD : FinanceGui.COL_WARN);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "活期" + bank.demandRateBps() / 100.0 + "% 定期" + bank.timeRateBps() / 100.0 + "%",
                    116,
                    y,
                    80,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    data.admin() ? bank.capitalBps() + "/" + bank.liquidityBps() : FinanceFormat.label(bank.status()),
                    202,
                    y,
                    86,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    data.admin() ? bank.reserves() + "/" + bank.deposits() : "不公开总账",
                    294,
                    y,
                    98,
                    FinanceGui.COL_TEXT);
            y += 15;
        }
        long demand = data.accounts().stream()
                .filter(a -> a.type() == finance.bank.BankAccountType.DEMAND_DEPOSIT
                        && a.ownerType() == finance.bank.CustomerType.PLAYER)
                .mapToLong(BankResponsePacket.AccountRow::balance)
                .sum();
        long company = data.accounts().stream()
                .filter(a -> a.ownerType() == finance.bank.CustomerType.COMPANY)
                .mapToLong(BankResponsePacket.AccountRow::balance)
                .sum();
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "我的活期 " + demand + "  公司存款 " + company + "  定期 "
                        + data.timeDeposits().size(),
                12,
                166,
                370,
                FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "金额", 50, 181, 70, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "期限", 162, 181, 60, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "间隔", 282, 181, 60, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawButton(screen.font(), g, "转", 54, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "存", 94, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "取", 134, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "定", 174, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "兑", 214, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "贷", 254, 210, 36, false);
        FinanceGui.drawButton(screen.font(), g, "公取", 294, 210, 42, false);
        if (screen.isAdmin) FinanceGui.drawButton(screen.font(), g, "处/压", 340, 210, 48, false);
    }

    public void renderFuturesTab(GuiGraphics g, int mouseX, int mouseY) {
        FuturesClientCache.Entry cache = FuturesClientCache.get();
        if (cache.state() == FuturesClientCache.State.NOT_REQUESTED) {
            requestFutures();
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
            screen.stockTabs.renderCandlestickView(
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
        FinanceGui.drawFilledButton(screen.font(), g, "买", 365, ay - 1, 28, FinanceGui.COL_ACCENT);
        FinanceGui.drawButton(screen.font(), g, "卖", 244, ay + 20, 28, false);
        FinanceGui.drawButton(screen.font(), g, "入", 274, ay + 20, 28, false);
        FinanceGui.drawButton(screen.font(), g, "出", 304, ay + 20, 28, false);
        FinanceGui.drawButton(screen.font(), g, "撤", 334, ay + 20, 28, false);
        if (screen.isAdmin) FinanceGui.drawButton(screen.font(), g, "建", 364, ay + 20, 28, false);
    }

    // ================================================================
    // 点击处理
    // ================================================================

    public boolean handleFinancialProductsClick(int mx, int my) {
        if (screen.menu().getInitialMode() == FinanceScreenMode.BANK
                && my >= FinanceGui.CONTENT_Y - 2
                && my <= FinanceGui.CONTENT_Y + 14) {
            if (mx >= 10 && mx <= 72) screen.financialSubTab = 7;
            else if (mx >= 76 && mx <= 138) screen.financialSubTab = 10;
            else return false;
            screen.updateInputVisibility();
            if (screen.financialSubTab == 10) requestRegionalRisk();
            else requestBanks();
            return true;
        }
        if (screen.menu().getInitialMode() != FinanceScreenMode.BANK
                && my >= FinanceGui.CONTENT_Y - 2
                && my <= FinanceGui.CONTENT_Y + 14) {
            for (int i = 0; i < 11; i++) {
                int x = 10 + i * 34;
                if (mx >= x && mx <= x + 32) {
                    screen.financialSubTab = i;
                    if (i != 6) screen.futuresChartVisible = false;
                    screen.updateInputVisibility();
                    if (i == 6) requestFutures();
                    else if (i == 7) requestBanks();
                    else if (i == 8) requestFunds();
                    else if (i == 9) requestInsurance();
                    else if (i == 10) requestRegionalRisk();
                    else requestFinancialProducts();
                    return true;
                }
            }
        }
        if (screen.financialSubTab == 6) {
            var cache = FuturesClientCache.get();
            var data = cache.data();
            if (data == null) return false;
            var chartContract = data.contracts().stream().findFirst().orElse(null);
            if (screen.futuresChartVisible && chartContract != null)
                return screen.stockTabs.handleCandlestickControls(
                        mx, my, MarketInstrumentType.FUTURES, chartContract.id().toString());
            if (my >= FinanceGui.CONTENT_Y + 22 && my < 188 && chartContract != null) {
                screen.futuresChartVisible = true;
                screen.stockTabs.requestCandlesticks(
                        MarketInstrumentType.FUTURES, chartContract.id().toString());
                screen.updateInputVisibility();
                return true;
            }
            var contract = data.contracts().stream()
                    .filter(c -> c.status() == finance.futures.FuturesContractStatus.TRADING
                            || c.status() == finance.futures.FuturesContractStatus.LAST_TRADING_DAY)
                    .findFirst()
                    .orElse(null);
            java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.stockPriceBox, "金额/限价"),
                    quantity = () -> screen.requireAmount(screen.stockQuantityBox, "数量");
            java.util.function.IntSupplier term = () -> screen.requireInt(screen.companyAmountBox, "期限", 2, 3650);
            if (my >= 188 && my <= 208 && mx >= 365 && contract != null) {
                FinancePacketHandler.CHANNEL.sendToServer(new FuturesActionPacket(
                        FuturesActionPacket.Action.PLACE_BUY,
                        contract.id(),
                        "",
                        0,
                        amount.getAsLong(),
                        quantity.getAsLong(),
                        0));
                requestFutures();
                return true;
            }
            if (my >= 208 && my <= 230) {
                if (mx >= 244 && mx < 274 && contract != null)
                    FinancePacketHandler.CHANNEL.sendToServer(new FuturesActionPacket(
                            FuturesActionPacket.Action.PLACE_SELL,
                            contract.id(),
                            "",
                            0,
                            amount.getAsLong(),
                            quantity.getAsLong(),
                            0));
                else if (mx >= 274 && mx < 304)
                    FinancePacketHandler.CHANNEL.sendToServer(new FuturesActionPacket(
                            FuturesActionPacket.Action.DEPOSIT_MARGIN, null, "", amount.getAsLong(), 0, 0, 0));
                else if (mx >= 304 && mx < 334)
                    FinancePacketHandler.CHANNEL.sendToServer(new FuturesActionPacket(
                            FuturesActionPacket.Action.WITHDRAW_MARGIN, null, "", amount.getAsLong(), 0, 0, 0));
                else if (mx >= 334 && mx < 364) {
                    var own = data.orders().stream()
                            .filter(o -> o.owned())
                            .findFirst()
                            .orElse(null);
                    if (own == null) return false;
                    FinancePacketHandler.CHANNEL.sendToServer(new FuturesActionPacket(
                            FuturesActionPacket.Action.CANCEL_ORDER, own.orderId(), "", 0, 0, 0, 0));
                } else if (mx >= 364 && screen.isAdmin)
                    FinancePacketHandler.CHANNEL.sendToServer(new FuturesActionPacket(
                            FuturesActionPacket.Action.CREATE_CONTRACT,
                            null,
                            screen.selectedCommodity,
                            0,
                            0,
                            0,
                            term.getAsInt()));
                else return false;
                requestFutures();
                return true;
            }
        }
        if (screen.financialSubTab == 7) {
            var cache = BankClientCache.get();
            var data = cache.data();
            if (data == null) return false;
            var bank = data.banks().stream()
                    .filter(b ->
                            b.status() == finance.bank.BankStatus.ACTIVE || b.status() == finance.bank.BankStatus.WATCH)
                    .findFirst()
                    .orElse(null);
            var demands = data.accounts().stream()
                    .filter(a -> a.ownerType() == finance.bank.CustomerType.PLAYER
                            && a.type() == finance.bank.BankAccountType.DEMAND_DEPOSIT)
                    .toList();
            var demand = demands.stream().findFirst().orElse(null);
            var transferTarget = demands.stream()
                    .filter(a -> demand != null && !a.id().equals(demand.id()))
                    .findFirst()
                    .orElse(null);
            var matured = data.timeDeposits().stream()
                    .filter(t -> t.status() == finance.bank.TimeDepositStatus.MATURED)
                    .findFirst()
                    .orElse(null);
            java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.stockPriceBox, "金额");
            java.util.function.IntSupplier term = () -> screen.requireInt(screen.stockQuantityBox, "期限", 2, 3650),
                    interval = () -> screen.requireInt(screen.companyAmountBox, "间隔", 1, 3650);
            if (my >= 208 && my <= 230) {
                BankActionPacket packet;
                if (mx >= 54 && mx < 90 && demand != null && transferTarget != null)
                    packet = new BankActionPacket(
                            BankActionPacket.Action.TRANSFER,
                            demand.id(),
                            transferTarget.id(),
                            amount.getAsLong(),
                            0,
                            0);
                else if (mx >= 94 && mx < 130 && bank != null)
                    packet = new BankActionPacket(
                            BankActionPacket.Action.DEPOSIT, bank.id(), null, amount.getAsLong(), 0, 0);
                else if (mx >= 134 && mx < 170 && demand != null)
                    packet = new BankActionPacket(
                            BankActionPacket.Action.WITHDRAW, demand.id(), null, amount.getAsLong(), 0, 0);
                else if (mx >= 174 && mx < 210 && demand != null)
                    packet = new BankActionPacket(
                            BankActionPacket.Action.OPEN_TIME,
                            demand.id(),
                            null,
                            amount.getAsLong(),
                            term.getAsInt(),
                            0);
                else if (mx >= 214 && mx < 250 && matured != null)
                    packet = new BankActionPacket(BankActionPacket.Action.REDEEM_TIME, matured.id(), null, 0, 0, 0);
                else if (mx >= 254 && mx < 290 && bank != null)
                    packet = new BankActionPacket(
                            BankActionPacket.Action.APPLY_COMPANY_LOAN,
                            bank.id(),
                            null,
                            amount.getAsLong(),
                            term.getAsInt(),
                            Math.min(interval.getAsInt(), term.getAsInt() - 1));
                else if (mx >= 294 && mx < 336 && bank != null)
                    packet = new BankActionPacket(
                            BankActionPacket.Action.WITHDRAW_COMPANY, bank.id(), null, amount.getAsLong(), 0, 0);
                else if (mx >= 340 && screen.isAdmin) {
                    var target = data.banks().stream()
                            .filter(b -> b.status() == finance.bank.BankStatus.RESOLUTION)
                            .findFirst()
                            .orElse(null);
                    packet = target == null
                            ? new BankActionPacket(BankActionPacket.Action.ADMIN_STRESS_TEST, null, null, 0, 0, 0)
                            : new BankActionPacket(BankActionPacket.Action.ADMIN_RESOLVE, target.id(), null, 0, 0, 0);
                } else return false;
                FinancePacketHandler.CHANNEL.sendToServer(packet);
                requestBanks();
                return true;
            }
        }
        if (screen.financialSubTab == 8) {
            var data = FundClientCache.get();
            if (data.state() != FundClientCache.State.READY || data.funds().isEmpty()) return false;
            var fund = data.funds().get(0);
            java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.stockPriceBox, "金额"),
                    shares =
                            () -> screen.requireLong(
                                    screen.stockQuantityBox, "份额", 100, FinanceScreen.MAX_INPUT_AMOUNT);
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
                requestFunds();
                return true;
            }
            if (my >= 210 && my <= 232 && mx < 270) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new FundActionPacket(FundActionPacket.Action.ACKNOWLEDGE_RISK, fund.id(), null, 0, 0, 0, ""));
                return true;
            }
        }
        if (screen.financialSubTab == 9 && my >= 208 && my <= 232) {
            var entry = InsuranceClientCache.get();
            if (mx >= 218
                    && mx < 266
                    && entry.data() != null
                    && !entry.data().policies().isEmpty()) {
                var policy = entry.data().policies().get(0);
                FinancePacketHandler.CHANNEL.sendToServer(new InsuranceActionPacket(
                        InsuranceActionPacket.Action.CANCEL,
                        policy.product(),
                        policy.id(),
                        0,
                        0,
                        UUID.randomUUID().toString()));
                requestInsurance();
                return true;
            }
            var company = screen.menu().getPlayerCompany();
            if (company == null) return false;
            finance.insurance.InsuranceProduct product = mx >= 330
                    ? finance.insurance.InsuranceProduct.BUSINESS_INTERRUPTION
                    : finance.insurance.InsuranceProduct.INVENTORY_DISASTER;
            long coverage = screen.requireAmount(screen.stockPriceBox, "保额");
            int term = screen.requireInt(screen.stockQuantityBox, "期限(天)", 2, 365);
            FinancePacketHandler.CHANNEL.sendToServer(new InsuranceActionPacket(
                    InsuranceActionPacket.Action.PURCHASE,
                    product,
                    company.companyId(),
                    coverage,
                    term,
                    UUID.randomUUID().toString()));
            requestInsurance();
            return true;
        }
        if (screen.financialSubTab == 10 && my >= 208 && my <= 232) {
            var entry = RegionalRiskClientCache.get();
            var data = entry.data();
            if (data == null) return false;
            java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.stockPriceBox, "金额"),
                    quantity = () -> screen.requireAmount(screen.stockQuantityBox, "数量");
            java.util.function.IntSupplier deadline = () -> screen.requireInt(screen.companyAmountBox, "截止天", 1, 3650);
            RegionalRiskActionPacket packet;
            if (screen.menu().getInitialMode() == FinanceScreenMode.BANK) {
                var agreement = data.collateral().stream().findFirst().orElse(null);
                var call = data.collateral().stream()
                        .filter(v -> v.status() == finance.collateral.InventoryCollateralStatus.MARGIN_CALL)
                        .findFirst()
                        .orElse(agreement);
                var bank = data.banks().stream().findFirst().orElse(null);
                if (mx >= 196 && mx < 244 && bank != null)
                    packet = new RegionalRiskActionPacket(
                            RegionalRiskActionPacket.Action.APPLY_COLLATERAL,
                            bank.id(),
                            screen.selectedCommodity,
                            quantity.getAsLong(),
                            0,
                            0,
                            UUID.randomUUID().toString());
                else if (mx >= 248 && mx < 296 && call != null)
                    packet = new RegionalRiskActionPacket(
                            RegionalRiskActionPacket.Action.SUPPLEMENT_COLLATERAL,
                            call.id(),
                            "",
                            quantity.getAsLong(),
                            0,
                            0,
                            UUID.randomUUID().toString());
                else if (mx >= 300 && mx < 348 && agreement != null)
                    packet = new RegionalRiskActionPacket(
                            RegionalRiskActionPacket.Action.REPAY_COLLATERAL,
                            agreement.id(),
                            "",
                            0,
                            amount.getAsLong(),
                            0,
                            UUID.randomUUID().toString());
                else return false;
            } else {
                var contract = data.contracts().stream()
                        .filter(v -> v.commodity().equals(screen.selectedCommodity))
                        .findFirst()
                        .orElse(data.contracts().stream().findFirst().orElse(null));
                var hedge = data.hedges().stream().findFirst().orElse(null);
                if (mx >= 184 && mx < 238 && contract != null)
                    packet = new RegionalRiskActionPacket(
                            RegionalRiskActionPacket.Action.CREATE_INPUT_HEDGE,
                            contract.id(),
                            "",
                            quantity.getAsLong(),
                            0,
                            deadline.getAsInt(),
                            UUID.randomUUID().toString());
                else if (mx >= 242 && mx < 296 && contract != null)
                    packet = new RegionalRiskActionPacket(
                            RegionalRiskActionPacket.Action.CREATE_OUTPUT_HEDGE,
                            contract.id(),
                            "",
                            quantity.getAsLong(),
                            0,
                            deadline.getAsInt(),
                            UUID.randomUUID().toString());
                else if (mx >= 300 && mx < 348 && hedge != null)
                    packet = new RegionalRiskActionPacket(
                            RegionalRiskActionPacket.Action.CANCEL_HEDGE,
                            hedge.id(),
                            "",
                            0,
                            0,
                            0,
                            UUID.randomUUID().toString());
                else return false;
            }
            FinancePacketHandler.CHANNEL.sendToServer(packet);
            requestRegionalRisk();
            return true;
        }
        if (my >= 188 && my <= 208 && mx >= 365) {
            // 利率上限 20000 基点、期限/间隔上限 3650 天与服务端解码器一致，越界会被直接断开连接
            FinancialProductActionPacket packet;
            if (screen.financialSubTab == 0 && screen.isAdmin) {
                packet = new FinancialProductActionPacket(
                        FinancialProductActionPacket.Action.SET_BENCHMARK_RATE,
                        null,
                        0,
                        0,
                        screen.requireInt(screen.companyAmountBox, "利率(基点)", 0, 20_000),
                        0,
                        0);
            } else if (screen.financialSubTab == 1) {
                packet = new FinancialProductActionPacket(
                        FinancialProductActionPacket.Action.ISSUE_BOND,
                        null,
                        screen.requireAmount(screen.stockPriceBox, "面值"),
                        screen.requireAmount(screen.stockQuantityBox, "数量"),
                        screen.requireInt(screen.companyAmountBox, "票息bp", 1, 20_000),
                        30,
                        7);
            } else if (screen.financialSubTab == 2) {
                var target = FinancialProductClientCache.get().bonds().stream()
                        .filter(b -> b.status() == finance.debt.BondStatus.ACTIVE)
                        .findFirst()
                        .orElse(null);
                if (target == null) return false;
                packet = new FinancialProductActionPacket(
                        FinancialProductActionPacket.Action.PLACE_BOND_BUY,
                        target.id(),
                        screen.requireAmount(screen.stockPriceBox, "限价"),
                        screen.requireAmount(screen.stockQuantityBox, "数量"),
                        0,
                        0,
                        0);
            } else if (screen.financialSubTab == 4) {
                packet = new FinancialProductActionPacket(
                        FinancialProductActionPacket.Action.SUBSCRIBE_CENTRAL_BANK_BILL,
                        null,
                        screen.requireAmount(screen.stockPriceBox, "本金"),
                        0,
                        0,
                        screen.requireInt(screen.companyAmountBox, "期限", 1, 3650),
                        0);
            } else if (screen.financialSubTab == 5) {
                packet = new FinancialProductActionPacket(
                        FinancialProductActionPacket.Action.APPLY_LOAN,
                        null,
                        screen.requireAmount(screen.stockPriceBox, "金额"),
                        0,
                        0,
                        screen.requireInt(screen.stockQuantityBox, "期限", 1, 365),
                        screen.requireInt(screen.companyAmountBox, "间隔", 1, 3650));
            } else return false;
            FinancePacketHandler.CHANNEL.sendToServer(packet);
            return true;
        }
        if (my >= 208 && my <= 230) {
            FinancialProductClientCache.Entry data = FinancialProductClientCache.get();
            if (screen.financialSubTab == 1 && !data.bonds().isEmpty()) {
                FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                        FinancialProductActionPacket.Action.SUBSCRIBE_BOND,
                        data.bonds().get(0).id(),
                        0,
                        screen.requireAmount(screen.stockQuantityBox, "数量"),
                        0,
                        0,
                        0));
                return true;
            }
            if (screen.financialSubTab == 2) {
                var target = data.bonds().stream()
                        .filter(b -> b.status() == finance.debt.BondStatus.ACTIVE && b.availableQuantity() > 0)
                        .findFirst()
                        .orElse(null);
                if (target != null && mx < 344) {
                    FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                            FinancialProductActionPacket.Action.PLACE_BOND_SELL,
                            target.id(),
                            screen.requireAmount(screen.stockPriceBox, "限价"),
                            screen.requireAmount(screen.stockQuantityBox, "数量"),
                            0,
                            0,
                            0));
                    return true;
                }
                var own = data.bondOrders().stream()
                        .filter(o -> o.ownedByPlayer())
                        .findFirst()
                        .orElse(null);
                if (own != null) {
                    FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                            FinancialProductActionPacket.Action.CANCEL_BOND_ORDER, own.orderId(), 0, 0, 0, 0, 0));
                    return true;
                }
            }
            if (screen.financialSubTab == 5 && !data.loans().isEmpty()) {
                FinancePacketHandler.CHANNEL.sendToServer(new FinancialProductActionPacket(
                        FinancialProductActionPacket.Action.REPAY_LOAN,
                        data.loans().get(0).id(),
                        screen.requireAmount(screen.stockPriceBox, "金额"),
                        0,
                        0,
                        0,
                        0));
                return true;
            }
        }
        return false;
    }
}
