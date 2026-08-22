package finance.data;

import finance.account.AccountManager;
import finance.diagnostic.EconomyConsistencyService;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MigrationMatrixTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000007001");

    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @ParameterizedTest
    @ValueSource(ints = {13, 15, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29})
    void historicalVersionFixtureUpgradesDirectlyAndRepeatedLoadDoesNotMint(int version) {
        AccountManager.deposit(PLAYER, 12_345);
        CompoundTag fixture = new EconomySavedData().save(new CompoundTag());
        fixture.putInt("DataVersion", version);
        fixture.remove("Diagnostics");
        if (version < 25) fixture.remove("Governance");
        if (version < 24) fixture.remove("Insurance");
        if (version < 23) {
            fixture.remove("Funds");
            fixture.remove("FundPositions");
            fixture.remove("FundRedemptions");
            fixture.remove("FundPlans");
            fixture.remove("FundOperationKeys");
            fixture.remove("FundRiskAcknowledgements");
        }
        if (version < 21) fixture.remove("Banking");
        if (version < 20) fixture.remove("Futures");
        if (version < 19) {
            fixture.remove("BondMarket");
            fixture.remove("CentralBankBills");
        }
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(fixture);
        long first = AccountManager.getBalance(PLAYER);
        assertTrue(EconomyConsistencyService.run(0).healthy());
        CompoundTag upgraded = new EconomySavedData().save(new CompoundTag());
        assertEquals(EconomySavedData.currentDataVersion(), upgraded.getInt("DataVersion"));
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(upgraded);
        assertEquals(first, AccountManager.getBalance(PLAYER));
        assertTrue(EconomyConsistencyService.run(0).healthy());
    }
}
