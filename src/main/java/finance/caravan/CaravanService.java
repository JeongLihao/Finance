package finance.caravan;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.settlement.SettlementStatus;
import finance.util.MathUtil;
import finance.warehouse.CommodityItemResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 商队服务：抽象自动商队的建路线、每日发车/到货结算。
 *
 * <p>每日流程（由 {@code EconomyCycleService.tickDay} 驱动）：
 * <ol>
 *   <li>到货结算：被劫判定（确定性随机）→ 丢货 → 保险赔付 → 按目的地实时价出售；</li>
 *   <li>新发车：ACTIVE 路线按源地价买入，生成在途货运；余额不足整趟取消。</li>
 * </ol>
 *
 * <p>负反馈双通道：{@code MarketPrice} 双向动量（买推高/卖压低 mid）＋路线饱和度
 * （跑得越勤卖价折扣越深）。金额全部 long，溢出即跳过该笔。</p>
 */
public final class CaravanService {

    private CaravanService() { }

    // ================================================================
    // 建路线 / 管理
    // ================================================================

    public record Result(boolean ok, String message, CaravanRoute route) {
        public static Result fail(String m) { return new Result(false, m, null); }
        public static Result ok(CaravanRoute r, String m) { return new Result(true, m, r); }
    }

    /**
     * 建路线。聚落按 displayName 精确匹配（歧义/不存在直接失败）。
     */
    public static Result createRoute(ServerPlayer player, String sourceName, String destName,
                                     String commodityId, int qty, int guardLevel,
                                     boolean insured, long day) {
        if (player == null) {
            return Result.fail("仅玩家可建商队路线。");
        }
        return createRoute(player.getUUID(), sourceName, destName,
                commodityId, qty, guardLevel, insured, day);
    }

