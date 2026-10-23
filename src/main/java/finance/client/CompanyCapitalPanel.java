package finance.client;

import finance.gameplay.company.CompanyMemberRole;
import finance.gameplay.company.CompanyPermission;
import finance.gameplay.company.capital.CapitalFundingSource;
import finance.gameplay.company.capital.WorldCapitalProjectType;
import finance.gui.CompanyGameplayMenu;
import finance.network.CapitalProjectActionPacket;
import finance.network.CompanyGameplayActionPacket;
import finance.network.FinancePacketHandler;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 公司经营界面的“实体项目”页：项目列表、新建仓库或机器升级项目、选资金来源、授权、筹资、施工、取消。
 * 原在 {@link CompanyGameplayScreen}，字体、请求状态、提示和“对方编号”输入框通过它取。
 */
final class CompanyCapitalPanel {
    private final CompanyGameplayScreen screen;
    private int selectedProject;
    private int projectOffset;
    private CapitalFundingSource capitalFundingSource = CapitalFundingSource.RETAINED_EARNINGS;

    CompanyCapitalPanel(CompanyGameplayScreen screen) {
        this.screen = screen;
    }

    private CompanyGameplayMenu menu() {
        return screen.getMenu();
    }

    /** 界面初始化时把选中项和滚动位置收回到列表范围内。 */
    void clamp() {
        selectedProject = CompanyGameplayScreen.clampSelection(
                selectedProject, menu().projects().size());
        projectOffset = CompanyGameplayScreen.clampOffset(
                projectOffset, menu().projects().size(), 9);
    }

    void render(GuiGraphics graphics) {
        screen.drawClipped(
                graphics,
                Component.translatable("screen.finance.company_gameplay.capital_projects")
                        .getString(),
                10,
                42,
                300,
                0xFF202020);
        int y = 56;
        int end = Math.min(menu().projects().size(), projectOffset + 9);
        for (int i = projectOffset; i < end; i++) {
            CompanyGameplayMenu.ProjectRow row = menu().projects().get(i);
            if (i == selectedProject) graphics.fill(8, y - 2, 312, y + 10, 0xFFD5E3C7);
            String line = row.type() + " L" + row.targetLevel() + "  " + row.funded() + "/" + row.budget() + "  "
                    + row.fundingSource() + "  " + row.status();
            screen.drawClipped(graphics, line, 12, y, 296, 0xFF202020);
            y += 12;
        }
        if (menu().projects().isEmpty()) {
            screen.drawClipped(
                    graphics,
                    Component.translatable("screen.finance.company_gameplay.capital_empty")
                            .getString(),
                    12,
                    58,
                    296,
                    0xFF777777);
        }
        String diagnosticKey = !menu().statusKey().isBlank()
                ? menu().statusKey()
                : menu().projects().isEmpty()
                        ? ""
                        : menu().projects().get(selectedProject).failureKey();
        if (!screen.localStatus().isEmpty()) {
            screen.drawClipped(graphics, screen.localStatus(), 10, 169, 300, 0xFF9A2F2F);
        } else if (!diagnosticKey.isBlank()) {
            screen.drawClipped(
                    graphics,
                    Component.translatable(diagnosticKey).getString(),
                    10,
                    169,
                    300,
                    diagnosticKey.contains("completed") || diagnosticKey.contains("created") ? 0xFF2D7D32 : 0xFF8A4B18);
        }
        screen.drawClipped(
                graphics,
                Component.translatable("screen.finance.company_gameplay.capital_id_hint")
                        .getString(),
                104,
                188,
                130,
                0xFF666666);
        capitalButton(graphics, 10, 204, 72, "create_warehouse");
        capitalButton(graphics, 86, 204, 72, "create_factory");
        capitalButton(graphics, 162, 204, 72, "source");
        capitalButton(graphics, 238, 204, 72, "authorize");
        capitalButton(graphics, 10, 222, 72, "fund");
        capitalButton(graphics, 86, 222, 72, "execute");
        capitalButton(graphics, 162, 222, 72, "cancel");
        capitalButton(graphics, 238, 222, 72, "advanced");
    }

    private void capitalButton(GuiGraphics graphics, int x, int y, int width, String key) {
        boolean enabled = !screen.pending() && canCapital(key);
        graphics.fill(x, y, x + width, y + 16, enabled ? 0xFFD0CAB8 : 0xFFAAA69A);
        String effectiveKey = "fund".equals(key) && selectedProjectFailed() ? "recover" : key;
        String label = "source".equals(key)
                ? shortSource(capitalFundingSource)
                : "authorize".equals(key)
                                && !menu().projects().isEmpty()
                                && menu().projects().get(selectedProject).proposalId() == null
                        ? Component.translatable("screen.finance.company_gameplay.capital_action.propose")
                                .getString()
                        : Component.translatable("screen.finance.company_gameplay.capital_action." + effectiveKey)
                                .getString();
        label = screen.clip(label, width - 6);
        graphics.drawString(
                screen.font(),
                label,
                x + Math.max(3, (width - screen.font().width(label)) / 2),
                y + 4,
                enabled ? 0xFF202020 : 0xFF666666,
                false);
    }

