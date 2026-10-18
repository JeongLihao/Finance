package finance.client;

import finance.client.simple.SimpleClientState;
import finance.client.ui.UiBuilder;
import finance.client.ui.UiElement;
import finance.client.ui.UiPainter;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.FinanceMenu;
import finance.network.CompanyManagePacket;
import finance.network.CreateCompanyPacket;
import finance.network.FinancePacketHandler;
import finance.network.SimpleActionPacket;
import finance.network.StockTradePacket;
import finance.util.MoneyFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

/**
 * 简单模式金融中心接到 {@link FinanceScreen} 上：每帧用 {@link SimplePages} 排版，再画出来；
 * 点击和悬停都按这一帧排出的元素判断。
 */
final class SimpleCenter {

    private final FinanceScreen screen;
    final SimplePages.State state = new SimplePages.State();
    private List<UiElement> elements = List.of();
    private final SimplePages.Actions actions = new SimplePages.Actions() {
        @Override public void simple(SimpleActionPacket.Action action, String target, long amount) {
            screen.setStatus("处理中……");
            FinancePacketHandler.CHANNEL.sendToServer(new SimpleActionPacket(action, target, amount));
        }
        @Override public void company(CompanyManagementAction action, long amount) {
            screen.setStatus("处理中……");
            FinancePacketHandler.CHANNEL.sendToServer(CompanyManagePacket.amount(action, amount));
        }
        @Override public void createCompany(CompanyType type) {
            String name = screen.companyNameBox.getValue().trim();
            if (name.isEmpty()) { screen.setStatus("先给公司起个名字。"); return; }
            if (name.length() > 32) { screen.setStatus("公司名字最多 32 个字。"); return; }
            screen.setStatus("正在开公司……");
            FinancePacketHandler.CHANNEL.sendToServer(new CreateCompanyPacket(type, name));
        }
        @Override public void stock(boolean buy, String symbol, int quantity) {
            if (quantity <= 0) return;
            screen.setStatus(buy ? "正在买入……" : "正在卖出……");
            FinancePacketHandler.CHANNEL.sendToServer(new StockTradePacket(
                    buy ? StockTradePacket.ActionType.BUY : StockTradePacket.ActionType.SELL, symbol, quantity));
        }
        @Override public void status(String message) { screen.setStatus(message); }
        @Override public void show(SimplePages.Page page) { SimpleCenter.this.show(page); }
        @Override public void dismissSummary() { SimpleClientState.dismissSummary(); }
        @Override public void setWantsAdvanced(boolean on) {
            SimpleClientState.setPlayerWantsAdvanced(on);
            screen.setStatus(on ? "已显示高级功能：右上角“高级”里是原有的全部页面。" : "已隐藏高级功能。");
        }
    };

    SimpleCenter(FinanceScreen screen) {
        this.screen = screen;
    }

    void setPage(SimplePages.Page page) { state.page = page; }

    void show(SimplePages.Page page) {
        state.page = page;
        screen.updateInputVisibility();
    }

    SimplePages.View view() {
        FinanceMenu menu = screen.menu();
        FinanceMenu.AssetSummary assets = menu.getAssetSummary();
        return new SimplePages.View(menu.getBalance(), assets == null ? 0 : assets.todayProfit(),
                assets == null ? 0 : assets.stockValue(), menu.getMcInventory(), menu.getMarketData(),
                menu.getTransactions(), menu.getPlayerCompany(), menu.getStocks(), menu.getStockHoldings(),
                SimpleClientState.status(), SimpleClientState.playerChooses(), SimpleClientState.playerWantsAdvanced(),
                SimpleClientState.pendingSummary());
    }

    /** 按当前数据重新排版。 */
    void relayout() {
        UiBuilder ui = new UiBuilder(screen.font()::width, screen::setStatus);
        SimplePages.layout(ui, view(), state, actions);
        elements = ui.elements();
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        relayout();
        UiPainter.paint(g, screen.font(), elements, mouseX, mouseY);
        renderFloaters(g);
    }

    void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        UiPainter.tooltip(g, screen.font(), elements, mouseX, mouseY);
    }

    boolean click(int mx, int my) {
        boolean handled = UiPainter.click(elements, mx, my);
        if (handled) screen.updateInputVisibility();
        return handled;
    }

    boolean scroll(double delta) {
        int step = (int) -Math.signum(delta);
        if (state.page == SimplePages.Page.TRADE) {
            state.tradeScroll += step;
            return true;
        }
        if (state.page == SimplePages.Page.HOME && state.homeTab == 2) {
            state.recordScroll += step;
            return true;
        }
        if (state.page == SimplePages.Page.INVEST) {
            state.investScroll += step;
            return true;
        }
        return false;
    }

    /** 简单模式只用公司名输入框：放到这一页排版里留出的位置；没有就隐藏。 */
    void updateInputs(EditBox nameBox) {
        relayout();
        UiElement slot = inputSlot("companyName");
        nameBox.setVisible(slot != null);
        if (slot != null) {
            nameBox.setX(screen.leftPos() + slot.x());
            nameBox.setY(screen.topPos() + slot.y());
            nameBox.setWidth(slot.w());
        }
    }

    List<EditBox> visibleBoxes(EditBox nameBox) {
        return inputSlot("companyName") != null ? List.of(nameBox) : List.of();
    }

    private UiElement inputSlot(String key) {
        for (UiElement e : elements) if (e.kind() == UiElement.Kind.INPUT && key.equals(e.key())) return e;
        return null;
    }

    // ---------------------------------------------------------------- 飘字

    private void renderFloaters(GuiGraphics g) {
        long now = System.currentTimeMillis();
        int i = 0;
        for (SimpleClientState.Floater f : SimpleClientState.floaters()) {
            float t = (now - f.createdAt()) / (float) SimpleClientState.FLOAT_MILLIS;
            int alpha = Math.max(16, (int) (255 * (1 - t)));
            int color = (alpha << 24) | ((f.delta() > 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD) & 0xFFFFFF);
            String label = MoneyFormat.signed(f.delta());
            g.pose().pushPose();
            g.pose().translate(FinanceGui.PANEL_WIDTH / 2.0F, SimplePages.TOP + 70 - t * 30 - i * 12, 400);
            g.pose().scale(1.5F, 1.5F, 1.0F);
            g.drawString(screen.font(), label, -screen.font().width(label) / 2, 0, color, false);
            g.pose().popPose();
            i++;
        }
    }
}
