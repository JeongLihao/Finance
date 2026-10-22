package finance.caravan;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.regional.RegionalCommodityMetricsService;
import finance.settlement.SettlementRecord;
import finance.settlement.SettlementStatus;
import finance.util.MathUtil;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * 商队定价与风险纯函数（无世界写操作，可单测）。
 *
 * <p>价格口径：买入取批发价（mid × 源地溢价），卖出取目的地需求价（mid × 目的地溢价 × 饱和折扣），
 * 均钳制在 {@code [1, base×3]}。溢价来自 {@code RegionalCommodityMetricsService} 的区域
 * shortage 数据（缺货越严重溢价越高；无数据回 10000）。</p>
 */
public final class CaravanPricing {

    private CaravanPricing() {}

    // ================================================================
    // 价格
    // ================================================================

    /**
     * 源地买入单价：{@code clamp(mid × srcMult / 10000, 1, base × capMult)}。
     *
     * @return ≤ 0 表示无法定价（调用方跳过本趟）
     */
    public static long sourceBuyPrice(String commodityId, SettlementRecord source) {
        Commodity def = CommodityRegistry.getCommodity(commodityId);
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        if (def == null || mp == null || source == null) {
            return 0;
        }
        long mid = mp.getMidPrice();
        if (mid <= 0) {
            return 0;
        }
        // 宏观经济启用时，用本地供需价格系数代替区域报价修正
        int mult = finance.macro.MacroEconomyService.caravanQuoteBps(
                source, commodityId, RegionalCommodityMetricsService.quoteMultiplierBps(source, commodityId));
        long scaled = MathUtil.multiplyExactOrNegative1(mid, Math.max(1, mult));
        if (scaled <= 0) {
            return 0;
        }
        long price = scaled / 10_000L;
        return clampPrice(price, def.getBasePrice());
    }

    /**
     * 目的地卖出单价：{@code clamp(mid × dstMult / 10000 × 折扣, 1, base × capMult)}，
     * 折扣 = {@code 10000 / (10000 + saturationBps)}。
     *
     * @return ≤ 0 表示无法定价（调用方跳过本趟）
     */
    public static long destSellPrice(String commodityId, SettlementRecord dest, long saturationBps) {
        Commodity def = CommodityRegistry.getCommodity(commodityId);
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        if (def == null || mp == null || dest == null) {
            return 0;
        }
        long mid = mp.getMidPrice();
        if (mid <= 0) {
            return 0;
        }
        int mult = finance.macro.MacroEconomyService.caravanQuoteBps(
                dest, commodityId, RegionalCommodityMetricsService.quoteMultiplierBps(dest, commodityId));
        long scaled = MathUtil.multiplyExactOrNegative1(mid, Math.max(1, mult));
        if (scaled <= 0) {
            return 0;
        }
        long price = scaled / 10_000L;
        long sat = Math.max(0L, saturationBps);
        if (sat > 0) {
            price = price * 10_000L / (10_000L + sat);
        }
        return clampPrice(price, def.getBasePrice());
    }

    private static long clampPrice(long price, long basePrice) {
        if (price <= 0) {
            return 0;
        }
        long capRaw = MathUtil.multiplyExactOrNegative1(
                Math.max(1L, basePrice), Math.max(1, FinanceConfig.caravanSellPriceCapMultiplier()));
        long cap = capRaw <= 0 ? Long.MAX_VALUE : capRaw;
        return Math.min(Math.max(1L, price), cap);
    }

    // ================================================================
    // 运输
    // ================================================================

