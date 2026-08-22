package finance.client;

import finance.gui.WarehouseMenu;
import finance.network.FinancePacketHandler;
import finance.network.WarehouseActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.UUID;

public final class WarehouseScreen extends AbstractContainerScreen<WarehouseMenu> {
    private int selected;
    private int selectedContract;
    private int rowOffset;
    private int contractOffset;
    private boolean pending;
    private EditBox amount;

    public WarehouseScreen(WarehouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 400;
        imageHeight = 280;
        inventoryLabelY = 10_000;
    }

    @Override protected void init() {
        super.init();
        selected = Math.min(selected, Math.max(0, menu.rows().size() - 1));
        selectedContract = Math.min(selectedContract, Math.max(0, menu.contracts().size() - 1));
        amount = new EditBox(font, leftPos + 250, topPos + 226, 55, 16, Component.translatable("screen.finance.warehouse.amount"));
        amount.setMaxLength(8);
        amount.setValue("1");
        addRenderableWidget(amount);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFE7E2D3);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 10, 9, 0xFF202020, false);
        g.drawString(font, Component.translatable("screen.finance.warehouse.summary", menu.ownerName(),
                menu.used(), menu.capacity(), menu.status().name()), 10, 25, 0xFF555555, false);
        g.drawString(font, Component.translatable("screen.finance.warehouse.columns"), 12, 44, 0xFF555555, false);
        int y = 60;
        int end = Math.min(menu.rows().size(), rowOffset + 6);
        for (int i = rowOffset; i < end; i++) {
            WarehouseMenu.CommodityRow row = menu.rows().get(i);
            if (i == selected) g.fill(8, y - 2, 392, y + 10, 0xFFD5E3C7);
            String text = row.name() + " [" + row.id() + "]   " + row.custodyAmount() + "   "
                    + row.inventoryAmount() + (row.physical() ? "" : "  (虚拟)");
            g.drawString(font, font.plainSubstrByWidth(text, 374), 12, y, row.physical() ? 0xFF202020 : 0xFF8A6500, false);
            y += 12;
        }
        g.drawString(font, Component.translatable("screen.finance.contracts"), 10, 136, 0xFF202020, false);
        int contractY = 151;
        for (int i = contractOffset; i < Math.min(menu.contracts().size(), contractOffset + 4); i++) {
            WarehouseMenu.ContractRow row = menu.contracts().get(i);
            if (i == selectedContract) g.fill(8, contractY - 2, 392, contractY + 10, 0xFFD5E3C7);
            String line = row.commodityId() + " x" + row.quantity() + "  奖励 " + row.reward()
                    + "  D" + row.deadlineDay() + "  " + row.status();
            g.drawString(font, font.plainSubstrByWidth(line, 374), 12, contractY, 0xFF202020, false);
            contractY += 12;
        }
        if (!menu.statusKey().isBlank()) {
            g.drawString(font, Component.translatable(menu.statusKey(), menu.statusAmount()), 10, 208,
                    menu.statusKey().contains("success") ? 0xFF2D7D32 : 0xFF9A2F2F, false);
        }
        g.drawString(font, Component.translatable("screen.finance.warehouse.amount"), 210, 230, 0xFF555555, false);
        drawButton(g, 310, 226, 38, Component.translatable("screen.finance.warehouse.deposit"));
        drawButton(g, 352, 226, 38, Component.translatable("screen.finance.warehouse.withdraw"));
        drawButton(g, 310, 247, 38, Component.translatable("screen.finance.contract.accept"));
        drawButton(g, 352, 247, 38, Component.translatable("screen.finance.contract.complete"));
        drawButton(g, 210, 247, 44, Component.translatable("screen.finance.warehouse.bind_company"));
        drawButton(g, 258, 247, 44, Component.translatable("screen.finance.warehouse.unbind_company"));
        g.drawString(font, Component.translatable("screen.finance.warehouse.lock_hint"), 10, 267, 0xFF555555, false);
    }

    private void drawButton(GuiGraphics g, int x, int y, int width, Component text) {
        g.fill(x, y, x + width, y + 16, 0xFFD0CAB8);
        g.drawCenteredString(font, text, x + width / 2, y + 4, 0xFF202020);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pending) return true;
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        if (mx >= 8 && mx < 392 && my >= 58 && my < 58 + Math.min(6, menu.rows().size()) * 12) {
            selected = Math.min(menu.rows().size() - 1, rowOffset + Math.max(0, (my - 58) / 12));
            return true;
        }
        if (mx >= 8 && mx < 392 && my >= 149 && my < 149 + Math.min(4, menu.contracts().size()) * 12) {
            selectedContract = Math.min(menu.contracts().size() - 1,
                    contractOffset + Math.max(0, (my - 149) / 12));
            return true;
        }
        if (my >= 226 && my < 242 && selected >= 0 && selected < menu.rows().size()) {
            WarehouseActionPacket.Action action = mx >= 310 && mx < 348 ? WarehouseActionPacket.Action.DEPOSIT
                    : mx >= 352 && mx < 390 ? WarehouseActionPacket.Action.WITHDRAW : null;
            if (action != null) {
                int requested;
                try { requested = Integer.parseInt(amount.getValue()); } catch (NumberFormatException ex) { return true; }
                FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(action, menu.warehouseId(),
                        menu.rows().get(selected).id(), requested, UUID.randomUUID().toString()));
                pending = true;
                return true;
            }
        }
        if (my >= 247 && my < 263 && selectedContract >= 0 && selectedContract < menu.contracts().size()) {
            finance.network.ContractActionPacket.Action action = mx >= 310 && mx < 348
                    ? finance.network.ContractActionPacket.Action.ACCEPT
                    : mx >= 352 && mx < 390 ? finance.network.ContractActionPacket.Action.COMPLETE : null;
            if (action != null) {
                FinancePacketHandler.CHANNEL.sendToServer(new finance.network.ContractActionPacket(action,
                        menu.contracts().get(selectedContract).id(), menu.warehouseId(), UUID.randomUUID().toString()));
                pending = true;
                return true;
            }
        }
        if (my >= 247 && my < 263 && (mx >= 210 && mx < 254 || mx >= 258 && mx < 302)) {
            WarehouseActionPacket.Action action = mx < 254 ? WarehouseActionPacket.Action.BIND_COMPANY
                    : WarehouseActionPacket.Action.UNBIND_COMPANY;
            FinancePacketHandler.CHANNEL.sendToServer(new WarehouseActionPacket(action, menu.warehouseId(),
                    "company", 1, UUID.randomUUID().toString()));
            pending = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int my = (int) mouseY - topPos;
        int direction = delta > 0 ? -1 : delta < 0 ? 1 : 0;
        if (direction == 0) return false;
        if (my >= 44 && my < 134) {
            rowOffset = Math.max(0, Math.min(Math.max(0, menu.rows().size() - 6), rowOffset + direction));
            return true;
        }
        if (my >= 136 && my < 203) {
            contractOffset = Math.max(0, Math.min(Math.max(0, menu.contracts().size() - 4),
                    contractOffset + direction));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