    private boolean canCapital(String action) {
        CompanyMemberRole role;
        try {
            role = CompanyMemberRole.valueOf(menu().role());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if ("advanced".equals(action)) return role.allows(CompanyPermission.OPEN_GOVERNANCE);
        if ("execute".equals(action))
            return role.allows(CompanyPermission.MANAGE_PRODUCTION)
                    && !menu().projects().isEmpty();
        if ("source".equals(action)) return role.allows(CompanyPermission.SPEND_COMPANY_CASH);
        if ("create_warehouse".equals(action))
            return role.allows(CompanyPermission.SPEND_COMPANY_CASH)
                    && !menu().warehouses().isEmpty();
        if ("create_factory".equals(action))
            return role.allows(CompanyPermission.SPEND_COMPANY_CASH)
                    && !menu().facilities().isEmpty();
        return role.allows(CompanyPermission.SPEND_COMPANY_CASH)
                && !menu().projects().isEmpty();
    }

    private boolean selectedProjectFailed() {
        return !menu().projects().isEmpty()
                && "FAILED_RECOVERABLE"
                        .equals(menu().projects().get(selectedProject).status());
    }

    boolean click(int mx, int my) {
        if (mx >= 8
                && mx < 312
                && my >= 54
                && my < 54 + Math.min(9, Math.max(0, menu().projects().size() - projectOffset)) * 12) {
            selectedProject = Math.min(menu().projects().size() - 1, projectOffset + Math.max(0, (my - 54) / 12));
            return true;
        }
        if (my < 204 || my >= 238 || mx < 10 || mx >= 310)
            return screen.superMouseClicked(screen.getGuiLeft() + mx, screen.getGuiTop() + my, 0);
        int column = (mx - 10) / 76;
        if (column < 0 || column > 3) return true;
        String key = my < 220
                ? switch (column) {
                    case 0 -> "create_warehouse";
                    case 1 -> "create_factory";
                    case 2 -> "source";
                    default -> "authorize";
                }
                : switch (column) {
                    case 0 -> "fund";
                    case 1 -> "execute";
                    case 2 -> "cancel";
                    default -> "advanced";
                };
        if (!canCapital(key)) return true;
        if ("source".equals(key)) {
            CapitalFundingSource[] values = CapitalFundingSource.values();
            capitalFundingSource = values[(capitalFundingSource.ordinal() + 1) % values.length];
            return true;
        }
        if ("advanced".equals(key)) return screen.send(CompanyGameplayActionPacket.Action.OPEN_ADVANCED);
        return sendCapital(key);
    }

    private boolean sendCapital(String key) {
        UUID projectId = menu().projects().isEmpty()
                ? null
                : menu().projects().get(selectedProject).id();
        UUID targetId = null, proposalId = null, bankId = null;
        WorldCapitalProjectType type = null;
        CapitalProjectActionPacket.Action action;
        try {
            switch (key) {
                case "create_warehouse" -> {
                    action = CapitalProjectActionPacket.Action.CREATE;
                    type = WorldCapitalProjectType.WAREHOUSE_UPGRADE;
                    targetId = menu().warehouses().get(0).id();
                }
                case "create_factory" -> {
                    action = CapitalProjectActionPacket.Action.CREATE;
                    type = WorldCapitalProjectType.FACTORY_UPGRADE;
                    targetId =
                            menu().facilities().get(screen.selectedFacility()).id();
                }
                case "authorize" -> {
                    proposalId = menu().projects().get(selectedProject).proposalId();
                    action = proposalId == null
                            ? CapitalProjectActionPacket.Action.PROPOSE
                            : CapitalProjectActionPacket.Action.AUTHORIZE;
                }
                case "fund" -> {
                    action = selectedProjectFailed()
                            ? CapitalProjectActionPacket.Action.RECOVER
                            : CapitalProjectActionPacket.Action.START_FUNDING;
                    CapitalFundingSource projectSource = CapitalFundingSource.valueOf(
                            menu().projects().get(selectedProject).fundingSource());
                    if (projectSource == CapitalFundingSource.COMMERCIAL_LOAN
                            && !screen.targetText().isBlank()) {
                        bankId = ScreenInput.parseUuid(screen.targetText());
                        if (bankId == null) return screen.reject("screen.finance.input.invalid_uuid");
                    }
                }
                case "execute" -> action = CapitalProjectActionPacket.Action.EXECUTE;
                case "cancel" -> action = CapitalProjectActionPacket.Action.CANCEL;
                default -> {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            return true;
        }
        FinancePacketHandler.CHANNEL.sendToServer(new CapitalProjectActionPacket(
                action,
                projectId,
                targetId,
                proposalId,
                bankId,
                type,
                capitalFundingSource,
                UUID.randomUUID().toString()));
        screen.markPending();
        return true;
    }

    /** 鼠标滚轮：滚动项目列表，选中项保持在可见范围内。 */
    boolean scroll(int direction) {
        projectOffset = CompanyGameplayScreen.clampOffset(
                projectOffset + direction, menu().projects().size(), 9);
        selectedProject = CompanyGameplayScreen.keepVisible(
                selectedProject, projectOffset, menu().projects().size(), 9);
        return true;
    }

    private static String shortSource(CapitalFundingSource source) {
        return switch (source) {
            case RETAINED_EARNINGS -> "留存收益";
            case COMMERCIAL_LOAN -> "银行贷款";
            case CORPORATE_BOND -> "企业债";
            case SHARE_ISSUE -> "增发";
        };
    }
}
