package finance.gameplay;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceAccessServiceTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test
    void portableLedgerCannotOpenAdministratorMode() {
        FinanceAccessDecision result = authorize(FinanceTerminalType.PORTABLE_LEDGER,
                FinanceScreenMode.ADMIN, 0, true, 0, defaultPolicy());
        assertFalse(result.allowed());
        assertEquals("finance.access.mode_mismatch", result.messageKey());
    }

    @Test
    void marketTerminalOnlyOpensMarketMode() {
        assertTrue(authorize(FinanceTerminalType.MARKET_TERMINAL,
                FinanceScreenMode.MARKET, 0, true, 4, defaultPolicy()).allowed());
        assertFalse(authorize(FinanceTerminalType.MARKET_TERMINAL,
                FinanceScreenMode.ADVANCED, 0, true, 4, defaultPolicy()).allowed());
    }

    @Test
    void securitiesTerminalOpensAdvancedMode() {
        FinanceAccessDecision result = authorize(FinanceTerminalType.SECURITIES_TERMINAL,
                FinanceScreenMode.ADVANCED, 0, true, 16, defaultPolicy());
        assertTrue(result.allowed());
        assertEquals(FinanceScreenMode.ADVANCED, result.screenMode());
    }

    @Test
    void disablingMinecraftFirstPreservesLegacyFullScreen() {
        FinanceAccessPolicy legacyPolicy = policy(false, true, false, true, true);
        assertTrue(authorize(FinanceTerminalType.LEGACY_FULL_SCREEN,
                FinanceScreenMode.ADVANCED, 0, true, 0, legacyPolicy).allowed());
    }

    @Test
    void defaultMinecraftFirstRequiresAdvancedTerminal() {
        FinanceAccessDecision result = authorize(FinanceTerminalType.LEGACY_FULL_SCREEN,
                FinanceScreenMode.ADVANCED, 0, true, 0, defaultPolicy());
        assertFalse(result.allowed());
        assertEquals("finance.access.terminal_required", result.messageKey());
    }

    @Test
    void physicalTerminalRequiresVerifiedNearbySameDimensionSession() {
        assertFalse(authorize(FinanceTerminalType.WAREHOUSE_CONTROLLER,
                FinanceScreenMode.WAREHOUSE, 0, false, 4, defaultPolicy()).allowed());
        FinanceAccessContext otherDimension = new FinanceAccessContext(PLAYER,
                FinanceTerminalType.WAREHOUSE_CONTROLLER, FinanceScreenMode.WAREHOUSE,
                "minecraft:overworld", net.minecraft.core.BlockPos.ZERO, 10L, 0, true, false, 4);
        assertEquals("finance.access.too_far", FinanceAccessService.authorize(
                otherDimension, defaultPolicy()).messageKey());
        assertFalse(authorize(FinanceTerminalType.WAREHOUSE_CONTROLLER,
                FinanceScreenMode.WAREHOUSE, 0, true, 65, defaultPolicy()).allowed());
    }

    @Test
    void centralBankConsoleRequiresAdministratorPermission() {
        assertFalse(authorize(FinanceTerminalType.CENTRAL_BANK_CONSOLE,
                FinanceScreenMode.ADMIN, 1, true, 4, defaultPolicy()).allowed());
        assertTrue(authorize(FinanceTerminalType.CENTRAL_BANK_CONSOLE,
                FinanceScreenMode.ADMIN, 2, true, 4, defaultPolicy()).allowed());
    }

    @Test
    void administratorRetainsEmergencyLegacyAccess() {
        assertTrue(authorize(FinanceTerminalType.LEGACY_FULL_SCREEN,
                FinanceScreenMode.ADVANCED, 2, true, 0, defaultPolicy()).allowed());
    }

    @Test
    void everyPhysicalEntryRejectsUnrelatedModes() {
        for (FinanceTerminalType type : FinanceTerminalType.values()) {
            if (!type.isPhysicalTerminal()) continue;
            FinanceScreenMode expected = FinanceAccessService.modeFor(type);
            assertTrue(type.allowedModes().contains(expected));
            FinanceScreenMode unrelated = expected == FinanceScreenMode.MARKET
                    ? FinanceScreenMode.COMPANY : FinanceScreenMode.MARKET;
            assertFalse(authorize(type, unrelated, type == FinanceTerminalType.CENTRAL_BANK_CONSOLE ? 2 : 0,
                    true, 1, defaultPolicy()).allowed());
        }
    }

    @Test
    void configuredTerminalDistanceIsEnforced() {
        FinanceAccessPolicy shortRange = new FinanceAccessPolicy(true, true, true, false,
                true, true, true, true, true, true, 2.0D);
        assertTrue(authorize(FinanceTerminalType.MARKET_TERMINAL,
                FinanceScreenMode.MARKET, 0, true, 4.0D, shortRange).allowed());
        assertFalse(authorize(FinanceTerminalType.MARKET_TERMINAL,
                FinanceScreenMode.MARKET, 0, true, 4.01D, shortRange).allowed());
    }

    private static FinanceAccessDecision authorize(FinanceTerminalType type, FinanceScreenMode mode,
                                                    int permission, boolean verified, double distance,
                                                    FinanceAccessPolicy policy) {
        return FinanceAccessService.authorize(
                new FinanceAccessContext(PLAYER, type, mode, "minecraft:overworld",
                        type.isPhysicalTerminal() ? net.minecraft.core.BlockPos.ZERO : null,
                        10L, permission, verified, true, distance), policy);
    }

    private static FinanceAccessPolicy defaultPolicy() {
        return policy(true, true, false, true, true);
    }

    private static FinanceAccessPolicy policy(boolean minecraftFirst, boolean physical,
                                              boolean legacyKey, boolean advancedTerminal,
                                              boolean adminPermission) {
        return new FinanceAccessPolicy(minecraftFirst, physical, true, legacyKey,
                true, true, true, true, advancedTerminal, adminPermission, 8.0D);
    }
}
