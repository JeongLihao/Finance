package finance.gameplay;

/** 服务端权威的入口类型、距离、权限和界面模式校验。 */
public final class FinanceAccessService {

    private FinanceAccessService() {
    }

    public static FinanceAccessDecision authorize(FinanceAccessContext context,
                                                   FinanceAccessPolicy policy) {
        if (context == null || context.playerId() == null || context.terminalType() == null
                || context.requestedMode() == null || policy == null) {
            return FinanceAccessDecision.deny("finance.access.invalid_request");
        }

        FinanceTerminalType terminal = context.terminalType();
        FinanceScreenMode requestedMode = context.requestedMode();
        if (!terminal.allowedModes().contains(requestedMode)) {
            return FinanceAccessDecision.deny("finance.access.mode_mismatch");
        }

        if (terminal == FinanceTerminalType.PORTABLE_LEDGER && !policy.enablePortableLedger()) {
            return FinanceAccessDecision.deny("finance.access.portable_disabled");
        }
        if (terminal == FinanceTerminalType.CENTRAL_BANK_CONSOLE
                && policy.adminConsoleRequiresPermission() && context.permissionLevel() < 2) {
            return FinanceAccessDecision.deny("finance.access.admin_required");
        }

        if (terminal == FinanceTerminalType.LEGACY_FULL_SCREEN) {
            boolean administrator = context.permissionLevel() >= 2;
            boolean legacyEnabled = !policy.minecraftFirstMode() || policy.legacyFullScreenKeybind()
                    || !policy.advancedFinanceRequiresTerminal() || administrator;
            return legacyEnabled
                    ? FinanceAccessDecision.allow(FinanceScreenMode.ADVANCED, null)
                    : FinanceAccessDecision.deny("finance.access.terminal_required");
        }

        if (!context.entryVerified()) {
            return FinanceAccessDecision.deny("finance.access.unverified_entry");
        }
        if (terminal.isPhysicalTerminal() && policy.requirePhysicalTerminal()) {
            double maxDistance = policy.terminalInteractionDistance();
            if (!context.sameDimension() || context.sourcePos() == null
                    || context.distanceSquared() > maxDistance * maxDistance) {
                return FinanceAccessDecision.deny("finance.access.too_far");
            }
        }
        return FinanceAccessDecision.allow(requestedMode, context.sourcePos());
    }

    public static FinanceAccessDecision validatePortableLedger(FinanceAccessContext context,
                                                                FinanceAccessPolicy policy) {
        return authorize(context, policy);
    }

    public static FinanceAccessDecision validateTerminal(FinanceAccessContext context,
                                                          FinanceAccessPolicy policy) {
        return authorize(context, policy);
    }

    public static boolean mayOpen(FinanceAccessContext context, FinanceAccessPolicy policy) {
        return authorize(context, policy).allowed();
    }

    public static java.util.Set<FinanceScreenMode> allowedModes(FinanceTerminalType terminal) {
        return terminal == null ? java.util.Set.of() : terminal.allowedModes();
    }

    public static FinanceScreenMode modeFor(FinanceTerminalType terminal) {
        return switch (terminal) {
            case PORTABLE_LEDGER -> FinanceScreenMode.WALLET;
            case MARKET_TERMINAL -> FinanceScreenMode.MARKET;
            case WAREHOUSE_CONTROLLER -> FinanceScreenMode.WAREHOUSE;
            case BANK_COUNTER -> FinanceScreenMode.BANK;
            case COMPANY_DESK, BOARDROOM_TABLE -> FinanceScreenMode.COMPANY;
            case SECURITIES_TERMINAL, LEGACY_FULL_SCREEN -> FinanceScreenMode.ADVANCED;
            case CENTRAL_BANK_CONSOLE -> FinanceScreenMode.ADMIN;
        };
    }
}
