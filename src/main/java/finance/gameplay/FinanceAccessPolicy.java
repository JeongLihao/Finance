package finance.gameplay;

import finance.config.FinanceConfig;

/** 可注入测试的 Minecraft-first 访问策略快照。 */
public record FinanceAccessPolicy(
        boolean minecraftFirstMode,
        boolean requirePhysicalTerminal,
        boolean enablePortableLedger,
        boolean legacyFullScreenKeybind,
        boolean warehouseCapacityEnabled,
        boolean contractsEnabled,
        boolean playerDrivenCompanyProduction,
        boolean allowLegacyAutomaticCompanyProduction,
        boolean advancedFinanceRequiresTerminal,
        boolean adminConsoleRequiresPermission,
        double terminalInteractionDistance
) {
    public FinanceAccessPolicy {
        if (!Double.isFinite(terminalInteractionDistance) || terminalInteractionDistance <= 0) {
            terminalInteractionDistance = 8.0D;
        }
    }

    public static FinanceAccessPolicy current() {
        return new FinanceAccessPolicy(
                FinanceConfig.minecraftFirstMode(),
                FinanceConfig.requirePhysicalTerminal(),
                FinanceConfig.enablePortableLedger(),
                FinanceConfig.legacyFullScreenKeybind(),
                FinanceConfig.warehouseCapacityEnabled(),
                FinanceConfig.contractsEnabled(),
                FinanceConfig.playerDrivenCompanyProduction(),
                FinanceConfig.allowLegacyAutomaticCompanyProduction(),
                FinanceConfig.advancedFinanceRequiresTerminal(),
                FinanceConfig.adminConsoleRequiresPermission(),
                FinanceConfig.terminalInteractionDistance());
    }
}
