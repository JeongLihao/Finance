package finance.simple;

import finance.bank.BankingManager;
import finance.data.EconomySavedData;
import finance.fund.FundManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimpleInvestServiceTest {

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        BankingManager.ensureDefaultBanks();
        FundManager.seedDefaultsIfNeeded();
    }

    @Test void lockedPlayersSeeSteadyAndBalancedOnly() {
        List<SimpleInvestService.Product> products = SimpleInvestService.products(name -> false);
        List<String> keys = products.stream().map(SimpleInvestService.Product::key).toList();
        assertEquals(List.of("tier:safe", "tier:balanced"), keys, "没解锁进取理财时只有两档");
        assertTrue(products.get(0).name().startsWith("稳健"), products.toString());
        assertTrue(products.get(1).name().startsWith("均衡"), products.toString());
        assertEquals("bank", SimpleInvestService.resolve("tier:safe", name -> false));
        assertTrue(SimpleInvestService.resolve("tier:balanced", name -> false).startsWith("time:"), "没解锁时均衡只买定期");
        assertNull(SimpleInvestService.resolve("tier:growth", name -> false), "没解锁的档位买不了");
        for (SimpleInvestService.Product p : products) {
            assertTrue(List.of("低", "中", "高").contains(p.risk()), p.toString());
            assertTrue(p.rateBps() >= -1, p.toString());
        }
    }

    @Test void growthTierBuysAStockFundOnceUnlocked() {
        List<String> keys = SimpleInvestService.products(name -> true).stream().map(SimpleInvestService.Product::key).toList();
        assertEquals(List.of("tier:safe", "tier:balanced", "tier:growth"), keys);
        String fund = SimpleInvestService.resolve("tier:growth", name -> true);
        assertNotNull(fund);
        assertTrue(fund.startsWith("fund:"), fund);
        String balanced = SimpleInvestService.resolve("tier:balanced", name -> true);
        assertTrue(balanced.startsWith("time:") || balanced.startsWith("cbond:"), balanced);
        assertEquals("time:30", SimpleInvestService.resolve("time:30", name -> false), "旧界面直接买具体产品照样可以");
    }

    @Test void newPlayerHasNoMarginPositions() {
        SimpleInvestService.MarginInfo m = SimpleInvestService.marginInfo(UUID.randomUUID());
        assertEquals(0, m.collateral());
        assertTrue(m.levered().isEmpty());
        assertTrue(m.shorts().isEmpty());
    }
}
