package finance.index;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketIndexServiceTest {
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void sectorIndicesRemainIsolated() {
        addListed("RAW", CompanyType.RAW_MATERIALS, 10);
        Stock food = addListed("FOOD", CompanyType.FOOD, 10);
        MarketIndexService.closeDay(0);
        food.setLastPrice(20);
        MarketIndexService.closeDay(1);
        assertEquals(0, MarketIndexService.changePercent("sector:RAW_MATERIALS"), 0.0001);
        assertEquals(100, MarketIndexService.changePercent("sector:FOOD"), 0.0001);
        assertTrue(MarketIndexService.changePercent(MarketIndexService.STOCK_COMPOSITE) > 0);
    }

    private static Stock addListed(String symbol, CompanyType type, long price) {
        UUID id = UUID.randomUUID();
        Company company = new Company(id, symbol, type, 1_000);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        Stock stock = new Stock(symbol, symbol, id, 100, 100, 0, price, price);
        StockMarketManager.putStockDirect(stock);
        return stock;
    }
}
