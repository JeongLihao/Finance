package finance.governance;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockOrderManager;
import finance.stock.StockPortfolioManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 要约收购（TakeoverService）回归测试 —— 覆盖设计文档 §8 的 11 个用例。
 */
class TakeoverServiceTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000020001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000020002");
    private static final UUID SELLER_A = UUID.fromString("00000000-0000-0000-0000-000000020003");
    private static final UUID SELLER_B = UUID.fromString("00000000-0000-0000-0000-000000020004");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000020005");
    private static final String SYMBOL = "TKVR";

    private Company company;
    private Stock stock;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        company = new Company(COMPANY_ID, "Takeover Target", CompanyType.RAW_MATERIALS, 500_000L, OWNER);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        // totalShares=100, fairValue=100, currentPrice=100
        stock = new Stock(SYMBOL, "Takeover Target", COMPANY_ID, 100L, 100L, 0L, 100L, 100L);
        StockMarketManager.putStockDirect(stock);
        AccountManager.deposit(BUYER, 1_000_000L);
    }

    @AfterEach
    void clean() {
        EconomySavedData.resetRuntimeState();
    }

    private void giveShares(UUID holder, long shares) {
        assertTrue(StockPortfolioManager.addHolding(holder, SYMBOL, shares, 100L));
    }

    // ---------- §8.1 功能用例 ----------

    @Test
    void takeoverInitiationRequires30Percent() {
        giveShares(BUYER, 29L);
        giveShares(SELLER_A, 71L);
        // 29% → 拒绝
        var rejected = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-30a");
        assertFalse(rejected.success(), "29% 持股不应能发起要约");
        // 补到 30% → 通过
        giveShares(BUYER, 1L);
        var ok = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-30b");
        assertTrue(ok.success(), "30% 持股应能发起要约: " + ok.message());
    }

    @Test
    void takeoverPriceBounds() {
        giveShares(BUYER, 40L);
        giveShares(SELLER_A, 60L);
        // fairValue=100 → 允许区间 [80, 150]
        assertFalse(
                TakeoverService.offerTender(BUYER, COMPANY_ID, 79L, 10L, 5L, 5, 0L, "k-p1")
                        .success(),
                "0.79x fairValue 应被拒绝");
        assertFalse(
                TakeoverService.offerTender(BUYER, COMPANY_ID, 151L, 10L, 5L, 5, 0L, "k-p2")
                        .success(),
                "1.51x fairValue 应被拒绝");
        assertTrue(
                TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-p3")
                        .success(),
                "1.0x fairValue 应通过");
    }

    @Test
    void takeoverEscrowFreezesFullAmount() {
        giveShares(BUYER, 40L);
        giveShares(SELLER_A, 60L);
        long before = AccountManager.getBalance(BUYER);
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 20L, 10L, 5, 0L, "k-esc");
        assertTrue(opened.success(), opened.message());
        TenderOffer offer = CorporateActionManager.tenders().get(opened.id());
        assertNotNull(offer);
        // 收购方余额精确减少 price × target
        assertEquals(before - 2_000L, AccountManager.getBalance(BUYER));
        // escrow 余额精确等于冻结金额（真实资金划转，非铸造）
        assertEquals(2_000L, AccountManager.getBalance(offer.escrowId()));
    }

    @Test
    void takeoverSettlementProRata() {
        giveShares(BUYER, 35L);
        giveShares(SELLER_A, 40L);
        giveShares(SELLER_B, 25L);
        long sellerABefore = AccountManager.getBalance(SELLER_A);
        long sellerBBefore = AccountManager.getBalance(SELLER_B);
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 40L, 10L, 3, 0L, "k-pro");
        assertTrue(opened.success(), opened.message());
        assertTrue(TakeoverService.acceptTender(SELLER_A, opened.id(), 40L, 0L, "k-pro-a")
                .success());
        assertTrue(TakeoverService.acceptTender(SELLER_B, opened.id(), 25L, 0L, "k-pro-b")
                .success());
        CorporateActionManager.processDay(3);
        TenderOffer offer = CorporateActionManager.tenders().get(opened.id());
        assertEquals(CapitalActionStatus.COMPLETED, offer.status());
        // 超额接受（65 > 40）按比例分配：买方恰好拿到 40 股
        assertEquals(35L + 40L, StockPortfolioManager.getHolding(BUYER, SYMBOL).getQuantity());
        // 卖方按分配比例收到款项：A 得 40/65×40×100，B 得 25/65×40×100
        long paidA = AccountManager.getBalance(SELLER_A) - sellerABefore;
        long paidB = AccountManager.getBalance(SELLER_B) - sellerBBefore;
        assertTrue(paidA > paidB && paidA + paidB == 4_000L, "卖方应按比例收款，A=" + paidA + " B=" + paidB);
        // escrow 已清空
        assertEquals(0L, AccountManager.getBalance(offer.escrowId()));
    }

    @Test
    void takeoverFailsBelowMinimum() {
        giveShares(BUYER, 40L);
        giveShares(SELLER_A, 60L);
        long buyerBefore = AccountManager.getBalance(BUYER);
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 60L, 50L, 3, 0L, "k-min");
        assertTrue(opened.success(), opened.message());
        assertTrue(TakeoverService.acceptTender(SELLER_A, opened.id(), 10L, 0L, "k-min-a")
                .success());
        CorporateActionManager.processDay(3);
        TenderOffer offer = CorporateActionManager.tenders().get(opened.id());
        assertEquals(CapitalActionStatus.FAILED, offer.status());
        // escrow 全额退回收购方，锁定份额按原成本价返还
        assertEquals(buyerBefore, AccountManager.getBalance(BUYER));
        assertEquals(60L, StockPortfolioManager.getHolding(SELLER_A, SYMBOL).getQuantity());
        assertEquals(100L, StockPortfolioManager.getHolding(SELLER_A, SYMBOL).getAverageCost());
        // 失败后复牌
        assertFalse(stock.isTradingHalted());
    }

    @Test
    void takeoverHaltsTrading() {
        giveShares(BUYER, 40L);
        giveShares(SELLER_A, 60L);
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-halt");
        assertTrue(opened.success(), opened.message());
        assertTrue(stock.isTradingHalted(), "要约期内应停牌");
        // 停牌期间不可挂新单
        var buyResult = StockOrderManager.placeBuyOrder(SELLER_A, SYMBOL, 100L, 1);
        assertFalse(buyResult.success());
        assertTrue(buyResult.message().contains("停牌"));
        var sellResult = StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 100L, 1);
        assertFalse(sellResult.success());
        assertTrue(sellResult.message().contains("停牌"));
        // 撤销后复牌
        assertTrue(
                TakeoverService.cancelTender(BUYER, opened.id(), 1L, "k-halt-c").success());
        assertFalse(stock.isTradingHalted(), "撤销后应复牌");
    }

    @Test
    void takeoverSqueezeOutDelists() {
        giveShares(BUYER, 90L);
        giveShares(SELLER_A, 10L);
        long sellerBefore = AccountManager.getBalance(SELLER_A);
        var result = TakeoverService.squeezeOut(BUYER, COMPANY_ID, 0L, "k-sq");
        assertTrue(result.success(), "90% 控股应能发起强制私有化: " + result.message());
        TenderOffer offer = CorporateActionManager.tenders().get(result.id());
        assertNotNull(offer);
        assertTrue(offer.isSqueezeOut());
        CorporateActionManager.processDay(1);
        assertEquals(CapitalActionStatus.COMPLETED, offer.status());
        // 小股东按保护价（= fairValue 100，无历史要约/VWAP）被收购
        assertEquals(sellerBefore + 1_000L, AccountManager.getBalance(SELLER_A));
        assertEquals(100L, StockPortfolioManager.getHolding(BUYER, SYMBOL).getQuantity());
        // 公司退市：摘牌 + 转非公开
        assertNull(StockMarketManager.getStockByCompanyId(COMPANY_ID));
        assertFalse(company.isPublic());
    }

    @Test
    void takeoverBankruptcyCancels() {
        giveShares(BUYER, 40L);
        giveShares(SELLER_A, 60L);
        long buyerBefore = AccountManager.getBalance(BUYER);
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 20L, 10L, 5, 0L, "k-br");
        assertTrue(opened.success(), opened.message());
        assertTrue(TakeoverService.acceptTender(SELLER_A, opened.id(), 20L, 0L, "k-br-a")
                .success());
        assertTrue(CorporateActionManager.cancelForBankruptcy(COMPANY_ID, 1L));
        TenderOffer offer = CorporateActionManager.tenders().get(opened.id());
        assertEquals(CapitalActionStatus.CANCELLED, offer.status());
        assertEquals(buyerBefore, AccountManager.getBalance(BUYER));
        assertEquals(60L, StockPortfolioManager.getHolding(SELLER_A, SYMBOL).getQuantity());
        assertFalse(stock.isTradingHalted(), "破产取消后应复牌");
    }

    @Test
    void tenderLockedDividendGoesToEscrow() {
        giveShares(BUYER, 40L);
        giveShares(SELLER_A, 60L);
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 20L, 10L, 5, 0L, "k-div");
        assertTrue(opened.success(), opened.message());
        assertTrue(TakeoverService.acceptTender(SELLER_A, opened.id(), 30L, 0L, "k-div-a")
                .success());
        TenderOffer offer = CorporateActionManager.tenders().get(opened.id());

        // 分红：100 股 × 每股 100 = 10_000；SELLER_A 自由 30 股 / 锁定 30 股 / BUYER 40 股
        company.addDistributableProfit(10_000L);
        CompanyManager.setDividendPolicy(1.0, 1);
        long sellerBefore = AccountManager.getBalance(SELLER_A);
        long buyerBefore = AccountManager.getBalance(BUYER);
        CompanyManager.tryDividends(1); // 首次仅登记分红日
        CompanyManager.tryDividends(2); // 第二次实际分红

        // SELLER_A 只拿到自由 30 股的分红（3_000），锁定 30 股的分红进了 escrow
        assertEquals(sellerBefore + 3_000L, AccountManager.getBalance(SELLER_A));
        assertEquals(buyerBefore + 4_000L, AccountManager.getBalance(BUYER));
        assertEquals(3_000L, offer.acceptedDividends().getOrDefault(SELLER_A, 0L));
        assertEquals(2_000L + 3_000L, AccountManager.getBalance(offer.escrowId()));

        // 撤销要约 → 锁定分红原路返还股东，收购方拿回全额托管金（2_000）+ 自己的分红（4_000）
        assertTrue(
                TakeoverService.cancelTender(BUYER, opened.id(), 1L, "k-div-c").success());
        assertEquals(sellerBefore + 6_000L, AccountManager.getBalance(SELLER_A));
        assertEquals(buyerBefore + 6_000L, AccountManager.getBalance(BUYER));
    }

    // ---------- §8.2 反刷钱用例 ----------

    @Test
    void founderLockedSharesCannotBeTendered() {
        // OWNER 是创始人且处于 7 天锁定期
        stock.setFounderLockup(OWNER, 7);
        giveShares(OWNER, 40L);
        giveShares(BUYER, 35L);
        giveShares(SELLER_A, 25L);
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-fl");
        assertTrue(opened.success(), opened.message());
        // 锁定期内创始人接受要约 → 拒绝（补 P0-2 后门）
        var rejected = TakeoverService.acceptTender(OWNER, opened.id(), 10L, 0L, "k-fl-a");
        assertFalse(rejected.success(), "锁定期内创始人不应能通过要约变现");
        assertEquals(40L, StockPortfolioManager.getHolding(OWNER, SYMBOL).getQuantity());
        // 锁定期结束后 → 允许
        stock.restoreFounderLockup(OWNER, 0);
        var ok = TakeoverService.acceptTender(OWNER, opened.id(), 10L, 0L, "k-fl-b");
        assertTrue(ok.success(), "锁定期结束后应允许接受: " + ok.message());
    }

    @Test
    void selfDealingRejected() {
        giveShares(OWNER, 40L);
        giveShares(BUYER, 35L);
        giveShares(SELLER_A, 25L);
        // 目标公司 owner 对自己公司发起要约 → 拒绝
        var selfOffer = TakeoverService.offerTender(OWNER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-sd1");
        assertFalse(selfOffer.success(), "owner 不应对自己公司发起要约");
        // 正常要约发起后，收购方接受自己的要约 → 拒绝
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-sd2");
        assertTrue(opened.success(), opened.message());
        var selfAccept = TakeoverService.acceptTender(BUYER, opened.id(), 5L, 0L, "k-sd3");
        assertFalse(selfAccept.success(), "收购方不应能接受自己的要约");
    }

    @Test
    void doubleLockPrevented() {
        giveShares(BUYER, 40L);
        giveShares(SELLER_A, 60L);
        // SELLER_A 先挂 60 股卖单（价格远高于做市商 bid，确保进订单簿而非立即成交）
        var orderResult = StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 10_000L, 60);
        assertTrue(orderResult.success(), "卖单应进入订单簿: " + orderResult.message());
        assertEquals(0L, StockPortfolioManager.getHolding(SELLER_A, SYMBOL).getQuantity());
        // 再发起要约（此时停牌，但已有订单保留在簿上）
        var opened = TakeoverService.offerTender(BUYER, COMPANY_ID, 100L, 10L, 5L, 5, 0L, "k-dl");
        assertTrue(opened.success(), opened.message());
        // 试图接受 50 股 → 拒绝（可用只有 0）
        var rejected = TakeoverService.acceptTender(SELLER_A, opened.id(), 50L, 0L, "k-dl-a");
        assertFalse(rejected.success(), "订单锁定的份额不应能重复接受要约");
        // 撤单后恢复持仓，接受应成功
        StockOrderManager.cancelOrdersForSymbol(SYMBOL);
        assertEquals(60L, StockPortfolioManager.getHolding(SELLER_A, SYMBOL).getQuantity());
        var ok = TakeoverService.acceptTender(SELLER_A, opened.id(), 50L, 0L, "k-dl-b");
        assertTrue(ok.success(), "撤单后应能正常接受: " + ok.message());
    }
}
