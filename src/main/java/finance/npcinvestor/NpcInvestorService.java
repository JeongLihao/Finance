package finance.npcinvestor;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.governance.ShareholderRegistryService;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockOrder;
import finance.stock.StockOrderType;
import finance.stock.StockPortfolioManager;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NPC 投资者每日流程：注资/补足 → 撤掉昨天没成交的挂单 → 逐股评分 → 按差额买卖。
 *
 * <p>不变量：资金只从 NPC 资金池经 {@link AccountManager#moveFunds} 转入并留痕，资金池低于
 * 保留线时跳过；买入先走残差配售（资金销毁），残差不足再走普通买单；卖出只走普通卖单；
 * 同一天只执行一次；配置关闭时完全不动，已有持仓保留。</p>
 */
public final class NpcInvestorService {
    /** 低于该差额（占有表决权股份的基点）不调仓，避免每天来回小额交易刷屏。 */
    static final int REBALANCE_THRESHOLD_BPS = 25;

    private NpcInvestorService() {}

    /** 每日参数。生产代码从配置读取，测试可以直接构造。 */
    public record Settings(
            boolean enabled,
            long initialCapitalEach,
            long dailyBudget,
            long poolReserveFloor,
            int maxHoldingBpsEach,
            int maxCombinedBps,
            int maxFloatBpsPerDay) {
        public static Settings fromConfig() {
            return new Settings(
                    FinanceConfig.npcInvestorsEnabled(),
                    FinanceConfig.npcInitialCapitalEach(),
                    FinanceConfig.npcDailyBudget(),
                    FinanceConfig.npcPoolReserveFloor(),
                    FinanceConfig.npcMaxHoldingPercentEach() * 100,
                    FinanceConfig.npcMaxCombinedHoldingPercent() * 100,
                    FinanceConfig.npcMaxFloatPercentPerDay() * 100);
        }
    }

    public static void processDay(long day) {
        processDay(day, Settings.fromConfig());
    }

    public static synchronized void processDay(long day, Settings settings) {
        if (settings == null || !settings.enabled()) return;
        if (day <= NpcInvestorManager.lastProcessedDay()) return;
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.NPC_ECONOMY)) return;
        NpcInvestorManager.setLastProcessedDay(day);
        NpcInvestorManager.ensureAccounts();

        for (NpcInvestorArchetype archetype : NpcInvestorArchetype.values()) fund(archetype, day, settings);
        cancelOpenOrders();

        List<Stock> stocks = new ArrayList<>(StockMarketManager.getListedStocks());
        stocks.sort(Comparator.comparing(Stock::getSymbol));
        Set<UUID> listed = new HashSet<>();
        for (Stock stock : stocks) {
            Company company = CompanyManager.getCompany(stock.getCompanyId());
            if (company == null) continue;
            listed.add(company.getCompanyId());
            if (stock.isTradingHalted()) continue;
            processStock(stock, company, day, settings);
        }
        for (NpcInvestorArchetype archetype : NpcInvestorArchetype.values()) {
            NpcInvestorManager.state(archetype).retainCompanies(listed);
        }
        EconomySavedData.markDirty();
    }

    // ---------------------------------------------------------------- 资金

    static void fund(NpcInvestorArchetype archetype, long day, Settings settings) {
        NpcInvestorState state = NpcInvestorManager.state(archetype);
        UUID account = archetype.accountId();
        long amount;
        if (!state.initialFunded()) {
            amount = settings.initialCapitalEach();
        } else {
            long balance = AccountManager.getBalance(account);
            amount = balance >= settings.dailyBudget() ? 0 : settings.dailyBudget() - balance;
        }
        if (amount <= 0) {
            if (!state.initialFunded() && settings.initialCapitalEach() <= 0) state.markInitialFunded();
            return;
        }
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        if (pool - amount < settings.poolReserveFloor()) return;
        if (!AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, account, amount)) return;
        AccountManager.addTransactionRecord(new TransactionRecord(
                NpcMarketMaker.NPC_UUID,
                account,
                amount,
                TransactionType.NPC_INVESTOR_FUNDING,
                account,
                archetype.name(),
                0));
        state.markInitialFunded();
        state.addContribution(amount);
        NpcInvestorManager.addDecision(
                new NpcInvestorManager.Decision(day, archetype, null, "", "FUND", 0, amount, NpcReason.FUNDING));
    }

    /** 撤掉 NPC 前一天没成交的挂单，冻结的资金和股份原路退回。 */
    static void cancelOpenOrders() {
        for (StockOrder order : new ArrayList<>(StockMarketManager.getOrders())) {
            if (NpcInvestorArchetype.isInvestorAccount(order.getPlayerId())) {
                StockMarketManager.cancelStockOrder(order.getOrderId(), order.getPlayerId());
            }
        }
    }

    // ---------------------------------------------------------------- 调仓

    static void processStock(Stock stock, Company company, long day, Settings settings) {
        long votingShares = stock.getVotingShares();
        if (votingShares <= 0 || stock.getLastPrice() <= 0) return;
        NpcInvestorScoring.Profile profile = profile(stock, company, day);
        long dayBudgetShares = bpsOf(stock.getFloatShares(), settings.maxFloatBpsPerDay());
        long combinedCapShares = bpsOf(votingShares, settings.maxCombinedBps());
        // 每只股票只取一次股东快照；之后按本轮各原型的成交量增减，结果与每次重算一致
        Map<UUID, Long> heldBy = new HashMap<>();
        long combined = 0;
        for (ShareholderRegistryService.Holder holder :
                ShareholderRegistryService.snapshot(stock.getCompanyId(), day).holders()) {
            if (NpcInvestorArchetype.isInvestorAccount(holder.id())) {
                heldBy.put(holder.id(), holder.total());
                combined += holder.total();
            }
        }

        for (NpcInvestorArchetype archetype : rotation(day)) {
            NpcInvestorScoring.Score score = NpcInvestorScoring.score(archetype, profile);
            int target = NpcInvestorScoring.targetBps(archetype, score.value(), settings.maxHoldingBpsEach());
            NpcInvestorManager.state(archetype)
                    .record(company.getCompanyId(), NpcInvestorScoring.sentiment(score.value()), target);
            if (dayBudgetShares <= 0) continue;

            UUID account = archetype.accountId();
            // 含挂卖单和要约锁定的部分，避免锁定后误以为仓位变少而重复买入
            long held = heldBy.getOrDefault(account, 0L);
            long targetShares = bpsOf(votingShares, target);
            long delta = targetShares - held;
            if (Math.abs(delta) < Math.max(1, bpsOf(votingShares, REBALANCE_THRESHOLD_BPS))) continue;

            if (delta > 0) {
                long combinedHeadroom = combinedCapShares - combined;
                if (combinedHeadroom <= 0) {
                    log(day, archetype, company, stock, "HOLD", 0, NpcReason.COMBINED_CAP);
                    continue;
                }
                long want = Math.min(delta, Math.min(dayBudgetShares, combinedHeadroom));
                Fill fill = buy(account, stock, want, day);
                if (fill.total() > 0) {
                    // 挂单部分随时可能成交，同样占用当日额度
                    dayBudgetShares -= fill.total();
                    combined += fill.total(); // 挂着的买单也算进合计上限，成交后不会超限
                    if (fill.filled() > 0) log(day, archetype, company, stock, "BUY", fill.filled(), score.reason());
                    if (fill.resting() > 0)
                        log(day, archetype, company, stock, "BUY_ORDER", fill.resting(), score.reason());
                } else {
                    log(day, archetype, company, stock, "HOLD", 0, NpcReason.NO_CASH);
                }
            } else {
                // held 含要约/回购锁定的股份，实际只能卖出持仓里的可用部分
                long free = StockPortfolioManager.getHolding(account, stock.getSymbol())
                        .getQuantity();
                long want = Math.min(Math.min(-delta, dayBudgetShares), free);
                if (want <= 0) {
                    log(day, archetype, company, stock, "HOLD", 0, NpcReason.SHARES_LOCKED);
                    continue;
                }
                if (!StockMarketManager.sell(account, stock.getSymbol(), want).success()) {
                    log(day, archetype, company, stock, "HOLD", 0, NpcReason.SELL_REJECTED);
                    continue;
                }
                dayBudgetShares -= want;
                long resting = Math.min(want, openOrderQuantity(account, stock.getSymbol(), StockOrderType.SELL));
                combined -= want - resting;
                if (want > resting) log(day, archetype, company, stock, "SELL", want - resting, score.reason());
                if (resting > 0) log(day, archetype, company, stock, "SELL_ORDER", resting, score.reason());
            }
        }
    }

    /** 每天轮换原型的处理顺序，避免排在前面的原型每天都先吃掉当日成交额度。 */
    static List<NpcInvestorArchetype> rotation(long day) {
        NpcInvestorArchetype[] all = NpcInvestorArchetype.values();
        int start = (int) Math.floorMod(day, (long) all.length);
        List<NpcInvestorArchetype> order = new ArrayList<>(all.length);
        for (int i = 0; i < all.length; i++) order.add(all[(start + i) % all.length]);
        return order;
    }

    /** 一次买入的结果：已成交（含残差配售）的股数，和挂在订单簿上尚未成交的股数。 */
    record Fill(long filled, long resting) {
        static final Fill NONE = new Fill(0, 0);

        long total() {
            return filled + resting;
        }
    }

    /** 先配售残差，再用普通买单补足。普通买单没撮合到对手盘的部分会挂进订单簿，单独计为 resting。 */
    static Fill buy(UUID account, Stock stock, long want, long day) {
        if (want <= 0) return Fill.NONE;
        long price = NpcFloatPlacementService.placementPrice(stock);
        if (price <= 0) return Fill.NONE;
        long affordable = AccountManager.getBalance(account) / price;
        long qty = Math.min(want, affordable);
        if (qty <= 0) return Fill.NONE;
        long residual = NpcFloatPlacementService.availableResidual(stock, day);
        long filled = 0;
        long resting = 0;
        long fromResidual = Math.min(qty, residual);
        if (fromResidual > 0
                && NpcFloatPlacementService.place(account, stock.getSymbol(), fromResidual, day)
                        .success()) {
            filled = fromResidual;
        }
        long rest = qty - filled;
        if (rest > 0
                && rest <= Integer.MAX_VALUE
                && StockMarketManager.buy(account, stock.getSymbol(), rest).success()) {
            resting = Math.min(rest, openOrderQuantity(account, stock.getSymbol(), StockOrderType.BUY));
            filled += rest - resting;
        }
        return new Fill(filled, resting);
    }

    /**
     * 该账户在订单簿上对该股票、该方向的未成交数量。每天开盘前已撤掉 NPC 的旧挂单，
     * 而每个原型每天对每只股票最多下一笔单，所以这里就是刚才那一笔的剩余量。
     */
    static long openOrderQuantity(UUID account, String symbol, StockOrderType type) {
        long total = 0;
        for (StockOrder order : StockMarketManager.getOrders()) {
            if (order.getType() == type
                    && account.equals(order.getPlayerId())
                    && order.getSymbol().equalsIgnoreCase(symbol)) {
                total += order.getQuantity();
            }
        }
        return total;
    }

    /** 全部 NPC 投资者对该股的持股合计（含挂卖单锁定的部分）。 */
    static long combinedNpcHoldings(Stock stock, long day) {
        ShareholderRegistryService.Snapshot snapshot = ShareholderRegistryService.snapshot(stock.getCompanyId(), day);
        long total = 0;
        for (ShareholderRegistryService.Holder holder : snapshot.holders()) {
            if (NpcInvestorArchetype.isInvestorAccount(holder.id())) total += holder.total();
        }
        return total;
    }

    static NpcInvestorScoring.Profile profile(Stock stock, Company company, long day) {
        long totalShares = Math.max(1, stock.getTotalShares());
        long fundamentalPerShare = company.getFundamentalAssetValue() / totalShares;
        int cycle = Math.max(1, company.effectiveDividendCycleDays(CompanyManager.getDividendCycleDays()));
        long dividendSum = 0;
        int dividendCount = 0;
        for (Company.DividendRecord record : company.getDividendHistory()) {
            if (day - record.mcDay() <= 2L * cycle) {
                dividendSum += Math.max(0, record.perShare());
                dividendCount++;
            }
        }
        return new NpcInvestorScoring.Profile(
                stock.getLastPrice(),
                fundamentalPerShare,
                stock.getDayChange(),
                company.getRecentProfits(),
                dividendSum,
                dividendCount,
                company.isBankruptcyRisk());
    }

    private static void log(
            long day,
            NpcInvestorArchetype archetype,
            Company company,
            Stock stock,
            String action,
            long qty,
            NpcReason reason) {
        NpcInvestorManager.addDecision(new NpcInvestorManager.Decision(
                day, archetype, company.getCompanyId(), stock.getSymbol(), action, qty, stock.getLastPrice(), reason));
    }

    static long bpsOf(long base, int bps) {
        if (base <= 0 || bps <= 0) return 0;
        return BigInteger.valueOf(base)
                .multiply(BigInteger.valueOf(bps))
                .divide(BigInteger.valueOf(10_000))
                .longValue();
    }
}