    /**
     * 建路线（UUID 版，逻辑与上同；测试可直接调）。
     */
    public static Result createRoute(UUID ownerId, String sourceName, String destName,
                                     String commodityId, int qty, int guardLevel,
                                     boolean insured, long day) {
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CARAVAN)) {
            return Result.fail("商队模块维护中，请稍后再试。");
        }
        if (ownerId == null) {
            return Result.fail("仅玩家可建商队路线。");
        }
        SettlementRecord source = findSettlement(sourceName);
        SettlementRecord dest = findSettlement(destName);
        if (source == null) {
            return Result.fail("找不到起点聚落：" + sourceName);
        }
        if (dest == null) {
            return Result.fail("找不到终点聚落：" + destName);
        }
        if (source.id().equals(dest.id())) {
            return Result.fail("起点与终点不能是同一聚落。");
        }
        if (!usable(source) || !usable(dest)) {
            return Result.fail("聚落状态异常，无法通商。");
        }
        if (CaravanPricing.distanceBlocks(source, dest) < 0) {
            return Result.fail("跨维度路线暂不支持。");
        }
        if (CommodityRegistry.getCommodity(commodityId) == null) {
            return Result.fail("未知商品：" + commodityId);
        }
        if (!CommodityItemResolver.resolve(commodityId).valid()) {
            return Result.fail("该商品无 MC 实物映射，不可运输。");
        }
        int maxQty = Math.max(1, FinanceConfig.caravanMaxQtyPerTrip());
        if (qty <= 0 || qty > maxQty) {
            return Result.fail("每趟数量须在 1~" + maxQty + " 之间。");
        }
        if (guardLevel < CaravanRoute.MIN_GUARD_LEVEL || guardLevel > CaravanRoute.MAX_GUARD_LEVEL) {
            return Result.fail("保镖等级须在 0~3 之间。");
        }
        int maxRoutes = Math.max(1, FinanceConfig.caravanMaxRoutesPerPlayer());
        if (CaravanManager.activeRouteCountForOwner(ownerId) >= maxRoutes) {
            return Result.fail("路线已达上限（" + maxRoutes + " 条）。");
        }
        CaravanRoute route;
        try {
            route = new CaravanRoute(UUID.randomUUID(), ownerId, source.id(), dest.id(),
                    commodityId, qty, guardLevel, insured, day);
        } catch (IllegalArgumentException e) {
            return Result.fail("建路线失败：" + e.getMessage());
        }
        if (!CaravanManager.addRoute(route)) {
            return Result.fail("建路线失败（id 冲突），请重试。");
        }
        EconomySavedData.markDirty();
        return Result.ok(route, "商队路线已建立：" + source.displayName() + " → " + dest.displayName());
    }

    public static Result pauseRoute(ServerPlayer player, UUID routeId) {
        return player == null ? Result.fail("仅玩家可用。")
                : pauseRoute(player.getUUID(), routeId);
    }

    public static Result pauseRoute(UUID ownerId, UUID routeId) {
        return setStatus(ownerId, routeId, CaravanRoute.Status.PAUSED, "已暂停");
    }

    public static Result resumeRoute(ServerPlayer player, UUID routeId) {
        return player == null ? Result.fail("仅玩家可用。")
                : resumeRoute(player.getUUID(), routeId);
    }

    public static Result resumeRoute(UUID ownerId, UUID routeId) {
        return setStatus(ownerId, routeId, CaravanRoute.Status.ACTIVE, "已恢复");
    }

    public static Result closeRoute(ServerPlayer player, UUID routeId) {
        return player == null ? Result.fail("仅玩家可用。")
                : closeRoute(player.getUUID(), routeId);
    }

    public static Result closeRoute(UUID ownerId, UUID routeId) {
        return setStatus(ownerId, routeId, CaravanRoute.Status.CLOSED, "已关闭（在途货运继续走完）");
    }

    private static Result setStatus(UUID ownerId, UUID routeId,
                                    CaravanRoute.Status status, String msg) {
        if (ownerId == null || routeId == null) {
            return Result.fail("参数非法。");
        }
        CaravanRoute route = CaravanManager.route(routeId);
        if (route == null || !ownerId.equals(route.ownerId())) {
            return Result.fail("找不到你的路线。");
        }
        route.setStatus(status);
        EconomySavedData.markDirty();
        return Result.ok(route, "路线" + msg + "。");
    }

    private static SettlementRecord findSettlement(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<SettlementRecord> hits = new ArrayList<>();
        for (SettlementRecord s : SettlementManager.settlements().values()) {
            if (name.equals(s.displayName())) {
                hits.add(s);
            }
        }
        return hits.size() == 1 ? hits.get(0) : null;
    }

    private static boolean usable(SettlementRecord s) {
        SettlementStatus st = s.status();
        return st != null && st != SettlementStatus.DISABLED && st != SettlementStatus.QUARANTINED;
    }

    // ================================================================
    // 每日结算
    // ================================================================

    /**
     * 每日商队结算，由 {@code EconomyCycleService.tickDay} 驱动。
     */
    public static synchronized void processDay(MinecraftServer server, long day) {
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.CARAVAN)) {
            return;
        }
        // 1. 到货结算
        for (CaravanShipment shipment : CaravanManager.arrivalsOn(day)) {
            settleShipment(shipment, day);
        }
        // 2. 饱和度衰减（所有路线，含暂停——停跑的市场自己冷却）
        long decayBps = Math.max(0, FinanceConfig.caravanSaturationDecayBps());
        for (CaravanRoute route : CaravanManager.routes()) {
            route.decaySaturation(decayBps);
        }
        // 3. 新发车
        for (CaravanRoute route : CaravanManager.routes()) {
            if (route.status() != CaravanRoute.Status.ACTIVE) {
                continue;
            }
            dispatch(route, day);
        }
        EconomySavedData.markDirty();
    }

    // ================================================================
    // 发车
    // ================================================================

    private static void dispatch(CaravanRoute route, long day) {
        UUID ownerId = route.ownerId();
        SettlementRecord source = SettlementManager.get(route.sourceSettlementId());
        SettlementRecord dest = SettlementManager.get(route.destSettlementId());
        if (source == null || dest == null || !usable(source) || !usable(dest)) {
            return;
        }
        long distance = CaravanPricing.distanceBlocks(source, dest);
        long travelDays = CaravanPricing.travelDays(distance);
        if (travelDays <= 0) {
            return;
        }
        String commodityId = route.commodityId();
        int qty = route.qtyPerTrip();
        long buyPrice = CaravanPricing.sourceBuyPrice(commodityId, source);
        if (buyPrice <= 0) {
            return;
        }
        long goodsCost = MathUtil.multiplyExactOrNegative1(buyPrice, qty);
        if (goodsCost <= 0) {
            return;
        }
        long wage = CaravanPricing.guardWage(route.guardLevel());
        int raidBps = CaravanPricing.raidBps(source, dest, distance, route.guardLevel());
        long premium = route.insured()
                ? CaravanPricing.insurancePremium(qty, buyPrice, raidBps) : 0L;
        long total = MathUtil.saturatedAddNonNegative(
                goodsCost, MathUtil.saturatedAddNonNegative(wage, premium));
        if (total <= 0 || AccountManager.getBalance(ownerId) < total) {
            return; // 余额不足：整趟取消，不负债
        }
        // 扣款：货款 → 系统（源地抽象市场），工资/保费 → 系统
        UUID npc = NpcMarketMaker.NPC_UUID;
        if (!AccountManager.moveFunds(ownerId, npc, total)) {
            return;
        }
        AccountManager.addTransactionRecord(new TransactionRecord(
                ownerId, npc, goodsCost, TransactionType.CARAVAN_BUY,
                ownerId, commodityId, qty));
        if (wage > 0) {
            AccountManager.addTransactionRecord(new TransactionRecord(
                    ownerId, npc, wage, TransactionType.CARAVAN_GUARD_WAGE,
                    ownerId, commodityId, qty));
        }
        if (premium > 0) {
            AccountManager.addTransactionRecord(new TransactionRecord(
                    ownerId, npc, premium, TransactionType.CARAVAN_INSURANCE_PREMIUM,
                    ownerId, commodityId, qty));
        }
        // 负反馈通道 1：买方动量（推高 mid）
        pushBuyMomentum(commodityId, qty);
        // 宏观经济：采购量计入出发地需求（出口）
        finance.macro.MacroEconomyService.recordExport(source.id(), commodityId, qty);
        CaravanShipment shipment = new CaravanShipment(UUID.randomUUID(), route.id(), ownerId,
                commodityId, qty, buyPrice, day, day + travelDays,
                route.guardLevel(), raidBps, route.insured() ? buyPrice : 0L, total);
        CaravanManager.addShipment(shipment);
    }

    // ================================================================
    // 到货结算
    // ================================================================

    private static void settleShipment(CaravanShipment shipment, long day) {
        UUID ownerId = shipment.ownerId();
        String commodityId = shipment.commodityId();
        CaravanRoute route = CaravanManager.route(shipment.routeId());
        SettlementRecord dest = route == null ? null
                : SettlementManager.get(route.destSettlementId());
        if (route == null || dest == null || !usable(dest)) {
            // 极端边缘：路线/终点没了，货物灭失，不退款（已在发车时支付）
            shipment.markArrived();
            CaravanManager.removeShipment(shipment.id());
            return;
        }
        // 被劫判定（确定性随机）：用发车时快照的 raidBps。
        // 结算时不再按到货日重算——途中 RAID_ALERT 开关不得改变已发车货运的
        // 结算概率，否则发车日锁定的保费与结算概率脱钩，"保费恒＞期望赔付"被打破。
        int raidBps = shipment.raidBpsSnapshot();
        boolean raided = CaravanPricing.rollRaid(route.id(), day, raidBps, 0L);
        int qty = shipment.qty();
        int lostQty = 0;
        long insurancePayout = 0L;
        if (raided) {
            lostQty = (int) Math.min((long) qty,
                    (long) qty * Math.max(0, FinanceConfig.caravanRaidLossRatioBps()) / 10_000L);
            if (shipment.insured() && lostQty > 0) {
                long payout = MathUtil.multiplyExactOrNegative1(
                        shipment.insuredValuePerUnit(), lostQty);
                if (payout > 0 && AccountManager.deposit(ownerId, payout)) {
                    insurancePayout = payout;
                    AccountManager.addTransactionRecord(new TransactionRecord(
                            NpcMarketMaker.NPC_UUID, ownerId, payout,
                            TransactionType.CARAVAN_INSURANCE_PAYOUT,
                            ownerId, commodityId, lostQty));
                }
            }
            if (lostQty > 0) {
                long loss = MathUtil.multiplyExactOrNegative1(shipment.buyUnitPrice(), lostQty);
                if (loss > 0) {
                    AccountManager.addTransactionRecord(new TransactionRecord(
                            ownerId, NpcMarketMaker.NPC_UUID, loss,
                            TransactionType.CARAVAN_LOSS, ownerId, commodityId, lostQty));
                }
            }
        }
        int soldQty = qty - lostQty;
        long revenue = 0L;
        if (soldQty > 0) {
            long sellPrice = CaravanPricing.destSellPrice(
                    commodityId, dest, route.saturationBps());
            if (sellPrice > 0) {
                revenue = MathUtil.multiplyExactOrNegative1(sellPrice, soldQty);
                if (revenue > 0 && AccountManager.deposit(ownerId, revenue)) {
                    AccountManager.addTransactionRecord(new TransactionRecord(
                            NpcMarketMaker.NPC_UUID, ownerId, revenue,
                            TransactionType.CARAVAN_SELL, ownerId, commodityId, soldQty));
                } else {
                    revenue = 0L;
                }
            }
            // 负反馈通道 1：卖方动量（压低 mid）
            pushSellMomentum(commodityId, soldQty);
            // 宏观经济：卖出的货真的进入目的地聚落托管，供人口消费（只进不出，玩家取不出来）
            if (revenue > 0) {
                // 成就判定要看送达前的饥荒状态，所以先判定再入库
                finance.macro.EconomyAchievements.onCaravanDelivered(ownerId, dest.id(), commodityId);
                finance.macro.MacroEconomyService.deliverImport(dest.id(), commodityId, soldQty);
            }
        }
        // 负反馈通道 2：路线饱和度（按整趟数量累积；只有真实成交才累积，
        // 销售失败（sellPrice≤0）时不累积，否则白跑也压价）
        if (revenue > 0) {
            route.addSaturation((long) qty * Math.max(0, FinanceConfig.caravanSaturationPerUnitBps()));
        }
        // 记账：利润 = 到账（货款＋赔付）− 发车总成本
        long profit = revenue + insurancePayout - shipment.dispatchCost();
        route.addProfit(profit);
        shipment.markArrived();
        CaravanManager.removeShipment(shipment.id());
    }

    // ================================================================
    // 负反馈：MarketPrice 双向动量
    // ================================================================

    private static void pushBuyMomentum(String commodityId, int qty) {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        if (mp == null || qty <= 0) {
            return;
        }
        int npcStock = Math.max(0, CommodityInventoryManager.getCommodityAmount(
                NpcMarketMaker.NPC_UUID, commodityId));
        mp.onNpcTrade(npcStock, true, Math.min(qty, 1_000_000));
    }

    private static void pushSellMomentum(String commodityId, int qty) {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        if (mp == null || qty <= 0) {
            return;
        }
        int npcStock = Math.max(0, CommodityInventoryManager.getCommodityAmount(
                NpcMarketMaker.NPC_UUID, commodityId));
        mp.onNpcTrade(npcStock, false, Math.min(qty, 1_000_000));
    }
}
