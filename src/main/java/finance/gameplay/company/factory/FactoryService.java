package finance.gameplay.company.factory;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 厂房日结：扫描厂区设备 → 判定当天生效的生产方式。生产（{@code CompanyProductionService}）和
 * 雇工（{@code MacroLaborService}）只读判定结果。
 *
 * <p>生产只认这里的统一扫描：同一块设备在同一次扫描里只算给一座工厂，"白天把设备搬给两座厂用"没有意义。
 * 区块未加载时不扫描，沿用最近一次结果（最多 {@code scanStaleDays} 天）。</p>
 */
public final class FactoryService {
    private FactoryService() {}

    public static FactoryZone zone() {
        return new FactoryZone(
                FinanceConfig.facilityZoneRadius(),
                FinanceConfig.facilityZoneBelow(),
                FinanceConfig.facilityZoneAbove());
    }

    /** 每天在招工之前调用。server 为 null（测试、模拟）时只做判定，不扫描。 */
    public static void processDay(MinecraftServer server, long day) {
        if (day < 0 || !writable()) return;
        if (server != null) scanAll(server, day);
        for (CompanyFacilityRecord facility : CompanyFacilityManager.all()) {
            ProductionTier before = facility.factory().activeTier();
            long jobsBefore = facility.factory().lastJobs();
            boolean fresh = facility.factory().lastResolvedDay() < day;
            prepare(facility, day);
            if (fresh) reportTransition(facility, before, jobsBefore);
        }
        reportCoalPrice(day);
        EconomySavedData.markDirty();
    }

    // ---------------------------------------------------------------- 周报新闻

    /** 煤价新闻同一条每 30 天最多一次（运行时状态，重启后最多再报一次）。 */
    static final int COAL_NEWS_GAP_DAYS = 30;

    static final int COAL_NEWS_FACTOR_PERCENT = 130;
    private static long lastCoalNewsDay = Long.MIN_VALUE;

    /** 工厂启用工业档时上周报："X 公司启用流水线，裁员 20 人"。 */
    static void reportTransition(CompanyFacilityRecord facility, ProductionTier before, long jobsBefore) {
        ProductionTier after = facility.factory().activeTier();
        if (after != ProductionTier.INDUSTRIAL || before == ProductionTier.INDUSTRIAL) return;
        if (!finance.macro.NewsService.enabled()) return;
        Company company = CompanyManager.getCompany(facility.companyId());
        ProductionMethod method = company == null ? null : ProductionMethods.get(company.getType(), after);
        if (method == null) return;
        long layoffs = jobsBefore < 0 ? 0 : Math.max(0, jobsBefore - jobs(facility));
        String headline = layoffs > 0
                ? company.getName() + "启用" + method.displayName() + "，裁员 " + layoffs + " 人"
                : company.getName() + "启用" + method.displayName();
        finance.macro.NewsService.report(45, headline, "机器取代了人手：产量更高，但要吃更多的煤和铁。", false);
    }

    /** 有工业档工厂且煤价比基准价高出 30% 以上时："煤价因工业化上涨 X%"。 */
    static void reportCoalPrice(long day) {
        if (!finance.macro.NewsService.enabled()) return;
        if (lastCoalNewsDay != Long.MIN_VALUE && day - lastCoalNewsDay < COAL_NEWS_GAP_DAYS) return;
        if (tierCounts().get(ProductionTier.INDUSTRIAL.ordinal()) <= 0) return;
        var price = finance.market.NpcMarketMaker.getMarketPrice("coal");
        var coal = finance.commodity.CommodityRegistry.getCommodity("coal");
        if (price == null || coal == null || coal.getBasePrice() <= 0) return;
        long percent = price.getAskPrice() * 100 / coal.getBasePrice();
        if (percent < COAL_NEWS_FACTOR_PERCENT) return;
        lastCoalNewsDay = day;
        finance.macro.NewsService.report(50, "煤价因工业化上涨 " + (percent - 100) + "%", "工厂的锅炉和高炉在抢煤，村民的取暖钱跟着涨。", false);
    }

    public static void clearDirect() {
        lastCoalNewsDay = Long.MIN_VALUE;
    }

