package finance.caravan;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.data.serializer.CaravanDataSerializer;
import finance.diagnostic.ModuleHealthRegistry;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.settlement.SettlementStatus;
import finance.testutil.MinecraftTestBootstrap;
import finance.util.MathUtil;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 商队商路测试：建路线校验、定价/风险纯函数、保险精算、负反馈、持久化，
 * 外加攻击视角（瞬移、双重结算、溢出）。
 */
class CaravanServiceTest {

    private UUID owner;
    private SettlementRecord src;
    private SettlementRecord dst;

    @BeforeEach
    void setup() {
        MinecraftTestBootstrap.ensureStarted();
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "小麦", CommodityCategory.FOOD, 8));
        ModuleHealthRegistry.clear();
        SettlementManager.clearDirect();
        CaravanManager.clearDirect();
        owner = UUID.randomUUID();
        src = record("minecraft:overworld", new BlockPos(0, 64, 0), "源村");
        dst = record("minecraft:overworld", new BlockPos(10_000, 64, 0), "终村");
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        SettlementManager.clearDirect();
        CaravanManager.clearDirect();
        ModuleHealthRegistry.clear();
    }

    private SettlementRecord record(String dim, BlockPos pos, String name) {
        SettlementRecord s =
                new SettlementRecord(UUID.randomUUID(), dim, pos, name, SettlementStatus.ACTIVE, -1, -1, "");
        assertTrue(SettlementManager.restoreSettlement(s));
        return s;
    }

    private CaravanService.Result createOk() {
        CaravanService.Result r = CaravanService.createRoute(owner, "源村", "终村", "wheat", 100, 1, true, 0L);
        assertTrue(r.ok(), r.message());
        return r;
    }

    // ================= 建路线校验 =================

    @Test
    void createRouteSuccess() {
        CaravanService.Result r = createOk();
        assertNotNull(r.route());
        assertEquals("wheat", r.route().commodityId());
        assertEquals(100, r.route().qtyPerTrip());
        assertEquals(1, r.route().guardLevel());
        assertTrue(r.route().insured());
    }

    @Test
    void createRouteRejectsNullOwner() {
        assertFalse(CaravanService.createRoute((UUID) null, "源村", "终村", "wheat", 100, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsSameSourceAndDest() {
        assertFalse(CaravanService.createRoute(owner, "源村", "源村", "wheat", 100, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsUnknownSettlement() {
        assertFalse(CaravanService.createRoute(owner, "不存在", "终村", "wheat", 100, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsAmbiguousName() {
        record("minecraft:overworld", new BlockPos(50_000, 64, 0), "源村");
        assertFalse(CaravanService.createRoute(owner, "源村", "终村", "wheat", 100, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsCrossDimension() {
        record("minecraft:the_nether", new BlockPos(10_000, 64, 0), "下界村");
        assertFalse(CaravanService.createRoute(owner, "源村", "下界村", "wheat", 100, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsDisabledSettlement() {
        SettlementRecord dead = record("minecraft:overworld", new BlockPos(20_000, 64, 0), "死村");
        dead.disable();
        assertFalse(CaravanService.createRoute(owner, "源村", "死村", "wheat", 100, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsUnknownCommodity() {
        assertFalse(CaravanService.createRoute(owner, "源村", "终村", "nope", 100, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsBadQty() {
        assertFalse(CaravanService.createRoute(owner, "源村", "终村", "wheat", 0, 0, false, 0L)
                .ok());
        assertFalse(CaravanService.createRoute(
                        owner, "源村", "终村", "wheat", FinanceConfig.caravanMaxQtyPerTrip() + 1, 0, false, 0L)
                .ok());
    }

    @Test
    void createRouteRejectsBadGuardLevel() {
        assertFalse(CaravanService.createRoute(owner, "源村", "终村", "wheat", 100, -1, false, 0L)
                .ok());
        assertFalse(CaravanService.createRoute(owner, "源村", "终村", "wheat", 100, 4, false, 0L)
                .ok());
    }

    @Test
    void createRouteEnforcesPerPlayerLimit() {
        int max = FinanceConfig.caravanMaxRoutesPerPlayer();
        for (int i = 0; i < max; i++) {
            record("minecraft:overworld", new BlockPos(30_000 + i * 1000, 64, 0), "村" + i);
            CaravanService.Result r = CaravanService.createRoute(owner, "源村", "村" + i, "wheat", 10, 0, false, 0L);
            assertTrue(r.ok(), r.message());
        }
        record("minecraft:overworld", new BlockPos(99_000, 64, 0), "村X");
        assertFalse(CaravanService.createRoute(owner, "源村", "村X", "wheat", 10, 0, false, 0L)
                .ok());
    }

    @Test
    void pauseResumeCloseAndOwnership() {
        CaravanRoute route = createOk().route();
        UUID other = UUID.randomUUID();
        // 非本人管不了
        assertFalse(CaravanService.pauseRoute(other, route.id()).ok());
        assertTrue(CaravanService.pauseRoute(owner, route.id()).ok());
        assertEquals(CaravanRoute.Status.PAUSED, route.status());
        assertTrue(CaravanService.resumeRoute(owner, route.id()).ok());
        assertEquals(CaravanRoute.Status.ACTIVE, route.status());
        assertTrue(CaravanService.closeRoute(owner, route.id()).ok());
        assertEquals(CaravanRoute.Status.CLOSED, route.status());
    }

    // ================= 纯函数：距离/天数/风险 =================

    @Test
    void travelDaysAtLeastOne() {
        // 攻击视角：距离再短也不能瞬移
        assertEquals(1, CaravanPricing.travelDays(0));
        assertEquals(1, CaravanPricing.travelDays(1));
        assertEquals(-1, CaravanPricing.travelDays(-1));
        long far = CaravanPricing.travelDays(1_000_000L);
        assertTrue(far > 1);
        // 攻击视角：极端距离不溢出
        assertTrue(CaravanPricing.travelDays(Long.MAX_VALUE) > 0);
    }

    @Test
    void distanceBlocksRejectsCrossDimensionAndNull() {
        SettlementRecord nether = record("minecraft:the_nether", new BlockPos(10_000, 64, 0), "下界村");
        assertEquals(-1, CaravanPricing.distanceBlocks(src, nether));
        assertEquals(-1, CaravanPricing.distanceBlocks(null, dst));
        assertEquals(10_000L, CaravanPricing.distanceBlocks(src, dst));
    }

    @Test
    void raidBpsDecreasesWithGuardsAndIsClamped() {
        long dist = 10_000L;
        int none = CaravanPricing.raidBps(src, dst, dist, 0);
        int guarded = CaravanPricing.raidBps(src, dst, dist, 3);
        assertTrue(guarded < none, "保镖必须降低被劫率");
        int min = FinanceConfig.caravanRaidMinBps();
        assertEquals(Math.max(min, none - 3 * FinanceConfig.caravanGuardReduceBps()), guarded);
        // 钳制
        assertTrue(CaravanPricing.raidBps(src, dst, 0, 3) >= min);
        int max = CaravanPricing.raidBps(src, dst, Long.MAX_VALUE / 2, 0);
        assertTrue(max <= FinanceConfig.caravanRaidMaxBps());
    }

    @Test
    void raidAlertBonusApplies() {
        long dist = 1_000L;
        int calm = CaravanPricing.raidBps(src, dst, dist, 0);
        dst.raidAlert(1L, "test");
        int alert = CaravanPricing.raidBps(src, dst, dist, 0);
        assertTrue(alert >= calm);
    }

    @Test
    void rollRaidIsDeterministic() {
        UUID routeId = UUID.randomUUID();
        boolean a = CaravanPricing.rollRaid(routeId, 7L, 5000, 0L);
        boolean b = CaravanPricing.rollRaid(routeId, 7L, 5000, 0L);
        assertEquals(a, b, "同一天同一路线结果必须固定（防读档刷结果）");
        assertFalse(CaravanPricing.rollRaid(routeId, 7L, 0, 0L));
        assertTrue(CaravanPricing.rollRaid(routeId, 7L, 10_000, 0L));
        // 统计口径：50% 概率下 2000 次独立掷骰应在 40%~60% 之间
        int raids = 0;
        int n = 2000;
        for (int i = 0; i < n; i++) {
            if (CaravanPricing.rollRaid(UUID.randomUUID(), i, 5000, 0L)) {
                raids++;
            }
        }
        assertTrue(raids > n * 0.4 && raids < n * 0.6, "被劫率应接近设定值，实际 " + raids + "/" + n);
    }

    // ================= 保险精算 =================

    @Test
    void insurancePremiumIsActuariallySound() {
        // 保费 = 货值 × 被劫率 × 丢货比 × margin，恒 ≥ 期望赔付（无逆向选择漏洞）
        long qty = 100, unit = 500;
        int raidBps = 800;
        long premium = CaravanPricing.insurancePremium(qty, unit, raidBps);
        long expectedPayout = qty * unit * raidBps / 10_000 * FinanceConfig.caravanRaidLossRatioBps() / 10_000;
        assertTrue(premium >= expectedPayout, "保费 " + premium + " 必须 ≥ 期望赔付 " + expectedPayout);
        assertTrue(premium > 0);
        assertEquals(0, CaravanPricing.insurancePremium(qty, unit, 0));
        assertEquals(0, CaravanPricing.insurancePremium(0, unit, raidBps));
    }

    @Test
    void guardWagesAreOrdered() {
        assertEquals(0, CaravanPricing.guardWage(0));
        assertTrue(CaravanPricing.guardWage(1) < CaravanPricing.guardWage(2));
        assertTrue(CaravanPricing.guardWage(2) < CaravanPricing.guardWage(3));
        assertEquals(FinanceConfig.caravanGuardWage1(), CaravanPricing.guardWage(1));
    }

    // ================= 饱和度负反馈 =================

    @Test
    void saturationAccumulatesAndDecays() {
        CaravanRoute route = createOk().route();
        assertEquals(0, route.saturationBps());
        route.addSaturation(500);
        assertEquals(500, route.saturationBps());
        route.decaySaturation(FinanceConfig.caravanSaturationDecayBps());
        long after = route.saturationBps();
        assertTrue(after < 500 && after >= 0);
        // 衰减不会变负
        route.decaySaturation(100_000);
        assertEquals(0, route.saturationBps());
    }

    @Test
    void saturationDiscountsSellPrice() {
        // 终点卖出价随饱和度单调下降（价差自动抹平）
        long p0 = CaravanPricing.destSellPrice("wheat", dst, 0);
        long p1 = CaravanPricing.destSellPrice("wheat", dst, 5000);
        assertTrue(p0 > 0 && p1 > 0);
        assertTrue(p1 < p0, "饱和度必须压低卖价");
    }

    @Test
    void buyAndSellPricesAreClamped() {
        long buy = CaravanPricing.sourceBuyPrice("wheat", src);
        long sell = CaravanPricing.destSellPrice("wheat", dst, 0);
        // basePrice=8，钳制在 [8, 8×capMultiplier]
        assertTrue(buy >= 8 && buy <= 8L * FinanceConfig.caravanSellPriceCapMultiplier());
        assertTrue(sell >= 1 && sell <= 8L * FinanceConfig.caravanSellPriceCapMultiplier());
        // 无法定价返回 ≤ 0（调用方跳过本趟）
        assertTrue(CaravanPricing.sourceBuyPrice("nope", src) <= 0);
    }

    // ================= 攻击视角 =================

    @Test
    void shipmentCannotBeDoubleCounted() {
        // 在途货运结算后必须从注册表移除：同一天 arrivalsOn 不再返回
        CaravanRoute route = createOk().route();
        CaravanShipment s =
                new CaravanShipment(UUID.randomUUID(), route.id(), owner, "wheat", 100, 8L, 0L, 3L, 1, 800, 8L, 1300L);
        CaravanManager.putShipmentDirect(s);
        assertEquals(1, CaravanManager.arrivalsOn(3L).size());
        s.markArrived();
        CaravanManager.removeShipment(s.id());
        assertTrue(CaravanManager.arrivalsOn(3L).isEmpty());
        assertEquals(0, CaravanManager.inTransitCount());
    }

    @Test
    void overflowNeverMintsMoney() {
        // 溢出即返回 -1，调用方跳过该笔（不印钱）
        assertEquals(-1, MathUtil.multiplyExactOrNegative1(Long.MAX_VALUE, 2));
        assertEquals(0, CaravanPricing.insurancePremium(Long.MAX_VALUE / 8, Long.MAX_VALUE / 8, 10_000));
        // 越界等级钳制到最高级（不抛异常、不返回奇怪值）
        assertEquals(CaravanPricing.guardWage(3), CaravanPricing.guardWage(99));
        assertEquals(0, CaravanPricing.guardWage(-5));
    }

    @Test
    void settlementUsesDepartDayRaidBpsSnapshot() {
        // P1-5：发车后 raidBps 变化不影响该趟结算概率。
        // A 趟快照 10000 → 必被劫；B 趟快照 0 → 永不被劫。
        // 途中把终点置为 RAID_ALERT：若结算用"到货日重算"，B 也会被高概率打劫；用快照则 B 安然无恙。
        CaravanRoute routeA = createOk().route();
        CaravanShipment a = new CaravanShipment(
                UUID.randomUUID(),
                routeA.id(),
                owner,
                "wheat",
                100,
                8L,
                0L,
                3L,
                routeA.guardLevel(),
                10_000,
                8L,
                1_500L);
        CaravanManager.putShipmentDirect(a);
        CaravanService.Result rb = CaravanService.createRoute(owner, "源村", "终村", "wheat", 100, 1, true, 0L);
        assertTrue(rb.ok(), rb.message());
        CaravanShipment b = new CaravanShipment(
                UUID.randomUUID(), rb.route().id(), owner, "wheat", 100, 8L, 0L, 3L, 1, 0, 8L, 1_500L);
        CaravanManager.putShipmentDirect(b);
        // 关闭路线：结算不受影响，但 processDay 不再为这两条路线发新车（测试隔离）
        CaravanService.closeRoute(owner, routeA.id());
        CaravanService.closeRoute(owner, rb.route().id());
        dst.raidAlert(1L, "test");

        CaravanService.processDay(null, 3L);

        assertEquals(0, CaravanManager.inTransitCount(), "两趟都应结算完毕");
        long losses = AccountManager.getTransactions().stream()
                .filter(t -> t.getType() == TransactionType.CARAVAN_LOSS)
                .count();
        long payouts = AccountManager.getTransactions().stream()
                .filter(t -> t.getType() == TransactionType.CARAVAN_INSURANCE_PAYOUT)
                .count();
        assertEquals(1, losses, "只有快照 10000 的货运应被劫");
        assertEquals(1, payouts, "只有被劫且投保的货运应获赔");
    }

    @Test
    void addProfitSaturatesInsteadOfWrapping() {
        // P2：totalProfit 饱和加法——上溢/下溢钳制而非 long 回绕，且负增量（亏损）不被吞
        CaravanRoute r1 = createOk().route();
        r1.addProfit(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, r1.totalProfit());
        r1.addProfit(1);
        assertEquals(Long.MAX_VALUE, r1.totalProfit(), "上溢应钳制而非回绕");
        CaravanService.Result r2res = CaravanService.createRoute(owner, "源村", "终村", "wheat", 100, 0, false, 0L);
        assertTrue(r2res.ok(), r2res.message());
        CaravanRoute r2 = r2res.route();
        r2.addProfit(Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, r2.totalProfit());
        r2.addProfit(-1);
        assertEquals(Long.MIN_VALUE, r2.totalProfit(), "下溢应钳制而非回绕");
        CaravanService.Result r3res = CaravanService.createRoute(owner, "源村", "终村", "wheat", 100, 0, false, 0L);
        assertTrue(r3res.ok(), r3res.message());
        CaravanRoute r3 = r3res.route();
        r3.addProfit(1000);
        r3.addProfit(-700);
        assertEquals(300, r3.totalProfit(), "亏损必须正常累积，不能被吞");
    }

    @Test
    void dispatchCostRecordedForExactAccounting() {
        long dispatchCost = 1300L;
        CaravanShipment s = new CaravanShipment(
                UUID.randomUUID(), UUID.randomUUID(), owner, "wheat", 100, 8L, 0L, 3L, 1, 800, 8L, dispatchCost);
        assertEquals(dispatchCost, s.dispatchCost());
        // 利润公式：到账 − 发车总成本（精确，不重算）
        long revenue = 900L, payout = 240L;
        assertEquals(revenue + payout - dispatchCost, revenue + payout - s.dispatchCost());
    }

    // ================= 持久化 =================

    @Test
    void serializerRoundTrip() {
        CaravanRoute route = createOk().route();
        route.addSaturation(1234);
        route.addProfit(5678);
        CaravanShipment s =
                new CaravanShipment(UUID.randomUUID(), route.id(), owner, "wheat", 100, 8L, 5L, 8L, 2, 800, 8L, 1500L);
        CaravanManager.putShipmentDirect(s);

        CompoundTag root = new CompoundTag();
        CaravanDataSerializer.save(root);
        CaravanManager.clearDirect();
        CaravanDataSerializer.load(root);

        assertEquals(1, CaravanManager.routes().size());
        CaravanRoute back = CaravanManager.route(route.id());
        assertNotNull(back);
        assertEquals(1234, back.saturationBps());
        assertEquals(5678, back.totalProfit());
        assertEquals(CaravanRoute.Status.ACTIVE, back.status());
        assertEquals(1, CaravanManager.arrivalsOn(8L).size());
        assertEquals(1500L, CaravanManager.arrivalsOn(8L).get(0).dispatchCost());
        assertEquals(800, CaravanManager.arrivalsOn(8L).get(0).raidBpsSnapshot(), "发车日 raidBps 快照必须随 NBT 往返保留");
    }

    @Test
    void serializerSkipsCorruptEntries() {
        createOk();
        CompoundTag root = new CompoundTag();
        CaravanDataSerializer.save(root);
        // 损坏一条路线（非法状态枚举）
        root.getCompound(CaravanDataSerializer.ROOT)
                .getList("Routes", 10)
                .getCompound(0)
                .putString("Status", "NOT_A_STATUS");
        CaravanManager.clearDirect();
        CaravanDataSerializer.load(root);
        assertTrue(CaravanManager.routes().isEmpty());
        // 熔断：模块被限制写入
        assertFalse(ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CARAVAN));
    }
}
