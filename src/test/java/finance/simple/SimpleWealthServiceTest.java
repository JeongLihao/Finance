package finance.simple;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.margin.MarginService;
import finance.network.PriceTablePacket;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 身家怎么算、押注战绩怎么累计、价格表怎么传。 */
class SimpleWealthServiceTest {

    private static final String SYMBOL = "RICH";

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        Company company = new Company(UUID.randomUUID(), "Rich Co", CompanyType.RAW_MATERIALS, 500_000L);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock(SYMBOL, "Rich Co", company.getCompanyId(), 10_000L, 10_000L, 0L, 100L, 100L));
    }

    @Test void netWorthAddsWalletStocksAndBetEquity() {
        UUID id = UUID.randomUUID();
        AccountManager.deposit(id, 50_000);
        long wallet = AccountManager.getBalance(id);
        assertEquals(wallet, SimpleWealthService.netWorth(id), "只有钱包时身家就是钱包");

        assertTrue(StockPortfolioManager.addHolding(id, SYMBOL, 10, 100));
        long price = StockMarketManager.getStock(SYMBOL).getAnchorPrice();
        assertEquals(wallet + 10 * price, SimpleWealthService.netWorth(id), "股票按市价算");

        long before = SimpleWealthService.netWorth(id);
        SimpleBetService.Opened o = SimpleBetService.openStockFor(id, SYMBOL, true, 2_000);
        assertTrue(o.result().success(), o.result().message());
        long after = SimpleWealthService.netWorth(id);
        assertTrue(Math.abs(after - before) <= o.own() / 10, "开押注只是把钱换成押注现值，身家基本不变：" + before + " → " + after);
        assertTrue(MarginService.checkLiquidations().isEmpty());
    }

    @Test void betStatsTrackCountWinsTotalBestAndWorst() {
        SimpleBetService.Stats s = SimpleBetService.Stats.EMPTY;
        assertEquals("", s.line(), "一笔都没有时不显示");
        s = s.plus(500).plus(-200).plus(1_000).plus(0);
        assertEquals(4, s.count());
        assertEquals(2, s.wins(), "赚了才算赢，持平不算");
        assertEquals(1_300, s.total());
        assertEquals(1_000, s.best());
        assertEquals(-200, s.worst());
        assertTrue(s.line().startsWith("战绩：4 笔，赚了 2 笔"), s.line());
        assertEquals(-50, SimpleBetService.Stats.EMPTY.plus(-50).best(), "第一笔就是最赚也是最亏");
    }

    @Test void priceTableRoundTrips() {
        List<PriceTablePacket.Row> rows = List.of(new PriceTablePacket.Row("minecraft:iron_ingot", 120, 150),
                new PriceTablePacket.Row("minecraft:wheat", 5, 7));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PriceTablePacket.encode(new PriceTablePacket(rows), buf);
        assertEquals(rows, PriceTablePacket.decode(buf).rows());
        for (PriceTablePacket.Row r : PriceTablePacket.snapshot()) {
            assertTrue(r.bid() > 0, r.toString());
            assertFalse(r.itemId().isBlank());
        }
    }
}
