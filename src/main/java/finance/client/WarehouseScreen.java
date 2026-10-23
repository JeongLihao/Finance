package finance.client;

import finance.gameplay.company.capital.CapitalFundingSource;
import finance.gameplay.company.capital.WorldCapitalProjectType;
import finance.gui.WarehouseMenu;
import finance.network.CapitalProjectActionPacket;
import finance.network.ContractActionPacket;
import finance.network.FinancePacketHandler;
import finance.network.WarehouseActionPacket;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WarehouseScreen extends AbstractContainerScreen<WarehouseMenu> {
    static final int PANEL_WIDTH = 320;
    static final int PANEL_HEIGHT = 240;
    static final int COMMODITY_ROWS = 5;
    static final int CONTRACT_ROWS = 3;

    private int selected;
    private int selectedContract;
    private int rowOffset;
    private int contractOffset;
    private int shipmentOffset;
    private boolean showShipments;
    private boolean pending;
    /** 画的时候登记的点击区域（面板内坐标）。 */
    private final ClickRegions clicks = new ClickRegions();

    private int pendingTicks;
    /** 客户端预校验失败的提示（已翻译），优先于服务端状态显示。 */
    private String localStatus = "";

    private CapitalFundingSource capitalFundingSource = CapitalFundingSource.RETAINED_EARNINGS;
    private EditBox amount;

    public WarehouseScreen(WarehouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        selected = clampSelection(selected, menu.rows().size());
        selectedContract = clampSelection(selectedContract, menu.contracts().size());
        rowOffset = clampOffset(rowOffset, menu.rows().size(), COMMODITY_ROWS);
        contractOffset = clampOffset(contractOffset, menu.contracts().size(), CONTRACT_ROWS);
        shipmentOffset = clampOffset(shipmentOffset, menu.shipments().size(), CONTRACT_ROWS);
        amount = new EditBox(
                font, leftPos + 52, topPos + 202, 55, 16, Component.translatable("screen.finance.warehouse.amount"));
        amount.setMaxLength(8);
        amount.setValue("1");
        addRenderableWidget(amount);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFE7E2D3);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        clicks.clear();
        drawClipped(graphics, title.getString(), 10, 9, 300, 0xFF202020);
        drawClipped(
                graphics,
                Component.translatable(
                                "screen.finance.warehouse.summary",
                                menu.ownerName(),
                                menu.used(),
                                menu.capacity(),
                                FinanceFormat.label(menu.status()))
                        .getString(),
                10,
                25,
                300,
                0xFF555555);
        drawClipped(
                graphics,
                Component.translatable("screen.finance.warehouse.columns").getString(),
                12,
                43,
                296,
                0xFF555555);

        int y = 59;
        int end = Math.min(menu.rows().size(), rowOffset + COMMODITY_ROWS);
        for (int i = rowOffset; i < end; i++) {
            WarehouseMenu.CommodityRow row = menu.rows().get(i);
            int index = i;
            clicks.add(8, y - 2, 304, 12, () -> selected = index);
            if (i == selected) graphics.fill(8, y - 2, 312, y + 10, 0xFFD5E3C7);
            String text = row.name() + "  仓库 " + row.custodyAmount()
                    + (row.pledgedAmount() > 0 ? "（押 " + row.pledgedAmount() + "）" : "")
                    + "  可用 " + row.availableAmount() + "  背包 " + row.inventoryAmount()
                    + (row.physical() ? "" : "  (虚拟)");
            drawClipped(graphics, text, 12, y, 296, row.physical() ? 0xFF202020 : 0xFF8A6500);
            y += 12;
        }

        // 点标题行切换“合同 / 在途货物”
        clicks.add(8, 121, 304, 15, () -> showShipments = !showShipments);
        drawClipped(
                graphics,
                Component.translatable(
                                showShipments ? "screen.finance.logistics.shipments" : "screen.finance.contracts")
                        .getString(),
                10,
                123,
                300,
                0xFF202020);
        int contractY = 139;
        if (showShipments) {
            int shipmentEnd = Math.min(menu.shipments().size(), shipmentOffset + CONTRACT_ROWS);
            for (int i = shipmentOffset; i < shipmentEnd; i++) {
                WarehouseMenu.ShipmentRow row = menu.shipments().get(i);
                String line = FinanceFormat.commodityDisplayName(row.commodityId()) + " x" + row.quantity() + "  "
                        + row.source() + "→" + row.destination() + "  第" + row.deadlineDay() + "天前  "
                        + FinanceFormat.label(row.status());
                drawClipped(graphics, line, 12, contractY, 296, 0xFF202020);
                contractY += 12;
            }
        } else {
            int contractEnd = Math.min(menu.contracts().size(), contractOffset + CONTRACT_ROWS);
            for (int i = contractOffset; i < contractEnd; i++) {
                WarehouseMenu.ContractRow row = menu.contracts().get(i);
                int index = i;
                clicks.add(8, contractY - 2, 304, 12, () -> selectedContract = index);
                if (i == selectedContract) graphics.fill(8, contractY - 2, 312, contractY + 10, 0xFFD5E3C7);
                String line = FinanceFormat.commodityDisplayName(row.commodityId()) + " x" + row.quantity() + "  奖励 "
                        + row.reward() + "  第" + row.deadlineDay() + "天前  " + FinanceFormat.label(row.status());
                drawClipped(graphics, line, 12, contractY, 296, 0xFF202020);
                contractY += 12;
            }
        }

        if (!localStatus.isEmpty()) {
            drawClipped(graphics, localStatus, 10, 177, 300, 0xFF9A2F2F);
        } else if (!menu.statusKey().isBlank() && !menu.companyBound()) {
            drawClipped(
                    graphics,
                    Component.translatable(menu.statusKey(), menu.statusAmount())
                            .getString(),
                    10,
                    177,
                    300,
                    menu.statusKey().contains("success") || menu.statusKey().contains("completed")
                            ? 0xFF2D7D32
                            : 0xFF9A2F2F);
        }
        drawClipped(
                graphics,
                Component.translatable(
                                "screen.finance.warehouse.tier",
                                menu.tier(),
                                menu.transferLimit(),
                                menu.upgradeMaterials())
                        .getString(),
                10,
                189,
                210,
                0xFF555555);
        if (menu.companyBound() && localStatus.isEmpty()) {
            String project = !menu.statusKey().isBlank()
                    ? Component.translatable(menu.statusKey(), menu.statusAmount())
                            .getString()
                    : menu.capitalProjectId() == null
                            ? Component.translatable("screen.finance.warehouse.capital_new", shortSource())
                                    .getString()
                            : Component.translatable(
                                            "screen.finance.warehouse.capital_status",
                                            menu.capitalStatus(),
                                            menu.capitalFunded(),
                                            menu.capitalBudget())
                                    .getString();
            drawClipped(graphics, project, 10, 177, 300, 0xFF6A5220);
        }

        drawClipped(
                graphics,
                Component.translatable("screen.finance.warehouse.amount").getString(),
                10,
                206,
                38,
                0xFF555555);
        drawButton(
                graphics,
                112,
                202,
                54,
                Component.translatable("screen.finance.warehouse.deposit"),
                b -> transfer(WarehouseActionPacket.Action.DEPOSIT));
        drawButton(
                graphics,
                170,
                202,
                54,
                Component.translatable("screen.finance.warehouse.withdraw"),
                b -> transfer(WarehouseActionPacket.Action.WITHDRAW));
        drawButton(
                graphics,
                228,
                202,
                82,
                Component.translatable(
                        menu.companyBound()
                                ? menu.capitalProjectId() == null
                                        ? "screen.finance.warehouse.capital_create"
                                        : "screen.finance.warehouse.capital_execute"
                                : "screen.finance.warehouse.upgrade"),
                this::upgrade);
        drawButton(
                graphics,
                10,
                221,
                66,
                Component.translatable("screen.finance.warehouse.bind_company"),
                b -> bind(WarehouseActionPacket.Action.BIND_COMPANY));
        drawButton(
                graphics,
                80,
                221,
                66,
                Component.translatable("screen.finance.warehouse.unbind_company"),
                b -> bind(WarehouseActionPacket.Action.UNBIND_COMPANY));
        drawButton(
                graphics,
                170,
                221,
                54,
                Component.translatable("screen.finance.contract.accept"),
                b -> contract(ContractActionPacket.Action.ACCEPT));
        drawButton(
                graphics,
                228,
                221,
                82,
                Component.translatable("screen.finance.contract.complete"),
                b -> contract(ContractActionPacket.Action.COMPLETE));
    }

    /** 画按钮并登记点击（参数是鼠标键）。 */
    private void drawButton(GuiGraphics graphics, int x, int y, int width, Component text, IntConsumer action) {
        clicks.addWithButton(x, y, width, 16, action);
        graphics.fill(x, y, x + width, y + 16, pending ? 0xFFAAA69A : 0xFFD0CAB8);
        String clipped = clip(text.getString(), width - 6);
        graphics.drawString(
                font,
                clipped,
                x + Math.max(3, (width - font.width(clipped)) / 2),
                y + 4,
                pending ? 0xFF666666 : 0xFF202020,
                false);
    }

    private void drawClipped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(font, clip(text, width), x, y, color, false);
    }

    private String clip(String text, int width) {
        return FinanceGui.clipText(font, text, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pending) return true;
        if (clicks.click((int) mouseX - leftPos, (int) mouseY - topPos, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 升级按钮：绑定公司后右键切换资金来源，左键发起或施工实体项目；未绑定时直接升级。 */
    private void upgrade(int button) {
        if (menu.companyBound()) {
            if (button == 1 && menu.capitalProjectId() == null) {
                CapitalFundingSource[] values = CapitalFundingSource.values();
                capitalFundingSource = values[(capitalFundingSource.ordinal() + 1) % values.length];
                return;
            }
            CapitalProjectActionPacket.Action action = menu.capitalProjectId() == null
                    ? CapitalProjectActionPacket.Action.CREATE
                    : CapitalProjectActionPacket.Action.EXECUTE;
            FinancePacketHandler.CHANNEL.sendToServer(new CapitalProjectActionPacket(
                    action,
                    menu.capitalProjectId(),
                    menu.warehouseId(),
                    null,
                    null,
                    menu.capitalProjectId() == null ? WorldCapitalProjectType.WAREHOUSE_UPGRADE : null,
                    capitalFundingSource,
                    UUID.randomUUID().toString()));
        } else {
            FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(
                    WarehouseActionPacket.Action.UPGRADE,
                    menu.warehouseId(),
                    "upgrade",
                    0,
                    UUID.randomUUID().toString()));
        }
        markPending();
    }

    private void transfer(WarehouseActionPacket.Action action) {
        if (selected < 0 || selected >= menu.rows().size()) return;
        // 服务端只接受 1..MAX_TRANSFER_AMOUNT，越界的包会被当作非法包断开连接
        int requested = ScreenInput.parsePositive(amount.getValue(), WarehouseActionPacket.MAX_TRANSFER_AMOUNT);
        if (requested < 0) {
            localStatus = Component.translatable(
                            "screen.finance.input.invalid_amount", WarehouseActionPacket.MAX_TRANSFER_AMOUNT)
                    .getString();
            return;
        }
        FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(
                action,
                menu.warehouseId(),
                menu.rows().get(selected).id(),
                requested,
                UUID.randomUUID().toString()));
        markPending();
    }

    private void contract(ContractActionPacket.Action action) {
        if (showShipments
                || selectedContract < 0
                || selectedContract >= menu.contracts().size()) return;
        FinancePacketHandler.CHANNEL.sendToServer(new ContractActionPacket(
                action,
                menu.contracts().get(selectedContract).id(),
                menu.warehouseId(),
                UUID.randomUUID().toString()));
        markPending();
    }

    private void bind(WarehouseActionPacket.Action action) {
        FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(
                action, menu.warehouseId(), "company", 1, UUID.randomUUID().toString()));
        markPending();
    }

    private void markPending() {
        pending = true;
        pendingTicks = 0;
        localStatus = "";
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pending && ++pendingTicks >= ScreenInput.PENDING_TIMEOUT_TICKS) pending = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return ScreenInput.handleTextBoxKey(getFocused(), keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String shortSource() {
        return switch (capitalFundingSource) {
            case RETAINED_EARNINGS -> "留存收益";
            case COMMERCIAL_LOAN -> "银行贷款";
            case CORPORATE_BOND -> "企业债";
            case SHARE_ISSUE -> "增发";
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int my = (int) mouseY - topPos;
        int direction = delta > 0 ? -1 : delta < 0 ? 1 : 0;
        if (direction == 0) return false;
        if (my >= 43 && my < 121) {
            rowOffset = clampOffset(rowOffset + direction, menu.rows().size(), COMMODITY_ROWS);
            selected = keepVisible(selected, rowOffset, menu.rows().size(), COMMODITY_ROWS);
            return true;
        }
        if (my >= 123 && my < 175) {
            if (showShipments)
                shipmentOffset =
                        clampOffset(shipmentOffset + direction, menu.shipments().size(), CONTRACT_ROWS);
            else {
                contractOffset =
                        clampOffset(contractOffset + direction, menu.contracts().size(), CONTRACT_ROWS);
                selectedContract = keepVisible(
                        selectedContract, contractOffset, menu.contracts().size(), CONTRACT_ROWS);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    static int clampOffset(int offset, int size, int visibleRows) {
        return Math.max(0, Math.min(Math.max(0, size - visibleRows), offset));
    }

    private static int clampSelection(int selected, int size) {
        return size == 0 ? 0 : Math.max(0, Math.min(size - 1, selected));
    }

    static int keepVisible(int selected, int offset, int size, int visibleRows) {
        if (size <= 0) return 0;
        return Math.max(offset, Math.min(selected, Math.min(size - 1, offset + visibleRows - 1)));
    }
}