    /** 判定某设施当天的生产方式（幂等）。 */
    public static void prepare(CompanyFacilityRecord facility, long day) {
        if (facility == null || !operational(facility) || !writable()) return;
        Company company = CompanyManager.getCompany(facility.companyId());
        if (company == null) return;
        boolean fresh = facility.factory().lastResolvedDay() < day;
        facility.factory().resolveDay(company.getType(), facility.productionLevel(), day);
        if (fresh) facility.factory().setLastJobs(jobs(facility));
    }

    /** 公司玩法模块只读或暂停时，厂房状态（档位、改造、临时窗口）也不能变。 */
    static boolean writable() {
        return finance.diagnostic.ModuleHealthRegistry.mayWrite(
                finance.diagnostic.ModuleHealthRegistry.Module.COMPANY_GAMEPLAY);
    }

    static boolean operational(CompanyFacilityRecord facility) {
        return facility.status() != CompanyFacilityStatus.DISABLED
                && facility.status() != CompanyFacilityStatus.ORPHANED;
    }

    /** 全服工厂档位统计：[手工, 改良, 工业, 停产或临时作坊]，供宏观页签显示。 */
    public static List<Integer> tierCounts() {
        int[] counts = new int[4];
        for (CompanyFacilityRecord f : CompanyFacilityManager.all()) {
            if (!operational(f)) continue;
            ProductionTier tier = f.factory().activeTier();
            counts[tier == null ? 3 : tier.ordinal()]++;
        }
        return List.of(counts[0], counts[1], counts[2], counts[3]);
    }

    /** 当天生效的生产方式；null 表示缺少设备。调用前应已 {@link #prepare}。 */
    public static ProductionMethod effectiveMethod(CompanyFacilityRecord facility, Company company, long day) {
        if (facility == null || company == null) return null;
        return facility.factory().effectiveMethod(company.getType(), day);
    }

    /** 改造期产量系数（基点）。 */
    public static int retoolBps(CompanyFacilityRecord facility, long day) {
        if (facility == null || !facility.factory().retooling(day)) return 10_000;
        return Math.max(0, Math.min(100, FinanceConfig.facilityRetoolOutputPercent())) * 100;
    }

    /**
     * 设施岗位：正式档位 = 该方式设备岗位（受等级上限约束）；临时作坊沿用旧规则（等级 × 每级岗位）；缺少设备为 0。
     */
    public static long jobs(CompanyFacilityRecord facility) {
        if (facility == null) return 0;
        Company company = CompanyManager.getCompany(facility.companyId());
        if (company == null) return 0;
        FactoryState state = facility.factory();
        long day = Math.max(0, state.lastResolvedDay());
        if (state.activeTier() != null) {
            return ProductionMethods.jobs(
                    ProductionMethods.get(company.getType(), state.activeTier()),
                    state.usableEquipment(day, FinanceConfig.facilityScanStaleDays()),
                    facility.productionLevel(),
                    FinanceConfig.facilityMaxEquipmentPerType(),
                    FinanceConfig.facilityJobsCapPerLevel());
        }
        if (state.provisionalOrUnset(day)) {
            return (long) facility.productionLevel() * Math.max(1, FinanceConfig.macroJobsPerLevel());
        }
        return 0;
    }

    /**
     * 模拟和测试用：假定玩家已按某档清单搭好厂房，直接写入恰好满足该档的设备快照并设为目标档。
     * 没有世界可扫描时，需要每天调用一次以保持快照新鲜。
     */
    public static void recordBuiltEquipment(CompanyFacilityRecord facility, ProductionTier tier, long day) {
        if (facility == null || tier == null) return;
        Company company = CompanyManager.getCompany(facility.companyId());
        ProductionMethod method = company == null ? null : ProductionMethods.get(company.getType(), tier);
        if (method == null) return;
        facility.factory().setTargetTier(tier);
        facility.factory().recordScan(method.required(), day, FinanceConfig.facilityMaxEquipmentPerType());
    }

    // ---------------------------------------------------------------- 世界扫描

