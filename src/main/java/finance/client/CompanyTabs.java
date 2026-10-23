package finance.client;

import finance.company.CompanyProposalType;
import finance.gui.CompanyRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 公司页：按视图分发渲染和点击。每个视图的画面和操作在面板类里：
 * {@link CompanyCreatePanel}（还没有公司）、{@link CompanyManagePanel}（我的公司）、{@link CompanyDetailsPanel}（财报与提案）、
 * {@link CompanyGovernancePanel}（治理）、{@link CompanyRestructuringPanel}（跨公司重组）。
 */
public final class CompanyTabs {
    private final FinanceScreen screen;
    private final CompanyCreatePanel create;
    private final CompanyManagePanel manage;
    private final CompanyDetailsPanel details;
    private final CompanyGovernancePanel governance;
    private final CompanyRestructuringPanel restructuring;

    public CompanyTabs(FinanceScreen screen) {
        this.screen = screen;
        create = new CompanyCreatePanel(screen);
        manage = new CompanyManagePanel(screen, this);
        details = new CompanyDetailsPanel(screen, this);
        governance = new CompanyGovernancePanel(screen, this);
        restructuring = new CompanyRestructuringPanel(screen);
    }

    public void renderCompanyTab(GuiGraphics g) {
        CompanyRows.CompanyInfo company = screen.menu().getPlayerCompany();
        int cardY = FinanceGui.CONTENT_Y;
        if (screen.restructuringView) {
            restructuring.render(g, cardY);
            return;
        }
        if (company == null) {
            create.render(g, cardY);
            return;
        }
        if (screen.governanceView) {
            governance.render(g, company, cardY);
            return;
        }
        if (screen.companyDetailsView) {
            details.render(g, company, cardY);
            return;
        }
        manage.render(g, company, cardY);
    }

    public boolean handleCompanyClick(int mx, int my) {
        int cardY = FinanceGui.CONTENT_Y;
        CompanyRows.CompanyInfo playerCompany = screen.menu().getPlayerCompany();
        if (screen.restructuringView) return restructuring.click(mx, my, cardY);
        if (playerCompany == null) return create.click(mx, my, cardY);
        if (screen.governanceView) return governance.click(mx, my, playerCompany, cardY);
        if (screen.companyDetailsView) return details.click(mx, my, playerCompany, cardY);
        return manage.click(mx, my, playerCompany, cardY);
    }

    public void requestGovernance(UUID companyId) {
        long requestId = GovernanceClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new GovernanceRequestPacket(requestId, companyId));
    }

    public void sendProposal(
            UUID companyId, CompanyProposalType type, String text, long value1, long value2, long value3) {
        if (type == CompanyProposalType.RENAME || type == CompanyProposalType.FUND_USAGE) {
            if (text == null || text.isBlank()) {
                screen.setStatus(type == CompanyProposalType.RENAME ? "请输入新公司名" : "请输入资金用途");
                return;
            }
        }
        screen.setStatus("已提交股东提案");
        FinancePacketHandler.CHANNEL.sendToServer(
                CompanyProposalPacket.create(companyId, type, text, value1, value2, value3, 3, 60));
    }
}
