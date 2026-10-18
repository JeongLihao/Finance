package finance.stock;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 新股价：家底 + 日利润 × 倍数；倍数只看央行利率；库存按底价算。 */
class StockValuationTest {

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("ore", "minecraft:iron_ore", "矿", CommodityCategory.RAW_MATERIALS, 10).reserveTier(1));
    }

    @Test void higherRatesMeanALowerMultiple() {
        assertEquals(83, StockValuation.multipleX10(400), "利率 4% 约 8 倍");
        assertEquals(55, StockValuation.multipleX10(1_000), "利率 10% 约 5.5 倍");
        assertEquals(120, StockValuation.multipleX10(0), "最多 12 倍");
        assertEquals(40, StockValuation.multipleX10(100_000), "最少 4 倍");
        assertTrue(StockValuation.multipleX10(800) < StockValuation.multipleX10(300));
    }

    @Test void cashOnlyCompanyIsWorthItsCash() {
        Company c = new Company(UUID.randomUUID(), "A", CompanyType.RAW_MATERIALS, 10_000);
        assertEquals(10_000, StockValuation.companyValue(c));
        assertEquals(100, StockValuation.perShare(c, 100));
    }

    @Test void inventoryCountsAtTheFloorPriceNotTheMarketPrice() {
        Company c = new Company(UUID.randomUUID(), "B", CompanyType.RAW_MATERIALS, 0);
        c.addInventory("ore", 100);
        assertEquals(500, StockValuation.inventoryAtFloor(c), "100 个 × 底价 5");
        assertEquals(500, StockValuation.companyValue(c));
    }

    @Test void profitsAddValueAndLossesNeverHalveItBelowHalfTheAssets() {
        Company rich = new Company(UUID.randomUUID(), "C", CompanyType.RAW_MATERIALS, 10_000);
        rich.restoreFinancials(0, 0, 0, 0, 0, List.of(1_000L), List.of());
        int m = StockValuation.currentMultipleX10();
        assertEquals(10_000 + 1_000L * m / 10, StockValuation.companyValue(rich));

        Company poor = new Company(UUID.randomUUID(), "D", CompanyType.RAW_MATERIALS, 10_000);
        poor.restoreFinancials(0, 0, 0, 0, 0, List.of(-1_000_000L), List.of());
        assertEquals(5_000, StockValuation.companyValue(poor), "亏损时估值不低于家底的一半");
    }
}
