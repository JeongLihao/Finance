package finance.client;

import finance.company.CompanyProposalType;
import finance.gui.CompanyRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 公司页 · 治理与资本结构：股本、估值、主要股东、公司行动，以及回购、注销、收购、注资、资产交易。由 {@link CompanyTabs} 分发渲染和点击。 */
final class CompanyGovernancePanel {
    private final FinanceScreen screen;
    private final CompanyTabs tabs;

    CompanyGovernancePanel(FinanceScreen screen, CompanyTabs tabs) {
        this.screen = screen;
        this.tabs = tabs;
    }

    void render(GuiGraphics g, CompanyRows.CompanyInfo company, int cardY) {
        int cardH = screen.imageHeight() - cardY - 8;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "公司治理与资本结构", 10, cardY + 2);
        FinanceGui.drawButton(screen.font(), g, "返回", 340, cardY + 2, 36, false);
        GovernanceClientCache.Entry entry = GovernanceClientCache.get();
        GovernanceResponsePacket data = entry.data();
        if (entry.state() == GovernanceClientCache.State.NOT_REQUESTED) tabs.requestGovernance(company.companyId());
        if (data == null || !data.companyId().equals(company.companyId())) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    entry.state() == GovernanceClientCache.State.SLOW ? "治理数据响应较慢，可返回后重试" : "正在加载治理数据…",
                    12,
                    cardY + 24,
                    330,
                    FinanceGui.COL_WARN);
            return;
        }
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "总股本 " + data.totalShares() + "  表决股 " + data.votingShares() + "  库存股 " + data.treasuryShares(),
                12,
                cardY + 22,
                360,
                FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "市值 " + data.valuation().marketCap() + "  企业价值 "
                        + data.valuation().enterpriseValue() + "  账面净资产 "
                        + data.valuation().bookEquity(),
                12,
                cardY + 38,
                360,
                FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "现金 " + data.structure().cash() + "  贷款 "
                        + data.structure().bankAndOtherLoans() + "  债券 "
                        + data.structure().bonds()
                        + "  近期债务 " + data.structure().nearTermDebt(),
                12,
                cardY + 54,
                360,
                FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "估值质量 " + data.valuation().quality() + "  控制者 "
                        + (data.controller() == null
                                ? "分散持有"
                                : data.controller().toString().substring(0, 8)),
                12,
                cardY + 70,
                360,
                FinanceGui.COL_WARN);
        FinanceGui.drawSectionTitle(screen.font(), g, "主要股东", 10, cardY + 88);
        int holderY = cardY + 104;
        // 一行列出前几大股东：身份（NPC 原型、竞争公司、创始人）+ 占比，NPC 附情绪
        if (!data.holders().isEmpty()) {
            FinanceGui.drawClippedString(
                    screen.font(), g, holderLine(data.holders()), 12, holderY, 364, FinanceGui.COL_TEXT);
        }
        if (data.holders().isEmpty())
            FinanceGui.drawClippedString(screen.font(), g, "暂无股东", 12, holderY, 160, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawSectionTitle(screen.font(), g, "公司行动", 10, cardY + 120);
        int actionY = cardY + 134;
        if (data.actions().isEmpty())
            FinanceGui.drawClippedString(screen.font(), g, "暂无回购或收购要约", 12, actionY, 260, FinanceGui.COL_TEXT_DIM);
        for (GovernanceResponsePacket.ActionRow action :
                data.actions().stream().limit(1).toList()) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.label(action.type()) + "  " + FinanceFormat.label(action.status()) + "  价格 "
                            + action.price() + "  股份 " + action.shares() + "  截止 D" + action.endDay(),
                    12,
                    actionY,
                    360,
                    FinanceGui.COL_TEXT);
        }
        int controlsY = cardY + 160;
        FinanceGui.drawClippedString(screen.font(), g, "目标/卖方", 12, cardY + 146, 90, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "价格", 12, controlsY + 4, 34, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "股份", 132, controlsY + 4, 34, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "期限", 252, controlsY + 4, 34, FinanceGui.COL_TEXT_DIM);
        int buttonY = controlsY + 23;
        FinanceGui.drawButton(screen.font(), g, "回购", 10, buttonY, 48, false);
        FinanceGui.drawButton(screen.font(), g, "注销", 62, buttonY, 48, false);
        FinanceGui.drawButton(screen.font(), g, "接受", 114, buttonY, 48, false);
        FinanceGui.drawButton(screen.font(), g, "收购", 166, buttonY, 48, false);
        FinanceGui.drawButton(screen.font(), g, "注资", 218, buttonY, 48, false);
        FinanceGui.drawButton(screen.font(), g, "资产", 270, buttonY, 48, false);
        FinanceGui.drawButton(screen.font(), g, "刷新", 322, buttonY, 54, false);
    }

    boolean click(int mx, int my, CompanyRows.CompanyInfo playerCompany, int cardY) {
        if (mx >= 340 && mx < 376 && my >= cardY + 2 && my < cardY + 15) {
            screen.governanceView = false;
            screen.updateInputVisibility();
            return true;
        }
        int controlsY = cardY + 160;
        java.util.function.LongSupplier price = () -> screen.requireAmount(screen.stockPriceBox, "价格");
        java.util.function.LongSupplier quantity = () -> screen.requireAmount(screen.stockQuantityBox, "股份");
        java.util.function.IntSupplier duration = () -> screen.requireInt(screen.companyAmountBox, "期限", 1, 90);
        if (my >= controlsY + 23 && my < controlsY + 36) {
            if (mx >= 10 && mx < 58) {
                tabs.sendProposal(
                        playerCompany.companyId(),
                        CompanyProposalType.SHARE_BUYBACK,
                        "",
                        price.getAsLong(),
                        quantity.getAsLong(),
                        duration.getAsInt());
                return true;
            }
            if (mx >= 62 && mx < 110) {
                tabs.sendProposal(
                        playerCompany.companyId(),
                        CompanyProposalType.TREASURY_RETIREMENT,
                        "",
                        quantity.getAsLong(),
                        0,
                        0);
                return true;
            }
            if (mx >= 114 && mx < 162) {
                GovernanceResponsePacket data = GovernanceClientCache.get().data();
                GovernanceResponsePacket.ActionRow action = data == null
                        ? null
                        : data.actions().stream()
                                .filter(a -> a.status() == finance.governance.CapitalActionStatus.OPEN)
                                .findFirst()
                                .orElse(null);
                if (action == null) {
                    screen.setStatus("没有可接受的开放行动");
                    return true;
                }
                GovernanceActionPacket.Action kind = "TENDER".equals(action.type())
                        ? GovernanceActionPacket.Action.ACCEPT_TENDER
                        : GovernanceActionPacket.Action.ACCEPT_BUYBACK;
                FinancePacketHandler.CHANNEL.sendToServer(new GovernanceActionPacket(
                        kind,
                        action.id(),
                        0,
                        quantity.getAsLong(),
                        0,
                        0,
                        UUID.randomUUID().toString()));
                screen.setStatus("已提交行动接受请求");
                return true;
            }
            String target = screen.companyNameBox.getValue().trim();
            if (mx >= 166 && mx < 214) {
                UUID targetCompany = ScreenInput.parseUuid(target);
                if (targetCompany == null) {
                    screen.setStatus("请输入目标公司的完整 UUID");
                    return true;
                }
                FinancePacketHandler.CHANNEL.sendToServer(new GovernanceActionPacket(
                        GovernanceActionPacket.Action.START_TENDER,
                        targetCompany,
                        price.getAsLong(),
                        quantity.getAsLong(),
                        quantity.getAsLong(),
                        duration.getAsInt(),
                        UUID.randomUUID().toString()));
                screen.setStatus("已提交公开收购要约");
                return true;
            }
            if (mx >= 218 && mx < 266) {
                tabs.sendProposal(
                        playerCompany.companyId(),
                        CompanyProposalType.EMERGENCY_RECAPITALIZATION,
                        "",
                        price.getAsLong(),
                        0,
                        0);
                return true;
            }
            if (mx >= 270 && mx < 318) {
                tabs.sendProposal(
                        playerCompany.companyId(),
                        CompanyProposalType.MAJOR_ASSET_PURCHASE,
                        target,
                        price.getAsLong(),
                        quantity.getAsLong(),
                        0);
                return true;
            }
            if (mx >= 322 && mx < 376) {
                GovernanceClientCache.clear();
                tabs.requestGovernance(playerCompany.companyId());
                screen.setStatus("正在刷新治理数据");
                return true;
            }
        }
        return false;
    }

    /** "创始人 60.0% · 分红派 20.0%(+35) · 价值派 12.0%(-10) · 未流通 8.0%" */
    static String holderLine(List<GovernanceResponsePacket.HolderRow> holders) {
        StringBuilder sb = new StringBuilder();
        for (GovernanceResponsePacket.HolderRow h : holders.stream().limit(5).toList()) {
            if (sb.length() > 0) sb.append(" · ");
            String name = h.label().isEmpty() ? h.id().toString().substring(0, 8) : h.label();
            sb.append(name).append(' ').append(String.format(Locale.ROOT, "%.1f%%", h.ownership()));
            if (h.sentiment() != 0) sb.append(String.format(Locale.ROOT, "(%+d)", h.sentiment()));
        }
        return sb.toString();
    }
}
