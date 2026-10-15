package finance.village;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.data.serializer.VillageDataSerializer;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 村民需求经济测试：需求模型、价格浮动、好感度、反刷钱（含攻击视角）。
 *
 * <p>风格沿用 SettlementManagerTest：真实静态 manager + resetRuntimeState 隔离，无 Mockito。
 */
class VillageServiceTest {

    private UUID player;
    private SettlementRecord settlement;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron", CommodityCategory.RAW_MATERIALS, 20));
        CommodityRegistry.register(new Commodity("stone", "minecraft:stone", "Stone", CommodityCategory.BUILDING_BLOCKS, 4));
        ModuleHealthRegistry.clear();
        player = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(player);
        // NPC 国际市场注资＋备货（行商进货来源）
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 10_000_000L);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wheat", 100_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "iron", 100_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "stone", 100_000);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    // ================================================================
    // 辅助
    // ================================================================

    /** 构造指定人口/职业构成的村庄并注入管理器（绕过默认分配，保证确定性）。 */
    private VillageRecord makeVillage(int population, int farmer, int artisan, int guard, int other) {
        settlement = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld",
                new BlockPos(0, 64, 0), "test-village");
        EnumMap<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
        mix.put(VillageProfession.FARMER, farmer);
        mix.put(VillageProfession.ARTISAN, artisan);
        mix.put(VillageProfession.GUARD, guard);
        mix.put(VillageProfession.OTHER, other);
        UUID budget = AccountManager.getOrCreateSystemAccount(UUID.randomUUID()).getPlayerId();
        VillageRecord village = new VillageRecord(settlement.id(), population, mix, budget, 0L, 0);
        assertTrue(VillageManager.restoreVillage(village));
        return village;
    }

    private long playerBalance() {
        return AccountManager.getBalance(player);
    }

    // ================================================================
    // 需求模型
    // ================================================================

    @Test
    void computeNeeds_matchesPopulationTimesBasket() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        Map<String, Long> needs = VillageService.computeNeeds(v);
        // wheat: 10*2.0 + 10*1.0 = 30；stone: 10*0.2 + 10*1.0 = 12；iron: 10*0.1 + 10*0.3 = 4
        assertEquals(30L, needs.get("wheat"));
        assertEquals(12L, needs.get("stone"));
        assertEquals(4L, needs.get("iron"));
    }

    @Test
    void computeNeeds_boundedByPopulation() {
        // 攻击视角：需求总量不可能超过人口×人均上限（人口来自抽象模型，不可刷）
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        Map<String, Long> needs = VillageService.computeNeeds(v);
        long total = needs.values().stream().mapToLong(Long::longValue).sum();
        assertTrue(total <= 20L * 4L, "total=" + total); // 人均各项之和 < 4
    }

    // ================================================================
    // 价格浮动
    // ================================================================

    @Test
    void premiumForCoverage_shortageMaxSurplusMin() {
        assertEquals(14000, VillageService.premiumForCoverage(0.0));   // 缺货 → 上限
        assertEquals(7500, VillageService.premiumForCoverage(3.0));    // 囤积 → 下限
        assertEquals(11833, VillageService.premiumForCoverage(1.0));   // 线性插值
    }

    @Test
    void computeWtp_clampedToBaseBand() {
        assertEquals(100L, VillageService.computeWtp(100, 100, 10_000));
        // 攻击视角：mid 再大也被钳制在 base×2.6
        assertEquals(20L, VillageService.computeWtp(100_000, 8, 10_000));
        assertEquals(0L, VillageService.computeWtp(0, 8, 10_000));
    }

    // ================================================================
    // 好感度
    // ================================================================

    @Test
    void favor_clampedAndDefaults() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        assertEquals(0, v.favorOf(player));
        assertEquals(100, v.addFavor(player, 500));
        assertEquals(-100, v.addFavor(player, -500));
    }

    @Test
    void marketDay_idempotent() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        assertTrue(v.markMarketDay(5));
        assertFalse(v.markMarketDay(5));
        assertTrue(v.markMarketDay(6));
    }

    // ================================================================
    // 集市结算：公允交易
    // ================================================================

    @Test
    void processDay_fairSalePaysPlayerAndConsumes() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        // wheat 需求 30；WTP = 8*1.4=11，公允线 13；定价 10 为公允
        VillageStall stall = VillageManager.registerStall(settlement.id(), player, "wheat", 10);
        assertNotNull(stall);
        stall.addStock(100);

        long before = playerBalance();
        VillageService.processDay(null, 1);

        assertEquals(before + 300, playerBalance());          // 30 × 10
        assertEquals(70, stall.stock());                       // 100 - 30
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(settlement.id(), "wheat")); // 买走即消费销毁
        assertEquals(1, v.favorOf(player));                    // 公允交易 +2，再自然衰减 -1
    }

    // ================================================================
    // 攻击视角
    // ================================================================

    @Test
    void attack_gougingRefusedAndFavorDown() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        // 定价 100 >> 宰客线 16：村民拒买，转投行商；摊主好感 -10
        VillageStall stall = VillageManager.registerStall(settlement.id(), player, "wheat", 100);
        stall.addStock(100);

        long before = playerBalance();
        VillageService.processDay(null, 1);

        assertEquals(before, playerBalance());                 // 一分没赚到
        assertEquals(100, stall.stock());                     // 一根没卖出
        assertEquals(-9, v.favorOf(player));                  // 宰客 -10，再自然衰减 +1
        // 村民转投行商：行商有成交（NPC 收到钱）
        assertTrue(AccountManager.getBalance(NpcMarketMaker.NPC_UUID) > 10_000_000L - 6000L);
    }

    @Test
    void attack_boycottSkipsStall() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        v.addFavor(player, -60); // 跌破 -50 抵制线
        VillageStall stall = VillageManager.registerStall(settlement.id(), player, "wheat", 10);
        stall.addStock(100);

        long before = playerBalance();
        VillageService.processDay(null, 1);

        assertEquals(before, playerBalance());                 // 抵制：公允价也卖不出去
        assertEquals(100, stall.stock());
        assertEquals(-59, v.favorOf(player));                  // 自然衰减 +1（向 0 回归）
    }

    @Test
    void attack_noBudgetNoSaleNoMint() {
        // 攻击视角：村庄预算为 0 且 NPC 没钱 → 买不起 → 不成交、不印钱
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        // 注意 setup 已注资，这里把 NPC 资金抽干
        long npcBal = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        assertTrue(AccountManager.withdraw(NpcMarketMaker.NPC_UUID, npcBal));
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        VillageStall stall = VillageManager.registerStall(settlement.id(), player, "wheat", 10);
        stall.addStock(100);

        long before = playerBalance();
        VillageService.processDay(null, 1);

        assertEquals(before, playerBalance());
        assertEquals(100, stall.stock());                      // 没卖出
        assertEquals(0, v.favorOf(player));                    // 没交易就没好感
    }

    @Test
    void attack_emptyStallSellsNothing() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        VillageStall stall = VillageManager.registerStall(settlement.id(), player, "wheat", 10);
        assertEquals(0, stall.stock());                       // 没上架

        long before = playerBalance();
        VillageService.processDay(null, 1);

        assertEquals(before, playerBalance());                 // 空库存零成交
        assertEquals(0, v.favorOf(player));
    }

    @Test
    void processDay_idempotentAcrossCalls() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        VillageStall stall = VillageManager.registerStall(settlement.id(), player, "wheat", 10);
        stall.addStock(100);

        VillageService.processDay(null, 1);
        long afterFirst = playerBalance();
        int stockAfterFirst = stall.stock();

        VillageService.processDay(null, 1); // 同一天重复调用

        assertEquals(afterFirst, playerBalance());
        assertEquals(stockAfterFirst, stall.stock());
    }

    // ================================================================
    // 摊位校验
    // ================================================================

    @Test
    void stall_duplicateRejectedAndPriceValidated() {
        makeVillage(20, 10, 10, 0, 0);
        assertNotNull(VillageManager.registerStall(settlement.id(), player, "wheat", 10));
        assertNull(VillageManager.registerStall(settlement.id(), player, "wheat", 12)); // 重复
        assertThrows(IllegalArgumentException.class,
                () -> new VillageStall(UUID.randomUUID(), settlement.id(), player, "wheat", 0)); // 单价≥1
    }

    // ================================================================
    // 持久化往返
    // ================================================================

    @Test
    void serializer_roundTrip() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        v.addFavor(player, 42);
        v.markMarketDay(7);
        VillageStall stall = VillageManager.registerStall(settlement.id(), player, "wheat", 10);
        stall.addStock(55);

        CompoundTag root = new CompoundTag();
        VillageDataSerializer.save(root);
        VillageManager.clearDirect();

        VillageDataSerializer.load(root);
        VillageRecord restored = VillageManager.get(settlement.id());
        assertNotNull(restored);
        assertEquals(20, restored.population());
        assertEquals(42, restored.favorOf(player));
        assertEquals(7, restored.lastMarketDay());
        VillageStall rs = VillageManager.stall(stall.id());
        assertNotNull(rs);
        assertEquals(55, rs.stock());
        assertEquals(10, rs.unitPrice());
    }

    // ================================================================
    // P2 修复回归：聚落索引 / 好感回收 / 存档钳制
    // ================================================================

    @Test
    void stallsForSettlement_usesIndexAndTracksRemoval() {
        makeVillage(20, 10, 10, 0, 0);
        UUID other = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld",
                new BlockPos(1000, 64, 1000), "other").id();
        UUID p2 = UUID.randomUUID();
        VillageStall a = VillageManager.registerStall(settlement.id(), player, "wheat", 10);
        VillageStall b = VillageManager.registerStall(other, p2, "wheat", 10);
        assertEquals(1, VillageManager.stallsForSettlement(settlement.id()).size());
        assertEquals(a.id(), VillageManager.stallsForSettlement(settlement.id()).get(0).id());
        assertEquals(1, VillageManager.stallsForSettlement(other).size());
        assertTrue(VillageManager.removeStall(a.id()));
        assertTrue(VillageManager.stallsForSettlement(settlement.id()).isEmpty());
        // 另一聚落索引不受影响
        assertEquals(1, VillageManager.stallsForSettlement(other).size());
        assertEquals(b.id(), VillageManager.stallsForSettlement(other).get(0).id());
    }

    @Test
    void addFavor_zeroValueEntryRemoved() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        v.addFavor(player, 5);
        assertTrue(v.favor().containsKey(player));
        assertEquals(0, v.addFavor(player, -5));
        assertFalse(v.favor().containsKey(player)); // 0 值条目回收，不堆积
        assertEquals(0, v.favorOf(player));
    }

    @Test
    void load_clampsCaravanRetreatFields() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        v.markMarketDay(7);
        // 模拟手改存档：超大撤退日＋负数零成交计数
        v.setCaravanRetreatUntilDay(Long.MAX_VALUE);
        v.setCaravanZeroSaleDays(-5);

        CompoundTag root = new CompoundTag();
        VillageDataSerializer.save(root);
        VillageManager.clearDirect();
        VillageDataSerializer.load(root);

        VillageRecord restored = VillageManager.get(settlement.id());
        assertNotNull(restored);
        assertTrue(restored.caravanRetreatUntilDay() >= 0);
        assertTrue(restored.caravanRetreatUntilDay() <= 7 + 30); // 被钳制到结算日+30 天
        assertEquals(0, restored.caravanZeroSaleDays()); // 负数→0
    }

    @Test
    void load_keepsLegitCaravanRetreatFields() {
        VillageRecord v = makeVillage(20, 10, 10, 0, 0);
        v.markMarketDay(7);
        v.setCaravanRetreatUntilDay(14); // 正常值：结算日+7
        v.setCaravanZeroSaleDays(2);

        CompoundTag root = new CompoundTag();
        VillageDataSerializer.save(root);
        VillageManager.clearDirect();
        VillageDataSerializer.load(root);

        VillageRecord restored = VillageManager.get(settlement.id());
        assertNotNull(restored);
        assertEquals(14, restored.caravanRetreatUntilDay());
        assertEquals(2, restored.caravanZeroSaleDays());
    }
}
