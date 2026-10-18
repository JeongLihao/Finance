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

    @Test void productListCoversDepositsBondsAndFunds() {
        List<SimpleInvestService.Product> products = SimpleInvestService.products();
        List<String> keys = products.stream().map(SimpleInvestService.Product::key).toList();
        assertTrue(keys.contains("bank"), keys.toString());
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("time:")), keys.toString());
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("cbond:")), keys.toString());
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("fund:")), keys.toString());
        for (SimpleInvestService.Product p : products) {
            assertFalse(p.name().isBlank());
            assertTrue(List.of("低", "中", "高").contains(p.risk()), p.toString());
            assertTrue(p.rateBps() >= -1, p.toString());
        }
    }

    @Test void newPlayerHasNoMarginPositions() {
        SimpleInvestService.MarginInfo m = SimpleInvestService.marginInfo(UUID.randomUUID());
        assertEquals(0, m.collateral());
        assertTrue(m.levered().isEmpty());
        assertTrue(m.shorts().isEmpty());
    }
}
