package finance.client;

import finance.company.CompanyType;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 公司页 · 还没有公司时：选行业、起名、创建公司，或去看重组任务。由 {@link CompanyTabs} 分发渲染和点击。 */
final class CompanyCreatePanel {
    private final FinanceScreen screen;

    CompanyCreatePanel(FinanceScreen screen) {
        this.screen = screen;
    }

    private static final CompanyType[] COMPANY_TYPES = CompanyType.values();

    void render(GuiGraphics g, int cardY) {
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + 126, 0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, 126, FinanceGui.COL_PANEL_BORDER);

        FinanceGui.drawSectionTitle(screen.font(), g, "创建公司", 10, cardY + 2);
        g.drawString(screen.font(), "费用", 12, cardY + 18, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "10,000", 55, cardY + 18, FinanceGui.COL_WARN, false);

        g.drawString(screen.font(), "名称", 12, cardY + 36, FinanceGui.COL_TEXT_DIM, false);

        g.drawString(screen.font(), "行业", 12, cardY + 58, FinanceGui.COL_TEXT_DIM, false);
        String typeLabel = "< " + screen.selectedType.getDisplayName() + " >";
        FinanceGui.drawButton(screen.font(), g, typeLabel, 55, cardY + 58, 120, true);

        String typeDesc = FinanceFormat.getTypeDescription(screen.selectedType);
        FinanceGui.drawClippedString(screen.font(), g, typeDesc, 55, cardY + 76, 300, FinanceGui.COL_TEXT_DIM);

        FinanceGui.drawButton(screen.font(), g, "重组任务", 210, cardY + 58, 76, false);
        FinanceGui.drawFilledButton(screen.font(), g, "创建公司", 300, cardY + 58, 76, FinanceGui.COL_GOOD);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "创建后可在这里管理经营状态；上市后才会进入股票市场。",
                12,
                cardY + 108,
                screen.imageWidth() - 24,
                FinanceGui.COL_TEXT_DIM);
    }

    boolean click(int mx, int my, int cardY) {
        if (mx >= 210 && mx < 286 && my >= cardY + 58 && my < cardY + 71) {
            screen.restructuringView = true;
            screen.updateInputVisibility();
            return true;
        }

        if (mx >= 55 && mx < 175 && my >= cardY + 58 && my < cardY + 71) {
            int idx = 0;
            for (int i = 0; i < COMPANY_TYPES.length; i++) {
                if (COMPANY_TYPES[i] == screen.selectedType) {
                    idx = i;
                    break;
                }
            }
            screen.selectedType = COMPANY_TYPES[(idx + 1) % COMPANY_TYPES.length];
            return true;
        }

        if (mx >= 300 && mx < 376 && my >= cardY + 58 && my < cardY + 71) {
            String name = screen.companyNameBox.getValue().trim();
            if (!name.isEmpty()) {
                screen.setStatus("已提交创建公司");
                FinancePacketHandler.CHANNEL.sendToServer(new CreateCompanyPacket(screen.selectedType, name));
            } else {
                screen.setStatus("请输入公司名称");
            }
            return true;
        }
        return false;
    }
}
