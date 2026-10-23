package finance.client;

import finance.gameplay.company.CompanyMemberRole;
import finance.gameplay.company.CompanyPermission;
import finance.gui.CompanyGameplayMenu;
import finance.network.CompanyGameplayActionPacket;
import finance.network.FinancePacketHandler;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CompanyGameplayScreen extends AbstractContainerScreen<CompanyGameplayMenu> {
    static final int PANEL_WIDTH = 320;
    static final int PANEL_HEIGHT = 240;
    static final int MEMBER_ROWS = 5;
    static final int FACILITY_ROWS = 5;
    static final int CONTRACT_ROWS = 2;

    private int member;
    private int facility;
    private int memberOffset;
    private int facilityOffset;
    private int contractOffset;
    private int selectedContract;
    private boolean capitalTab;
    /** 画的时候登记的点击区域（面板内坐标）；每帧在 renderLabels 开头清空。 */
    final ClickRegions clicks = new ClickRegions();

    private final CompanyCapitalPanel capital = new CompanyCapitalPanel(this);

    private EditBox target;
    private EditBox commodity;
    private EditBox quantity;
    private EditBox reward;
    private boolean pending;
    private int pendingTicks;
    /** 客户端预校验失败的提示（已翻译），优先于服务端状态显示。 */
    private String localStatus = "";

    public CompanyGameplayScreen(CompanyGameplayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        member = clampSelection(member, menu.members().size());
        facility = clampSelection(facility, menu.facilities().size());
        memberOffset = clampOffset(memberOffset, menu.members().size(), MEMBER_ROWS);
        facilityOffset = clampOffset(facilityOffset, menu.facilities().size(), FACILITY_ROWS);
        contractOffset = clampOffset(contractOffset, menu.contracts().size(), CONTRACT_ROWS);
        selectedContract = clampSelection(selectedContract, menu.contracts().size());
        capital.clamp();
        capitalTab = menu.statusKey().startsWith("finance.capital_project.");
        target = box(10, 184, 90, "对方编号");
        commodity = box(104, 184, 60, "商品");
        quantity = box(168, 184, 40, "数量");
        reward = box(212, 184, 50, "金额");
        quantity.setMaxLength(8);
        reward.setMaxLength(12);
        quantity.setValue("10");
        reward.setValue("100");
        updateFieldVisibility();
    }

    private EditBox box(int x, int y, int width, String hint) {
        EditBox box = new EditBox(font, leftPos + x, topPos + y, width, 16, Component.literal(hint));
        box.setMaxLength(64);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333333);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFE8E2D2);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        clicks.clear();
        drawClipped(
                graphics,
                menu.name() + "  " + menu.mode() + "  " + menu.role(),
                10,
                9,
                300,
                menu.risk() ? 0xFFA02020 : 0xFF202020);
        drawClipped(
                graphics,
                Component.translatable("screen.finance.company_gameplay.cash", menu.cash())
                        .getString(),
                10,
                23,
                180,
                0xFF555555);
        tab(graphics, 200, 20, 52, "screen.finance.company_gameplay.tab.operations", !capitalTab);
        tab(graphics, 256, 20, 54, "screen.finance.company_gameplay.tab.capital", capitalTab);
        if (capitalTab) {
            capital.render(graphics);
            return;
        }

        drawClipped(
                graphics,
                Component.translatable("screen.finance.company_gameplay.members")
                        .getString(),
                10,
                42,
                144,
                0xFF202020);
        int y = 56;
        int memberEnd = Math.min(menu.members().size(), memberOffset + MEMBER_ROWS);
        for (int i = memberOffset; i < memberEnd; i++) {
            CompanyGameplayMenu.MemberRow row = menu.members().get(i);
            int memberIndex = i;
            clicks.add(8, y - 2, 146, 12, () -> member = memberIndex);
            if (i == member) graphics.fill(8, y - 2, 154, y + 10, 0xFFD5E3C7);
            drawClipped(graphics, row.playerId().toString().substring(0, 8) + " " + row.role(), 12, y, 138, 0xFF202020);
            y += 12;
        }

        drawClipped(
                graphics,
                Component.translatable("screen.finance.company_gameplay.facilities")
                        .getString(),
                165,
                42,
                147,
                0xFF202020);
        y = 56;
        int facilityEnd = Math.min(menu.facilities().size(), facilityOffset + FACILITY_ROWS);
        for (int i = facilityOffset; i < facilityEnd; i++) {
            CompanyGameplayMenu.FacilityRow row = menu.facilities().get(i);
            int facilityIndex = i;
            clicks.add(162, y - 2, 150, 12, () -> facility = facilityIndex);
            if (i == facility) graphics.fill(162, y - 2, 312, y + 10, 0xFFD5E3C7);
            drawClipped(graphics, machineLine(row), 166, y, 142, 0xFF202020);
            y += 12;
        }

        String valuation = menu.inventoryValuationDegraded() ? "~" : "";
        drawClipped(
                graphics,
                FinanceFormat.label(menu.operatingHealth()) + "  货值 " + valuation + menu.inventoryValue()
                        + "  仓位 " + menu.warehouseUsed() + "/" + menu.warehouseCapacity()
                        + "  在途 " + menu.activeShipments() + "  负债 " + menu.debtPrincipal()
                        + "  7天内到期 " + menu.dueWithinSevenDays(),
                10,
                119,
                300,
                0xFF555555);

        drawClipped(
                graphics,
                Component.translatable("screen.finance.company_gameplay.contracts")
                        .getString(),
                10,
                133,
                300,
                0xFF202020);
        y = 146;
        int contractEnd = Math.min(menu.contracts().size(), contractOffset + CONTRACT_ROWS);
        for (int i = contractOffset; i < contractEnd; i++) {
            CompanyGameplayMenu.ContractRow row = menu.contracts().get(i);
            int contractIndex = i;
            clicks.add(8, y - 2, 304, 12, () -> selectedContract = contractIndex);
            if (i == selectedContract) graphics.fill(8, y - 2, 312, y + 10, 0xFFD5E3C7);
            String side = menu.companyId().equals(row.issuerCompanyId())
                    ? "BUY"
                    : menu.companyId().equals(row.acceptedCompanyId()) ? "SELL" : "OPEN";
            drawClipped(
                    graphics,
                    side + " " + row.commodity() + " " + row.delivered() + "/" + row.quantity() + "  $" + row.reward()
                            + "  D" + row.deadlineDay() + "  " + row.status(),
                    12,
                    y,
                    296,
                    0xFF202020);
            y += 12;
        }

        if (!localStatus.isEmpty()) {
            drawClipped(graphics, localStatus, 10, 173, 300, 0xFF9A2F2F);
        } else {
            drawClipped(
                    graphics,
                    !menu.statusKey().isBlank()
                            ? Component.translatable(menu.statusKey()).getString()
                            : Component.translatable("screen.finance.company_gameplay.input_hint")
                                    .getString(),
                    10,
                    173,
                    300,
                    menu.statusKey().isBlank() ? 0xFF666666 : 0xFF8A4B18);
        }
        boolean invited = "INVITED".equals(menu.role());
        button(graphics, 10, 204, 72, "mode");
        button(graphics, 86, 204, 72, invited ? "accept" : "leave");
        button(graphics, 162, 204, 72, invited ? "reject" : "invite");
        button(graphics, 238, 204, 72, "role");
        button(graphics, 10, 222, 72, "remove");
        contractButton(graphics, 86, 222, 72);
        button(graphics, 162, 222, 72, "advanced");
    }

    /** 机器清单的一行：种类、档次、状态、在岗/岗位、坐标。升档在机器自己的界面里做。 */
    private String machineLine(CompanyGameplayMenu.FacilityRow row) {
        return Component.translatable(
                        "screen.finance.company_gameplay.machine",
                        Component.translatable("screen.finance.company_gameplay.machine."
                                + row.type().toLowerCase(java.util.Locale.ROOT)),
                        row.level(),
                        FinanceFormat.facilityStatusLabel(row.status()),
                        row.employed(),
                        row.jobs(),
                        row.pos().getX() + "," + row.pos().getY() + ","
                                + row.pos().getZ())
                .getString();
    }

    private void tab(GuiGraphics graphics, int x, int y, int width, String key, boolean selected) {
        boolean capital = key.endsWith(".capital");
        clicks.add(x, y, width, 13, () -> {
            capitalTab = capital;
            updateFieldVisibility();
        });
        graphics.fill(x, y, x + width, y + 13, selected ? 0xFFB8C9A9 : 0xFFD0CAB8);
        String text = clip(Component.translatable(key).getString(), width - 4);
        graphics.drawString(font, text, x + Math.max(2, (width - font.width(text)) / 2), y + 3, 0xFF202020, false);
    }

    private void button(GuiGraphics graphics, int x, int y, int width, String key) {
        clicks.add(x, y, width, 16, () -> send(actionForKey(key)));
        boolean enabled = !pending && can(actionForKey(key));
        graphics.fill(x, y, x + width, y + 16, enabled ? 0xFFD0CAB8 : 0xFFAAA69A);
        String label = clip(
                Component.translatable("screen.finance.company_gameplay.action." + key)
                        .getString(),
                width - 6);
        graphics.drawString(
                font,
                label,
                x + Math.max(3, (width - font.width(label)) / 2),
                y + 4,
                enabled ? 0xFF202020 : 0xFF666666,
                false);
    }

    private void contractButton(GuiGraphics graphics, int x, int y, int width) {
        clicks.add(x, y, width, 16, () -> send(contractAction()));
        CompanyGameplayActionPacket.Action action = contractAction();
        boolean enabled = !pending && can(action);
        graphics.fill(x, y, x + width, y + 16, enabled ? 0xFFD0CAB8 : 0xFFAAA69A);
        String suffix = action == CompanyGameplayActionPacket.Action.ACCEPT_SUPPLY_CONTRACT
                ? "supply_accept"
                : action == CompanyGameplayActionPacket.Action.DELIVER_SUPPLY_CONTRACT ? "supply_deliver" : "procure";
        String label = clip(
                Component.translatable("screen.finance.company_gameplay.action." + suffix)
                        .getString(),
                width - 6);
        graphics.drawString(
                font,
                label,
                x + Math.max(3, (width - font.width(label)) / 2),
                y + 4,
                enabled ? 0xFF202020 : 0xFF666666,
                false);
    }

    private CompanyGameplayActionPacket.Action actionForKey(String key) {
        return switch (key) {
            case "mode" -> CompanyGameplayActionPacket.Action.MODE_NEXT;
            case "autosell" -> CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT;
            case "accept" -> CompanyGameplayActionPacket.Action.ACCEPT_INVITE;
            case "reject" -> CompanyGameplayActionPacket.Action.REJECT_INVITE;
            case "leave" -> CompanyGameplayActionPacket.Action.LEAVE;
            case "invite" -> CompanyGameplayActionPacket.Action.INVITE;
            case "role" -> CompanyGameplayActionPacket.Action.ROLE_NEXT;
            case "remove" -> CompanyGameplayActionPacket.Action.REMOVE_MEMBER;
            case "procure" -> contractAction();
            default -> CompanyGameplayActionPacket.Action.OPEN_ADVANCED;
        };
    }

    private boolean can(CompanyGameplayActionPacket.Action action) {
        if ("INVITED".equals(menu.role())) {
            return action == CompanyGameplayActionPacket.Action.ACCEPT_INVITE
                    || action == CompanyGameplayActionPacket.Action.REJECT_INVITE;
        }
        CompanyMemberRole role;
        try {
            role = CompanyMemberRole.valueOf(menu.role());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return switch (action) {
            case MODE_NEXT -> role == CompanyMemberRole.OWNER;
            case AUTO_SELL_NEXT -> role.allows(CompanyPermission.MANAGE_PRODUCTION);
            case LEAVE -> role != CompanyMemberRole.OWNER;
            case INVITE, ROLE_NEXT, REMOVE_MEMBER -> role.allows(CompanyPermission.MANAGE_MEMBERS);
            case PUBLISH_CONTRACT -> role.allows(CompanyPermission.PUBLISH_CONTRACT);
            case ACCEPT_SUPPLY_CONTRACT, DELIVER_SUPPLY_CONTRACT -> role.allows(CompanyPermission.MANAGE_PRODUCTION);
            case OPEN_ADVANCED -> role.allows(CompanyPermission.OPEN_GOVERNANCE);
            case ACCEPT_INVITE, REJECT_INVITE -> false;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pending) return true;
        if (clicks.click((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        int direction = delta > 0 ? -1 : delta < 0 ? 1 : 0;
        if (direction == 0) return false;
        if (capitalTab) return capital.scroll(direction);
        if (my >= 42 && my < 118 && mx >= 8 && mx < 158) {
            memberOffset = clampOffset(memberOffset + direction, menu.members().size(), MEMBER_ROWS);
            member = keepVisible(member, memberOffset, menu.members().size(), MEMBER_ROWS);
            return true;
        }
        if (my >= 42 && my < 118 && mx >= 162 && mx < 312) {
            facilityOffset =
                    clampOffset(facilityOffset + direction, menu.facilities().size(), FACILITY_ROWS);
            facility = keepVisible(facility, facilityOffset, menu.facilities().size(), FACILITY_ROWS);
            return true;
        }
        if (my >= 133 && my < 171) {
            contractOffset =
                    clampOffset(contractOffset + direction, menu.contracts().size(), CONTRACT_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    boolean send(CompanyGameplayActionPacket.Action action) {
        if (!can(action)) return true;
        UUID id = null;
        try {
            if (action == CompanyGameplayActionPacket.Action.INVITE) {
                id = ScreenInput.parseUuid(target.getValue());
                if (id == null) return reject("screen.finance.input.invalid_uuid");
            } else if (action == CompanyGameplayActionPacket.Action.ROLE_NEXT
                    || action == CompanyGameplayActionPacket.Action.REMOVE_MEMBER) {
                id = menu.members().get(member).playerId();
            } else if (action == CompanyGameplayActionPacket.Action.ACCEPT_SUPPLY_CONTRACT
                    || action == CompanyGameplayActionPacket.Action.DELIVER_SUPPLY_CONTRACT) {
                id = menu.contracts().get(selectedContract).id();
            }
        } catch (RuntimeException ignored) {
            return true;
        }
        // 服务端解码器会把越界的数量/奖励当作非法包断开连接，只有合同操作才带上输入框的数值
        int requestedQuantity = 0;
        long requestedReward = 0;
        String requestedCommodity = "";
        if (action == CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT
                || action == CompanyGameplayActionPacket.Action.DELIVER_SUPPLY_CONTRACT) {
            requestedQuantity =
                    ScreenInput.parsePositive(quantity.getValue(), CompanyGameplayActionPacket.MAX_CONTRACT_QUANTITY);
            if (requestedQuantity < 0)
                return reject("screen.finance.input.invalid_amount", CompanyGameplayActionPacket.MAX_CONTRACT_QUANTITY);
        }
        if (action == CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT) {
            requestedCommodity = commodity.getValue().trim();
            if (requestedCommodity.isEmpty()) return reject("screen.finance.input.missing_commodity");
            requestedReward =
                    ScreenInput.parsePositive(reward.getValue(), CompanyGameplayActionPacket.MAX_CONTRACT_REWARD);
            if (requestedReward < 0)
                return reject("screen.finance.input.invalid_amount", CompanyGameplayActionPacket.MAX_CONTRACT_REWARD);
        }
        FinancePacketHandler.CHANNEL.sendToServer(new CompanyGameplayActionPacket(
                action,
                menu.companyId(),
                id,
                requestedCommodity,
                requestedQuantity,
                requestedReward,
                UUID.randomUUID().toString()));
        markPending();
        return true;
    }

    boolean reject(String key, Object... args) {
        localStatus = Component.translatable(key, args).getString();
        return true;
    }

    void markPending() {
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

    private CompanyGameplayActionPacket.Action contractAction() {
        if (menu.contracts().isEmpty()) return CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT;
        CompanyGameplayMenu.ContractRow row = menu.contracts()
                .get(clampSelection(selectedContract, menu.contracts().size()));
        if ("OPEN".equals(row.status()) && !menu.companyId().equals(row.issuerCompanyId()))
            return CompanyGameplayActionPacket.Action.ACCEPT_SUPPLY_CONTRACT;
        if ("ACCEPTED".equals(row.status()) && menu.companyId().equals(row.acceptedCompanyId()))
            return CompanyGameplayActionPacket.Action.DELIVER_SUPPLY_CONTRACT;
        return CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!capitalTab) {
            clicks.add(266, 184, 44, 16, () -> send(CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT));
            boolean enabled = !pending && can(CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT);
            graphics.fill(leftPos + 266, topPos + 184, leftPos + 310, topPos + 200, enabled ? 0xFFD0CAB8 : 0xFFAAA69A);
            String label = clip(
                    Component.translatable(
                                    "screen.finance.company_gameplay.action.autosell",
                                    Math.round(menu.autoSellRatio() * 100))
                            .getString(),
                    38);
            graphics.drawString(
                    font,
                    label,
                    leftPos + 266 + Math.max(3, (44 - font.width(label)) / 2),
                    topPos + 188,
                    enabled ? 0xFF202020 : 0xFF666666,
                    false);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void updateFieldVisibility() {
        if (target == null) return;
        commodity.visible = !capitalTab;
        quantity.visible = !capitalTab;
        reward.visible = !capitalTab;
    }

    void drawClipped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(font, clip(text, width), x, y, color, false);
    }

    String clip(String text, int width) {
        return FinanceGui.clipText(font, text, width);
    }

    static int clampOffset(int offset, int size, int visibleRows) {
        return Math.max(0, Math.min(Math.max(0, size - visibleRows), offset));
    }

    static int clampSelection(int selected, int size) {
        return size == 0 ? 0 : Math.max(0, Math.min(size - 1, selected));
    }

    static int keepVisible(int selected, int offset, int size, int visibleRows) {
        if (size <= 0) return 0;
        return Math.max(offset, Math.min(selected, Math.min(size - 1, offset + visibleRows - 1)));
    }

    // ---- 给 CompanyCapitalPanel 用 ----

    net.minecraft.client.gui.Font font() {
        return font;
    }

    boolean pending() {
        return pending;
    }

    String localStatus() {
        return localStatus;
    }

    String targetText() {
        return target.getValue();
    }

    int selectedFacility() {
        return facility;
    }
}
