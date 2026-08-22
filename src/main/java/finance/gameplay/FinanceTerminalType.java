package finance.gameplay;

import java.util.Set;

/**
 * 玩家进入金融系统时所使用的世界入口。
 *
 * <p>入口类型只是服务端验证的声明，不能替代方块、物品、距离或权限检查。</p>
 */
public enum FinanceTerminalType {
    PORTABLE_LEDGER,
    MARKET_TERMINAL,
    WAREHOUSE_CONTROLLER,
    BANK_COUNTER,
    COMPANY_DESK,
    SECURITIES_TERMINAL,
    BOARDROOM_TABLE,
    CENTRAL_BANK_CONSOLE,
    LEGACY_FULL_SCREEN;

    public boolean isPhysicalTerminal() {
        return this != PORTABLE_LEDGER && this != LEGACY_FULL_SCREEN;
    }

    public Set<FinanceScreenMode> allowedModes() {
        return switch (this) {
            case PORTABLE_LEDGER -> Set.of(FinanceScreenMode.WALLET);
            case MARKET_TERMINAL -> Set.of(FinanceScreenMode.MARKET);
            case WAREHOUSE_CONTROLLER -> Set.of(FinanceScreenMode.WAREHOUSE);
            case BANK_COUNTER -> Set.of(FinanceScreenMode.BANK);
            case COMPANY_DESK, BOARDROOM_TABLE -> Set.of(FinanceScreenMode.COMPANY);
            case SECURITIES_TERMINAL -> Set.of(FinanceScreenMode.ADVANCED);
            case CENTRAL_BANK_CONSOLE -> Set.of(FinanceScreenMode.ADMIN, FinanceScreenMode.ADVANCED);
            case LEGACY_FULL_SCREEN -> Set.of(FinanceScreenMode.ADVANCED);
        };
    }
}
