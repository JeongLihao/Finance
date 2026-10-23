package finance.client;

import finance.company.CompanyManagementAction;
import finance.company.CompanyStrategy;
import finance.gui.CompanyRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 公司页 · 我的公司主卡片：策略、出售比例、升级、注资提取、IPO 和增发融资，以及去重组、财报提案、治理的入口。由 {@link CompanyTabs} 分发渲染和点击。 */
final class CompanyManagePanel {
    private final FinanceScreen screen;
    private final CompanyTabs tabs;

    CompanyManagePanel(FinanceScreen screen, CompanyTabs tabs) {
        this.screen = screen;
        this.tabs = tabs;
    }

    void render(GuiGraphics g, CompanyRows.CompanyInfo company, int cardY) {
        int cardH = screen.imageHeight() - cardY - 8;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawButton(screen.font(), g, "重组", 224, cardY + 14, 48, false);
        FinanceGui.drawButton(screen.font(), g, "财报提案", 276, cardY + 14, 58, false);
        if (company.isPublic()) FinanceGui.drawButton(screen.font(), g, "治理", 338, cardY + 14, 38, false);

        FinanceGui.drawSectionTitle(screen.font(), g, "我的公司", 10, cardY + 2);
        g.drawString(screen.font(), "名称", 12, cardY + 18, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(screen.font(), g, company.name(), 58, cardY + 18, 214, FinanceGui.COL_TEXT);
        g.drawString(screen.font(), "行业", 12, cardY + 32, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(screen.font(), g, company.type(), 58, cardY + 32, 180, FinanceGui.COL_ACCENT);
        g.drawString(screen.font(), "状态", 252, cardY + 32, FinanceGui.COL_TEXT_DIM, false);
        String statusText = company.bankruptcyRisk()
                ? "风险D" + company.bankruptcyRiskStartDay()
                : (company.isPublic() ? "已上市" : "未上市");
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                statusText,
                298,
                cardY + 32,
                70,
                company.bankruptcyRisk()
                        ? FinanceGui.COL_BAD
                        : (company.isPublic() ? FinanceGui.COL_GOOD : FinanceGui.COL_WARN));
        g.drawString(screen.font(), "现金", 12, cardY + 46, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(company.cash()), 58, cardY + 46, 100, FinanceGui.COL_GOOD);
        g.drawString(screen.font(), "公开估值", 176, cardY + 46, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                company.isPublic() ? Long.toString(company.totalValue()) : "未披露",
                240,
                cardY + 46,
                120,
                company.isPublic() ? FinanceGui.COL_WARN : FinanceGui.COL_TEXT_DIM);
        g.drawString(screen.font(), "策略", 12, cardY + 64, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawButton(
                screen.font(),
                g,
                "< " + FinanceFormat.displayCompanyStrategy(screen, company) + " >",
                58,
                cardY + 62,
                118,
                true);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "出售 " + Math.round(company.autoSellRatio() * 100) + "%",
                190,
                cardY + 65,
                70,
                FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawButton(screen.font(), g, "-10%", 262, cardY + 62, 38, false);
        FinanceGui.drawButton(screen.font(), g, "+10%", 306, cardY + 62, 38, false);

        FinanceGui.drawClippedString(
                screen.font(), g, "生产线 Lv." + company.productionLevel(), 12, cardY + 86, 80, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawFilledButton(screen.font(), g, "升级", 96, cardY + 82, 36, FinanceGui.COL_GOOD);
        FinanceGui.drawClippedString(
                screen.font(), g, "仓储 Lv." + company.storageLevel(), 146, cardY + 86, 70, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawFilledButton(screen.font(), g, "升级", 216, cardY + 82, 36, FinanceGui.COL_GOOD);
        FinanceGui.drawClippedString(
                screen.font(), g, "管理 Lv." + company.managementLevel(), 266, cardY + 86, 70, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawFilledButton(screen.font(), g, "升级", 336, cardY + 82, 36, FinanceGui.COL_GOOD);

        String capital = company.capitalProjectCount() == 0
                ? "实体项目：无"
                : "实体项目 " + company.capitalProjectCount() + "  已投入 " + company.capitalCommitted() + "  "
                        + company.capitalProjectSummary();
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                capital,
                12,
                cardY + 99,
                360,
                company.capitalProjectCount() == 0 ? FinanceGui.COL_TEXT_DIM : FinanceGui.COL_ACCENT);

        g.drawString(screen.font(), "金额", 12, cardY + 110, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawFilledButton(screen.font(), g, "注资", 150, cardY + 106, 48, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "提取", 206, cardY + 106, 48, FinanceGui.COL_ACCENT);
        if (!company.isPublic()) {
            g.drawString(screen.font(), "发行价", 12, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
            g.drawString(screen.font(), "发行数", 126, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
            long price = Math.max(1, FinanceFormat.parseLong(screen.ipoPriceBox.getValue()));
            long quantity = Math.max(1, FinanceFormat.parseLong(screen.ipoQuantityBox.getValue()));
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "募资 " + FinanceFormat.safeMultiplyLong(price, quantity),
                    252,
                    cardY + 138,
                    70,
                    FinanceGui.COL_ACCENT);
            FinanceGui.drawFilledButton(screen.font(), g, "提交IPO", 322, cardY + 136, 50, FinanceGui.COL_GOOD);
        } else {
            CompanyRows.CompanyFinancingRow financing = financingForCompany(company.companyId());
            g.drawString(screen.font(), "增发价", 12, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
            g.drawString(screen.font(), "增发数", 126, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
            if (financing == null) {
                long price = Math.max(1, FinanceFormat.parseLong(screen.ipoPriceBox.getValue()));
                long quantity = Math.max(1, FinanceFormat.parseLong(screen.ipoQuantityBox.getValue()));
                long target = Math.max(1, FinanceFormat.parseLong(screen.companyAmountBox.getValue()));
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        "目标 " + target + " / 上限 " + FinanceFormat.safeMultiplyLong(price, quantity),
                        252,
                        cardY + 124,
                        120,
                        FinanceGui.COL_TEXT_DIM);
                FinanceGui.drawFilledButton(screen.font(), g, "发起融资", 322, cardY + 136, 50, FinanceGui.COL_GOOD);
            } else {
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        "融资: " + financing.raisedAmount() + "/" + financing.fundingTarget() + "  股 "
                                + financing.subscribedShares() + "/" + financing.issueQuantity(),
                        252,
                        cardY + 124,
                        120,
                        FinanceGui.COL_ACCENT);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        "发行价 " + financing.issuePrice() + " 截止第 " + financing.deadlineMcDay() + " 天",
                        252,
                        cardY + 138,
                        120,
                        FinanceGui.COL_TEXT_DIM);
            }
        }
    }

    boolean click(int mx, int my, CompanyRows.CompanyInfo playerCompany, int cardY) {
        if (playerCompany.isPublic() && mx >= 338 && mx < 376 && my >= cardY + 14 && my < cardY + 27) {
            screen.governanceView = true;
            screen.companyDetailsView = false;
            GovernanceClientCache.clear();
            tabs.requestGovernance(playerCompany.companyId());
            screen.updateInputVisibility();
            return true;
        }
        if (mx >= 224 && mx < 272 && my >= cardY + 14 && my < cardY + 27) {
            screen.restructuringView = true;
            screen.governanceView = false;
            screen.companyDetailsView = false;
            screen.updateInputVisibility();
            return true;
        }
        if (mx >= 276 && mx < 334 && my >= cardY + 14 && my < cardY + 27) {
            screen.companyDetailsView = true;
            screen.governanceView = false;
            screen.restructuringView = false;
            screen.updateInputVisibility();
            return true;
        }
        if (mx >= 58 && mx < 176 && my >= cardY + 62 && my < cardY + 75) {
            CompanyStrategy[] strategies = CompanyStrategy.values();
            int idx = 0;
            for (int i = 0; i < strategies.length; i++) {
                if (strategies[i]
                        .getDisplayName()
                        .equals(FinanceFormat.displayCompanyStrategy(screen, playerCompany))) {
                    idx = i;
                    break;
                }
            }
            CompanyStrategy next = strategies[(idx + 1) % strategies.length];
            screen.companyStrategyOverride = next.getDisplayName();
            screen.setStatus("已提交策略调整");
            FinancePacketHandler.CHANNEL.sendToServer(CompanyManagePacket.strategy(next));
            return true;
        }
        if (mx >= 262 && mx < 300 && my >= cardY + 62 && my < cardY + 75) {
            screen.setStatus("已提交出售比例调整");
            FinancePacketHandler.CHANNEL.sendToServer(
                    CompanyManagePacket.sellRatio(playerCompany.autoSellRatio() - 0.10));
            return true;
        }
        if (mx >= 306 && mx < 344 && my >= cardY + 62 && my < cardY + 75) {
            screen.setStatus("已提交出售比例调整");
            FinancePacketHandler.CHANNEL.sendToServer(
                    CompanyManagePacket.sellRatio(playerCompany.autoSellRatio() + 0.10));
            return true;
        }
        if (my >= cardY + 82 && my < cardY + 95) {
            if (mx >= 96 && mx < 132) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new CompanyManagePacket(CompanyManagementAction.UPGRADE_PRODUCTION));
                screen.setStatus("已提交生产线升级");
                return true;
            }
            if (mx >= 216 && mx < 252) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new CompanyManagePacket(CompanyManagementAction.UPGRADE_STORAGE));
                screen.setStatus("已提交仓储升级");
                return true;
            }
            if (mx >= 336 && mx < 372) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new CompanyManagePacket(CompanyManagementAction.UPGRADE_MANAGEMENT));
                screen.setStatus("已提交管理升级");
                return true;
            }
        }
        if (my >= cardY + 106 && my < cardY + 119 && (mx >= 150 && mx < 198 || mx >= 206 && mx < 254)) {
            long amount = screen.requireAmount(screen.companyAmountBox, "金额");
            if (mx >= 150 && mx < 198) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        CompanyManagePacket.amount(CompanyManagementAction.INVEST, amount));
                screen.setStatus("已提交公司注资");
                return true;
            }
            if (mx >= 206 && mx < 254) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        CompanyManagePacket.amount(CompanyManagementAction.WITHDRAW, amount));
                screen.setStatus("已提交公司提取");
                return true;
            }
        }
        if (!playerCompany.isPublic() && mx >= 322 && mx < 372 && my >= cardY + 136 && my < cardY + 149) {
            long price = screen.requireAmount(screen.ipoPriceBox, "发行价");
            long quantity = screen.requireAmount(screen.ipoQuantityBox, "发行数");
            screen.setStatus("已提交公司IPO");
            FinancePacketHandler.CHANNEL.sendToServer(new CompanyIPOPacket(playerCompany.companyId(), price, quantity));
            return true;
        }
        if (playerCompany.isPublic()
                && financingForCompany(playerCompany.companyId()) == null
                && mx >= 322
                && mx < 372
                && my >= cardY + 136
                && my < cardY + 149) {
            long price = screen.requireAmount(screen.ipoPriceBox, "增发价");
            long quantity = screen.requireAmount(screen.ipoQuantityBox, "增发数");
            long target = screen.requireAmount(screen.companyAmountBox, "目标金额");
            screen.setStatus("已提交融资申请");
            FinancePacketHandler.CHANNEL.sendToServer(
                    CompanyFinancingPacket.start(playerCompany.companyId(), quantity, price, target));
            return true;
        }
        return false;
    }

    public CompanyRows.CompanyFinancingRow financingForCompany(UUID companyId) {
        if (companyId == null) {
            return null;
        }
        for (CompanyRows.CompanyFinancingRow row : screen.menu().getCompanyFinancingRows()) {
            if (companyId.equals(row.companyId())) {
                return row;
            }
        }
        return null;
    }
}
