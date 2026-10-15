package finance.npcinvestor;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.governance.ShareholderRegistryService;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockOrder;
import finance.stock.StockPortfolioManager;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcInvestorServiceTest {
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000a002");
    private static final String SYMBOL = "NPCT";
    private static final long TOTAL = 10_000L;
    private static final long POOL = 2_000_000L;

    /** 单原型 20%、合计 49%、每日 2% 流通股。 */
    private static final NpcInvestorService.Settings SETTINGS =
            new NpcInvestorService.Settings(true, 50_000L, 5_000L, 1_000_000L, 2_000, 4_900, 200);

    private Company company;
    private Stock stock;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID).deposit(POOL);
        // 公司现金 500k → 每股基本面 50，远高于股价 10，价值派会满仓
        company = new Company(COMPANY_ID, "NPC Target", CompanyType.RAW_MATERIALS, 500_000L);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        stock = new Stock(SYMBOL, "NPC Target", COMPANY_ID, TOTAL, TOTAL, 0L, 10L, 10L);
        StockMarketManager.putStockDirect(stock);
    }

    @AfterEach
    void clean() {
        EconomySavedData.resetRuntimeState();
    }

    private static long held(NpcInvestorArchetype a) {
        return StockPortfolioManager.getHolding(a.accountId(), SYMBOL).getQuantity();
    }

    private long combined() {
        return NpcInvestorService.combinedNpcHoldings(stock, 0);
    }

    private static long totalMoney() {
        long total = 0;
        for (Account account : AccountManager.getAccounts().values()) {
            total += account.getBalance() + account.getFrozenBalance();
        }
        for (Company c : CompanyManager.getCompanies()) total += c.getCash();
        return total;
    }

    private static long burned() {
        long total = 0;
        for (TransactionRecord r : AccountManager.getTransactions()) {
            if (r.getType() == TransactionType.NPC_FLOAT_PLACEMENT) total += r.getAmount();
        }
        return total;
    }

    @Test void fundingComesFromPoolWithoutStarterMoney() {
        NpcInvestorService.processDay(1, SETTINGS);
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
            NpcInvestorState state = NpcInvestorManager.state(a);
            assertTrue(state.initialFunded(), a.name());
            assertEquals(50_000L, state.contributedCapital(), a.name());
        }
        // 资金池只减少注资额；四个账户没有拿到 1000 新手资金：总货币 + 配售销毁 = 初始
        assertEquals(POOL - 4 * 50_000L, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(POOL + 500_000L, totalMoney() + burned());
    }

    @Test void poolBelowReserveFloorSkipsFunding() {
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID).withdraw(POOL - 1_010_000L);
        NpcInvestorService.processDay(1, SETTINGS);
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
            assertFalse(NpcInvestorManager.state(a).initialFunded(), a.name());
            assertEquals(0, held(a));
        }
        assertEquals(1_010_000L, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
    }

    @Test void dailyTradeVolumeIsCapped() {
        NpcInvestorService.processDay(1, SETTINGS);
        // 流通股 2% = 200 股
        assertTrue(combined() > 0);
        assertTrue(combined() <= 200, "第一天合计买入不能超过 200 股，实际 " + combined());
    }

    @Test void holdingsRespectPerArchetypeAndCombinedCaps() {
        for (long day = 1; day <= 60; day++) {
            NpcInvestorService.processDay(day, SETTINGS);
            for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
                long cap = (long) (TOTAL * 0.20 * a.holdingCapFactor());
                assertTrue(held(a) <= cap, a + " 超过单原型上限: " + held(a));
            }
            assertTrue(combined() <= TOTAL * 49 / 100, "合计超过 49%: " + combined());
            assertTrue(ShareholderRegistryService.snapshot(COMPANY_ID, day).consistent(), "股本不守恒 day " + day);
        }
        assertEquals(2_000L, held(NpcInvestorArchetype.VALUE), "价值派应在 10 天内配满 20%");
        assertEquals(POOL + 500_000L, totalMoney() + burned(), "货币只能流向配售水槽");
    }

    @Test void sameDayIsIdempotent() {
        NpcInvestorService.processDay(5, SETTINGS);
        long before = combined();
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        NpcInvestorService.processDay(5, SETTINGS);
        NpcInvestorService.processDay(4, SETTINGS); // 时间回拨
        assertEquals(before, combined());
        assertEquals(pool, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
    }

    @Test void disabledIsCompleteNoOpAndKeepsHoldings() {
        NpcInvestorService.Settings off = new NpcInvestorService.Settings(false, 50_000L, 5_000L, 1_000_000L, 2_000, 4_900, 200);
        NpcInvestorService.processDay(1, off);
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
            assertFalse(AccountManager.getAccounts().containsKey(a.accountId()), "关闭时不应创建账户");
        }
        for (long day = 2; day <= 5; day++) NpcInvestorService.processDay(day, SETTINGS);
        long held = held(NpcInvestorArchetype.VALUE);
        assertTrue(held > 0);
        NpcInvestorService.processDay(6, off);
        assertEquals(held, held(NpcInvestorArchetype.VALUE), "关闭后持仓保留");
    }

    @Test void bankruptcyRiskMakesInvestorsSellThroughOrderBook() {
        for (long day = 1; day <= 30; day++) NpcInvestorService.processDay(day, SETTINGS);
        long before = held(NpcInvestorArchetype.VALUE);
        assertEquals(2_000L, before);
        company.setBankruptcyRisk(true, 30);
        NpcInvestorService.processDay(31, SETTINGS);
        long after = held(NpcInvestorArchetype.VALUE);
        assertTrue(after < before, "破产风险时价值派应减持");
        assertTrue(before - after <= 200, "减持同样受每日 2% 上限约束");
        assertTrue(ShareholderRegistryService.snapshot(COMPANY_ID, 31).consistent());
        // 没成交的卖单第二天会被撤掉，股份回到持仓
        NpcInvestorService.cancelOpenOrders();
        for (StockOrder order : StockMarketManager.getOrders()) {
            assertFalse(NpcInvestorArchetype.isInvestorAccount(order.getPlayerId()));
        }
    }

    @Test void placementRejectsPlayersAndResidualShortfall() {
        AccountManager.deposit(PLAYER, 1_000_000L);
        assertFalse(NpcFloatPlacementService.place(PLAYER, SYMBOL, 10, 0).success(), "玩家不能走残差配售");
        NpcInvestorManager.ensureAccounts();
        UUID value = NpcInvestorArchetype.VALUE.accountId();
        AccountManager.deposit(value, 10_000_000L);
        assertFalse(NpcFloatPlacementService.place(value, SYMBOL, TOTAL + 1, 0).success(), "不能配售超过残差的股份");
        var ok = NpcFloatPlacementService.place(value, SYMBOL, 100, 0);
        assertTrue(ok.success(), ok.message());
        assertEquals(10L, ok.price());
        assertEquals(TOTAL - 100, NpcFloatPlacementService.availableResidual(stock, 0));
    }

    @Test void stateSurvivesSaveLoadAndLegacySavesLoadClean() {
        for (long day = 1; day <= 3; day++) NpcInvestorService.processDay(day, SETTINGS);
        long held = held(NpcInvestorArchetype.VALUE);
        int decisions = NpcInvestorManager.decisionsDirect().size();
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);
        assertEquals(3, NpcInvestorManager.lastProcessedDay());
        assertTrue(NpcInvestorManager.state(NpcInvestorArchetype.VALUE).initialFunded());
        assertEquals(decisions, NpcInvestorManager.decisionsDirect().size());
        assertEquals(held, StockPortfolioManager.getHolding(NpcInvestorArchetype.VALUE.accountId(), SYMBOL).getQuantity());
        assertTrue(NpcInvestorManager.state(NpcInvestorArchetype.VALUE).targetBpsFor(COMPANY_ID) > 0);

        // 旧存档（DataVersion 37）没有 NpcEconomy 标签：按"刚启用"初始化，不报错
        saved.remove(finance.data.serializer.NpcEconomyDataSerializer.ROOT);
        EconomySavedData.load(saved);
        assertEquals(Long.MIN_VALUE, NpcInvestorManager.lastProcessedDay());
        assertFalse(NpcInvestorManager.state(NpcInvestorArchetype.VALUE).initialFunded());
        assertTrue(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.NPC_ECONOMY));
    }

    @Test void corruptRecordsAreIsolated() {
        NpcInvestorService.processDay(1, SETTINGS);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        CompoundTag npc = saved.getCompound(finance.data.serializer.NpcEconomyDataSerializer.ROOT);
        CompoundTag bad = new CompoundTag();
        bad.putString("Archetype", "NOT_A_TYPE");
        npc.getList("Investors", 10).add(bad);
        EconomySavedData.load(saved);
        assertEquals(1, NpcInvestorManager.lastProcessedDay(), "坏记录不影响其他数据");
        assertFalse(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.NPC_ECONOMY),
                "有坏记录时模块降为只读");
    }
}
