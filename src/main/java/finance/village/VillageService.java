package finance.village;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.Commodity;
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
import finance.warehouse.InventoryTransactionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 村庄集市服务：每日一次的村民集中采购（设计文档 §4）。
 *
 * <p>流程（对每个 ACTIVE/REBUILDING 聚落，顺序固定）：
 * <ol>
 *   <li>模块熔断检查（{@code Module.VILLAGE_ECONOMY}）；</li>
 *   <li>幂等：{@code markMarketDay(day)} 失败则跳过；</li>
 *   <li>预算补足：从 {@code NpcMarketMaker.NPC_UUID} 向村庄预算账户补足到上限；</li>
 *   <li>逐商品采购：玩家摊位（价格+好感排序）→ NPC 行商，WTP 钳制，三段式回滚；</li>
 *   <li>消费：村民买走的货真销毁（反刷钱核心）；</li>
 *   <li>好感自然衰减：每日向 0 回归 1 点；</li>
 *   <li>全球联动：本日净采购量回写 {@code MarketPrice} 动量。</li>
 * </ol>
 *
 * <p>金额全部 long；乘法统一用 {@link MathUtil#multiplyExactOrNegative1}，
 * 结果 ≤ 0 直接跳过该笔（防溢出印钱）。
 */
public final class VillageService {
    private VillageService() { }

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 回滚补偿连续失败熔断阈值。单线程模型下补偿失败理论上不可达，
     * 达到阈值说明账本已出现不一致，需要人工介入。
     */
    private static final int COMPENSATION_FAILURE_TRIP = 3;
    /** 摊位 id → 连续回滚补偿失败次数（整笔成功一次即清零）。 */
    private static final Map<UUID, Integer> STALL_COMP_FAILURES = new HashMap<>();
    /** 当日熔断暂停结算的摊位 id（processDay 开始时清空，次日自动重试）。 */
    private static final Set<UUID> SUSPENDED_STALLS = new HashSet<>();
    /** 聚落 id → 行商回滚补偿连续失败次数（整笔成功一次即清零）。 */
    private static final Map<UUID, Integer> CARAVAN_COMP_FAILURES = new HashMap<>();
    /** 当日熔断暂停行商采购的聚落 id（processDay 开始时清空）。 */
    private static final Set<UUID> SUSPENDED_CARAVAN = new HashSet<>();

    /** 行商连续零成交触发撤退的天数。 */
    private static final int CARAVAN_RETREAT_TRIGGER_DAYS = 3;
    /** 行商撤退时长（天）。 */
    private static final int CARAVAN_RETREAT_DAYS = 7;
    /** 宰客记过：单次好感惩罚。 */
    private static final int GOUGE_FAVOR_PENALTY = -10;
    /** 公允交易：单次好感奖励。 */
    private static final int FAIR_FAVOR_REWARD = 2;
    /** 公允奖励每日每村庄每玩家上限。 */
    private static final int FAIR_FAVOR_DAILY_CAP = 6;
    /** 倾销奖励：单次好感奖励。 */
    private static final int DUMP_FAVOR_REWARD = 1;
    /** 倾销奖励每日每村庄每玩家上限。 */
    private static final int DUMP_FAVOR_DAILY_CAP = 3;

    /**
     * 每日集市结算，由 {@code EconomyCycleService.tickDay} 驱动。
     *
     * <p>注意：本方法<strong>仅由服务端主线程调用</strong>（tickDay 同一调用链），
     * 全库采用单线程模型——除本方法外没有其他入口加锁，不要给它加
     * {@code synchronized} 制造虚假的线程安全承诺。
     *
     * @param server 服务器（保留参数，供将来扩展；当前未使用）
     * @param day    MC 天（与 {@code lastMarketDay} 同一时间轴）
     */
    /** 世界切换时清空熔断计数与暂停集合，避免旧世界状态带入新世界。 */
    public static void clearRuntimeDirect() {
        STALL_COMP_FAILURES.clear();
        SUSPENDED_STALLS.clear();
        CARAVAN_COMP_FAILURES.clear();
        SUSPENDED_CARAVAN.clear();
    }

    public static void processDay(MinecraftServer server, long day) {
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return;
        }
        // 新的一天：熔断暂停名单清空，允许重试（连续失败计数保留，仍连续则会再次熔断）
        SUSPENDED_STALLS.clear();
        SUSPENDED_CARAVAN.clear();
        // 本日各商品净采购量（村民从摊位+行商买走的总量），用于全球联动
        Map<String, Long> netBuy = new HashMap<>();
        for (SettlementRecord settlement : SettlementManager.settlements().values()) {
            SettlementStatus status = settlement.status();
            if (status != SettlementStatus.ACTIVE && status != SettlementStatus.REBUILDING) {
                continue;
            }
            VillageRecord village = VillageManager.getOrCreate(settlement.id());
            if (village == null) {
                continue;
            }
            if (!village.markMarketDay(day)) {
                continue; // 幂等：本日已结算
            }
            processVillage(village, day, netBuy);
            EconomySavedData.markDirty();
        }
        // 全球联动：只产生动量，不碰 NPC 中央库存。村民买货是需求，相当于国际市场卖出（npcWasBuyer=false，利多）
        for (Map.Entry<String, Long> e : netBuy.entrySet()) {
            long total = e.getValue();
            if (total <= 0) {
                continue;
            }
            MarketPrice mp = NpcMarketMaker.getMarketPrice(e.getKey());
            if (mp == null) {
                continue;
            }
            int qty = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            int npcStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, e.getKey());
            mp.onNpcTrade(npcStock, false, qty);
        }
    }

    // ================================================================
    // 单村庄一日流程
    // ================================================================

    private static void processVillage(VillageRecord village, long day, Map<String, Long> netBuy) {
        UUID settlementId = village.settlementId();
        topUpBudget(village);

        // 宏观经济启用时按需求类别和替代品分摊，否则用旧的职业篮子
        Map<String, Long> planned = finance.macro.MacroEconomyService.planNeeds(village);
        Map<String, Long> needs = planned != null ? planned : computeNeeds(village);
        Map<UUID, Integer> fairFavorGiven = new HashMap<>();
        Map<UUID, Integer> dumpFavorGiven = new HashMap<>();
        long caravanSoldToday = 0;
        boolean caravanPresent = day >= village.caravanRetreatUntilDay();
        for (Map.Entry<String, Long> e : needs.entrySet()) {
            String commodity = e.getKey();
            long need = e.getValue();
            if (need <= 0) {
                continue;
            }
            caravanSoldToday += procureCommodity(village, settlementId, commodity, need,
                    caravanPresent, netBuy, fairFavorGiven, dumpFavorGiven);
        }

        // 行商进退：只有行商在场时的零成交才计入连续天数；
        // 撤退期间不计数（否则撤退会自我延长成永久放逐）。
        if (caravanPresent) {
            if (caravanSoldToday > 0) {
                village.setCaravanZeroSaleDays(0);
            } else {
                int zero = village.caravanZeroSaleDays() + 1;
                if (zero >= CARAVAN_RETREAT_TRIGGER_DAYS) {
                    village.setCaravanRetreatUntilDay(day + CARAVAN_RETREAT_DAYS);
                    village.setCaravanZeroSaleDays(0);
                } else {
                    village.setCaravanZeroSaleDays(zero);
                }
            }
        }

        // 好感自然衰减：每日向 0 回归 1 点
        for (UUID playerId : village.favor().keySet()) {
            int f = village.favorOf(playerId);
            if (f > 0) {
                village.addFavor(playerId, -1);
            } else if (f < 0) {
                village.addFavor(playerId, 1);
            }
        }
    }

    // ================================================================
    // 预算补足：有界水龙头，全服每日最大流出 = Σ村庄人口 × 人均日预算
    // ================================================================

    private static void topUpBudget(VillageRecord village) {
        UUID budget = village.budgetAccountId();
        if (budget == null) {
            return;
        }
        int population = Math.max(0, village.population());
        long perCapita = FinanceConfig.villagePerCapitaBudget();
        int reserveDays = Math.max(0, FinanceConfig.villageBudgetReserveDays());
        long daily = MathUtil.multiplyExactOrNegative1(perCapita, population);
        long cap = MathUtil.multiplyExactOrNegative1(daily, reserveDays);
        if (cap <= 0) {
            return;
        }
        long shortfall = cap - AccountManager.getBalance(budget);
        if (shortfall <= 0) {
            return;
        }
        long npcBalance = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        long amount = Math.min(shortfall, npcBalance);
        if (amount <= 0) {
            return;
        }
        if (!AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, budget, amount)) {
            return;
        }
        AccountManager.addTransactionRecord(new TransactionRecord(
                NpcMarketMaker.NPC_UUID, budget, amount,
                TransactionType.VILLAGE_BUDGET_TOPUP, NpcMarketMaker.NPC_UUID, "", 0));
    }

    // ================================================================
    // 采购：玩家摊位 → NPC 行商
    // ================================================================

    /**
     * 对一种商品执行村民采购，返回本商品行商成交量。
     */
    private static long procureCommodity(VillageRecord village, UUID settlementId, String commodity,
                                         long need, boolean caravanPresent,
                                         Map<String, Long> netBuy,
                                         Map<UUID, Integer> fairFavorGiven,
                                         Map<UUID, Integer> dumpFavorGiven) {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodity);
        Commodity def = CommodityRegistry.getCommodity(commodity);
        if (mp == null || def == null) {
            return 0;
        }
        long mid = mp.getMidPrice();
        long base = def.getBasePrice();

        // 库存覆盖率 → 溢价（直接计算目标值，钳制 [minBps, maxBps]；不做跨日平滑）
        int villageStock = Math.max(0, CommodityInventoryManager.getCommodityAmount(settlementId, commodity));
        double coverage = (double) villageStock / Math.max(1L, need);
        int premium = finance.macro.MacroEconomyService.premiumBps(settlementId, commodity,
                premiumForCoverage(coverage));
        long wtp = computeWtp(mid, base, premium);
        if (wtp <= 0) {
            return 0;
        }

        long caravanBasePrice = caravanPriceOf(mp, premium);
        long caravanPrice = caravanBasePrice;
        boolean caravanAvailable = caravanPresent && caravanPrice > 0
                && !SUSPENDED_CARAVAN.contains(settlementId);

        // 候选摊位：抵制（favor ≤ boycott）直接排除
        int boycottFavor = FinanceConfig.villageBoycottFavor();
        int trustFavor = FinanceConfig.villageTrustFavor();
        List<VillageStall> stalls = new ArrayList<>();
        Collection<VillageStall> all = VillageManager.stallsForSettlement(settlementId);
        if (all != null) {
            for (VillageStall s : all) {
                if (s == null || !commodity.equals(s.commodityId())) {
                    continue;
                }
                if (s.ownerId() == null || s.stock() <= 0 || s.unitPrice() <= 0) {
                    continue;
                }
                if (village.favorOf(s.ownerId()) <= boycottFavor) {
                    continue; // 抵制：村民拒绝该玩家所有摊位
                }
                stalls.add(s);
            }
        }
        // 排序：信赖玩家置顶 → 单价升序 → 同价好感高者先
        stalls.sort((a, b) -> {
            boolean ta = village.favorOf(a.ownerId()) >= trustFavor;
            boolean tb = village.favorOf(b.ownerId()) >= trustFavor;
            if (ta != tb) {
                return ta ? -1 : 1;
            }
            int cmp = Long.compare(a.unitPrice(), b.unitPrice());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(village.favorOf(b.ownerId()), village.favorOf(a.ownerId()));
        });

        long stallSupply = 0;
        for (VillageStall s : stalls) stallSupply += Math.max(0, s.stock());
        long caravanSupply = caravanAvailable ? Math.min(need * 12 / 10, (long) Math.max(0,
                CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodity))) : 0;
        long supplySeen = stallSupply + villageStock + caravanSupply;

        long fairLine = multiplyDivide(wtp, FinanceConfig.villageFairToleranceBps());
        long gougeLine = multiplyDivide(wtp, FinanceConfig.villageGougeToleranceBps());

        long remaining = need;
        for (VillageStall stall : stalls) {
            if (remaining <= 0) {
                break;
            }
            if (SUSPENDED_STALLS.contains(stall.id())) {
                continue; // 当日熔断：该摊位暂停结算，等管理员排查
            }
            long price = stall.unitPrice();
            if (price > fairLine) {
                // 高于公允线：本轮跳过；高于宰客线才记过（-10）
                if (price > gougeLine) {
                    village.addFavor(stall.ownerId(), GOUGE_FAVOR_PENALTY);
                }
                continue;
            }
            long qtyL = Math.min(remaining, (long) Math.max(0, stall.stock()));
            int qty = qtyL > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) qtyL;
            if (qty <= 0) {
                continue;
            }
            long payment = MathUtil.multiplyExactOrNegative1(price, qty);
            if (payment <= 0) {
                continue;
            }
            if (!buyFromStall(village, settlementId, stall, commodity, qty, payment)) {
                continue;
            }
            remaining -= qty;
            netBuy.merge(commodity, (long) qty, Long::sum);
            finance.macro.EconomyAchievements.recordStallSale(stall.ownerId(), settlementId, commodity, qty);
            AccountManager.addTransactionRecord(new TransactionRecord(
                    village.budgetAccountId(), stall.ownerId(), payment,
                    TransactionType.VILLAGE_STALL_SALE, stall.ownerId(), commodity, qty));
            // 公允交易：favor +2（每日每村庄每玩家上限 +6）
            int given = fairFavorGiven.getOrDefault(stall.ownerId(), 0);
            if (given < FAIR_FAVOR_DAILY_CAP) {
                village.addFavor(stall.ownerId(), FAIR_FAVOR_REWARD);
                fairFavorGiven.put(stall.ownerId(), given + FAIR_FAVOR_REWARD);
            }
            // 倾销：单价 < 行商价×0.8 且抢到单，favor +1（每日上限 +3）
            if (caravanAvailable && isDumpPrice(price, caravanPrice)) {
                int dg = dumpFavorGiven.getOrDefault(stall.ownerId(), 0);
                if (dg < DUMP_FAVOR_DAILY_CAP) {
                    village.addFavor(stall.ownerId(), DUMP_FAVOR_REWARD);
                    dumpFavorGiven.put(stall.ownerId(), dg + DUMP_FAVOR_REWARD);
                }
            }
        }

        // NPC 行商：供货上限 = 日需求 × 1.2，库存不足按实际
        long caravanSold = 0;
        if (remaining > 0 && caravanAvailable) {
            long capL = need * 12 / 10;
            int npcStock = Math.max(0,
                    CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodity));
            long qtyL = Math.min(remaining, Math.min(capL, (long) npcStock));
            int qty = qtyL > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) qtyL;
            if (qty > 0) {
                long payment = MathUtil.multiplyExactOrNegative1(caravanPrice, qty);
                if (payment > 0 && buyFromCaravan(village, settlementId, commodity, qty, payment)) {
                    remaining -= qty;
                    caravanSold += qty;
                    netBuy.merge(commodity, (long) qty, Long::sum);
                    AccountManager.addTransactionRecord(new TransactionRecord(
                            village.budgetAccountId(), NpcMarketMaker.NPC_UUID, payment,
                            TransactionType.VILLAGE_CARAVAN_SALE, NpcMarketMaker.NPC_UUID, commodity, qty));
                }
            }
        }

        // 消费：真销毁，不是转移（反刷钱核心）
        int stockNow = Math.max(0, CommodityInventoryManager.getCommodityAmount(settlementId, commodity));
        long consumedL = Math.min(need, (long) stockNow);
        int consumed = consumedL > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) consumedL;
        if (consumed > 0) {
            CommodityInventoryManager.removeCommodity(settlementId, commodity, consumed);
        }
        finance.macro.MacroEconomyService.recordProcurement(settlementId, commodity, need, supplySeen, consumed);
        return caravanSold;
    }

    /**
     * 从玩家摊位购买。三段式（参考 {@code SettlementService.deliver}），任一步失败回滚：
     * 摊位扣库存 → 村庄收货 → 预算付款。回滚补偿本身的结果必须检查：
     * 补偿失败记 error 日志，同一摊位连续失败达到阈值则熔断（当日暂停该摊位结算并告警）。
     */
    private static boolean buyFromStall(VillageRecord village, UUID settlementId,
                                        VillageStall stall, String commodity, int qty, long payment) {
        UUID budget = village.budgetAccountId();
        if (budget == null || qty <= 0 || payment <= 0) {
            return false;
        }
        if (!stall.takeStock(qty)) {
            return false;
        }
        if (!CommodityInventoryManager.addCommodity(settlementId, commodity, qty)) {
            if (!addStockQuiet(stall, qty)
                    && onCompensationFailure(STALL_COMP_FAILURES, stall.id(), "buyFromStall/stock")) {
                SUSPENDED_STALLS.add(stall.id());
            }
            return false;
        }
        if (!AccountManager.moveFunds(budget, stall.ownerId(), payment)) {
            boolean invOk = CommodityInventoryManager.removeCommodity(settlementId, commodity, qty);
            boolean stockOk = addStockQuiet(stall, qty);
            if ((!invOk || !stockOk)
                    && onCompensationFailure(STALL_COMP_FAILURES, stall.id(), "buyFromStall/refund")) {
                SUSPENDED_STALLS.add(stall.id());
            }
            return false;
        }
        STALL_COMP_FAILURES.remove(stall.id()); // 整笔成功：连续失败计数清零
        return true;
    }

    /**
     * 从 NPC 行商购买。三段式，任一步失败回滚：
     * NPC 扣库存 → 预算付款 → 村庄收货。行商永不印货。
     * 回滚补偿失败记 error 日志，同一聚落连续失败达到阈值则熔断（当日暂停该聚落行商采购并告警）。
     */
    private static boolean buyFromCaravan(VillageRecord village, UUID settlementId,
                                          String commodity, int qty, long payment) {
        UUID budget = village.budgetAccountId();
        UUID npc = NpcMarketMaker.NPC_UUID;
        if (budget == null || qty <= 0 || payment <= 0) {
            return false;
        }
        if (!CommodityInventoryManager.removeCommodity(npc, commodity, qty)) {
            return false;
        }
        if (!AccountManager.moveFunds(budget, npc, payment)) {
            if (!CommodityInventoryManager.addCommodity(npc, commodity, qty)
                    && onCompensationFailure(CARAVAN_COMP_FAILURES, settlementId, "buyFromCaravan/stock")) {
                SUSPENDED_CARAVAN.add(settlementId);
            }
            return false;
        }
        if (!CommodityInventoryManager.addCommodity(settlementId, commodity, qty)) {
            boolean fundsOk = AccountManager.moveFunds(npc, budget, payment);
            boolean stockOk = CommodityInventoryManager.addCommodity(npc, commodity, qty);
            if ((!fundsOk || !stockOk)
                    && onCompensationFailure(CARAVAN_COMP_FAILURES, settlementId, "buyFromCaravan/refund")) {
                SUSPENDED_CARAVAN.add(settlementId);
            }
            return false;
        }
        CARAVAN_COMP_FAILURES.remove(settlementId); // 整笔成功：连续失败计数清零
        return true;
    }

    /** 摊位库存补偿：addStock 抛 IllegalArgumentException（溢出）即视为补偿失败。 */
    private static boolean addStockQuiet(VillageStall stall, int qty) {
        try {
            stall.addStock(qty);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 记录一次回滚补偿失败：打 error 日志；同一键连续失败达到
     * {@code COMPENSATION_FAILURE_TRIP} 时返回 true（调用方熔断并告警）。
     */
    private static boolean onCompensationFailure(Map<UUID, Integer> counters, UUID key, String stage) {
        int n = counters.getOrDefault(key, 0) + 1;
        counters.put(key, n);
        LOGGER.error("[village] rollback compensation FAILED at {} (key={}, consecutive={})",
                stage, key, n);
        if (n >= COMPENSATION_FAILURE_TRIP) {
            LOGGER.error("[village] CIRCUIT BREAKER tripped at {}: suspended for today, "
                    + "admin check required (key={})", stage, key);
            return true;
        }
        return false;
    }

    // ================================================================
    // 供测试的包可见方法
    // ================================================================

    /**
     * 合并所有职业篮子，计算本日各商品需求量（向下取整）。
     *
     * @return 商品 id → 需求单位数（只含需求 &gt; 0 的商品）
     */
    static Map<String, Long> computeNeeds(VillageRecord v) {
        Map<String, Double> totals = new HashMap<>();
        if (v != null && v.professionMix() != null) {
            for (Map.Entry<VillageProfession, Integer> e : v.professionMix().entrySet()) {
                VillageProfession prof = e.getKey();
                int count = e.getValue() == null ? 0 : e.getValue();
                if (prof == null || count <= 0) {
                    continue;
                }
                Map<String, Double> basket = prof.needsBasket();
                if (basket == null) {
                    continue;
                }
                for (Map.Entry<String, Double> b : basket.entrySet()) {
                    if (b.getKey() == null || b.getValue() == null || b.getValue() <= 0) {
                        continue;
                    }
                    totals.merge(b.getKey(), count * b.getValue(), Double::sum);
                }
            }
        }
        Map<String, Long> needs = new HashMap<>();
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            long need = (long) Math.floor(e.getValue());
            if (need > 0) {
                needs.put(e.getKey(), need);
            }
        }
        return needs;
    }

    /**
     * 村民愿意支付价：{@code clamp(mid×premium/10000, max(1, base×0.35), base×2.6)}。
     *
     * @return 钳制后的 WTP（≥ 1）；输入非法或溢出时返回 0（调用方跳过）
     */
    static long computeWtp(long midPrice, long basePrice, int premiumBps) {
        if (midPrice <= 0 || basePrice <= 0 || premiumBps <= 0) {
            return 0;
        }
        long scaled = MathUtil.multiplyExactOrNegative1(midPrice, premiumBps);
        if (scaled <= 0) {
            return 0;
        }
        long raw = scaled / 10_000;
        long floorRaw = MathUtil.multiplyExactOrNegative1(basePrice, 35);
        long floor = Math.max(1L, floorRaw <= 0 ? 1L : floorRaw / 100);
        long ceilRaw = MathUtil.multiplyExactOrNegative1(basePrice, 26);
        long ceil = ceilRaw <= 0 ? Long.MAX_VALUE : ceilRaw / 10;
        return Math.min(Math.max(raw, floor), ceil);
    }

    /**
     * 库存覆盖率 → 溢价（基点）：覆盖率 &lt; 0.5 取上限（缺货越贵），
     * &gt; 2.0 取下限（囤积越便宜），之间线性插值。
     *
     * <p>直接计算目标值并钳制到
     * [{@code villagePremiumMinBps}, {@code villagePremiumMaxBps}]，不做跨日平滑。
     */
    static int premiumForCoverage(double coverage) {
        int min = FinanceConfig.villagePremiumMinBps();
        int max = FinanceConfig.villagePremiumMaxBps();
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        if (!(coverage >= 0.5)) {
            return max; // 覆盖率 < 0.5（含 NaN/负数）：缺货，取上限
        }
        if (coverage > 2.0) {
            return min; // 囤积，取下限
        }
        double t = (coverage - 0.5) / 1.5;
        return (int) Math.round(max - t * (max - min));
    }

    // ================================================================
    // 内部小工具
    // ================================================================

    /** 行商价 = ask×premium/10000；非法或溢出返回 0。 */
    private static long caravanPriceOf(MarketPrice mp, int premiumBps) {
        long ask = mp.getAskPrice();
        if (ask <= 0 || premiumBps <= 0) {
            return 0;
        }
        long scaled = MathUtil.multiplyExactOrNegative1(ask, premiumBps);
        if (scaled <= 0) {
            return 0;
        }
        return scaled / 10_000;
    }

    /**
     * {@code value×bps/10000}，溢出时返回 0（fail-closed：调用方跳过本次计算，
     * 与文件内 multiplyExactOrNegative1 的 fail-closed 惯例一致）。
     */
    private static long multiplyDivide(long value, long bps) {
        if (value <= 0 || bps <= 0) {
            return 0;
        }
        int bpsInt = bps > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bps;
        long scaled = MathUtil.multiplyExactOrNegative1(value, bpsInt);
        if (scaled <= 0) {
            return 0;
        }
        return scaled / 10_000;
    }

    /** 倾销判定：单价 &lt; 行商价×0.8 ⟺ 单价×10 &lt; 行商价×8。 */
    private static boolean isDumpPrice(long price, long caravanPrice) {
        long lhs = MathUtil.multiplyExactOrNegative1(price, 10);
        long rhs = MathUtil.multiplyExactOrNegative1(caravanPrice, 8);
        return lhs > 0 && rhs > 0 && lhs < rhs;
    }

    // ================================================================
    // 摊位管理（供 VillageActionPacket 调用）
    // ================================================================

    /**
     * 登记摊位：每玩家每聚落每种商品限 1 个摊位。
     *
     * @return 摊位（已存在/非法返回 null）
     */
    public static VillageStall registerStall(ServerPlayer player, UUID settlementId,
                                             String commodityId, long price) {
        if (player == null || settlementId == null || commodityId == null
                || commodityId.isBlank() || price < 1) {
            return null;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return null;
        }
        if (SettlementManager.get(settlementId) == null) {
            return null;
        }
        if (CommodityRegistry.getCommodity(commodityId) == null) {
            return null;
        }
        // 摊位商品必须有 MC 实物映射（与需求篮子一致，纯虚拟商品不能上架）
        if (!CommodityItemResolver.resolve(commodityId).valid()) {
            return null;
        }
        final VillageStall stall;
        try {
            VillageManager.getOrCreate(settlementId);
            stall = VillageManager.registerStall(settlementId, player.getUUID(), commodityId, price);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // IllegalStateException：摊位/村庄数达上限（VillageManager 抛），同样走 register_failed 反馈，
            // 不能让异常逃到 Forge 网络线程
            return null;
        }
        if (stall != null) {
            EconomySavedData.markDirty();
        }
        return stall;
    }

    /**
     * 上架：从玩家背包扣除实物 → 摊位库存。没货不能卖（反刷钱核心）。
     */
    public static boolean stockStall(ServerPlayer player, UUID stallId, int quantity) {
        if (player == null || stallId == null || quantity <= 0) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        CommodityItemResolver.Resolution resolution =
                CommodityItemResolver.resolve(stall.commodityId());
        if (!resolution.valid()) {
            return false;
        }
        InventoryTransactionService.RemovalPlan plan = InventoryTransactionService.planRemoval(
                player.getInventory(), resolution.item(), quantity);
        if (plan == null) {
            return false;
        }
        if (!InventoryTransactionService.commitRemoval(player.getInventory(), plan)) {
            return false;
        }
        try {
            stall.addStock(quantity);
        } catch (IllegalArgumentException e) {
            InventoryTransactionService.rollbackRemoval(player, plan);
            return false;
        }
        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 改价：仅摊主，单价 ≥ 1。
     */
    public static boolean setStallPrice(ServerPlayer player, UUID stallId, long price) {
        if (player == null || stallId == null || price < 1) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        try {
            stall.setUnitPrice(price);
        } catch (IllegalArgumentException e) {
            return false;
        }
        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 下架：扣摊位库存 → 返还玩家背包实物；背包放不下则掉落脚下（自己的货，无刷钱风险）。
     */
    public static boolean withdrawStall(ServerPlayer player, UUID stallId, int quantity) {
        if (player == null || stallId == null || quantity <= 0) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        CommodityItemResolver.Resolution resolution =
                CommodityItemResolver.resolve(stall.commodityId());
        if (!resolution.valid()) {
            return false;
        }
        if (!stall.takeStock(quantity)) {
            return false;
        }
        int remaining = quantity;
        InventoryTransactionService.InsertionPlan plan = InventoryTransactionService.planInsertion(
                player.getInventory(), resolution.item(), quantity);
        if (plan != null && InventoryTransactionService.commitInsertion(player.getInventory(), plan)) {
            remaining = 0;
        }
        while (remaining > 0) {
            int n = Math.min(remaining, resolution.item().getMaxStackSize());
            player.drop(new ItemStack(resolution.item(), n), false);
            remaining -= n;
        }
        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 关店：仅摊主；有库存先自动下架再关闭。
     */
    public static boolean closeStall(ServerPlayer player, UUID stallId) {
        if (player == null || stallId == null) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        int stock = stall.stock();
        if (stock > 0 && !withdrawStall(player, stallId, stock)) {
            return false;
        }
        boolean ok = VillageManager.removeStall(stallId);
        if (ok) {
            EconomySavedData.markDirty();
        }
        return ok;
    }
}
