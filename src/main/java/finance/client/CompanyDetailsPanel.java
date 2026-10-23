package finance.client;

import finance.company.CompanyProposalType;
import finance.gui.CompanyRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 公司页 · 财报与股东提案：发起分红、增发、改名、资金用途提案，对进行中的提案投票。由 {@link CompanyTabs} 分发渲染和点击。 */
final class CompanyDetailsPanel {
    private final FinanceScreen screen;
    private final CompanyTabs tabs;

    CompanyDetailsPanel(FinanceScreen screen, CompanyTabs tabs) {
        this.screen = screen;
        this.tabs = tabs;
    }

    /** 财报和股东提案使用独立视图，避免与 IPO/融资表单共享坐标。 */
    void render(GuiGraphics g, CompanyRows.CompanyInfo company, int cardY) {
        int cardH = screen.imageHeight() - cardY - 8;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "财报与股东提案", 10, cardY + 2);
        FinanceGui.drawButton(screen.font(), g, "返回", 340, cardY + 2, 36, false);

        FinanceGui.drawClippedString(
                screen.font(), g, "收入 " + company.reportRevenue(), 12, cardY + 22, 112, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(
                screen.font(), g, "支出 " + company.reportExpenses(), 134, cardY + 22, 112, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "净利润 " + FinanceFormat.signedLong(company.reportNetProfit()),
                256,
                cardY + 22,
                120,
                company.reportNetProfit() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        FinanceGui.drawClippedString(
                screen.font(), g, "资产 " + company.reportAssets(), 12, cardY + 38, 112, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, "负债 " + company.reportLiabilities(), 134, cardY + 38, 112, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, "现金 " + company.cash(), 256, cardY + 38, 120, FinanceGui.COL_TEXT_DIM);

        if (!company.isPublic()) {
            FinanceGui.drawClippedString(
                    screen.font(), g, "公司上市后才可创建和参与股东提案。", 12, cardY + 64, 300, FinanceGui.COL_WARN);
            return;
        }

        FinanceGui.drawClippedString(screen.font(), g, "文本", 12, cardY + 62, 32, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "金额", 12, cardY + 84, 32, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "价格", 132, cardY + 84, 32, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "数量", 252, cardY + 84, 32, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawFilledButton(screen.font(), g, "分红", 10, cardY + 106, 48, FinanceGui.COL_ACCENT);
        FinanceGui.drawFilledButton(screen.font(), g, "增发", 64, cardY + 106, 48, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "改名", 118, cardY + 106, 48, FinanceGui.COL_WARN);
        FinanceGui.drawFilledButton(screen.font(), g, "用途", 172, cardY + 106, 48, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(screen.font(), g, "金额=分红比例/目标/预算", 230, cardY + 109, 146, FinanceGui.COL_TEXT_DIM);

        int proposalY = cardY + 128;
        FinanceGui.drawSectionTitle(screen.font(), g, "提案记录", 10, proposalY);
        List<CompanyRows.CompanyProposalRow> proposals = proposalsForCompany(company.companyId());
        int py = proposalY + 16;
        if (proposals.isEmpty()) {
            FinanceGui.drawClippedString(screen.font(), g, "暂无提案。", 12, py, 180, FinanceGui.COL_TEXT_DIM);
            return;
        }
        int limit = proposalAreaBottom();
        for (int i = 0; i < proposals.size(); i++) {
            if (py + FinanceGui.ROW_HEIGHT > limit) break;
            CompanyRows.CompanyProposalRow row = proposals.get(i);
            boolean active = "ACTIVE".equals(row.status());
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.displayProposalType(row.type()) + " " + FinanceFormat.displayProposalValues(row),
                    12,
                    py,
                    120,
                    active ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "赞 " + row.yesVotes() + " 反 " + row.noVotes() + " 需" + Math.round(row.passRatio() * 100) + "%",
                    138,
                    py,
                    92,
                    FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    active ? "截止" + row.endMcDay() : FinanceFormat.displayProposalStatus(row),
                    236,
                    py,
                    58,
                    active
                            ? FinanceGui.COL_WARN
                            : ("PASSED".equals(row.status()) ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD));
            if (active && !row.playerVoted()) {
                FinanceGui.drawButton(screen.font(), g, "赞成", 300, py - 2, 36, false);
                FinanceGui.drawButton(screen.font(), g, "反对", 340, py - 2, 36, false);
            }
            py += FinanceGui.ROW_HEIGHT;
            if (hasDetailLine(row)) {
                if (py + FinanceGui.ROW_HEIGHT <= limit) {
                    boolean low = row.participationBps() < row.minParticipationBps();
                    String detail = (low
                                    ? "参与率 " + row.participationBps() / 100 + "%（需 " + row.minParticipationBps() / 100
                                            + "%）  "
                                    : "")
                            + (row.npcSummary().isEmpty() ? "" : "NPC：" + row.npcSummary());
                    FinanceGui.drawClippedString(
                            screen.font(), g, detail, 20, py, 356, low ? FinanceGui.COL_WARN : FinanceGui.COL_TEXT_DIM);
                }
                py += FinanceGui.ROW_HEIGHT;
            }
        }
    }

    boolean click(int mx, int my, CompanyRows.CompanyInfo playerCompany, int cardY) {
        if (mx >= 340 && mx < 376 && my >= cardY + 2 && my < cardY + 15) {
            screen.companyDetailsView = false;
            screen.updateInputVisibility();
            return true;
        }
        if (!playerCompany.isPublic()) return false;
        if (my >= cardY + 106 && my < cardY + 119) {
            java.util.function.LongSupplier amount = () -> screen.requireAmount(screen.companyAmountBox, "金额");
            java.util.function.LongSupplier price = () -> screen.requireAmount(screen.ipoPriceBox, "价格");
            java.util.function.LongSupplier quantity = () -> screen.requireAmount(screen.ipoQuantityBox, "数量");
            String text = screen.companyNameBox.getValue().trim();
            if (mx >= 10 && mx < 58) {
                tabs.sendProposal(
                        playerCompany.companyId(), CompanyProposalType.DIVIDEND, "", amount.getAsLong(), 0, 0);
                return true;
            }
            if (mx >= 64 && mx < 112) {
                tabs.sendProposal(
                        playerCompany.companyId(),
                        CompanyProposalType.SHARE_ISSUE,
                        "",
                        quantity.getAsLong(),
                        price.getAsLong(),
                        amount.getAsLong());
                return true;
            }
            if (mx >= 118 && mx < 166) {
                tabs.sendProposal(playerCompany.companyId(), CompanyProposalType.RENAME, text, 0, 0, 0);
                return true;
            }
            if (mx >= 172 && mx < 220) {
                tabs.sendProposal(
                        playerCompany.companyId(), CompanyProposalType.FUND_USAGE, text, amount.getAsLong(), 0, 0);
                return true;
            }
        }
        List<CompanyRows.CompanyProposalRow> proposals = proposalsForCompany(playerCompany.companyId());
        int py = cardY + 144;
        int limit = proposalAreaBottom();
        for (int i = 0; i < proposals.size(); i++) {
            if (py + FinanceGui.ROW_HEIGHT > limit) break;
            CompanyRows.CompanyProposalRow row = proposals.get(i);
            if ("ACTIVE".equals(row.status()) && !row.playerVoted()) {
                if (mx >= 300 && mx < 336 && my >= py - 2 && my < py + 11) {
                    FinancePacketHandler.CHANNEL.sendToServer(CompanyProposalPacket.vote(row.proposalId(), true));
                    screen.setStatus("已提交赞成票");
                    return true;
                }
                if (mx >= 340 && mx < 376 && my >= py - 2 && my < py + 11) {
                    FinancePacketHandler.CHANNEL.sendToServer(CompanyProposalPacket.vote(row.proposalId(), false));
                    screen.setStatus("已提交反对票");
                    return true;
                }
            }
            py += FinanceGui.ROW_HEIGHT;
            if (hasDetailLine(row)) py += FinanceGui.ROW_HEIGHT;
        }
        return false;
    }

    /** 提案区的下边界（渲染和点击共用，保证行位置一致）。 */
    int proposalAreaBottom() {
        return screen.imageHeight() - 12;
    }

    /** 进行中的提案若有 NPC 表态或参与率不足，下面多占一行显示说明。 */
    static boolean hasDetailLine(CompanyRows.CompanyProposalRow row) {
        return "ACTIVE".equals(row.status())
                && (!row.npcSummary().isEmpty() || row.participationBps() < row.minParticipationBps());
    }

    public List<CompanyRows.CompanyProposalRow> proposalsForCompany(UUID companyId) {
        List<CompanyRows.CompanyProposalRow> rows = new ArrayList<>();
        if (companyId == null) {
            return rows;
        }
        for (CompanyRows.CompanyProposalRow row : screen.menu().getCompanyProposalRows()) {
            if (companyId.equals(row.companyId())) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing((CompanyRows.CompanyProposalRow row) -> !"ACTIVE".equals(row.status()))
                .thenComparingLong(CompanyRows.CompanyProposalRow::endMcDay));
        return rows;
    }
}
