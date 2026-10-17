package finance.rival;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyIPOService;
import finance.company.CompanyManager;
import finance.company.CompanyStrategy;
import finance.company.CompanyType;
import finance.config.FinanceConfig;
import finance.contract.ContractManager;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NPC 竞争公司（单人经济 A1）：新建、每日经营决策、上市。
 *
 * <p>不变量：种子资金只从 NPC 资金池转入（资金池扣除后不低于保留线，否则不建）；升级费付给 NPC 资金池；
 * 采购合同走现有 escrow；上市走 {@link CompanyIPOService#ipoOwnerless}，遵守玩家 IPO 的全部上限。
 * 同一天只执行一次；配置关闭时完全不动，已有公司照常存在。必须排在当天破产处理之后。</p>
 */
public final class RivalService {
    /** 新建门槛：产品卖价至少比基准价高 15%。 */
    static final int SPAWN_PREMIUM_BPS = 11_500;
    /** 两次升级之间至少隔 7 天。 */
    static final int UPGRADE_GAP_DAYS = 7;
    /** 原料低于 3 天用量就发采购合同，一次买 5 天用量，期限 5 天。 */
    static final int CONTRACT_LOW_DAYS = 3, CONTRACT_BUY_DAYS = 5, CONTRACT_DEADLINE_DAYS = 5;
    /** 上市门槛：成立满 30 天、现金至少 80,000、近期盈利。 */
    static final int IPO_MIN_AGE_DAYS = 30;
    static final long IPO_MIN_CASH = 80_000L;

    private RivalService() { }

    public record Settings(boolean enabled, int maxCompanies, int spawnCooldownDays, long seedCapital,
                           long poolReserveFloor, boolean ipoEnabled, boolean contractsEnabled) {
        public static Settings fromConfig() {
            return new Settings(FinanceConfig.rivalsEnabled(), FinanceConfig.rivalsMaxCompanies(),
                    FinanceConfig.rivalsSpawnCooldownDays(), FinanceConfig.rivalsSeedCapital(),
                    FinanceConfig.npcPoolReserveFloor(), FinanceConfig.rivalsIpoEnabled(),
                    FinanceConfig.rivalsContractsEnabled());
        }
    }

    public static void processDay(long day) {
        processDay(day, Settings.fromConfig());
    }

    public static synchronized void processDay(long day, Settings settings) {
        if (settings == null || !settings.enabled() || day < 0) return;
        if (day <= RivalManager.lastProcessedDay()) return;
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.RIVALS)) return;
        RivalManager.setLastProcessedDay(day);
        RivalManager.ensureProfiles(day);
        if (RivalManager.pruneRemoved() > 0) RivalManager.setLastBankruptcyDay(day);
        trySpawn(day, settings);
        List<RivalProfile> profiles = new ArrayList<>(RivalManager.all());
        profiles.sort(Comparator.comparing(p -> p.companyId().toString()));
        for (RivalProfile profile : profiles) {
            Company company = CompanyManager.getCompany(profile.companyId());
            if (company == null || company.isPlayerOwned() || company.isBankruptcyRisk()) continue;
            moveContractDeliveries(company);
            decideStrategy(company, profile, day);
            tryUpgrade(company, profile, day);
            if (settings.contractsEnabled()) tryProcure(company, profile, day);
            if (settings.ipoEnabled()) tryIpo(company, profile, day);
        }
        EconomySavedData.markDirty();
    }

    // ---------------------------------------------------------------- 新建

    /** 无主（NPC）公司数。 */
    static int npcCompanyCount() {
        int n = 0;
        for (Company c : CompanyManager.getCompanies()) if (!c.isPlayerOwned()) n++;
        return n;
    }

    static void trySpawn(long day, Settings s) {
        if (npcCompanyCount() >= s.maxCompanies() || s.seedCapital() <= 0) return;
        long since = Math.max(RivalManager.lastSpawnDay(), RivalManager.lastBankruptcyDay());
        if (since != Long.MIN_VALUE && day - since < s.spawnCooldownDays()) return;
        CompanyType type = mostShortType();
        if (type == null) return;
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        if (pool - s.seedCapital() < s.poolReserveFloor()) return;
        UUID id = UUID.nameUUIDFromBytes(("finance-rival-company-" + day + "-" + type.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (CompanyManager.getCompany(id) != null) return;
        Company company = new Company(id, RivalNames.pick(type, day), type, 0);
        if (!AccountManager.withdraw(NpcMarketMaker.NPC_UUID, s.seedCapital())) return;
        if (!company.deposit(s.seedCapital())) {
            AccountManager.deposit(NpcMarketMaker.NPC_UUID, s.seedCapital());
            return;
        }
        CompanyManager.register(company);
        String commodity = type.getCommodityIds().isEmpty() ? "" : type.getCommodityIds().get(0);
        RivalProfile profile = new RivalProfile(id, RivalPersonality.forCompany(id), commodity, day);
        if (!RivalManager.put(profile)) return;
        RivalManager.setLastSpawnDay(day);
        profile.record(day, "SPAWN", type.getDisplayName());
        AccountManager.addTransactionRecord(new TransactionRecord(NpcMarketMaker.NPC_UUID, id, s.seedCapital(),
                TransactionType.RIVAL_SEED, null, company.getName(), 0));
        news(40, "新竞争者入场：" + company.getName(),
                company.getName() + "（" + profile.personality().displayName() + "型）看中了紧缺的"
                        + commodityName(commodity) + "，开始和你抢生意。", false);
    }

    /** 产品卖价相对基准价溢价最高、且超过门槛的行业；没有则返回 null。 */
    static CompanyType mostShortType() {
        CompanyType best = null;
        long bestBps = SPAWN_PREMIUM_BPS - 1;
        for (CompanyType type : CompanyType.values()) {
            for (String commodity : type.getDailyProduction().keySet()) {
                long bps = premiumBps(commodity);
                if (bps > bestBps) {
                    bestBps = bps;
                    best = type;
                }
            }
        }
        return best;
    }

    /** 卖价 / 基准价（基点）；拿不到价格返回 0。 */
    static long premiumBps(String commodity) {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodity);
        Commodity c = CommodityRegistry.getCommodity(commodity);
        if (mp == null || c == null || c.getBasePrice() <= 0) return 0;
        return mp.getAskPrice() * 10_000 / c.getBasePrice();
    }

    // ---------------------------------------------------------------- 经营决策

    /** 人格 × 利润 × 产品价格 → 经营策略。 */
    static CompanyStrategy chooseStrategy(RivalPersonality personality, long smoothedProfit, long productPremiumBps) {
        return switch (personality) {
            case AGGRESSIVE -> smoothedProfit > 0 ? CompanyStrategy.EXPAND : CompanyStrategy.STABLE;
            case CONSERVATIVE -> smoothedProfit < 0 ? CompanyStrategy.COST_CONTROL : CompanyStrategy.STABLE;
            case OPPORTUNIST -> productPremiumBps >= 11_000 ? CompanyStrategy.FAST_CASH
                    : productPremiumBps > 0 && productPremiumBps <= 9_000 ? CompanyStrategy.HOLD_INVENTORY
                    : CompanyStrategy.STABLE;
        };
    }

    static void decideStrategy(Company company, RivalProfile profile, long day) {
        CompanyStrategy next = chooseStrategy(profile.personality(), company.getSmoothedDailyProfit(),
                premiumBps(profile.mainCommodity()));
        if (next == company.getStrategy()) return;
        company.setStrategy(next);
        profile.record(day, "STRATEGY", next.getDisplayName());
    }

    /** 现金达到“升级费 × 人格倍数”时升级产线（满级后升级仓储），升级费付给 NPC 资金池。 */
    static void tryUpgrade(Company company, RivalProfile profile, long day) {
        if (profile.lastUpgradeDay() != Long.MIN_VALUE && day - profile.lastUpgradeDay() < UPGRADE_GAP_DAYS) return;
        String type = company.getProductionLevel() < 5 ? "PRODUCTION" : company.getStorageLevel() < 5 ? "STORAGE" : null;
        if (type == null) return;
        long cost = company.getUpgradeCost(type);
        long needed = BigInteger.valueOf(cost).multiply(BigInteger.valueOf(profile.personality().upgradeCashMultiple()))
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        if (cost <= 0 || company.getCash() < needed) return;
        if (!AccountManager.canDeposit(NpcMarketMaker.NPC_UUID, cost) || !company.withdraw(cost)) return;
        boolean upgraded = "PRODUCTION".equals(type) ? company.upgradeProduction() : company.upgradeStorage();
        if (!upgraded) {
            company.deposit(cost);
            return;
        }
        // 升级是资本开支，不计入当天经营成本（否则当天会显示亏损，影响上市判断）
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, cost);
        profile.setLastUpgradeDay(day);
        profile.record(day, "UPGRADE", ("PRODUCTION".equals(type) ? "产线 " + company.getProductionLevel()
                : "仓储 " + company.getStorageLevel()) + " 级");
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(), NpcMarketMaker.NPC_UUID, cost,
                TransactionType.RIVAL_UPGRADE, null, company.getName(), 1));
    }

    /** 加工类公司原料低于 3 天用量时发采购合同（一次 5 天用量），和玩家争供应。 */
    static void tryProcure(Company company, RivalProfile profile, long day) {
        for (Map.Entry<String, Integer> input : company.getType().getDailyConsumption().entrySet()) {
            String commodity = input.getKey();
            int daily = input.getValue();
            if (daily <= 0 || ContractManager.hasLiveForIssuerCommodity(company.getCompanyId(), commodity)) continue;
            long have = company.getInventoryAmount(commodity)
                    + CommodityInventoryManager.getCommodityAmount(CompanyInventoryFacade.custodyId(company.getCompanyId()), commodity);
            if (have >= (long) daily * CONTRACT_LOW_DAYS) continue;
            MarketPrice mp = NpcMarketMaker.getMarketPrice(commodity);
            if (mp == null || mp.getAskPrice() <= 0) continue;
            int qty = daily * CONTRACT_BUY_DAYS;
            long reward = BigInteger.valueOf(mp.getAskPrice()).multiply(BigInteger.valueOf(qty))
                    .multiply(BigInteger.valueOf(profile.personality().contractPremiumBps())).divide(BigInteger.valueOf(10_000))
                    .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            // 保留三倍赏金的现金，避免为了抢原料把自己掏空
            if (reward <= 0 || company.getCash() < reward * 3) continue;
            if (ContractManager.createCompanyProcurement(company.getCompanyId(), commodity, qty, reward, day,
                    day + CONTRACT_DEADLINE_DAYS) != null) {
                profile.record(day, "CONTRACT", commodityName(commodity) + " ×" + qty + "，赏金 " + reward);
            }
        }
    }

    /**
     * 合同交付的货物进入公司托管；竞争公司走传统自动经营，只用 Company 库存，所以把托管里的原料转进库存。
     * 两边都归公司所有，商品守恒。
     */
    static void moveContractDeliveries(Company company) {
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        for (String commodity : company.getType().getDailyConsumption().keySet()) {
            int amount = CommodityInventoryManager.getCommodityAmount(custody, commodity);
            if (amount <= 0 || !company.canAddInventory(commodity, amount)) continue;
            if (CommodityInventoryManager.removeCommodity(custody, commodity, amount)) {
                if (!company.addInventory(commodity, amount)) CommodityInventoryManager.addCommodity(custody, commodity, amount);
            }
        }
    }

    /** 成立满 30 天、现金 ≥ 80,000、近期盈利时上市。发行价按估值折算，募资不超过玩家 IPO 上限。 */
    static void tryIpo(Company company, RivalProfile profile, long day) {
        if (company.isPublic() || day - profile.createdDay() < IPO_MIN_AGE_DAYS || company.getCash() < IPO_MIN_CASH
                || company.getSmoothedDailyProfit() <= 0) return;
        long price = Math.max(1, Math.min(CompanyIPOService.MAX_ISSUE_PRICE, company.getEstimatedValue() / 25_000));
        long quantity = Math.max(1, Math.min(100_000, CompanyIPOService.MAX_TOTAL_RAISE / price));
        var result = CompanyIPOService.ipoOwnerless(company.getCompanyId(), RivalProfile.investmentAccount(company.getCompanyId()),
                price, quantity);
        if (!result.success()) return;
        profile.record(day, "IPO", result.stock().getSymbol() + " @" + price);
        news(55, company.getName() + "上市", company.getName() + "以每股 " + price + " 发行 " + quantity
                + " 股，股票代码 " + result.stock().getSymbol() + "。", false);
    }

    // ---------------------------------------------------------------- 工具

    private static String commodityName(String id) {
        Commodity c = id == null ? null : CommodityRegistry.getCommodity(id);
        return c == null ? String.valueOf(id) : c.getDisplayName();
    }

    private static void news(int score, String headline, String body, boolean major) {
        if (finance.macro.NewsService.enabled()) finance.macro.NewsService.report(score, headline, body, major);
    }
}
