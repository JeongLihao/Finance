package finance.client;

import finance.gameplay.company.CompanyMemberRole;
import finance.gameplay.company.CompanyPermission;
import finance.gui.CompanyGameplayMenu;
import finance.network.CompanyGameplayActionPacket;
import finance.network.CapitalProjectActionPacket;
import finance.network.FinancePacketHandler;
import finance.gameplay.company.capital.CapitalFundingSource;
import finance.gameplay.company.capital.WorldCapitalProjectType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.UUID;

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
    private int selectedProject;
    private int projectOffset;
    private boolean capitalTab;
    private CapitalFundingSource capitalFundingSource = CapitalFundingSource.RETAINED_EARNINGS;
    private EditBox target;
    private EditBox commodity;
    private EditBox quantity;
    private EditBox reward;
    private boolean pending;

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
        selectedProject = clampSelection(selectedProject, menu.projects().size());
        projectOffset = clampOffset(projectOffset, menu.projects().size(), 9);
        capitalTab = menu.statusKey().startsWith("finance.capital_project.");
        target = box(10, 184, 90, "UUID");
        commodity = box(104, 184, 60, "ID");
        quantity = box(168, 184, 40, "Qty");
        reward = box(212, 184, 50, "Money");
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
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                0xFFE8E2D2);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawClipped(graphics, menu.name() + "  " + menu.mode() + "  " + menu.role(),
                10, 9, 300, menu.risk() ? 0xFFA02020 : 0xFF202020);
        drawClipped(graphics, Component.translatable("screen.finance.company_gameplay.cash", menu.cash()).getString(),
                10, 23, 180, 0xFF555555);
        tab(graphics, 200, 20, 52, "screen.finance.company_gameplay.tab.operations", !capitalTab);
        tab(graphics, 256, 20, 54, "screen.finance.company_gameplay.tab.capital", capitalTab);
        if (capitalTab) {
            renderCapital(graphics);
            return;
        }

        drawClipped(graphics, Component.translatable("screen.finance.company_gameplay.members").getString(),
                10, 42, 144, 0xFF202020);
        int y = 56;
        int memberEnd = Math.min(menu.members().size(), memberOffset + MEMBER_ROWS);
        for (int i = memberOffset; i < memberEnd; i++) {
            CompanyGameplayMenu.MemberRow row = menu.members().get(i);
            if (i == member) graphics.fill(8, y - 2, 154, y + 10, 0xFFD5E3C7);
            drawClipped(graphics, row.playerId().toString().substring(0, 8) + " " + row.role(),
                    12, y, 138, 0xFF202020);
            y += 12;
        }

        drawClipped(graphics, Component.translatable("screen.finance.company_gameplay.facilities").getString(),
                165, 42, 147, 0xFF202020);
        y = 56;
        int facilityEnd = Math.min(menu.facilities().size(), facilityOffset + FACILITY_ROWS);
        for (int i = facilityOffset; i < facilityEnd; i++) {
            CompanyGameplayMenu.FacilityRow row = menu.facilities().get(i);
            if (i == facility) graphics.fill(162, y - 2, 312, y + 10, 0xFFD5E3C7);
            drawClipped(graphics, row.id().toString().substring(0, 8) + " L" + row.level() + " " + row.status(),
                    166, y, 142, 0xFF202020);
            y += 12;
        }

        String valuation = menu.inventoryValuationDegraded() ? "~" : "";
        drawClipped(graphics, menu.operatingHealth() + "  INV " + valuation + menu.inventoryValue()
                + "  CAP " + menu.warehouseUsed() + "/" + menu.warehouseCapacity()
                + "  SHIP " + menu.activeShipments() + "  DEBT " + menu.debtPrincipal()
                + "  DUE7 " + menu.dueWithinSevenDays(), 10, 119, 300, 0xFF555555);

        drawClipped(graphics, Component.translatable("screen.finance.company_gameplay.contracts").getString(),
                10, 133, 300, 0xFF202020);
        y = 146;
        int contractEnd = Math.min(menu.contracts().size(), contractOffset + CONTRACT_ROWS);
        for (int i = contractOffset; i < contractEnd; i++) {
            CompanyGameplayMenu.ContractRow row = menu.contracts().get(i);
            drawClipped(graphics, row.commodity() + " x" + row.quantity() + " / " + row.reward()
                    + " / " + row.status(), 12, y, 296, 0xFF202020);
            y += 12;
        }

        drawClipped(graphics, !menu.statusKey().isBlank()
                        ? Component.translatable(menu.statusKey()).getString()
                        : Component.translatable("screen.finance.company_gameplay.input_hint").getString(),
                10, 173, 300, menu.statusKey().isBlank() ? 0xFF666666 : 0xFF8A4B18);
        boolean invited = "INVITED".equals(menu.role());
        button(graphics, 10, 204, 72, "mode");
        button(graphics, 86, 204, 72, invited ? "accept" : "leave");
        button(graphics, 162, 204, 72, invited ? "reject" : "invite");
        button(graphics, 238, 204, 72, "role");
        button(graphics, 10, 222, 72, "remove");
        button(graphics, 86, 222, 72, "upgrade");
        button(graphics, 162, 222, 72, "procure");
        button(graphics, 238, 222, 72, "advanced");
    }

    private void renderCapital(GuiGraphics graphics) {
        drawClipped(graphics, Component.translatable("screen.finance.company_gameplay.capital_projects").getString(),
                10, 42, 300, 0xFF202020);
        int y = 56;
        int end = Math.min(menu.projects().size(), projectOffset + 9);
        for (int i = projectOffset; i < end; i++) {
            CompanyGameplayMenu.ProjectRow row = menu.projects().get(i);
            if (i == selectedProject) graphics.fill(8, y - 2, 312, y + 10, 0xFFD5E3C7);
            String line = row.type() + " L" + row.targetLevel() + "  " + row.funded() + "/" + row.budget()
                    + "  " + row.fundingSource() + "  " + row.status();
            drawClipped(graphics, line, 12, y, 296, 0xFF202020);
            y += 12;
        }
        if (menu.projects().isEmpty()) {
            drawClipped(graphics, Component.translatable("screen.finance.company_gameplay.capital_empty").getString(),
                    12, 58, 296, 0xFF777777);
        }
        String diagnosticKey = !menu.statusKey().isBlank() ? menu.statusKey()
                : menu.projects().isEmpty() ? "" : menu.projects().get(selectedProject).failureKey();
        if (!diagnosticKey.isBlank()) {
            drawClipped(graphics, Component.translatable(diagnosticKey).getString(),
                    10, 169, 300, diagnosticKey.contains("completed") || diagnosticKey.contains("created")
                            ? 0xFF2D7D32 : 0xFF8A4B18);
        }
        drawClipped(graphics, Component.translatable("screen.finance.company_gameplay.capital_id_hint").getString(),
                104, 188, 130, 0xFF666666);
        capitalButton(graphics, 10, 204, 72, "create_warehouse");
        capitalButton(graphics, 86, 204, 72, "create_factory");
        capitalButton(graphics, 162, 204, 72, "source");
        capitalButton(graphics, 238, 204, 72, "authorize");
        capitalButton(graphics, 10, 222, 72, "fund");
        capitalButton(graphics, 86, 222, 72, "execute");
        capitalButton(graphics, 162, 222, 72, "cancel");
        capitalButton(graphics, 238, 222, 72, "advanced");
    }

    private void tab(GuiGraphics graphics, int x, int y, int width, String key, boolean selected) {
        graphics.fill(x, y, x + width, y + 13, selected ? 0xFFB8C9A9 : 0xFFD0CAB8);
        String text = clip(Component.translatable(key).getString(), width - 4);
        graphics.drawString(font, text, x + Math.max(2, (width - font.width(text)) / 2), y + 3,
                0xFF202020, false);
    }

    private void capitalButton(GuiGraphics graphics, int x, int y, int width, String key) {
        boolean enabled = !pending && canCapital(key);
        graphics.fill(x, y, x + width, y + 16, enabled ? 0xFFD0CAB8 : 0xFFAAA69A);
        String effectiveKey = "fund".equals(key) && selectedProjectFailed() ? "recover" : key;
        String label = "source".equals(key)
                ? shortSource(capitalFundingSource)
                : "authorize".equals(key) && !menu.projects().isEmpty()
                && menu.projects().get(selectedProject).proposalId() == null
                ? Component.translatable("screen.finance.company_gameplay.capital_action.propose").getString()
                : Component.translatable("screen.finance.company_gameplay.capital_action." + effectiveKey).getString();
        label = clip(label, width - 6);
        graphics.drawString(font, label, x + Math.max(3, (width - font.width(label)) / 2), y + 4,
                enabled ? 0xFF202020 : 0xFF666666, false);
    }

    private boolean canCapital(String action) {
        CompanyMemberRole role;
        try { role = CompanyMemberRole.valueOf(menu.role()); }
        catch (IllegalArgumentException exception) { return false; }
        if ("advanced".equals(action)) return role.allows(CompanyPermission.OPEN_GOVERNANCE);
        if ("execute".equals(action)) return role.allows(CompanyPermission.MANAGE_PRODUCTION)
                && !menu.projects().isEmpty();
        if ("source".equals(action)) return role.allows(CompanyPermission.SPEND_COMPANY_CASH);
        if ("create_warehouse".equals(action)) return role.allows(CompanyPermission.SPEND_COMPANY_CASH)
                && !menu.warehouses().isEmpty();
        if ("create_factory".equals(action)) return role.allows(CompanyPermission.SPEND_COMPANY_CASH)
                && !menu.facilities().isEmpty();
        return role.allows(CompanyPermission.SPEND_COMPANY_CASH) && !menu.projects().isEmpty();
    }

    private boolean selectedProjectFailed() {
        return !menu.projects().isEmpty()
                && "FAILED_RECOVERABLE".equals(menu.projects().get(selectedProject).status());
    }

    private void button(GuiGraphics graphics, int x, int y, int width, String key) {
        boolean enabled = !pending && can(actionForKey(key));
        graphics.fill(x, y, x + width, y + 16, enabled ? 0xFFD0CAB8 : 0xFFAAA69A);
        String label = clip(Component.translatable("screen.finance.company_gameplay.action." + key).getString(),
                width - 6);
        graphics.drawString(font, label, x + Math.max(3, (width - font.width(label)) / 2), y + 4,
                enabled ? 0xFF202020 : 0xFF666666, false);
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
            case "upgrade" -> CompanyGameplayActionPacket.Action.UPGRADE_FACILITY;
            case "procure" -> CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT;
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
            case AUTO_SELL_NEXT, UPGRADE_FACILITY -> role.allows(CompanyPermission.MANAGE_PRODUCTION);
            case LEAVE -> role != CompanyMemberRole.OWNER;
            case INVITE, ROLE_NEXT, REMOVE_MEMBER -> role.allows(CompanyPermission.MANAGE_MEMBERS);
            case PUBLISH_CONTRACT -> role.allows(CompanyPermission.PUBLISH_CONTRACT);
            case OPEN_ADVANCED -> role.allows(CompanyPermission.OPEN_GOVERNANCE);
            case ACCEPT_INVITE, REJECT_INVITE -> false;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pending) return true;
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        if (my >= 20 && my < 34 && mx >= 200 && mx < 310) {
            capitalTab = mx >= 256;
            updateFieldVisibility();
            return true;
        }
        if (capitalTab) return capitalMouseClicked(mx, my);
        if (mx >= 8 && mx < 154 && my >= 54
                && my < 54 + Math.min(MEMBER_ROWS, Math.max(0, menu.members().size() - memberOffset)) * 12) {
            member = Math.min(menu.members().size() - 1, memberOffset + Math.max(0, (my - 54) / 12));
            return true;
        }
        if (mx >= 162 && mx < 312 && my >= 54
                && my < 54 + Math.min(FACILITY_ROWS, Math.max(0, menu.facilities().size() - facilityOffset)) * 12) {
            facility = Math.min(menu.facilities().size() - 1,
                    facilityOffset + Math.max(0, (my - 54) / 12));
            return true;
        }
        if (mx >= 266 && mx < 310 && my >= 184 && my < 200) {
            return send(CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT);
        }
        if (mx >= 10 && mx < 310 && (my >= 204 && my < 220 || my >= 222 && my < 238)) {
            int column = (mx - 10) / 76;
            if (column < 0 || column > 3) return true;
            boolean invited = "INVITED".equals(menu.role());
            CompanyGameplayActionPacket.Action action;
            if (my < 220) {
                action = switch (column) {
                    case 0 -> CompanyGameplayActionPacket.Action.MODE_NEXT;
                    case 1 -> invited ? CompanyGameplayActionPacket.Action.ACCEPT_INVITE
                            : CompanyGameplayActionPacket.Action.LEAVE;
                    case 2 -> invited ? CompanyGameplayActionPacket.Action.REJECT_INVITE
                            : CompanyGameplayActionPacket.Action.INVITE;
                    default -> CompanyGameplayActionPacket.Action.ROLE_NEXT;
                };
            } else {
                action = switch (column) {
                    case 0 -> CompanyGameplayActionPacket.Action.REMOVE_MEMBER;
                    case 1 -> CompanyGameplayActionPacket.Action.UPGRADE_FACILITY;
                    case 2 -> CompanyGameplayActionPacket.Action.PUBLISH_CONTRACT;
                    default -> CompanyGameplayActionPacket.Action.OPEN_ADVANCED;
                };
            }
            return send(action);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean capitalMouseClicked(int mx, int my) {
        if (mx >= 8 && mx < 312 && my >= 54
                && my < 54 + Math.min(9, Math.max(0, menu.projects().size() - projectOffset)) * 12) {
            selectedProject = Math.min(menu.projects().size() - 1,
                    projectOffset + Math.max(0, (my - 54) / 12));
            return true;
        }
        if (my < 204 || my >= 238 || mx < 10 || mx >= 310) return super.mouseClicked(
                leftPos + mx, topPos + my, 0);
        int column = (mx - 10) / 76;
        if (column < 0 || column > 3) return true;
        String key = my < 220 ? switch (column) {
            case 0 -> "create_warehouse"; case 1 -> "create_factory";
            case 2 -> "source"; default -> "authorize";
        } : switch (column) {
            case 0 -> "fund"; case 1 -> "execute"; case 2 -> "cancel"; default -> "advanced";
        };
        if (!canCapital(key)) return true;
        if ("source".equals(key)) {
            CapitalFundingSource[] values = CapitalFundingSource.values();
            capitalFundingSource = values[(capitalFundingSource.ordinal() + 1) % values.length];
            return true;
        }
        if ("advanced".equals(key)) return send(CompanyGameplayActionPacket.Action.OPEN_ADVANCED);
        return sendCapital(key);
    }

    private boolean sendCapital(String key) {
        UUID projectId = menu.projects().isEmpty() ? null : menu.projects().get(selectedProject).id();
        UUID targetId = null, proposalId = null, bankId = null;
        WorldCapitalProjectType type = null;
        CapitalProjectActionPacket.Action action;
        try {
            switch (key) {
                case "create_warehouse" -> {
                    action = CapitalProjectActionPacket.Action.CREATE;
                    type = WorldCapitalProjectType.WAREHOUSE_UPGRADE;
                    targetId = menu.warehouses().get(0).id();
                }
                case "create_factory" -> {
                    action = CapitalProjectActionPacket.Action.CREATE;
                    type = WorldCapitalProjectType.FACTORY_UPGRADE;
                    targetId = menu.facilities().get(facility).id();
                }
                case "authorize" -> {
                    proposalId = menu.projects().get(selectedProject).proposalId();
                    action = proposalId == null ? CapitalProjectActionPacket.Action.PROPOSE
                            : CapitalProjectActionPacket.Action.AUTHORIZE;
                }
                case "fund" -> {
                    action = selectedProjectFailed() ? CapitalProjectActionPacket.Action.RECOVER
                            : CapitalProjectActionPacket.Action.START_FUNDING;
                    CapitalFundingSource projectSource = CapitalFundingSource.valueOf(
                            menu.projects().get(selectedProject).fundingSource());
                    if (projectSource == CapitalFundingSource.COMMERCIAL_LOAN && !target.getValue().isBlank())
                        bankId = UUID.fromString(target.getValue().trim());
                }
                case "execute" -> action = CapitalProjectActionPacket.Action.EXECUTE;
                case "cancel" -> action = CapitalProjectActionPacket.Action.CANCEL;
                default -> { return true; }
            }
        } catch (RuntimeException exception) {
            return true;
        }
        FinancePacketHandler.CHANNEL.sendToServer(new CapitalProjectActionPacket(action, projectId, targetId,
                proposalId, bankId, type, capitalFundingSource, UUID.randomUUID().toString()));
        pending = true;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        int direction = delta > 0 ? -1 : delta < 0 ? 1 : 0;
        if (direction == 0) return false;
        if (capitalTab) {
            projectOffset = clampOffset(projectOffset + direction, menu.projects().size(), 9);
            selectedProject = keepVisible(selectedProject, projectOffset, menu.projects().size(), 9);
            return true;
        }
        if (my >= 42 && my < 118 && mx >= 8 && mx < 158) {
            memberOffset = clampOffset(memberOffset + direction, menu.members().size(), MEMBER_ROWS);
            member = keepVisible(member, memberOffset, menu.members().size(), MEMBER_ROWS);
            return true;
        }
        if (my >= 42 && my < 118 && mx >= 162 && mx < 312) {
            facilityOffset = clampOffset(facilityOffset + direction, menu.facilities().size(), FACILITY_ROWS);
            facility = keepVisible(facility, facilityOffset, menu.facilities().size(), FACILITY_ROWS);
            return true;
        }
        if (my >= 133 && my < 171) {
            contractOffset = clampOffset(contractOffset + direction, menu.contracts().size(), CONTRACT_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean send(CompanyGameplayActionPacket.Action action) {
        if (!can(action)) return true;
        UUID id = null;
        try {
            if (action == CompanyGameplayActionPacket.Action.INVITE) id = UUID.fromString(target.getValue());
            else if (action == CompanyGameplayActionPacket.Action.ROLE_NEXT
                    || action == CompanyGameplayActionPacket.Action.REMOVE_MEMBER) {
                id = menu.members().get(member).playerId();
            } else if (action == CompanyGameplayActionPacket.Action.UPGRADE_FACILITY) {
                id = menu.facilities().get(facility).id();
            }
        } catch (RuntimeException ignored) {
            return true;
        }
        int requestedQuantity = 0;
        long requestedReward = 0;
        try {
            requestedQuantity = Integer.parseInt(quantity.getValue());
            requestedReward = Long.parseLong(reward.getValue());
        } catch (NumberFormatException ignored) {
        }
        FinancePacketHandler.CHANNEL.sendToServer(new CompanyGameplayActionPacket(action, menu.companyId(), id,
                commodity.getValue(), requestedQuantity, requestedReward, UUID.randomUUID().toString()));
        pending = true;
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!capitalTab) {
            boolean enabled = !pending && can(CompanyGameplayActionPacket.Action.AUTO_SELL_NEXT);
            graphics.fill(leftPos + 266, topPos + 184, leftPos + 310, topPos + 200,
                    enabled ? 0xFFD0CAB8 : 0xFFAAA69A);
            String label = clip(Component.translatable("screen.finance.company_gameplay.action.autosell",
                    Math.round(menu.autoSellRatio() * 100)).getString(), 38);
            graphics.drawString(font, label, leftPos + 266 + Math.max(3, (44 - font.width(label)) / 2), topPos + 188,
                    enabled ? 0xFF202020 : 0xFF666666, false);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void updateFieldVisibility() {
        if (target == null) return;
        target.visible = true;
        commodity.visible = !capitalTab;
        quantity.visible = !capitalTab;
        reward.visible = !capitalTab;
    }

    private static String shortSource(CapitalFundingSource source) {
        return switch (source) {
            case RETAINED_EARNINGS -> "留存收益";
            case COMMERCIAL_LOAN -> "银行贷款";
            case CORPORATE_BOND -> "企业债";
            case SHARE_ISSUE -> "增发";
        };
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
