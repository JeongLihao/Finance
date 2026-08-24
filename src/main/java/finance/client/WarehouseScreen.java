package finance.client;

import finance.gui.WarehouseMenu;
import finance.network.ContractActionPacket;
import finance.network.FinancePacketHandler;
import finance.network.WarehouseActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.UUID;

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
        amount = new EditBox(font, leftPos + 52, topPos + 202, 55, 16,
                Component.translatable("screen.finance.warehouse.amount"));
        amount.setMaxLength(8);
        amount.setValue("1");
        addRenderableWidget(amount);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                0xFFE7E2D3);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawClipped(graphics, title.getString(), 10, 9, 300, 0xFF202020);
        drawClipped(graphics, Component.translatable("screen.finance.warehouse.summary", menu.ownerName(),
                menu.used(), menu.capacity(), menu.status().name()).getString(), 10, 25, 300, 0xFF555555);
        drawClipped(graphics, Component.translatable("screen.finance.warehouse.columns").getString(),
                12, 43, 296, 0xFF555555);

        int y = 59;
        int end = Math.min(menu.rows().size(), rowOffset + COMMODITY_ROWS);
        for (int i = rowOffset; i < end; i++) {
            WarehouseMenu.CommodityRow row = menu.rows().get(i);
            if (i == selected) graphics.fill(8, y - 2, 312, y + 10, 0xFFD5E3C7);
            String text = row.name() + " [" + row.id() + "]   " + row.custodyAmount() + "   "
                    + row.inventoryAmount() + (row.physical() ? "" : "  (虚拟)");
            drawClipped(graphics, text, 12, y, 296, row.physical() ? 0xFF202020 : 0xFF8A6500);
            y += 12;
        }

        drawClipped(graphics, Component.translatable(showShipments
                        ? "screen.finance.logistics.shipments" : "screen.finance.contracts").getString(),
                10, 123, 300, 0xFF202020);
        int contractY = 139;
        if (showShipments) {
            int shipmentEnd = Math.min(menu.shipments().size(), shipmentOffset + CONTRACT_ROWS);
            for (int i = shipmentOffset; i < shipmentEnd; i++) {
                WarehouseMenu.ShipmentRow row = menu.shipments().get(i);
                String line = row.commodityId() + " x" + row.quantity() + "  " + row.source()
                        + "→" + row.destination() + "  D" + row.deadlineDay() + "  " + row.status();
                drawClipped(graphics, line, 12, contractY, 296, 0xFF202020);
                contractY += 12;
            }
        } else {
            int contractEnd = Math.min(menu.contracts().size(), contractOffset + CONTRACT_ROWS);
            for (int i = contractOffset; i < contractEnd; i++) {
                WarehouseMenu.ContractRow row = menu.contracts().get(i);
                if (i == selectedContract) graphics.fill(8, contractY - 2, 312, contractY + 10, 0xFFD5E3C7);
                String line = row.commodityId() + " x" + row.quantity() + "  奖励 " + row.reward()
                        + "  D" + row.deadlineDay() + "  " + row.status();
                drawClipped(graphics, line, 12, contractY, 296, 0xFF202020);
                contractY += 12;
            }
        }

        if (!menu.statusKey().isBlank()) {
            drawClipped(graphics, Component.translatable(menu.statusKey(), menu.statusAmount()).getString(),
                    10, 177, 300, menu.statusKey().contains("success") ? 0xFF2D7D32 : 0xFF9A2F2F);
        }
        drawClipped(graphics, Component.translatable("screen.finance.warehouse.tier", menu.tier(),
                menu.transferLimit(), menu.upgradeMaterials()).getString(), 10, 189, 210, 0xFF555555);

        drawClipped(graphics, Component.translatable("screen.finance.warehouse.amount").getString(),
                10, 206, 38, 0xFF555555);
        drawButton(graphics, 112, 202, 54, Component.translatable("screen.finance.warehouse.deposit"));
        drawButton(graphics, 170, 202, 54, Component.translatable("screen.finance.warehouse.withdraw"));
        drawButton(graphics, 228, 202, 82, Component.translatable("screen.finance.warehouse.upgrade"));
        drawButton(graphics, 10, 221, 66, Component.translatable("screen.finance.warehouse.bind_company"));
        drawButton(graphics, 80, 221, 66, Component.translatable("screen.finance.warehouse.unbind_company"));
        drawButton(graphics, 170, 221, 54, Component.translatable("screen.finance.contract.accept"));
        drawButton(graphics, 228, 221, 82, Component.translatable("screen.finance.contract.complete"));
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, Component text) {
        graphics.fill(x, y, x + width, y + 16, pending ? 0xFFAAA69A : 0xFFD0CAB8);
        String clipped = clip(text.getString(), width - 6);
        graphics.drawString(font, clipped, x + Math.max(3, (width - font.width(clipped)) / 2), y + 4,
                pending ? 0xFF666666 : 0xFF202020, false);
    }

    private void drawClipped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(font, clip(text, width), x, y, color, false);
    }

    private String clip(String text, int width) {
        String value = text == null ? "" : text;
        if (font.width(value) <= width) return value;
        String ellipsis = "…";
        return width <= font.width(ellipsis) ? ""
                : font.plainSubstrByWidth(value, width - font.width(ellipsis)) + ellipsis;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pending) return true;
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        if (mx >= 8 && mx < 312 && my >= 57
                && my < 57 + Math.min(COMMODITY_ROWS, Math.max(0, menu.rows().size() - rowOffset)) * 12) {
            selected = Math.min(menu.rows().size() - 1, rowOffset + Math.max(0, (my - 57) / 12));
            return true;
        }
        if (mx >= 8 && mx < 312 && my >= 121 && my < 136) {
            showShipments = !showShipments;
            return true;
        }
        if (!showShipments && mx >= 8 && mx < 312 && my >= 137
                && my < 137 + Math.min(CONTRACT_ROWS, Math.max(0, menu.contracts().size() - contractOffset)) * 12) {
            selectedContract = Math.min(menu.contracts().size() - 1,
                    contractOffset + Math.max(0, (my - 137) / 12));
            return true;
        }
        if (my >= 202 && my < 218 && mx >= 228 && mx < 310) {
            FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(
                    WarehouseActionPacket.Action.UPGRADE, menu.warehouseId(), "upgrade", 0,
                    UUID.randomUUID().toString()));
            pending = true;
            return true;
        }
        if (my >= 202 && my < 218 && selected >= 0 && selected < menu.rows().size()) {
            WarehouseActionPacket.Action action = mx >= 112 && mx < 166
                    ? WarehouseActionPacket.Action.DEPOSIT
                    : mx >= 170 && mx < 224 ? WarehouseActionPacket.Action.WITHDRAW : null;
            if (action != null) {
                int requested;
                try {
                    requested = Integer.parseInt(amount.getValue());
                } catch (NumberFormatException exception) {
                    return true;
                }
                FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(action, menu.warehouseId(),
                        menu.rows().get(selected).id(), requested, UUID.randomUUID().toString()));
                pending = true;
                return true;
            }
        }
        if (!showShipments && my >= 221 && my < 237 && selectedContract >= 0 && selectedContract < menu.contracts().size()) {
            ContractActionPacket.Action action = mx >= 170 && mx < 224
                    ? ContractActionPacket.Action.ACCEPT
                    : mx >= 228 && mx < 310 ? ContractActionPacket.Action.COMPLETE : null;
            if (action != null) {
                FinancePacketHandler.CHANNEL.sendToServer(new ContractActionPacket(action,
                        menu.contracts().get(selectedContract).id(), menu.warehouseId(),
                        UUID.randomUUID().toString()));
                pending = true;
                return true;
            }
        }
        if (my >= 221 && my < 237 && (mx >= 10 && mx < 76 || mx >= 80 && mx < 146)) {
            WarehouseActionPacket.Action action = mx < 76 ? WarehouseActionPacket.Action.BIND_COMPANY
                    : WarehouseActionPacket.Action.UNBIND_COMPANY;
            FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(action, menu.warehouseId(),
                    "company", 1, UUID.randomUUID().toString()));
            pending = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
            if (showShipments) shipmentOffset = clampOffset(shipmentOffset + direction,
                    menu.shipments().size(), CONTRACT_ROWS);
            else {
                contractOffset = clampOffset(contractOffset + direction, menu.contracts().size(), CONTRACT_ROWS);
                selectedContract = keepVisible(selectedContract, contractOffset, menu.contracts().size(), CONTRACT_ROWS);
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
