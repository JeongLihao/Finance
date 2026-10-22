package finance.market;

import static org.junit.jupiter.api.Assertions.*;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 央行储备解锁只看玩家：玩家买卖、玩家自己公司的买卖算，系统公司不算。 */
class ReserveTierCountingTest {

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
    }

    @Test
    void onlyPlayerActivityCounts() {
        UUID player = UUID.randomUUID();
        Company system = new Company(UUID.randomUUID(), "系统", CompanyType.RAW_MATERIALS, 1_000);
        Company owned = new Company(UUID.randomUUID(), "玩家的", CompanyType.RAW_MATERIALS, 1_000, player);
        CompanyManager.registerDirect(system);
        CompanyManager.registerDirect(owned);
        assertTrue(CommodityTradeRecorder.countsTowardReserveTiers(
                NpcMarketMaker.NPC_UUID, player, CommodityTradeSource.PLAYER_NPC));
        assertFalse(CommodityTradeRecorder.countsTowardReserveTiers(
                NpcMarketMaker.NPC_UUID, system.getCompanyId(), CommodityTradeSource.COMPANY_NPC));
        assertTrue(CommodityTradeRecorder.countsTowardReserveTiers(
                NpcMarketMaker.NPC_UUID, owned.getCompanyId(), CommodityTradeSource.COMPANY_NPC));
    }
}
