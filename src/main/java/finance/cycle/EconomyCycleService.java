package finance.cycle;

import finance.alert.PriceAlertManager;
import finance.chart.CandlestickService;
import finance.company.CompanyBankruptcyManager;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyManager;
import finance.company.CompanyProposalManager;
import finance.event.EventManager;
import finance.market.NpcMarketMaker;
import finance.metrics.EconomyMetricsService;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.StockMarketManager;
import net.minecraft.server.MinecraftServer;

public final class EconomyCycleService {

    public static final long TICKS_PER_MC_DAY = 24_000L;
    private static final long MOMENTUM_INTERVAL = 1_200L;
    private static final long NOISE_INTERVAL = 3_600L;

    private EconomyCycleService() {}

    /**
     * 最近一次执行过 tickDay 的 mcDay。/time set 回拨会让同一天的 tickDay 被重复调用，
     * 幂等跳过可防止重复发布零季报等副作用（只防"同一天重复"，跨天回拨仍属时间旅行，另案处理）。
     */
    private static long lastTickDayProcessed = Long.MIN_VALUE;

    /** 世界切换时重置日结算幂等标记，避免新世界的同一天被误判为已处理。 */
    /** 1 游戏小时 = 1000 tick。 */
    static final long TICKS_PER_MC_HOUR = 1_000L;

    public static void resetDirect() {
        lastTickDayProcessed = Long.MIN_VALUE;
    }

    public static long currentMcDay(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        return server.overworld().getGameTime() / TICKS_PER_MC_DAY;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        CandlestickService.observeDay(gameTime / TICKS_PER_MC_DAY);
        long mcDay = gameTime / TICKS_PER_MC_DAY;
        boolean marketClosed = FinancialCycleService.observeMarketDay(mcDay);
        if (marketClosed)
            finance.futures.FuturesRiskService.notifyOnline(server, FinancialCycleService.lastClosedMarketDay());
        if (gameTime <= 0) {
            return;
        }

        if (gameTime % TICKS_PER_MC_DAY == 0) {
            tickDay(server, mcDay);
        }

        FinancialCycleService.advanceTo(mcDay);

        if (gameTime % TICKS_PER_MC_HOUR == 0) {
            StockMarketManager.updateFairValuesHourly();
        }

        if (gameTime % NOISE_INTERVAL == 0) {
            tickNoiseAndMomentum(server);
        } else if (gameTime % MOMENTUM_INTERVAL == 0) {
            tickMomentum(server);
        }
    }

    private static void tickDay(MinecraftServer server, long mcDay) {
        if (mcDay == lastTickDayProcessed) {
            return; // 同一天重复调用（/time set 回拨）直接跳过
        }
        lastTickDayProcessed = mcDay;
        // At the first tick of a new MC day, the previous day's counters are
        // still intact. Persist them before individual price engines reset.
        EconomyMetricsService.closeDay(mcDay - 1);
        CandlestickService.closeDay(mcDay - 1);
        NpcMarketMaker.resetAllDayStats();
        EventManager.onDayTick(server);
        finance.event.EventContractService.processDay(server, mcDay);
        finance.settlement.SettlementService.processDay(server, mcDay);
        // 宏观经济：村庄集市（按类别需求采购并回报供需）→ 本地价格/人口收盘
        finance.village.VillageService.processDay(server, mcDay);
        finance.macro.MacroEconomyService.afterVillages(mcDay);
        finance.exploration.ExplorationService.processDay(mcDay);
        NpcMarketMaker.naturalConsumeAll();
        NpcMarketMaker.centralBankIntervention();
        // 中央银行：旧存档国库余额并入央行（一次性）→ 公开市场操作、商品平准、物价稳定判定
        finance.centralbank.LegacyTreasuryMigration.run();
        finance.centralbank.CentralBankService.processDay(mcDay);
        // 招工、发工资：在岗人数决定接下来一天的机器速度
        finance.macro.MacroLaborService.processDay(mcDay);
        CompanyManager.tickAll(mcDay);
        CompanyManager.settleDailyProfits(mcDay);
        finance.earnings.EarningsSeasonService.processDay(server, mcDay);
        finance.npcinvestor.NpcInvestorService.processDay(mcDay);
        // 上市认购：NPC 投资者补足资金之后认购，期满结算
        finance.company.CompanyIPOService.processDay(mcDay);
        // NPC 投票必须在当天 CompanyProposalManager.tick 结算提案之前
        finance.npcinvestor.NpcGovernanceService.processDay(mcDay);
        StockMarketManager.tickNewDay();
        CompanyManager.tryDividends(mcDay);
        CompanyFinancingManager.tick(mcDay);
        CompanyProposalManager.tick(mcDay);
        CompanyBankruptcyManager.tick(server, mcDay);
        // NPC 竞争公司：排在破产处理之后，避免对当天破产的公司做决策
        finance.rival.RivalService.processDay(mcDay);
        // 敌对要约与股东激进主义（默认关闭）；提案在明天的日结开始表决，要约在 FinancialCycleService 结算
        finance.rival.RivalHostileService.processDay(mcDay);
        finance.feedback.WorldTerminalStateService.auditDay(server, mcDay);
        finance.policy.PolicyMeetingService.onDayTick(server, mcDay);
        finance.caravan.CaravanService.processDay(server, mcDay);
        // 经济成就：只读当天收盘后的状态
        finance.macro.EconomyAchievements.processDay(server, mcDay);
        // 《金融周报》：到期出周刊，有大事出号外
        finance.macro.NewsService.processDay(server, mcDay);
        // 每 7 天播报身家排行
        finance.simple.SimpleWealthService.weekly(server, mcDay);
    }

    /** 每分钟：NPC 短线派追涨杀跌，然后按市价检查融资仓、跌破维持线的卖进市场，并通知被强卖和快被强卖的玩家。 */
    static void stockMinute(MinecraftServer server) {
        finance.npcinvestor.SpeculatorService.tickMinute();
        finance.margin.MarginService.checkLiquidations(finance.simple.BetNotices::liquidated);
        finance.simple.BetNotices.warnDanger(server, currentMcDay(server));
    }

    private static void tickNoiseAndMomentum(MinecraftServer server) {
        NpcMarketMaker.tickAllMomentum();
        NpcMarketMaker.tickAllNoise();
        NpcMarketMaker.recalculateAll();
        StockMarketManager.tickMomentum();
        StockMarketManager.tickNoise();
        StockMarketManager.recalculateAllPrices();
        stockMinute(server);
        ConditionalStockOrderManager.checkOrders(server);
        PriceAlertManager.checkAlerts(server);
    }

    private static void tickMomentum(MinecraftServer server) {
        NpcMarketMaker.tickAllMomentum();
        NpcMarketMaker.recalculateAll();
        StockMarketManager.tickMomentum();
        StockMarketManager.recalculateAllPrices();
        stockMinute(server);
        ConditionalStockOrderManager.checkOrders(server);
        PriceAlertManager.checkAlerts(server);
    }
}
