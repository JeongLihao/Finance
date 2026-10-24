package finance.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.account.Account;
import finance.account.AccountManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.governance.ShareholderRegistryService;
import finance.market.MarketMakerLedger;
import finance.npcinvestor.NpcFloatPlacementService;
import finance.npcinvestor.NpcInvestorArchetype;
import finance.npcinvestor.NpcInvestorManager;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** B3：上市改成真实认购——钱来自认购人，期满按实际认到的股数发股，不够就退款。 */
class CompanyIpoSubscriptionTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000004301");
    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-000000004302");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000004303");

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        // NPC 资金池（也是做市商）先以系统账户建好：普通账户第一次被访问会领新手资金，测试里会多出钱
        AccountManager.getOrCreateSystemAccount(finance.market.NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(OWNER, 100_000);
        AccountManager.deposit(PLAYER, 100_000);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    /** 估值约 100,000 的玩家公司（现金），总股本 10,000 时每股约 10。 */
    private static Company company() {
        Company c = new Company(COMPANY, "Ipo Works", CompanyType.RAW_MATERIALS, 100_000, OWNER);
        CompanyManager.registerDirect(c);
        return c;
    }

    private static void fundNpcs(long each) {
        NpcInvestorManager.ensureAccounts();
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) AccountManager.deposit(a.accountId(), each);
    }

    private static long totalMoney() {
        long sum = 0;
        for (Account a : AccountManager.getAccounts().values()) sum = Math.addExact(sum, a.getBalance());
        for (Company c : CompanyManager.getCompanies()) sum = Math.addExact(sum, c.getCash());
        return sum;
    }

    private static long perShare(Company c) {
        return finance.stock.StockValuation.perShare(c, 10_000);
    }

    @Test
    void npcSubscriptionListsCompanyAndConservesMoney() {
        Company c = company();
        fundNpcs(200_000);
        long price = perShare(c);
        long moneyBefore = totalMoney();

        CompanyIPOService.Result r = CompanyIPOService.start(OWNER, COMPANY, price, 4_000, 0);
        assertTrue(r.success(), r.message());
        IpoSubscription ipo = r.subscription();
        assertFalse(c.isPublic(), "认购期内还没上市");
        assertEquals(10_000, ipo.totalShares());

        for (long day = 1; day <= ipo.endDay(); day++) CompanyIPOService.processDay(day);

        assertTrue(c.isPublic());
        assertEquals(IpoSubscription.Status.LISTED, ipo.status());
        assertEquals(moneyBefore, totalMoney(), "全程不凭空出钱：上市费进 NPC 资金池，募资来自认购人");
        assertEquals(0, AccountManager.getBalance(ipo.escrowAccount()), "托管清空");
        Stock stock = StockMarketManager.getStockByCompanyId(COMPANY);
        assertNotNull(stock);
        long sold = ipo.subscribedShares();
        assertTrue(sold >= ipo.minimumShares(FinanceConfig.ipoMinFillBasisPoints()));
        assertEquals(sold, stock.getFloatShares());
        assertEquals(
                10_000 - sold,
                StockPortfolioManager.getHolding(OWNER, stock.getSymbol()).getQuantity(),
                "创始人拿剩下的");
        long npc = 0;
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values())
            npc += StockPortfolioManager.getHolding(a.accountId(), stock.getSymbol())
                    .getQuantity();
        assertTrue(npc > 0, "NPC 投资者认购了");
        assertEquals(
                ipo.subscribedBy(MarketMakerLedger.MASTER_UUID),
                MarketMakerLedger.getStockInventory(stock.getSymbol()));
        // 股份守恒：持仓 + 做市商库存 = 总股本，没有可配售的残差
        ShareholderRegistryService.Snapshot snap = ShareholderRegistryService.snapshot(COMPANY, 0);
        assertTrue(snap.consistent());
        assertEquals(MarketMakerLedger.getStockInventory(stock.getSymbol()), snap.systemLiquidity());
        assertEquals(0, NpcFloatPlacementService.availableResidual(stock, 0));
        assertTrue(stock.isFounderLocked(OWNER));
    }

    @Test
    void undersubscribedIpoIsCancelledAndRefundedExactly() {
        Company c = company();
        // NPC 不认（发行价远高于每股价值），只有玩家认了一点
        long price = Math.min(CompanyIPOService.MAX_ISSUE_PRICE, perShare(c) * 5);
        CompanyIPOService.Result r = CompanyIPOService.start(OWNER, COMPANY, price, 4_000, 0);
        assertTrue(r.success(), r.message());
        IpoSubscription ipo = r.subscription();
        long ownerAfterFee = AccountManager.getBalance(OWNER);
        long playerBefore = AccountManager.getBalance(PLAYER);
        long mmBefore = AccountManager.getBalance(MarketMakerLedger.MASTER_UUID);
        long mmPaid = ipo.subscribedBy(MarketMakerLedger.MASTER_UUID) * price;
        assertTrue(CompanyIPOService.subscribe(PLAYER, ipo.id(), 100, 1).success());
        assertEquals(playerBefore - 100 * price, AccountManager.getBalance(PLAYER));

        fundNpcs(200_000);
        for (long day = 1; day <= ipo.endDay(); day++) CompanyIPOService.processDay(day);

        assertEquals(IpoSubscription.Status.CANCELLED, ipo.status());
        assertFalse(c.isPublic());
        assertEquals(null, StockMarketManager.getStockByCompanyId(COMPANY));
        assertEquals(playerBefore, AccountManager.getBalance(PLAYER), "认购款原路退回");
        assertEquals(mmBefore + mmPaid, AccountManager.getBalance(MarketMakerLedger.MASTER_UUID), "做市商也退回");
        assertEquals(ownerAfterFee, AccountManager.getBalance(OWNER), "上市费不退");
        assertEquals(0, AccountManager.getBalance(ipo.escrowAccount()));
        assertEquals(100_000, c.getCash(), "公司现金没动");

        // 取消后可以重新发起
        assertTrue(CompanyIPOService.start(OWNER, COMPANY, perShare(c), 4_000, ipo.endDay())
                .success());
    }

    @Test
    void repeatedRequestsDoNotDoubleChargeOrDoubleSettle() {
        Company c = company();
        long ownerBefore = AccountManager.getBalance(OWNER);
        IpoSubscription ipo =
                CompanyIPOService.start(OWNER, COMPANY, perShare(c), 4_000, 0).subscription();
        long fee = FinanceConfig.ipoFee();
        assertEquals(ownerBefore - fee, AccountManager.getBalance(OWNER));

        CompanyIPOService.Result again = CompanyIPOService.start(OWNER, COMPANY, perShare(c), 4_000, 0);
        assertFalse(again.success(), "同一公司只能有一个进行中的认购");
        assertEquals(ownerBefore - fee, AccountManager.getBalance(OWNER), "重复发起不重复收上市费");

        assertFalse(CompanyIPOService.subscribe(OWNER, ipo.id(), 10, 1).success(), "创始人不能认购");
        assertTrue(CompanyIPOService.subscribe(PLAYER, ipo.id(), 1_000_000, 1).success());
        assertEquals(4_000, ipo.subscribedShares(), "超过剩余额度的部分不认");
        assertFalse(CompanyIPOService.subscribe(PLAYER, ipo.id(), 1, 1).success(), "认满后不再收钱");

        CompanyIPOService.processDay(1);
        assertTrue(c.isPublic(), "认满当天就结算");
        long cash = c.getCash();
        CompanyIPOService.processDay(2);
        CompanyIPOService.processDay(ipo.endDay());
        assertEquals(cash, c.getCash(), "结算只做一次");
        assertFalse(CompanyIPOService.subscribe(PLAYER, ipo.id(), 1, 2).success());
    }

    @Test
    void subscriptionSurvivesSaveAndLoad() {
        Company c = company();
        IpoSubscription ipo =
                CompanyIPOService.start(OWNER, COMPANY, perShare(c), 4_000, 0).subscription();
        assertTrue(CompanyIPOService.subscribe(PLAYER, ipo.id(), 3_000, 1).success());
        long escrow = AccountManager.getBalance(ipo.escrowAccount());

        CompoundTag tag = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(tag);

        IpoSubscription loaded = CompanyIPOService.get(ipo.id());
        assertNotNull(loaded);
        assertEquals(IpoSubscription.Status.OPEN, loaded.status());
        assertEquals(3_000, loaded.subscribedBy(PLAYER));
        assertEquals(escrow, AccountManager.getBalance(loaded.escrowAccount()));

        CompanyIPOService.processDay(loaded.endDay());
        Company reloaded = CompanyManager.getCompany(COMPANY);
        assertTrue(reloaded.isPublic(), "读档后认购继续，期满照常结算");
        Stock stock = StockMarketManager.getStockByCompanyId(COMPANY);
        assertEquals(
                3_000,
                StockPortfolioManager.getHolding(PLAYER, stock.getSymbol()).getQuantity());
    }

    @Test
    void npcsOnlySubscribeAtOrBelowTheirValuation() {
        Company c = company();
        fundNpcs(200_000);
        IpoSubscription cheap =
                CompanyIPOService.start(OWNER, COMPANY, perShare(c), 4_000, 0).subscription();
        assertTrue(CompanyIPOService.npcDemand(cheap, c, 1) > 0, "按估值定价时 NPC 认购");
        CompanyIPOService.cancel(cheap, "test");

        IpoSubscription dear = CompanyIPOService.start(OWNER, COMPANY, perShare(c) * 2, 4_000, 1)
                .subscription();
        assertEquals(0, CompanyIPOService.npcDemand(dear, c, 2), "发行价比每股价值高一倍，没有 NPC 认购");
    }
}
