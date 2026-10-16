package finance.macro;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 第三步：利益集团、法律、福利、游说。 */
class PoliticsServiceTest {
    private UUID player;
    private VillageRecord village;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 10_000_000L);
        player = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(player);
        AccountManager.deposit(player, 10_000L);
        SettlementRecord s = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        village = MacroEconomyServiceTest.makeVillage(s, 20, 5, 3, 2);
        MacroEconomyManager.getOrCreate(s.id());
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @Test void defaultsAreTheMiddleGround() {
        assertEquals(LawLevel.MERCANTILISM, MacroEconomyManager.law(LawGroup.TRADE));
        assertEquals(LawLevel.NO_LABOR_PROTECTION, MacroEconomyManager.law(LawGroup.LABOR));
        assertEquals(LawLevel.NO_WELFARE, MacroEconomyManager.law(LawGroup.WELFARE));
        assertEquals(4L, MacroLaborService.reservationWage());
    }

    @Test void popularWelfareLawPassesAfterDebate() {
        PoliticsService.recompute();
        assertTrue(MacroEconomyManager.cloutBps(InterestGroup.RURAL_FOLK) > 5_000, "这个世界几乎只有农民");
        long fee = 2_000;
        var r = PoliticsService.propose(player, LawLevel.PUBLIC_HEALTH, 10);
        assertTrue(r.success(), r.message());
        assertEquals(10_000L - fee, AccountManager.getBalance(player));
        assertFalse(PoliticsService.propose(player, LawLevel.POOR_RELIEF, 10).success(), "同一时间只有一个提案");
        PoliticsService.processDay(14);
        assertNotNull(MacroEconomyManager.proposal(), "辩论期未满");
        PoliticsService.processDay(15);
        assertNull(MacroEconomyManager.proposal());
        assertEquals(LawLevel.PUBLIC_HEALTH, MacroEconomyManager.law(LawGroup.WELFARE));
        assertTrue(MacroEconomyManager.lawHistory().get(0).passed());
    }

    @Test void unpopularLawFails() {
        // 资本集团几乎没有影响力时，取消劳工保护对工业家有利也通不过
        MacroEconomyManager.setLaw(LawLevel.WORKER_PROTECTION);
        MacroEconomyManager.getOrCreate(village.settlementId()).employed = 15;
        var r = PoliticsService.propose(player, LawLevel.NO_LABOR_PROTECTION, 1);
        assertTrue(r.success());
        PoliticsService.processDay(6);
        assertEquals(LawLevel.WORKER_PROTECTION, MacroEconomyManager.law(LawGroup.LABOR));
        assertFalse(MacroEconomyManager.lawHistory().get(0).passed());
    }

    @Test void welfarePaysFromNpcPoolIntoVillageBudget() {
        MacroEconomyManager.setLaw(LawLevel.POOR_RELIEF);
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        PoliticsService.payWelfare(1);
        long expected = 5L * village.population();
        assertEquals(expected, AccountManager.getBalance(village.budgetAccountId()));
        assertEquals(pool - expected, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(expected, MacroEconomyManager.settlement(village.settlementId()).incomeToday);
    }

    @Test void minimumWageLawRaisesReservationWage() {
        MacroEconomyManager.setLaw(LawLevel.MINIMUM_WAGE);
        assertEquals(10L, MacroLaborService.reservationWage());
    }

    @Test void tradePolicyChangesCaravanPrices() {
        assertEquals(100, MacroEconomyService.applyTradePolicy(100));
        MacroEconomyManager.setLaw(LawLevel.PROTECTIONISM);
        assertEquals(125, MacroEconomyService.applyTradePolicy(100));
        MacroEconomyManager.setLaw(LawLevel.FREE_TRADE);
        assertEquals(90, MacroEconomyService.applyTradePolicy(100));
    }

    @Test void lobbyingCostsMoneyCapsAndDecays() {
        var r = PoliticsService.lobby(player, InterestGroup.INDUSTRIALISTS, 3_500);
        assertTrue(r.success(), r.message());
        assertEquals(3, MacroEconomyManager.lobby(InterestGroup.INDUSTRIALISTS));
        assertEquals(10_000L - 3_000, AccountManager.getBalance(player), "只收整点的钱");
        PoliticsService.lobby(player, InterestGroup.INDUSTRIALISTS, 50_000);
        assertEquals(PoliticsService.MAX_LOBBY, MacroEconomyManager.lobby(InterestGroup.INDUSTRIALISTS));
        assertFalse(PoliticsService.lobby(player, InterestGroup.INDUSTRIALISTS, 1_000).success(), "达到上限");
        PoliticsService.processDay(1);
        assertEquals(PoliticsService.MAX_LOBBY - 1, MacroEconomyManager.lobby(InterestGroup.INDUSTRIALISTS));
    }

    @Test void miserableWorkersStrike() {
        MacroEconomyManager.getOrCreate(village.settlementId()).employed = 10;
        MacroEconomyManager.getOrCreate(village.settlementId()).solBps = 0;
        // 无劳工保护（工会 −2）+ 无福利（−1）+ 处境 0（无就业设施记录）→ −3，再压低游说
        MacroEconomyManager.setLobby(InterestGroup.TRADE_UNIONS, -3);
        PoliticsService.processDay(1);
        assertTrue(MacroEconomyManager.approval(InterestGroup.TRADE_UNIONS) <= PoliticsService.UNREST_THRESHOLD);
        assertTrue(MacroEconomyManager.strike());
        assertTrue(MacroEconomyManager.unrest(), "乡村民众生活水平为 0，满意度也跌破阈值");
    }

    @Test void lawsAndProposalSurviveSaveLoad() {
        MacroEconomyManager.setLaw(LawLevel.PROTECTIONISM);
        assertTrue(PoliticsService.propose(player, LawLevel.MINIMUM_WAGE, 3).success());
        PoliticsService.lobby(player, InterestGroup.FINANCIERS, 2_000);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(LawLevel.PROTECTIONISM, MacroEconomyManager.law(LawGroup.TRADE));
        assertNotNull(MacroEconomyManager.proposal());
        assertEquals(LawLevel.MINIMUM_WAGE, MacroEconomyManager.proposal().level());
        assertEquals(2, MacroEconomyManager.lobby(InterestGroup.FINANCIERS));
    }
}