    static void scanAll(MinecraftServer server, long day) {
        Map<String, List<CompanyFacilityRecord>> byDimension = new LinkedHashMap<>();
        for (CompanyFacilityRecord f : CompanyFacilityManager.all()) {
            if (operational(f))
                byDimension
                        .computeIfAbsent(f.dimensionId(), k -> new ArrayList<>())
                        .add(f);
        }
        FactoryZone zone = zone();
        int maxPerType = FinanceConfig.facilityMaxEquipmentPerType();
        byDimension.forEach((dimension, facilities) -> {
            ServerLevel level = level(server, dimension);
            if (level == null) return;
            List<BlockPos> controllers =
                    facilities.stream().map(CompanyFacilityRecord::blockPos).toList();
            for (int i = 0; i < facilities.size(); i++) {
                BlockPos c = controllers.get(i);
                if (!zoneLoaded(level, c, zone)) continue; // 区块未加载：沿用旧快照
                facilities
                        .get(i)
                        .factory()
                        .recordScan(
                                zone.count(i, controllers, pos -> equipmentAt(level, pos), maxPerType),
                                day,
                                maxPerType);
            }
        });
    }

    /** 界面用的即时扫描：只用于显示清单和校验切换，不写入设施记录。 */
    public static Map<FactoryEquipment, Integer> scanForDisplay(ServerLevel level, CompanyFacilityRecord facility) {
        if (facility == null) return Map.of();
        if (level == null) return facility.factory().equipment(); // 没有世界可扫：用最近一次快照
        List<CompanyFacilityRecord> same = CompanyFacilityManager.all().stream()
                .filter(f -> operational(f) && f.dimensionId().equals(facility.dimensionId()))
                .toList();
        List<BlockPos> controllers =
                same.stream().map(CompanyFacilityRecord::blockPos).toList();
        int self = same.indexOf(facility);
        FactoryZone zone = zone();
        if (self < 0 || !zoneLoaded(level, facility.blockPos(), zone))
            return facility.factory().equipment();
        return zone.count(
                self, controllers, pos -> equipmentAt(level, pos), FinanceConfig.facilityMaxEquipmentPerType());
    }

    /** 运转表现：随机抽几格，是设备就在上方冒对应的工作粒子（烟熏炉、熔炉冒烟，织布机冒绒毛……）。 */
    public static void workParticles(ServerLevel level, CompanyFacilityRecord facility) {
        if (level == null || facility == null) return;
        FactoryZone zone = zone();
        BlockPos c = facility.blockPos();
        var random = level.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos pos = c.offset(
                    random.nextInt(zone.radius() * 2 + 1) - zone.radius(),
                    random.nextInt(zone.below() + zone.above() + 1) - zone.below(),
                    random.nextInt(zone.radius() * 2 + 1) - zone.radius());
            FactoryEquipment e = equipmentAt(level, pos);
            net.minecraft.core.particles.ParticleOptions particle = e == null
                    ? null
                    : switch (e) {
                        case SMOKER, FURNACE, BLAST_FURNACE -> net.minecraft.core.particles.ParticleTypes.SMOKE;
                        case LOOM -> net.minecraft.core.particles.ParticleTypes.WHITE_ASH;
                        case COMPOSTER, HAY_BLOCK -> net.minecraft.core.particles.ParticleTypes.COMPOSTER;
                        case GRINDSTONE, STONECUTTER, ANVIL -> net.minecraft.core.particles.ParticleTypes.CRIT;
                        case PISTON, OBSERVER, REDSTONE_LAMP, HOPPER -> net.minecraft.core.particles.DustParticleOptions
                                .REDSTONE;
                        default -> null;
                    };
            if (particle != null) {
                level.sendParticles(
                        particle, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    static boolean zoneLoaded(ServerLevel level, BlockPos controller, FactoryZone zone) {
        return level.hasChunksAt(
                controller.getX() - zone.radius(),
                controller.getZ() - zone.radius(),
                controller.getX() + zone.radius(),
                controller.getZ() + zone.radius());
    }

    static FactoryEquipment equipmentAt(ServerLevel level, BlockPos pos) {
        ResourceLocation id =
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return id == null ? null : FactoryEquipment.fromBlockId(id.toString());
    }

    static ServerLevel level(MinecraftServer server, String dimensionId) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        return id == null
                ? null
                : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }
}