    /** 两聚落直线距离（格）。跨维度返回 -1。 */
    public static long distanceBlocks(SettlementRecord a, SettlementRecord b) {
        if (a == null || b == null || a.anchor() == null || b.anchor() == null) {
            return -1;
        }
        String da = a.dimensionId(), db = b.dimensionId();
        if (da == null || !da.equals(db)) {
            return -1;
        }
        BlockPos pa = a.anchor(), pb = b.anchor();
        double dx = pa.getX() - pb.getX();
        double dy = pa.getY() - pb.getY();
        double dz = pa.getZ() - pb.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(d >= 0) || d > 30_000_000d) {
            return -1;
        }
        return (long) d;
    }

    /** 运输天数：{@code 1 + floor(dist / blocksPerDay)}，≥ 1（不可瞬移）。 */
    public static long travelDays(long distanceBlocks) {
        if (distanceBlocks < 0) {
            return -1;
        }
        long perDay = Math.max(1L, FinanceConfig.caravanBlocksPerDay());
        return 1 + distanceBlocks / perDay;
    }

    // ================================================================
    // 风险
    // ================================================================

    /**
     * 被劫概率（基点），钳制到 [minBps, maxBps]：
     * {@code base + distKm × perKm（上限） + raidAlertBonus − guardLevel × guardReduce}。
     */
    public static int raidBps(SettlementRecord source, SettlementRecord dest, long distanceBlocks, int guardLevel) {
        long risk = FinanceConfig.caravanRaidBaseBps();
        if (distanceBlocks >= 0) {
            long km = distanceBlocks / 1000;
            long perKm = MathUtil.multiplyExactOrNegative1(
                    Math.max(0L, km), Math.max(0, FinanceConfig.caravanRaidPerKmBps()));
            if (perKm < 0) {
                perKm = Long.MAX_VALUE;
            }
            risk += Math.min(perKm, FinanceConfig.caravanRaidPerKmCapBps());
        }
        if (isRaidAlert(source) || isRaidAlert(dest)) {
            risk += FinanceConfig.caravanRaidAlertBonusBps();
        }
        risk -= (long) Math.max(0, Math.min(CaravanRoute.MAX_GUARD_LEVEL, guardLevel))
                * Math.max(0, FinanceConfig.caravanGuardReduceBps());
        int min = FinanceConfig.caravanRaidMinBps();
        int max = Math.max(min, FinanceConfig.caravanRaidMaxBps());
        return (int) Math.max(min, Math.min(max, risk));
    }

    private static boolean isRaidAlert(SettlementRecord s) {
        return s != null && s.status() == SettlementStatus.RAID_ALERT;
    }

    /**
     * 确定性被劫判定：同一路线同一天结果固定（防读档刷结果）。
     *
     * @param randSeedExtra 测试用额外种子（生产传 0）
     */
    public static boolean rollRaid(UUID routeId, long day, int raidBps, long randSeedExtra) {
        if (routeId == null || raidBps <= 0) {
            return false;
        }
        long seed = (long) routeId.hashCode() * 31_415_926_535_897L + day * 1_000_003L + randSeedExtra;
        int roll = new Random(seed).nextInt(10_000);
        return roll < Math.min(10_000, raidBps);
    }

    // ================================================================
    // 保镖与保险
    // ================================================================

    /** 保镖工资（每趟），按等级查表。 */
    public static long guardWage(int guardLevel) {
        switch (Math.max(0, Math.min(CaravanRoute.MAX_GUARD_LEVEL, guardLevel))) {
            case 1:
                return Math.max(0L, FinanceConfig.caravanGuardWage1());
            case 2:
                return Math.max(0L, FinanceConfig.caravanGuardWage2());
            case 3:
                return Math.max(0L, FinanceConfig.caravanGuardWage3());
            default:
                return 0L;
        }
    }

    /**
     * 保费（风险定价）：{@code qty × unitPrice × raidBps × lossRatioBps × marginBps / 10^12}。
     * 保费恒大于期望赔付（margin > 10000），保险是花钱买心安而非套利工具。
     *
     * @return ≤ 0 表示无法计价
     */
    public static long insurancePremium(long qty, long unitPrice, int raidBps) {
        if (qty <= 0 || unitPrice <= 0 || raidBps <= 0) {
            return 0;
        }
        long value = MathUtil.multiplyExactOrNegative1(unitPrice, (int) Math.min(qty, Integer.MAX_VALUE));
        if (value <= 0) {
            return 0;
        }
        // value × raidBps / 10000 = 期望风险敞口
        long exposure = MathUtil.multiplyExactOrNegative1(value, Math.min(10_000, raidBps));
        if (exposure <= 0) {
            return 0;
        }
        exposure = exposure / 10_000L;
        // × lossRatio × margin
        long withLoss =
                MathUtil.multiplyExactOrNegative1(exposure, Math.max(0, FinanceConfig.caravanRaidLossRatioBps()));
        if (withLoss <= 0) {
            return 0;
        }
        withLoss = withLoss / 10_000L;
        long premium =
                MathUtil.multiplyExactOrNegative1(withLoss, Math.max(10_000, FinanceConfig.caravanInsureMarginBps()));
        if (premium <= 0) {
            return 0;
        }
        return premium / 10_000L;
    }
}
