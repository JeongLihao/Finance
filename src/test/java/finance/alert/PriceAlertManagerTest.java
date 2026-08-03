package finance.alert;

import finance.data.EconomySavedData;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceAlertManagerTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000003001");

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
        MarketPrice price = new MarketPrice("iron", 100, 0.05);
        price.setMidPrice(100);
        NpcMarketMaker.putMarketPrice("iron", price);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    @Test
    void triggeredAlertIsRemovedAfterOneCheck() {
        PriceAlertManager.AddResult result = PriceAlertManager.addAlert(
                PLAYER_ID, PriceAlertType.COMMODITY, "iron", PriceAlertDirection.ABOVE, 90);

        assertTrue(result.success());
        assertEquals(1, PriceAlertManager.checkAlertsForTest());

        assertEquals(0, PriceAlertManager.getAlertsForPlayer(PLAYER_ID).size());
    }
}
