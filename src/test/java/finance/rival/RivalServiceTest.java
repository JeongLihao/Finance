package finance.rival;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.Account;
import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.*;
import finance.contract.ContractManager;
import finance.data.EconomySavedData;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RivalServiceTest {
    private static final RivalService.Settings ON =
            new RivalService.Settings(true, 6, 14, 50_000L, 1_000_000L, true, true);

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        registerCommodities();
        NpcMarketMaker.resetSeedState();
        NpcMarketMaker.seedNpcIfNeeded();
        SystemCompanyInitializer.initialize();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
    }

    /** 单测环境不跑模组构造，按 FinanceMod 的默认商品注册。 */
    private static void registerCommodities() {
        CommodityRegistry.register(
                new Commodity("iron", "minecraft:iron_ingot", "Iron", CommodityCategory.RAW_MATERIALS, 10));
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        CommodityRegistry.register(
                new Commodity("stone", "minecraft:stone", "Stone", CommodityCategory.BUILDING_BLOCKS, 3));
        CommodityRegistry.register(new Commodity("bread", "minecraft:bread", "Bread", CommodityCategory.FOOD, 20));
        CommodityRegistry.register(
                new Commodity("wood", "minecraft:oak_log", "Wood", CommodityCategory.BUILDING_BLOCKS, 4));
        CommodityRegistry.register(new Commodity("coal", "minecraft:coal", "Coal", CommodityCategory.RAW_MATERIALS, 6));
        CommodityRegistry.register(
                new Commodity("wool", "minecraft:white_wool", "Wool", CommodityCategory.MISCELLANEOUS, 9));
    }

    /** 把某商品的 NPC 库存压低，让它的卖价明显高于基准价。 */
    private static void makeShort(String commodity) {
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, commodity, 500);
        NpcMarketMaker.getMarketPrice(commodity).recomputePrice(500);
    }

    private static long totalMoney() {
        long total = 0;
        for (Account a : AccountManager.getAccounts().values()) total += a.getBalance() + a.getFrozenBalance();
        for (Company c : CompanyManager.getCompanies()) total += c.getCash();
        return total;
    }

    @Test
    void systemCompaniesGetDeterministicProfiles() {
        RivalService.processDay(1, ON);
        assertEquals(3, RivalManager.size());
        for (RivalProfile p : RivalManager.all()) {
            assertEquals(RivalPersonality.forCompany(p.companyId()), p.personality());
            assertFalse(p.mainCommodity().isBlank());
        }
    }

    @Test
    void shortageSpawnsARivalFundedFromThePoolWithCooldown() {
        makeShort("coal");
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        long money = totalMoney();
        RivalService.processDay(1, ON);
        Company rival = CompanyManager.getCompanies().stream()
                .filter(c -> c.getType() == CompanyType.COAL_MINING)
                .findFirst()
                .orElse(null);
        assertNotNull(rival, "煤紧缺时应出现煤矿竞争者");
        assertNull(rival.getOwnerId());
        assertTrue(rival.getCash() <= 50_000L, "种子资金 5 万（当天可能已拿一部分去发原料合同）");
        assertTrue(
                AccountManager.getTransactions().stream()
                        .anyMatch(r -> r.getType() == finance.account.TransactionType.RIVAL_SEED
                                && r.getAmount() == 50_000L
                                && rival.getCompanyId().equals(r.getTo())),
                "种子资金从 NPC 资金池转出并留痕");
        assertEquals(money, totalMoney(), "种子资金只是从资金池转出，不凭空产生");
        assertEquals(
                "SPAWN", RivalManager.get(rival.getCompanyId()).history().get(0).action());
        // 冷却期内不再新建
        makeShort("wool");
        RivalService.processDay(2, ON);
        assertEquals(4, RivalService.npcCompanyCount());
        RivalService.processDay(16, ON);
        assertEquals(5, RivalService.npcCompanyCount(), "冷却 14 天后可以再建");
    }

    @Test
    void capAndPoolFloorBlockSpawning() {
        makeShort("coal");
        RivalService.processDay(1, new RivalService.Settings(true, 3, 14, 50_000L, 1_000_000L, true, true));
        assertEquals(3, RivalService.npcCompanyCount(), "已达上限（3 家系统公司）");
        AccountManager.withdraw(
                NpcMarketMaker.NPC_UUID, AccountManager.getBalance(NpcMarketMaker.NPC_UUID) - 1_020_000L);
        RivalService.processDay(2, ON);
        assertEquals(3, RivalService.npcCompanyCount(), "资金池扣除后会低于保留线，不建");
    }

    @Test
    void personalityStrategyTable() {
        assertEquals(CompanyStrategy.EXPAND, RivalService.chooseStrategy(RivalPersonality.AGGRESSIVE, 10, 10_000));
        assertEquals(CompanyStrategy.STABLE, RivalService.chooseStrategy(RivalPersonality.AGGRESSIVE, -10, 10_000));
        assertEquals(
                CompanyStrategy.COST_CONTROL, RivalService.chooseStrategy(RivalPersonality.CONSERVATIVE, -10, 10_000));
        assertEquals(CompanyStrategy.FAST_CASH, RivalService.chooseStrategy(RivalPersonality.OPPORTUNIST, 0, 12_000));
        assertEquals(
                CompanyStrategy.HOLD_INVENTORY, RivalService.chooseStrategy(RivalPersonality.OPPORTUNIST, 0, 8_000));
        assertEquals(CompanyStrategy.STABLE, RivalService.chooseStrategy(RivalPersonality.OPPORTUNIST, 0, 10_000));
    }

    @Test
    void richRivalUpgradesPayingThePool() {
        RivalService.processDay(1, ON);
        Company c = CompanyManager.getCompanies().iterator().next();
        assertEquals(1, c.getProductionLevel(), "系统公司现金 5 万，第一天就升了第一级");
        long cost = c.getUpgradeCost("PRODUCTION");
        // 系统公司的 ID 随机，人格（升级要求的现金倍数）也随之随机；补足现金，让第二次升级不取决于抽到哪种人格
        c.deposit(cost * 10);
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        long cash = c.getCash();
        long money = totalMoney();
        long allCash = CompanyManager.getCompanies().stream()
                .mapToLong(Company::getCash)
                .sum();
        RivalService.processDay(10, ON);
        long spent = allCash
                - CompanyManager.getCompanies().stream()
                        .mapToLong(Company::getCash)
                        .sum();
        assertEquals(2, c.getProductionLevel(), "隔 7 天以上可以再升");
        assertEquals(cash - cost, c.getCash());
        assertEquals(pool + spent, AccountManager.getBalance(NpcMarketMaker.NPC_UUID), "各公司的升级费全部进了 NPC 资金池");
        assertEquals(money, totalMoney());
        RivalService.processDay(11, ON);
        assertEquals(2, c.getProductionLevel(), "两次升级至少隔 7 天");
    }

    @Test
    void processingRivalPublishesProcurementContract() {
        Company bakery = new Company(UUID.randomUUID(), "晨光食品", CompanyType.FOOD_PROCESSING, 200_000);
        CompanyManager.registerDirect(bakery);
        RivalService.processDay(1, ON);
        var contract = ContractManager.contracts().values().stream()
                .filter(k -> bakery.getCompanyId().equals(k.issuerId()))
                .findFirst()
                .orElse(null);
        assertNotNull(contract, "缺小麦时应发采购合同");
        assertEquals("wheat", contract.commodityId());
        assertEquals(40 * 5, contract.requiredQuantity());
        assertTrue(RivalManager.get(bakery.getCompanyId()).history().stream()
                .anyMatch(d -> d.action().equals("CONTRACT")));
    }

    @Test
    void matureProfitableRivalListsUnderPlayerIpoCaps() {
        Company works = new Company(UUID.randomUUID(), "北山铁业", CompanyType.RAW_MATERIALS, 200_000);
        CompanyManager.registerDirect(works);
        RivalService.processDay(1, ON);
        works.recordGameplayRevenue(5_000);
        long cashBefore = works.getCash();
        RivalService.processDay(40, ON);
        assertTrue(works.isPublic(), "成立满 30 天、现金充足且盈利时上市");
        Stock stock = StockMarketManager.getStockByCompanyId(works.getCompanyId());
        long raise = stock.getFloatShares() * stock.getFairValue();
        assertTrue(raise <= CompanyIPOService.MAX_TOTAL_RAISE);
        UUID founder = RivalProfile.investmentAccount(works.getCompanyId());
        assertEquals(
                stock.getTotalShares() - stock.getFloatShares(),
                StockPortfolioManager.getHolding(founder, stock.getSymbol()).getQuantity());
        assertTrue(stock.isFounderLocked(founder));
        assertEquals(0, AccountManager.getBalance(founder), "投资账户不领新手资金");
        assertTrue(works.getCash() < cashBefore + raise, "上市费从公司现金扣");
    }

    @Test
    void ownerlessIpoRejectsPlayerCompanies() {
        Company mine = new Company(UUID.randomUUID(), "Mine", CompanyType.FOOD, 200_000, UUID.randomUUID());
        CompanyManager.registerDirect(mine);
        assertFalse(CompanyIPOService.ipoOwnerless(mine.getCompanyId(), UUID.randomUUID(), 10, 100)
                .success());
        Company npc = new Company(UUID.randomUUID(), "NPC", CompanyType.FOOD, 200_000);
        CompanyManager.registerDirect(npc);
        assertFalse(
                CompanyIPOService.ipoOwnerless(npc.getCompanyId(), UUID.randomUUID(), 101, 10)
                        .success(),
                "发行价上限");
        assertFalse(
                CompanyIPOService.ipoOwnerless(npc.getCompanyId(), UUID.randomUUID(), 100, 101)
                        .success(),
                "募资上限");
    }

    @Test
    void disabledIsNoOpAndSameDayIsIdempotent() {
        makeShort("coal");
        RivalService.processDay(1, new RivalService.Settings(false, 6, 14, 50_000L, 1_000_000L, true, true));
        assertEquals(0, RivalManager.size());
        assertEquals(3, RivalService.npcCompanyCount());
        RivalService.processDay(2, ON);
        int count = RivalService.npcCompanyCount();
        RivalService.processDay(2, ON);
        RivalService.processDay(1, ON);
        assertEquals(count, RivalService.npcCompanyCount());
    }

    @Test
    void profilesSurviveSaveLoadAndLegacySaves() {
        makeShort("coal");
        RivalService.processDay(1, ON);
        int profiles = RivalManager.size();
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(profiles, RivalManager.size());
        assertEquals(1, RivalManager.lastSpawnDay());
        assertEquals(1, RivalManager.lastProcessedDay());
        saved.remove(finance.data.serializer.RivalDataSerializer.ROOT);
        EconomySavedData.load(saved);
        assertEquals(0, RivalManager.size(), "旧存档没有档案，第一次日结时再懒创建");
        RivalService.processDay(2, ON);
        assertTrue(RivalManager.size() >= 3);
    }

    @Test
    void fullYearStaysBoundedAndSolvent() {
        RivalService.Settings s = new RivalService.Settings(true, 6, 14, 50_000L, 1_000_000L, true, true);
        for (long day = 1; day <= 365; day++) {
            if (day % 20 == 0) makeShort(day % 40 == 0 ? "coal" : "wool");
            CompanyManager.tickAll(-1);
            CompanyManager.settleDailyProfits(day);
            CompanyBankruptcyManager.tick(null, day);
            RivalService.processDay(day, s);
            assertTrue(RivalService.npcCompanyCount() <= 6, "NPC 公司数超过上限");
            for (Company c : CompanyManager.getCompanies()) assertTrue(c.getCash() >= 0, c.getName() + " 现金为负");
        }
        for (String id : new String[] {"iron", "stone", "wheat", "coal", "wool", "bread", "wood"}) {
            var mp = NpcMarketMaker.getMarketPrice(id);
            if (mp != null) assertTrue(mp.getAskPrice() > 0, id + " 价格异常");
        }
        assertTrue(RivalManager.size() <= RivalManager.MAX_PROFILES);
    }
}
