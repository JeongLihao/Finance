package finance.client;

import finance.gui.CompanyRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 公司页 · 跨公司重组任务：执行已通过的紧急注资和资产购买提案。由 {@link CompanyTabs} 分发渲染和点击。 */
final class CompanyRestructuringPanel {
    private final FinanceScreen screen;

    CompanyRestructuringPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g, int cardY) {
        int cardH = screen.imageHeight() - cardY - 8;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "跨公司重组任务", 10, cardY + 2);
        FinanceGui.drawButton(screen.font(), g, "返回", 340, cardY + 2, 36, false);
        FinanceGui.drawClippedString(
                screen.font(), g, "紧急注资允许外部投资者执行；资产交易必须由提案指定卖方的经营者确认。", 12, cardY + 20, 360, FinanceGui.COL_TEXT_DIM);
        List<CompanyRows.CompanyProposalRow> tasks = executableRestructuringTasks();
        int y = cardY + 42;
        if (tasks.isEmpty()) {
            FinanceGui.drawClippedString(screen.font(), g, "目前没有等待执行的重组提案。", 12, y, 300, FinanceGui.COL_TEXT_DIM);
            return;
        }
        for (CompanyRows.CompanyProposalRow row : tasks) {
            if (y + FinanceGui.ROW_HEIGHT > cardY + cardH - 4) break;
            boolean recap = "EMERGENCY_RECAPITALIZATION".equals(row.type());
            String company = FinanceFormat.companyName(screen, row.companyId());
            String details = recap ? "目标 " + row.value1() : "价格 " + row.value1() + " 数量 " + row.value2();
            FinanceGui.drawClippedString(
                    screen.font(), g, (recap ? "紧急注资 " : "资产购买 ") + company, 12, y + 3, 150, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, details, 166, y + 3, 154, FinanceGui.COL_TEXT_DIM);
            boolean pending = GovernanceTaskClientState.isPending(row.proposalId());
            FinanceGui.drawButton(screen.font(), g, pending ? "处理中" : recap ? "投资" : "卖方确认", 322, y + 1, 54, pending);
            y += FinanceGui.ROW_HEIGHT;
        }
    }

    boolean click(int mx, int my, int cardY) {
        if (mx >= 340 && mx < 376 && my >= cardY + 2 && my < cardY + 15) {
            screen.restructuringView = false;
            screen.updateInputVisibility();
            return true;
        }
        int y = cardY + 42;
        for (CompanyRows.CompanyProposalRow proposal : executableRestructuringTasks()) {
            if (y + FinanceGui.ROW_HEIGHT > screen.imageHeight() - 12) break;
            if (mx >= 322 && mx < 376 && my >= y + 1 && my < y + 14) {
                if (!GovernanceTaskClientState.begin(proposal.proposalId())) return true;
                GovernanceActionPacket.Action action = "EMERGENCY_RECAPITALIZATION".equals(proposal.type())
                        ? GovernanceActionPacket.Action.EXECUTE_RECAPITALIZATION
                        : GovernanceActionPacket.Action.EXECUTE_ASSET_PURCHASE;
                FinancePacketHandler.CHANNEL.sendToServer(new GovernanceActionPacket(
                        action,
                        proposal.proposalId(),
                        0,
                        0,
                        0,
                        0,
                        UUID.randomUUID().toString()));
                screen.setStatus(
                        action == GovernanceActionPacket.Action.EXECUTE_RECAPITALIZATION ? "已提交外部注资" : "已提交卖方确认");
                return true;
            }
            y += FinanceGui.ROW_HEIGHT;
        }
        return false;
    }

    public List<CompanyRows.CompanyProposalRow> executableRestructuringTasks() {
        return screen.menu().getCompanyProposalRows().stream()
                .filter(p -> "PASSED".equals(p.status()))
                .filter(p -> "EMERGENCY_RECAPITALIZATION".equals(p.type()) || "MAJOR_ASSET_PURCHASE".equals(p.type()))
                .filter(CompanyRows.CompanyProposalRow::playerCanExecute)
                .filter(p -> !GovernanceTaskClientState.hasSucceeded(p.proposalId()))
                .toList();
    }
}
