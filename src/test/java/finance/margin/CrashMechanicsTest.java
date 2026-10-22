package finance.margin;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.npcinvestor.NpcInvestorArchetype;
import finance.npcinvestor.SpeculatorService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPriceEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 玩家引发股灾需要的几样机制：噪音不累积、加杠杆来回买卖必亏、强制卖出连环踩踏、短线派追涨。 */
class CrashMechanicsTest {

    private static final String SYMBOL = "CRSH";
    private Stock stock;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        Company company = new Company(UUID.randomUUID(), "Crash Co", CompanyType.RAW_MATERIALS, 500_000L);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        stock = new Stock(SYMBOL, "Crash Co", company.getCompanyId(), 10_000L, 10_000L, 0L, 100L, 100L);
        StockMarketManager.putStockDirect(stock);
    }

    private UUID trader(long collateral) {
        UUID id = UUID.randomUUID();
        AccountManager.deposit(id, 1_000_000L);
        assertTrue(MarginService.depositCollateral(id, collateral).success());
        return id;
    }

    @Test void noiseNoLongerDriftsThePriceAwayFromFairValue() {
        StockPriceEngine engine = new StockPriceEngine("N", 1_000, 1_000);
        for (int i = 0; i < 2_000; i++) {
            engine.tickNoise();
            engine.recalculateFromCurrent();
            assertTrue(Math.abs(engine.getCurrentPrice() - 1_000) <= 20, "第 " + i + " 分钟：" + engine.getCurrentPrice());
        }
    }

    @Test void aLeveragedRoundTripAlwaysLosesMoney() {
        UUID id = trader(50_000);
        long before = AccountManager.getBalance(id) + MarginPoolManager.getAccount(id).getCashCollateral();
        assertTrue(MarginService.marginBuy(id, SYMBOL, 1_000).success());
        assertTrue(stock.getAnchorPrice() > 100, "买入推高市价");
        assertTrue(MarginService.closeFinanced(id, SYMBOL).success());
        long after = AccountManager.getBalance(id) + MarginPoolManager.getAccount(id).getCashCollateral();
        assertTrue(after < before, "自己推高再自己卖，按推动后的价格成交，必亏：" + before + " → " + after);
    }

    @Test void forcedSalesPushThePriceDownAndTriggerTheNextOne() {
        UUID early = trader(16_000);
        UUID late = trader(16_000);
        assertTrue(MarginService.marginBuy(early, SYMBOL, 300).success());
        assertTrue(MarginService.marginBuy(late, SYMBOL, 300).success()); // 后进的买得更贵，更早撑不住
        // 一笔大单砸掉 30% 流通股：市价跌 45%，两人都跌破 130% 维持线
        stock.recordTrade(1, 3_000, false);
        long afterDump = stock.getAnchorPrice();
        var liquidated = MarginService.checkLiquidations();
        assertEquals(2, liquidated.size(), "两人都被强制卖出：" + liquidated);
        assertTrue(stock.getAnchorPrice() < afterDump, "强制卖出卖进市场，继续压低股价");
    }

    @Test void debtLeftAfterForcedSaleIsSettledFromCollateralThenBadDebt() {
        UUID id = trader(16_000);
        assertTrue(MarginService.marginBuy(id, SYMBOL, 300).success());
        MarginAccount acct = MarginPoolManager.getAccount(id);
        long debtBefore = acct.getFinancedDebt();
        // 单笔成交最多推动 50%，连砸三笔才能跌到每股价值的 0.2 倍下限：卖光融资股也还不清垫付款
        for (int i = 0; i < 3; i++) stock.recordTrade(1, 3_000, false);
        assertTrue(300 * stock.impactedPrice(300, false) < debtBefore, "前提：卖出所得不够还债");
        assertFalse(MarginService.checkLiquidations().isEmpty());
        assertTrue(acct.getFinancedShares().isEmpty(), "融资股全部卖掉");
        assertEquals(0, acct.getFinancedDebt(), "剩下的垫付款不再挂着");
        assertEquals(0, acct.getFinancedInterestOwed());
        assertEquals(0, acct.getCashCollateral(), "缺口先扣光现金保证金");
        assertTrue(acct.getBadDebt() > 0, "还不够的部分记不良债务");
        assertFalse(MarginService.marginBuy(id, SYMBOL, 1).success(), "有不良债务时不能新开仓");
    }

    @Test void breakerStopsForcedSalesForAMinute() {
        UUID id = trader(20_000);
        assertTrue(MarginService.marginBuy(id, SYMBOL, 300).success());
        for (int i = 0; i < 6; i++) stock.recalculateFromCurrent();
        stock.recordTrade(1, 1_500, false); // 跌 22.5%，触发熔断
        assertTrue(stock.isBreakerHalted());
        assertTrue(MarginService.checkLiquidations().isEmpty(), "熔断中不强制卖出");
        stock.recalculateFromCurrent();
        assertFalse(stock.isBreakerHalted());
    }

    @Test void speculatorsChaseARallyWithLeverage() {
        UUID spec = NpcInvestorArchetype.SPECULATOR.accountId();
        AccountManager.getAccount(spec).setBalance(100_000);
        SpeculatorService.clearDirect();
        for (int i = 0; i < 11; i++) {
            SpeculatorService.tickMinute();
            stock.recordTrade(100, 50, true); // 每分钟有人买 0.5% 流通股，10 分钟涨约 7.7%
        }
        SpeculatorService.tickMinute();
        assertTrue(MarginService.financedSharesOf(spec, SYMBOL) > 0, "涨超 5% 后短线派加杠杆跟进");
    }
}
