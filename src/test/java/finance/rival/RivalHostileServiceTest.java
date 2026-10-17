package finance.rival;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.*;
import finance.data.EconomySavedData;
import finance.governance.TakeoverService;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RivalHostileServiceTest {
    /** 只建档、不新建公司、不升级（现金倍数够也无妨），专测敌对动作。 */
    private static final RivalService.Settings PROFILES_ONLY =
            new RivalService.Settings(true, 0, 14, 0L, 1_000_000L, false, false);
    private static final RivalHostileService.Settings ON = new RivalHostileService.Settings(true, true);
    private static final RivalHostileService.Settings OFF = new RivalHostileService.Settings(false, false);

    private Company rival;
    private Company target;
    private Stock stock;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron", CommodityCategory.RAW_MATERIALS, 10));
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        NpcMarketMaker.resetSeedState();
        NpcMarketMaker.seedNpcIfNeeded();
        rival = new Company(idWith(RivalPersonality.AGGRESSIVE), "红炉钢铁", CompanyType.RAW_MATERIALS, 1_000_000);
        CompanyManager.registerDirect(rival);
        // 目标：现金 50 万、1 万股全部流通、股价 5 → 每股基本面 50，严重低估
        target = new Company(UUID.randomUUID(), "麦田农庄", CompanyType.FOOD, 500_000);
        target.setPublic(true);
        CompanyManager.registerDirect(target);
        stock = new Stock("MTNZ", "麦田农庄", target.getCompanyId(), 10_000, 10_000, 0, 5, 5);
        StockMarketManager.putStockDirect(stock);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
    }

    private static UUID idWith(RivalPersonality personality) {
        for (long i = 0; ; i++) {
            UUID id = UUID.nameUUIDFromBytes(("rival-test-" + personality + i).getBytes());
            if (RivalPersonality.forCompany(id) == personality) return id;
        }
    }

    private void runDays(long from, long to, RivalHostileService.Settings settings) {
        for (long day = from; day <= to; day++) {
            RivalService.processDay(day, PROFILES_ONLY);
            RivalHostileService.processDay(day, settings);
        }
    }

    @Test void aggressiveRivalBuildsStakeThenLaunchesTender() {
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        runDays(1, 20, ON);
        var offer = TakeoverService.openTenders().stream().filter(o -> o.buyerId().equals(account)).findFirst().orElse(null);
        assertNotNull(offer, "建仓到 30% 后应发起要约");
        assertEquals(target.getCompanyId(), offer.targetCompanyId());
        assertEquals(7, offer.price(), "公允价 5 × 1.3 向上取整，且不超过 1.5 倍上限");
        assertTrue(TakeoverService.votingPercent(account, target.getCompanyId(), 20) >= 30.0);
        assertEquals(0, AccountManager.getAccounts().get(account).getBalance() < 0 ? 1 : 0);
        assertTrue(AccountManager.getTransactions().stream().anyMatch(r -> r.getType() == TransactionType.RIVAL_INVESTMENT),
                "建仓和托管资金从竞争公司现金转入投资账户并留痕");
        assertTrue(RivalManager.get(rival.getCompanyId()).history().stream().anyMatch(d -> d.action().equals("TENDER")));
        assertTrue(rival.getCash() >= RivalHostileService.RESERVE_CASH);
    }

    @Test void disabledIsCompleteNoOp() {
        runDays(1, 20, OFF);
        assertTrue(TakeoverService.openTenders().isEmpty());
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        assertEquals(0, StockPortfolioManager.getHolding(account, "MTNZ").getQuantity());
        assertTrue(AccountManager.getTransactions().stream().noneMatch(r -> r.getType() == TransactionType.RIVAL_INVESTMENT),
                "关闭时不往投资账户转钱（现金变化只来自 P3 的升级）");
    }

    @Test void conservativeRivalsDoNotGoHostile() {
        CompanyManager.removeCompany(rival.getCompanyId());
        Company calm = new Company(idWith(RivalPersonality.CONSERVATIVE), "青岩石料", CompanyType.BUILDING_BLOCKS, 1_000_000);
        CompanyManager.registerDirect(calm);
        runDays(1, 20, new RivalHostileService.Settings(true, false));
        assertTrue(TakeoverService.openTenders().stream().noneMatch(o ->
                o.buyerId().equals(RivalProfile.investmentAccount(calm.getCompanyId()))));
    }

    @Test void activistRivalProposesHigherDividendAtUnderperformingPlayerCompany() {
        Company player = new Company(UUID.randomUUID(), "玩家公司", CompanyType.FOOD, 1_000, UUID.randomUUID());
        player.setPublic(true);
        CompanyManager.registerDirect(player);
        StockMarketManager.putStockDirect(new Stock("WJGS", "玩家公司", player.getCompanyId(), 10_000, 4_000, 6_000, 5, 5));
        StockPortfolioManager.addHolding(player.getOwnerId(), "WJGS", 6_000, 5);
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        RivalService.processDay(1, PROFILES_ONLY);
        AccountManager.getOrCreateSystemAccount(account);
        assertTrue(StockPortfolioManager.addHolding(account, "WJGS", 2_600, 5)); // 26% ≥ 提案资格 25%
        RivalHostileService.processDay(1, new RivalHostileService.Settings(false, true));
        var proposal = CompanyProposalManager.getProposalsForCompany(player.getCompanyId()).stream()
                .filter(p -> account.equals(p.getCreatorId())).findFirst().orElse(null);
        assertNotNull(proposal, "长期不分红时激进股东应发起提案");
        assertEquals(CompanyProposalType.DIVIDEND, proposal.getType());
        assertTrue(proposal.getValue1() >= 30);
        var vote = proposal.getVotes().get(account);
        assertNotNull(vote, "激进股东应给自己的提案投票");
        assertTrue(vote.support());
        // 30 天内不重复提
        RivalHostileService.processDay(2, new RivalHostileService.Settings(false, true));
        assertEquals(1, CompanyProposalManager.getProposalsForCompany(player.getCompanyId()).size());
    }

    @Test void activismIgnoresSharesLockedInSellOrders() {
        Company player = new Company(UUID.randomUUID(), "玩家公司", CompanyType.FOOD, 1_000, UUID.randomUUID());
        player.setPublic(true);
        CompanyManager.registerDirect(player);
        StockMarketManager.putStockDirect(new Stock("WJGS", "玩家公司", player.getCompanyId(), 10_000, 4_000, 6_000, 5, 5));
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        RivalService.processDay(1, PROFILES_ONLY);
        AccountManager.getOrCreateSystemAccount(account);
        assertTrue(StockPortfolioManager.addHolding(account, "WJGS", 2_400, 5)); // 可用 24%，不够资格
        var holder = finance.governance.ShareholderRegistryService.holder(player.getCompanyId(), account, 1);
        assertNotNull(holder);
        assertEquals(2_400, holder.available());
        RivalHostileService.processDay(1, new RivalHostileService.Settings(false, true));
        assertTrue(CompanyProposalManager.getProposalsForCompany(player.getCompanyId()).isEmpty());
    }

    @Test void staleRivalOrdersAreCancelledEachDay() {
        RivalService.processDay(1, PROFILES_ONLY);
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        AccountManager.getOrCreateSystemAccount(account).deposit(10_000);
        // 一笔挂在 1 元的买单：没有卖家，会一直挂着
        assertTrue(StockMarketManager.placeLimitBuy(account, "MTNZ", 1, 100).success());
        assertTrue(StockMarketManager.getOrders().stream().anyMatch(o -> account.equals(o.getPlayerId())));
        RivalHostileService.processDay(2, new RivalHostileService.Settings(false, true));
        assertTrue(StockMarketManager.getOrders().stream().noneMatch(o -> account.equals(o.getPlayerId())),
                "竞争公司投资账户的旧挂单应在每日开始时撤掉");
        assertEquals(10_000, AccountManager.getBalance(account), "冻结资金退回投资账户");
    }

    @Test void rivalInvestmentCashIsNpcMoneyInMetrics() {
        RivalService.processDay(1, PROFILES_ONLY);
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        long before = finance.metrics.EconomyMetricsService.getCurrentMetrics().playerCash();
        AccountManager.getOrCreateSystemAccount(account).deposit(200_000);
        assertEquals(before, finance.metrics.EconomyMetricsService.getCurrentMetrics().playerCash());
    }

    @Test void hostileStateSurvivesSaveLoad() {
        runDays(1, 5, ON);
        RivalProfile before = RivalManager.get(rival.getCompanyId());
        assertEquals(target.getCompanyId(), before.hostileTarget(), "建仓进行中");
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        RivalProfile after = RivalManager.get(rival.getCompanyId());
        assertEquals(before.hostileTarget(), after.hostileTarget());
        assertEquals(before.hostileStartDay(), after.hostileStartDay());
        assertEquals(5, RivalManager.lastHostileDay());
    }

    @Test void playersCannotUseResidualPlacementButRivalAccountsCan() {
        RivalService.processDay(1, PROFILES_ONLY);
        UUID player = UUID.randomUUID();
        AccountManager.deposit(player, 100_000);
        assertFalse(finance.npcinvestor.NpcFloatPlacementService.place(player, "MTNZ", 10, 1).success());
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        AccountManager.getOrCreateSystemAccount(account).deposit(10_000);
        assertTrue(finance.npcinvestor.NpcFloatPlacementService.place(account, "MTNZ", 10, 1).success());
    }
}
