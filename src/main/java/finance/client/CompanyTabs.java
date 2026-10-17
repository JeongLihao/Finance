package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.company.CompanyStrategy;
import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertType;
import finance.company.CompanyProposalType;
import finance.stock.ConditionalStockOrderType;
import finance.gui.FinanceMenu;
import finance.gameplay.FinanceScreenMode;
import finance.market.NpcMarketMaker;
import finance.network.*;
import finance.util.FormatUtil;
import finance.chart.MarketInstrumentType;
import finance.chart.MarketSummary;
import finance.client.chart.CandlestickChart;
import finance.client.chart.CandlestickClientCache;
import finance.client.chart.TechnicalIndicatorChart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** 公司：创建 / 详情 / 治理 / 重组：从 FinanceScreen 抽取的页签行为类。
 * 通过 screen 反向引用共享界面状态；未来做跨模组页签注册表时，此类即天然的注册单元。 */

public final class CompanyTabs {
    private final FinanceScreen screen;

    public CompanyTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    private static final CompanyType[] COMPANY_TYPES = CompanyType.values();


    // ================================================================
    // 标签 4: 公司管理
    // ================================================================

    public void renderCompanyTab(GuiGraphics g) {
        FinanceMenu.CompanyInfo company = screen.menu().getPlayerCompany();
        int cardY = FinanceGui.CONTENT_Y;
        if(screen.restructuringView){renderRestructuringTasks(g,cardY);return;}
        if (company != null) {
            if (screen.governanceView) {
                renderGovernanceCompany(g, company, cardY);
                return;
            }
            if (screen.companyDetailsView) {
                renderCompanyDetails(g, company, cardY);
                return;
            }
            int cardH = screen.imageHeight() - cardY - 8;
            g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFE0E0E0);
            FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);
            FinanceGui.drawButton(screen.font(), g,"重组",224,cardY+14,48,false);
            FinanceGui.drawButton(screen.font(), g,"财报提案",276,cardY+14,58,false);
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
            FinanceGui.drawClippedString(screen.font(), g, statusText, 298, cardY + 32, 70,
                    company.bankruptcyRisk() ? FinanceGui.COL_BAD : (company.isPublic() ? FinanceGui.COL_GOOD : FinanceGui.COL_WARN));
            g.drawString(screen.font(), "现金", 12, cardY + 46, FinanceGui.COL_TEXT_DIM, false);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(company.cash()), 58, cardY + 46, 100, FinanceGui.COL_GOOD);
            g.drawString(screen.font(), "公开估值", 176, cardY + 46, FinanceGui.COL_TEXT_DIM, false);
            FinanceGui.drawClippedString(screen.font(), g, company.isPublic() ? Long.toString(company.totalValue()) : "未披露",
                    240, cardY + 46, 120, company.isPublic() ? FinanceGui.COL_WARN : FinanceGui.COL_TEXT_DIM);
            g.drawString(screen.font(), "策略", 12, cardY + 64, FinanceGui.COL_TEXT_DIM, false);
            FinanceGui.drawButton(screen.font(), g, "< " + FinanceFormat.displayCompanyStrategy(screen, company) + " >", 58, cardY + 62, 118, true);
            FinanceGui.drawClippedString(screen.font(), g, "出售 " + Math.round(company.autoSellRatio() * 100) + "%", 190, cardY + 65, 70, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawButton(screen.font(), g, "-10%", 262, cardY + 62, 38, false);
            FinanceGui.drawButton(screen.font(), g, "+10%", 306, cardY + 62, 38, false);

            FinanceGui.drawClippedString(screen.font(), g, "生产线 Lv." + company.productionLevel(), 12, cardY + 86, 80, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "升级", 96, cardY + 82, 36, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(screen.font(), g, "仓储 Lv." + company.storageLevel(), 146, cardY + 86, 70, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "升级", 216, cardY + 82, 36, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(screen.font(), g, "管理 Lv." + company.managementLevel(), 266, cardY + 86, 70, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "升级", 336, cardY + 82, 36, FinanceGui.COL_GOOD);

            String capital = company.capitalProjectCount() == 0 ? "实体项目：无"
                    : "实体项目 " + company.capitalProjectCount() + "  已投入 " + company.capitalCommitted()
                    + "  " + company.capitalProjectSummary();
            FinanceGui.drawClippedString(screen.font(), g, capital, 12, cardY + 99, 360,
                    company.capitalProjectCount() == 0 ? FinanceGui.COL_TEXT_DIM : FinanceGui.COL_ACCENT);

            g.drawString(screen.font(), "金额", 12, cardY + 110, FinanceGui.COL_TEXT_DIM, false);
            FinanceGui.drawFilledButton(screen.font(), g, "注资", 150, cardY + 106, 48, FinanceGui.COL_GOOD);
            FinanceGui.drawFilledButton(screen.font(), g, "提取", 206, cardY + 106, 48, FinanceGui.COL_ACCENT);
            if (!company.isPublic()) {
                g.drawString(screen.font(), "发行价", 12, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
                g.drawString(screen.font(), "发行数", 126, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
                long price = Math.max(1, FinanceFormat.parseLong(screen.ipoPriceBox.getValue()));
                long quantity = Math.max(1, FinanceFormat.parseLong(screen.ipoQuantityBox.getValue()));
                FinanceGui.drawClippedString(screen.font(), g, "募资 " + FinanceFormat.safeMultiplyLong(price, quantity), 252, cardY + 138, 70, FinanceGui.COL_ACCENT);
                FinanceGui.drawFilledButton(screen.font(), g, "提交IPO", 322, cardY + 136, 50, FinanceGui.COL_GOOD);
            } else {
                FinanceMenu.CompanyFinancingRow financing = financingForCompany(company.companyId());
                g.drawString(screen.font(), "增发价", 12, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
                g.drawString(screen.font(), "增发数", 126, cardY + 138, FinanceGui.COL_TEXT_DIM, false);
                if (financing == null) {
                    long price = Math.max(1, FinanceFormat.parseLong(screen.ipoPriceBox.getValue()));
                    long quantity = Math.max(1, FinanceFormat.parseLong(screen.ipoQuantityBox.getValue()));
                    long target = Math.max(1, FinanceFormat.parseLong(screen.companyAmountBox.getValue()));
                    FinanceGui.drawClippedString(screen.font(), g, "目标 " + target + " / 上限 " + FinanceFormat.safeMultiplyLong(price, quantity),
                            252, cardY + 124, 120, FinanceGui.COL_TEXT_DIM);
                    FinanceGui.drawFilledButton(screen.font(), g, "发起融资", 322, cardY + 136, 50, FinanceGui.COL_GOOD);
                } else {
                    FinanceGui.drawClippedString(screen.font(), g, "融资: " + financing.raisedAmount() + "/" + financing.fundingTarget()
                                    + "  股 " + financing.subscribedShares() + "/" + financing.issueQuantity(),
                            252, cardY + 124, 120, FinanceGui.COL_ACCENT);
                    FinanceGui.drawClippedString(screen.font(), g, "发行价 " + financing.issuePrice() + " 截止第 " + financing.deadlineMcDay() + " 天",
                            252, cardY + 138, 120, FinanceGui.COL_TEXT_DIM);
                }
            }
        } else {
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

            FinanceGui.drawButton(screen.font(), g,"重组任务",210,cardY+58,76,false);
            FinanceGui.drawFilledButton(screen.font(), g, "创建公司", 300, cardY + 58, 76, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(screen.font(), g, "创建后可在这里管理经营状态；上市后才会进入股票市场。",
                    12, cardY + 108, screen.imageWidth() - 24, FinanceGui.COL_TEXT_DIM);
        }
    }

    /** 财报和股东提案使用独立视图，避免与 IPO/融资表单共享坐标。 */
    public void renderCompanyDetails(GuiGraphics g, FinanceMenu.CompanyInfo company, int cardY) {
        int cardH = screen.imageHeight() - cardY - 8;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "财报与股东提案", 10, cardY + 2);
        FinanceGui.drawButton(screen.font(), g, "返回", 340, cardY + 2, 36, false);

        FinanceGui.drawClippedString(screen.font(), g, "收入 " + company.reportRevenue(), 12, cardY + 22, 112, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "支出 " + company.reportExpenses(), 134, cardY + 22, 112, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "净利润 " + FinanceFormat.signedLong(company.reportNetProfit()), 256, cardY + 22, 120,
                company.reportNetProfit() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        FinanceGui.drawClippedString(screen.font(), g, "资产 " + company.reportAssets(), 12, cardY + 38, 112, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "负债 " + company.reportLiabilities(), 134, cardY + 38, 112, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "现金 " + company.cash(), 256, cardY + 38, 120, FinanceGui.COL_TEXT_DIM);

        if (!company.isPublic()) {
            FinanceGui.drawClippedString(screen.font(), g, "公司上市后才可创建和参与股东提案。", 12, cardY + 64, 300, FinanceGui.COL_WARN);
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
        List<FinanceMenu.CompanyProposalRow> proposals = proposalsForCompany(company.companyId());
        int py = proposalY + 16;
        if (proposals.isEmpty()) {
            FinanceGui.drawClippedString(screen.font(), g, "暂无提案。", 12, py, 180, FinanceGui.COL_TEXT_DIM);
            return;
        }
        int maxProposals = Math.min(proposals.size(), Math.max(0, (cardY + cardH - py - 4) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxProposals; i++) {
            FinanceMenu.CompanyProposalRow row = proposals.get(i);
            boolean active = "ACTIVE".equals(row.status());
            FinanceGui.drawClippedString(screen.font(), g, FinanceFormat.displayProposalType(row.type()) + " " + FinanceFormat.displayProposalValues(row),
                    12, py, 120, active ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "赞 " + row.yesVotes() + " 反 " + row.noVotes()
                            + " 需" + Math.round(row.passRatio() * 100) + "%",
                    138, py, 92, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, active ? "截止" + row.endMcDay() : FinanceFormat.displayProposalStatus(row),
                    236, py, 58, active ? FinanceGui.COL_WARN : ("PASSED".equals(row.status()) ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD));
            if (active && !row.playerVoted()) {
                FinanceGui.drawButton(screen.font(), g, "赞成", 300, py - 2, 36, false);
                FinanceGui.drawButton(screen.font(), g, "反对", 340, py - 2, 36, false);
            }
            py += FinanceGui.ROW_HEIGHT;
        }
    }
    public void renderRestructuringTasks(GuiGraphics g,int cardY){
        int cardH=screen.imageHeight()-cardY-8;
        g.fill(8,cardY,screen.imageWidth()-8,cardY+cardH,0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g,8,cardY,screen.imageWidth()-16,cardH,FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g,"跨公司重组任务",10,cardY+2);
        FinanceGui.drawButton(screen.font(), g,"返回",340,cardY+2,36,false);
        FinanceGui.drawClippedString(screen.font(), g,"紧急注资允许外部投资者执行；资产交易必须由提案指定卖方的经营者确认。",12,cardY+20,360,FinanceGui.COL_TEXT_DIM);
        List<FinanceMenu.CompanyProposalRow> tasks=executableRestructuringTasks();
        int y=cardY+42;
        if(tasks.isEmpty()){FinanceGui.drawClippedString(screen.font(), g,"目前没有等待执行的重组提案。",12,y,300,FinanceGui.COL_TEXT_DIM);return;}
        for(FinanceMenu.CompanyProposalRow row:tasks){
            if(y+FinanceGui.ROW_HEIGHT>cardY+cardH-4)break;
            boolean recap="EMERGENCY_RECAPITALIZATION".equals(row.type());
            String company=FinanceFormat.companyName(screen, row.companyId());
            String details=recap?"目标 "+row.value1():"价格 "+row.value1()+" 数量 "+row.value2();
            FinanceGui.drawClippedString(screen.font(), g,(recap?"紧急注资 ":"资产购买 ")+company,12,y+3,150,FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g,details,166,y+3,154,FinanceGui.COL_TEXT_DIM);
            boolean pending=GovernanceTaskClientState.isPending(row.proposalId());
            FinanceGui.drawButton(screen.font(), g,pending?"处理中":recap?"投资":"卖方确认",322,y+1,54,pending);
            y+=FinanceGui.ROW_HEIGHT;
        }
    }
    public List<FinanceMenu.CompanyProposalRow> executableRestructuringTasks(){
        return screen.menu().getCompanyProposalRows().stream()
                .filter(p->"PASSED".equals(p.status()))
                .filter(p->"EMERGENCY_RECAPITALIZATION".equals(p.type())||"MAJOR_ASSET_PURCHASE".equals(p.type()))
                .filter(FinanceMenu.CompanyProposalRow::playerCanExecute)
                .filter(p->!GovernanceTaskClientState.hasSucceeded(p.proposalId())).toList();
    }

    // ================================================================
    // 标签 5: 股票
    // ================================================================

    public void renderGovernanceCompany(GuiGraphics g, FinanceMenu.CompanyInfo company, int cardY) {
        int cardH = screen.imageHeight() - cardY - 8;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFE0E0E0);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth() - 16, cardH, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawSectionTitle(screen.font(), g, "公司治理与资本结构", 10, cardY + 2);
        FinanceGui.drawButton(screen.font(), g, "返回", 340, cardY + 2, 36, false);
        GovernanceClientCache.Entry entry = GovernanceClientCache.get();
        GovernanceResponsePacket data = entry.data();
        if (entry.state() == GovernanceClientCache.State.NOT_REQUESTED) requestGovernance(company.companyId());
        if (data == null || !data.companyId().equals(company.companyId())) {
            FinanceGui.drawClippedString(screen.font(), g, entry.state() == GovernanceClientCache.State.SLOW
                    ? "治理数据响应较慢，可返回后重试" : "正在加载治理数据…", 12, cardY + 24, 330, FinanceGui.COL_WARN);
            return;
        }
        FinanceGui.drawClippedString(screen.font(), g, "总股本 " + data.totalShares() + "  表决股 " + data.votingShares()
                + "  库存股 " + data.treasuryShares(), 12, cardY + 22, 360, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "市值 " + data.valuation().marketCap() + "  企业价值 "
                + data.valuation().enterpriseValue() + "  账面净资产 " + data.valuation().bookEquity(),
                12, cardY + 38, 360, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "现金 " + data.structure().cash() + "  贷款 "
                + data.structure().bankAndOtherLoans() + "  债券 " + data.structure().bonds()
                + "  近期债务 " + data.structure().nearTermDebt(), 12, cardY + 54, 360, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g, "估值质量 " + data.valuation().quality() + "  控制者 "
                + (data.controller() == null ? "分散持有" : data.controller().toString().substring(0, 8)),
                12, cardY + 70, 360, FinanceGui.COL_WARN);
        FinanceGui.drawSectionTitle(screen.font(), g, "主要股东", 10, cardY + 88);
        int holderY = cardY + 104;
        for (GovernanceResponsePacket.HolderRow holder : data.holders().stream().limit(1).toList()) {
            FinanceGui.drawClippedString(screen.font(), g, holder.id().toString().substring(0, 8), 12, holderY, 70, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, "持股 " + holder.shares() + "  冻结 " + holder.locked()
                    + "  表决权 " + String.format(Locale.ROOT, "%.1f%%", holder.voting()), 88, holderY, 280, FinanceGui.COL_TEXT_DIM);
        }
        if (data.holders().isEmpty()) FinanceGui.drawClippedString(screen.font(), g, "暂无股东", 12, holderY, 160, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawSectionTitle(screen.font(), g, "公司行动", 10, cardY + 120);
        int actionY = cardY + 134;
        if (data.actions().isEmpty()) FinanceGui.drawClippedString(screen.font(), g, "暂无回购或收购要约", 12, actionY, 260, FinanceGui.COL_TEXT_DIM);
        for (GovernanceResponsePacket.ActionRow action : data.actions().stream().limit(1).toList()) {
            FinanceGui.drawClippedString(screen.font(), g, action.type() + "  " + action.status() + "  价格 " + action.price()
                    + "  股份 " + action.shares() + "  截止 D" + action.endDay(), 12, actionY, 360, FinanceGui.COL_TEXT);
        }
        int controlsY=cardY+160;
        FinanceGui.drawClippedString(screen.font(), g,"目标/卖方",12,cardY+146,90,FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g,"价格",12,controlsY+4,34,FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g,"股份",132,controlsY+4,34,FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font(), g,"期限",252,controlsY+4,34,FinanceGui.COL_TEXT_DIM);
        int buttonY = controlsY + 23;
        FinanceGui.drawButton(screen.font(), g,"回购",10,buttonY,48,false);
        FinanceGui.drawButton(screen.font(), g,"注销",62,buttonY,48,false);
        FinanceGui.drawButton(screen.font(), g,"接受",114,buttonY,48,false);
        FinanceGui.drawButton(screen.font(), g,"收购",166,buttonY,48,false);
        FinanceGui.drawButton(screen.font(), g,"注资",218,buttonY,48,false);
        FinanceGui.drawButton(screen.font(), g,"资产",270,buttonY,48,false);
        FinanceGui.drawButton(screen.font(), g,"刷新",322,buttonY,54,false);
    }
    public void requestGovernance(UUID companyId) {
        long requestId = GovernanceClientCache.begin();
        FinancePacketHandler.CHANNEL.sendToServer(new GovernanceRequestPacket(requestId, companyId));
    }

    // ---- 标签 4 点击: 公司 ----

    public boolean handleCompanyClick(int mx, int my) {
        int cardY = FinanceGui.CONTENT_Y;
        FinanceMenu.CompanyInfo playerCompany = screen.menu().getPlayerCompany();
        if(screen.restructuringView){
            if(mx>=340&&mx<376&&my>=cardY+2&&my<cardY+15){screen.restructuringView=false;screen.updateInputVisibility();return true;}
            int y=cardY+42;
            for(FinanceMenu.CompanyProposalRow proposal:executableRestructuringTasks()){
                if(y+FinanceGui.ROW_HEIGHT>screen.imageHeight()-12)break;
                if(mx>=322&&mx<376&&my>=y+1&&my<y+14){
                    if(!GovernanceTaskClientState.begin(proposal.proposalId()))return true;
                    GovernanceActionPacket.Action action="EMERGENCY_RECAPITALIZATION".equals(proposal.type())
                            ?GovernanceActionPacket.Action.EXECUTE_RECAPITALIZATION:GovernanceActionPacket.Action.EXECUTE_ASSET_PURCHASE;
                    FinancePacketHandler.CHANNEL.sendToServer(new GovernanceActionPacket(action,proposal.proposalId(),0,0,0,0,UUID.randomUUID().toString()));
                    screen.setStatus(action==GovernanceActionPacket.Action.EXECUTE_RECAPITALIZATION?"已提交外部注资":"已提交卖方确认");
                    return true;
                }
                y+=FinanceGui.ROW_HEIGHT;
            }
            return false;
        }
        if (playerCompany != null) {
            if (screen.governanceView) {
                if (mx >= 340 && mx < 376 && my >= cardY + 2 && my < cardY + 15) {
                    screen.governanceView = false;
                    screen.updateInputVisibility();
                    return true;
                }
                int controlsY=cardY+160;
                java.util.function.LongSupplier price=()->screen.requireAmount(screen.stockPriceBox,"价格");
                java.util.function.LongSupplier quantity=()->screen.requireAmount(screen.stockQuantityBox,"股份");
                java.util.function.IntSupplier duration=()->screen.requireInt(screen.companyAmountBox,"期限",1,90);
                if(my>=controlsY+23&&my<controlsY+36){
                    if(mx>=10&&mx<58){sendProposal(playerCompany.companyId(),CompanyProposalType.SHARE_BUYBACK,"",price.getAsLong(),quantity.getAsLong(),duration.getAsInt());return true;}
                    if(mx>=62&&mx<110){sendProposal(playerCompany.companyId(),CompanyProposalType.TREASURY_RETIREMENT,"",quantity.getAsLong(),0,0);return true;}
                    if(mx>=114&&mx<162){
                        GovernanceResponsePacket data=GovernanceClientCache.get().data();
                        GovernanceResponsePacket.ActionRow action=data==null?null:data.actions().stream().filter(a->a.status()==finance.governance.CapitalActionStatus.OPEN).findFirst().orElse(null);
                        if(action==null){screen.setStatus("没有可接受的开放行动");return true;}
                        GovernanceActionPacket.Action kind="TENDER".equals(action.type())?GovernanceActionPacket.Action.ACCEPT_TENDER:GovernanceActionPacket.Action.ACCEPT_BUYBACK;
                        FinancePacketHandler.CHANNEL.sendToServer(new GovernanceActionPacket(kind,action.id(),0,quantity.getAsLong(),0,0,UUID.randomUUID().toString()));
                        screen.setStatus("已提交行动接受请求");return true;
                    }
                    String target=screen.companyNameBox.getValue().trim();
                    if(mx>=166&&mx<214){
                        try{UUID targetCompany=UUID.fromString(target);FinancePacketHandler.CHANNEL.sendToServer(new GovernanceActionPacket(GovernanceActionPacket.Action.START_TENDER,targetCompany,price.getAsLong(),quantity.getAsLong(),quantity.getAsLong(),duration.getAsInt(),UUID.randomUUID().toString()));screen.setStatus("已提交公开收购要约");}catch(IllegalArgumentException invalid){screen.setStatus("请输入目标公司的完整 UUID");}return true;
                    }
                    if(mx>=218&&mx<266){sendProposal(playerCompany.companyId(),CompanyProposalType.EMERGENCY_RECAPITALIZATION,"",price.getAsLong(),0,0);return true;}
                    if(mx>=270&&mx<318){sendProposal(playerCompany.companyId(),CompanyProposalType.MAJOR_ASSET_PURCHASE,target,price.getAsLong(),quantity.getAsLong(),0);return true;}
                    if(mx>=322&&mx<376){GovernanceClientCache.clear();requestGovernance(playerCompany.companyId());screen.setStatus("正在刷新治理数据");return true;}
                }
                return false;
            }
            if (screen.companyDetailsView) {
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
                    if (mx >= 10 && mx < 58) { sendProposal(playerCompany.companyId(), CompanyProposalType.DIVIDEND, "", amount.getAsLong(), 0, 0); return true; }
                    if (mx >= 64 && mx < 112) { sendProposal(playerCompany.companyId(), CompanyProposalType.SHARE_ISSUE, "", quantity.getAsLong(), price.getAsLong(), amount.getAsLong()); return true; }
                    if (mx >= 118 && mx < 166) { sendProposal(playerCompany.companyId(), CompanyProposalType.RENAME, text, 0, 0, 0); return true; }
                    if (mx >= 172 && mx < 220) { sendProposal(playerCompany.companyId(), CompanyProposalType.FUND_USAGE, text, amount.getAsLong(), 0, 0); return true; }
                }
                List<FinanceMenu.CompanyProposalRow> proposals = proposalsForCompany(playerCompany.companyId());
                int py = cardY + 144;
                int maxProposals = Math.min(proposals.size(), Math.max(0, (screen.imageHeight() - 8 - py - 4) / FinanceGui.ROW_HEIGHT));
                for (int i = 0; i < maxProposals; i++) {
                    FinanceMenu.CompanyProposalRow row = proposals.get(i);
                    if ("ACTIVE".equals(row.status()) && !row.playerVoted()) {
                        if (mx >= 300 && mx < 336 && my >= py - 2 && my < py + 11) {
                            FinancePacketHandler.CHANNEL.sendToServer(CompanyProposalPacket.vote(row.proposalId(), true));
                            screen.setStatus("已提交赞成票"); return true;
                        }
                        if (mx >= 340 && mx < 376 && my >= py - 2 && my < py + 11) {
                            FinancePacketHandler.CHANNEL.sendToServer(CompanyProposalPacket.vote(row.proposalId(), false));
                            screen.setStatus("已提交反对票"); return true;
                        }
                    }
                    py += FinanceGui.ROW_HEIGHT;
                }
                return false;
            }
            if (playerCompany.isPublic() && mx >= 338 && mx < 376 && my >= cardY + 14 && my < cardY + 27) {
                screen.governanceView = true;
                screen.companyDetailsView = false;
                GovernanceClientCache.clear();
                requestGovernance(playerCompany.companyId());
                screen.updateInputVisibility();
                return true;
            }
            if(mx>=224&&mx<272&&my>=cardY+14&&my<cardY+27){screen.restructuringView=true;screen.governanceView=false;screen.companyDetailsView=false;screen.updateInputVisibility();return true;}
            if(mx>=276&&mx<334&&my>=cardY+14&&my<cardY+27){screen.companyDetailsView=true;screen.governanceView=false;screen.restructuringView=false;screen.updateInputVisibility();return true;}
            if (mx >= 58 && mx < 176 && my >= cardY + 62 && my < cardY + 75) {
                CompanyStrategy[] strategies = CompanyStrategy.values();
                int idx = 0;
                for (int i = 0; i < strategies.length; i++) {
                    if (strategies[i].getDisplayName().equals(FinanceFormat.displayCompanyStrategy(screen, playerCompany))) {
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
                    FinancePacketHandler.CHANNEL.sendToServer(new CompanyManagePacket(CompanyManagementAction.UPGRADE_PRODUCTION));
                    screen.setStatus("已提交生产线升级");
                    return true;
                }
                if (mx >= 216 && mx < 252) {
                    FinancePacketHandler.CHANNEL.sendToServer(new CompanyManagePacket(CompanyManagementAction.UPGRADE_STORAGE));
                    screen.setStatus("已提交仓储升级");
                    return true;
                }
                if (mx >= 336 && mx < 372) {
                    FinancePacketHandler.CHANNEL.sendToServer(new CompanyManagePacket(CompanyManagementAction.UPGRADE_MANAGEMENT));
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
                FinancePacketHandler.CHANNEL.sendToServer(
                        new CompanyIPOPacket(playerCompany.companyId(), price, quantity));
                return true;
            }
            if (playerCompany.isPublic() && financingForCompany(playerCompany.companyId()) == null
                    && mx >= 322 && mx < 372 && my >= cardY + 136 && my < cardY + 149) {
                long price = screen.requireAmount(screen.ipoPriceBox, "增发价");
                long quantity = screen.requireAmount(screen.ipoQuantityBox, "增发数");
                long target = screen.requireAmount(screen.companyAmountBox, "目标金额");
                screen.setStatus("已提交融资申请");
                FinancePacketHandler.CHANNEL.sendToServer(
                        CompanyFinancingPacket.start(playerCompany.companyId(), quantity, price, target));
                return true;
            }
            return false;
        } else if(mx>=210&&mx<286&&my>=cardY+58&&my<cardY+71){screen.restructuringView=true;screen.updateInputVisibility();return true;}

        if (mx >= 55 && mx < 175 && my >= cardY + 58 && my < cardY + 71) {
            int idx = 0;
            for (int i = 0; i < COMPANY_TYPES.length; i++) {
                if (COMPANY_TYPES[i] == screen.selectedType) { idx = i; break; }
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
    public FinanceMenu.CompanyFinancingRow financingForCompany(UUID companyId) {
        if (companyId == null) {
            return null;
        }
        for (FinanceMenu.CompanyFinancingRow row : screen.menu().getCompanyFinancingRows()) {
            if (companyId.equals(row.companyId())) {
                return row;
            }
        }
        return null;
    }
    public List<FinanceMenu.CompanyProposalRow> proposalsForCompany(UUID companyId) {
        List<FinanceMenu.CompanyProposalRow> rows = new ArrayList<>();
        if (companyId == null) {
            return rows;
        }
        for (FinanceMenu.CompanyProposalRow row : screen.menu().getCompanyProposalRows()) {
            if (companyId.equals(row.companyId())) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing((FinanceMenu.CompanyProposalRow row) -> !"ACTIVE".equals(row.status()))
                .thenComparingLong(FinanceMenu.CompanyProposalRow::endMcDay));
        return rows;
    }
    public void sendProposal(UUID companyId, CompanyProposalType type, String text, long value1, long value2, long value3) {
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
