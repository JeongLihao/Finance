package finance.simple;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import finance.margin.MarginService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 押注：预估算得对、开了不会马上被强卖、结束后押金退回钱包。 */
class SimpleBetServiceTest {

    private static final String SYMBOL = "BETS";

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        Company company = new Company(UUID.randomUUID(), "Bet Co", CompanyType.RAW_MATERIALS, 500_000L);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(
                new Stock(SYMBOL, "Bet Co", company.getCompanyId(), 10_000L, 10_000L, 0L, 100L, 100L));
    }

    private UUID player(long wallet) {
        UUID id = UUID.randomUUID();
        AccountManager.deposit(id, wallet);
        return id;
    }

    @Test
    void stockPreviewDoublesTheStakeAndNamesTheStopLines() {
        SimpleBetService.Preview up = SimpleBetService.stockPreview(1_000, 100, true, 130);
        assertEquals(20, up.quantity(), "投入 10 磅押 20 股（一半是借的）");
        assertEquals(2_000, up.notional());
        assertEquals(200, up.swing10(), "涨 10% 赚投入的 20%");
        assertEquals(35, up.stopPct(), "维持率 130%：看涨跌超 35% 被强卖");
        assertEquals(15, SimpleBetService.stockPreview(1_000, 100, false, 130).stopPct(), "看跌涨超 15% 被强制买回");
        assertEquals(0, SimpleBetService.stockPreview(40, 100, true, 130).quantity(), "不够一股的投入不能押");
    }

    @Test
    void futuresPreviewCountsWholeContracts() {
        // 每份 64 个、单价 10、保证金 10% → 每份押 64
        SimpleBetService.Preview pv = SimpleBetService.futuresPreview(1_000, 10, 64, 1_000);
        assertEquals(15, pv.quantity());
        assertEquals(64, SimpleBetService.futuresMinStake(10, 64, 1_000));
        assertEquals(0, SimpleBetService.futuresPreview(63, 10, 64, 1_000).quantity());
    }

    @Test
    void longBetPaysOnlyTheStakeIsSafeAndRefundsOnClose() {
        UUID id = player(100_000);
        long start = AccountManager.getBalance(id);
        SimpleBetService.Opened o = SimpleBetService.openStockFor(id, SYMBOL, true, 5_000);
        assertTrue(o.result().success(), o.result().message());
        assertEquals(o.own(), start - AccountManager.getBalance(id), "钱包只扣自己出的那一半");
        assertTrue(o.borrowed() > 0);
        assertTrue(MarginService.checkLiquidations().isEmpty(), "刚开的押注不能低于强制结束线");

        assertNull(SimpleBetService.closeStockFor(id, o.key()));
        MarginAccount acct = MarginPoolManager.getAccount(id);
        assertTrue(acct.getFinancedShares().isEmpty());
        assertEquals(0, acct.getCashCollateral(), "结束后押金自动退回钱包");
        long after = AccountManager.getBalance(id);
        assertTrue(after <= start, "自己推高再卖掉，来回赚不到钱");
        assertTrue(after > start - o.own(), "但不会把投入全亏光");
    }

    @Test
    void secondShortTopsUpTheDepositSoTheAccountIsNotUnderwater() {
        UUID id = player(100_000);
        assertTrue(
                SimpleBetService.openStockFor(id, SYMBOL, false, 2_000).result().success());
        SimpleBetService.Opened second = SimpleBetService.openStockFor(id, SYMBOL, false, 2_000);
        assertTrue(second.result().success(), second.result().message());
        MarginAccount acct = MarginPoolManager.getAccount(id);
        long frozen = acct.getShortProceeds().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        assertTrue(acct.getCashCollateral() * 2 >= frozen, "每笔借股卖出都押了一半：" + acct.getCashCollateral() + " / " + frozen);
        assertTrue(MarginService.checkLiquidations().isEmpty(), "连开两笔也不会马上被强制结束");
    }

    @Test
    void tooSmallOrUnaffordableBetsLeaveTheWalletAlone() {
        UUID poor = player(100);
        long start = AccountManager.getBalance(poor);
        assertFalse(
                SimpleBetService.openStockFor(poor, SYMBOL, true, 10).result().success(), "不够一股");
        assertFalse(
                SimpleBetService.openStockFor(poor, SYMBOL, true, start * 4)
                        .result()
                        .success(),
                "钱包不够");
        assertEquals(start, AccountManager.getBalance(poor));
        assertEquals(0, MarginPoolManager.getAccount(poor).getCashCollateral());
        assertFalse(SimpleBetService.openStockFor(poor, "NOPE", true, 5_000)
                .result()
                .success());
    }
}
