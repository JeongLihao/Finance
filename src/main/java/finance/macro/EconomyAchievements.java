package finance.macro;

import finance.advancement.FinanceAdvancementTriggers;
import finance.caravan.CaravanManager;
import finance.caravan.CaravanRoute;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.util.Uuids;
import finance.village.VillageManager;
import finance.village.VillageRecord;
import finance.village.VillageStall;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 经济成就：把宏观经济里本来就在发生的事变成原版进度（{@code finance:progress} 触发器）。
 *
 * <p>判定只读经济状态，不改任何数值。大多数条件在日结末尾统一检查（{@link #processDay}），
 * 少数瞬间事件（商队救灾、摊位成交、央行议息、提前支取）由对应服务直接通知。
 * 达成时玩家不在线就先记进待发队列（随存档保存），上线时补发。</p>
 */
public final class EconomyAchievements {
    public static final String FIRST_HIRE = "macro_first_hire";
    public static final String GOOD_BOSS = "macro_good_boss";
    public static final String SWEATSHOP = "macro_sweatshop";
    public static final String BOOMTOWN = "macro_boomtown";
    public static final String GOLDEN_AGE = "macro_golden_age";
    public static final String FAMINE_RELIEF = "macro_famine_relief";
    public static final String MONOPOLY = "macro_monopoly";
    public static final String HOARDER = "macro_hoarder";
    public static final String CARAVAN_TYCOON = "macro_caravan_tycoon";
    /** 央行降息前的前瞻窗口里认购央行债券或开定存，锁住了更高的利率。 */
    public static final String RATE_CALL = "cb_rate_call";
    /** 央行下调存款准备金率时你拥有一家银行。 */
    public static final String RESERVE_CUT = "cb_reserve_cut";
    /** 通胀连续 30 天落在央行目标区间内（全服在线玩家）。 */
    public static final String PRICE_STABILITY = "cb_price_stability";
    /** 隐藏：通胀高出央行目标 5 个百分点以上（全服在线玩家）。 */
    public static final String RUNAWAY_INFLATION = "cb_runaway_inflation";
    /** 隐藏：提前支取定期存款或提前赎回央行债券，利息全部放弃。 */
    public static final String EARLY_EXIT = "cb_early_exit";
    /** 第一次有工厂进入工业档。 */
    public static final String INDUSTRIAL_REVOLUTION = "macro_industrial_revolution";
    /** 同一家公司至少三座工厂都坚持手工档，并且盈利。 */
    public static final String ARTISAN = "macro_artisan";
    /** 单人经济：自己提出的提案在 NPC 股东赞成下通过。 */
    public static final String SHAREHOLDER_DEMOCRACY = "solo_shareholder_democracy";

    /** 全部成就 id（顺序即展示顺序）。 */
    public static final java.util.List<String> ALL = java.util.List.of(
            FIRST_HIRE,
            GOOD_BOSS,
            SWEATSHOP,
            BOOMTOWN,
            GOLDEN_AGE,
            FAMINE_RELIEF,
            MONOPOLY,
            HOARDER,
            CARAVAN_TYCOON,
            RATE_CALL,
            RESERVE_CUT,
            PRICE_STABILITY,
            RUNAWAY_INFLATION,
            EARLY_EXIT,
            INDUSTRIAL_REVOLUTION,
            ARTISAN,
            SHAREHOLDER_DEMOCRACY);

    // ---- 阈值 ----
    static final int GOOD_BOSS_WORKERS = 9;
    static final int SWEATSHOP_WORKERS = 6;
    static final int BOOMTOWN_POPULATION = 100;
    static final int GOLDEN_AGE_SOL = 8_000;
    static final double FAMINE_FOOD_FULFILMENT = 0.5;
    static final int MONOPOLY_SHARE_BPS = 7_000;
    static final long MONOPOLY_MIN_UNITS = 20;
    static final int HOARDER_FACTOR_BPS = 16_000;
    static final long CARAVAN_TYCOON_PROFIT = 10_000;

    public static final int MAX_PENDING_PLAYERS = 1_024;

    private static final Map<UUID, LinkedHashSet<String>> PENDING = new LinkedHashMap<>();
    /** 当天各玩家摊位在各聚落各商品上的成交量：settlement|commodity → 玩家 → 数量。不落盘。 */
    private static final Map<String, Map<UUID, Long>> STALL_SALES = new HashMap<>();

    private static MinecraftServer server;
    private static long lastProcessedDay = Long.MIN_VALUE;

    private EconomyAchievements() {}

    // ================================================================ 发放

    /** 给某玩家发成就；不在线时进待发队列。 */
    public static void award(UUID player, String event) {
        if (player == null || !ALL.contains(event)) return;
        MinecraftServer current =
                server != null ? server : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer online = current == null ? null : current.getPlayerList().getPlayer(player);
        if (online != null) {
            FinanceAdvancementTriggers.trigger(online, event);
            return;
        }
        LinkedHashSet<String> queue = PENDING.get(player);
        if (queue == null) {
            if (PENDING.size() >= MAX_PENDING_PLAYERS) return;
            queue = new LinkedHashSet<>();
            PENDING.put(player, queue);
        }
        queue.add(event);
    }

    /** 发给当前所有在线玩家（全服性的成就，如物价稳定）。拿不到服务器时不发。 */
    public static void awardOnline(String event) {
        if (!ALL.contains(event)) return;
        MinecraftServer current =
                server != null ? server : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (current == null) return;
        for (ServerPlayer p : current.getPlayerList().getPlayers()) FinanceAdvancementTriggers.trigger(p, event);
    }

    /** 补发给当前在线、但之前因为拿不到服务器引用而进了队列的玩家。 */
    private static void flushOnline() {
        if (server == null || PENDING.isEmpty()) return;
        for (UUID id : java.util.List.copyOf(PENDING.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) deliverPending(p);
        }
    }

    /** 玩家上线时补发待发成就。 */
    public static void deliverPending(ServerPlayer player) {
        if (player == null) return;
        LinkedHashSet<String> queue = PENDING.remove(player.getUUID());
        if (queue == null) return;
        for (String event : queue) FinanceAdvancementTriggers.trigger(player, event);
    }

    public static Set<String> pending(UUID player) {
        Set<String> q = player == null ? null : PENDING.get(player);
        return q == null ? Set.of() : Collections.unmodifiableSet(q);
    }

    public static Map<UUID, Set<String>> pendingDirect() {
        Map<UUID, Set<String>> out = new LinkedHashMap<>();
        PENDING.forEach((k, v) -> out.put(k, Set.copyOf(v)));
        return out;
    }

    // ================================================================ 瞬间事件

    /** 村民从玩家摊位买货（VillageService 调用）。 */
    public static void recordStallSale(UUID owner, UUID settlementId, String commodityId, long qty) {
        if (owner == null || settlementId == null || commodityId == null || qty <= 0) return;
        STALL_SALES
                .computeIfAbsent(settlementId + "|" + commodityId, k -> new HashMap<>())
                .merge(owner, qty, Long::sum);
    }

    /** 玩家商队把货送到聚落（CaravanService 调用）：送粮到饥荒中的聚落即“饥荒救援”。 */
    public static void onCaravanDelivered(UUID owner, UUID settlementId, String commodityId) {
        if (owner == null || NeedCategory.FOOD.unitsOf(commodityId) <= 0) return;
        SettlementEconomy econ = MacroEconomyManager.settlement(settlementId);
        if (econ == null) return;
        Double food = econ.lastFulfilment.get(NeedCategory.FOOD);
        if (food != null && food < FAMINE_FOOD_FULFILMENT) award(owner, FAMINE_RELIEF);
    }

    // ================================================================ 日结检查

    /** 每天日结末尾调用（在商队结算之后）。 */
    public static void processDay(MinecraftServer currentServer, long day) {
        server = currentServer;
        if (!MacroEconomyService.active() || day <= lastProcessedDay) {
            STALL_SALES.clear();
            return;
        }
        lastProcessedDay = day;
        flushOnline();
        checkEmployers();
        checkMarkets();
        checkCaravans();
        checkFactories();
        STALL_SALES.clear();
    }

    static final int ARTISAN_FACTORIES = 3;

    /** 机器成就：工业革命（有机器升到 3 档）、手艺人（三台以上机器都还是 1 档且公司盈利）。 */
    private static void checkFactories() {
        Map<UUID, int[]> byCompany = new HashMap<>(); // [工厂数, 手工档数]
        for (CompanyFacilityRecord facility : CompanyFacilityManager.all()) {
            if (facility.status() == finance.gameplay.company.CompanyFacilityStatus.DISABLED
                    || facility.status() == finance.gameplay.company.CompanyFacilityStatus.ORPHANED) continue;
            Company company = CompanyManager.getCompany(facility.companyId());
            if (company == null || company.getOwnerId() == null) continue;
            int tier = facility.productionLevel();
            if (tier >= CompanyFacilityRecord.MAX_LEVEL) award(company.getOwnerId(), INDUSTRIAL_REVOLUTION);
            int[] c = byCompany.computeIfAbsent(company.getCompanyId(), k -> new int[2]);
            c[0]++;
            if (tier == 1) c[1]++;
        }
        byCompany.forEach((companyId, c) -> {
            Company company = CompanyManager.getCompany(companyId);
            if (company != null && c[0] >= ARTISAN_FACTORIES && c[1] == c[0] && company.getSmoothedDailyProfit() > 0) {
                award(company.getOwnerId(), ARTISAN);
            }
        });
    }

    private static void checkEmployers() {
        long reservation = MacroLaborService.reservationWage();
        Map<UUID, Long> employedByCompany = new HashMap<>();
        Map<UUID, Set<UUID>> employerSettlements = new HashMap<>();
        for (CompanyFacilityRecord facility : CompanyFacilityManager.all()) {
            long n = MacroEconomyManager.employment(facility.facilityId());
            if (n <= 0) continue;
            employedByCompany.merge(facility.companyId(), n, Long::sum);
            UUID source = MacroEconomyManager.laborSource(facility.facilityId());
            Company company = CompanyManager.getCompany(facility.companyId());
            if (source != null && company != null && company.getOwnerId() != null) {
                employerSettlements
                        .computeIfAbsent(company.getOwnerId(), k -> new LinkedHashSet<>())
                        .add(source);
            }
        }
        for (Map.Entry<UUID, Long> e : employedByCompany.entrySet()) {
            Company company = CompanyManager.getCompany(e.getKey());
            if (company == null || company.getOwnerId() == null) continue;
            UUID owner = company.getOwnerId();
            long workers = e.getValue();
            long wage = MacroLaborService.wageOf(company.getCompanyId());
            award(owner, FIRST_HIRE);
            if (workers >= GOOD_BOSS_WORKERS && wage >= reservation * 2) award(owner, GOOD_BOSS);
            if (workers >= SWEATSHOP_WORKERS && wage <= reservation) award(owner, SWEATSHOP);
        }
        for (Map.Entry<UUID, Set<UUID>> e : employerSettlements.entrySet()) {
            for (UUID settlementId : e.getValue()) {
                VillageRecord village = VillageManager.get(settlementId);
                SettlementEconomy econ = MacroEconomyManager.settlement(settlementId);
                if (village != null && village.population() >= BOOMTOWN_POPULATION) award(e.getKey(), BOOMTOWN);
                if (econ != null && econ.solBps >= GOLDEN_AGE_SOL) award(e.getKey(), GOLDEN_AGE);
            }
        }
    }

    private static void checkMarkets() {
        for (Map.Entry<String, Map<UUID, Long>> e : STALL_SALES.entrySet()) {
            int sep = e.getKey().indexOf('|');
            SettlementEconomy econ =
                    MacroEconomyManager.settlement(Uuids.parseOrNull(e.getKey().substring(0, sep)));
            GoodBook book = econ == null ? null : econ.goods.get(e.getKey().substring(sep + 1));
            if (book == null || book.lastConsumed < MONOPOLY_MIN_UNITS) continue;
            for (Map.Entry<UUID, Long> sale : e.getValue().entrySet()) {
                if (sale.getValue() * 10_000 >= book.lastConsumed * MONOPOLY_SHARE_BPS) award(sale.getKey(), MONOPOLY);
            }
        }
        for (VillageStall stall : VillageManager.stalls()) {
            if (stall == null || stall.ownerId() == null || stall.stock() <= 0) continue;
            SettlementEconomy econ = MacroEconomyManager.settlement(stall.settlementId());
            if (econ != null && econ.factorBps(stall.commodityId()) >= HOARDER_FACTOR_BPS)
                award(stall.ownerId(), HOARDER);
        }
    }

    private static void checkCaravans() {
        Map<UUID, Long> profit = new HashMap<>();
        for (CaravanRoute route : CaravanManager.routes()) {
            if (route.ownerId() != null) profit.merge(route.ownerId(), route.totalProfit(), Long::sum);
        }
        profit.forEach((owner, p) -> {
            if (p >= CARAVAN_TYCOON_PROFIT) award(owner, CARAVAN_TYCOON);
        });
    }

    // ================================================================ 存档与隔离

    public static long lastProcessedDay() {
        return lastProcessedDay;
    }

    static void restore(long lastDay, Map<UUID, Set<String>> pending) {
        lastProcessedDay = lastDay;
        PENDING.clear();
        if (pending == null) return;
        for (Map.Entry<UUID, Set<String>> e : pending.entrySet()) {
            if (PENDING.size() >= MAX_PENDING_PLAYERS) break;
            LinkedHashSet<String> q = new LinkedHashSet<>();
            for (String s : e.getValue()) if (ALL.contains(s)) q.add(s);
            if (!q.isEmpty()) PENDING.put(e.getKey(), q);
        }
    }

    public static void clearDirect() {
        PENDING.clear();
        STALL_SALES.clear();
        server = null;
        lastProcessedDay = Long.MIN_VALUE;
    }
}
