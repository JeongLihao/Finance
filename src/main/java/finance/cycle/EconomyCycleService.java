package finance.cycle;

import finance.alert.PriceAlertManager;
import finance.company.CompanyBankruptcyManager;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyManager;
import finance.company.CompanyProposalManager;
import finance.event.EventManager;
import finance.market.NpcMarketMaker;
import finance.metrics.EconomyMetricsService;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.StockMarketManager;
import finance.chart.CandlestickService;
import net.minecraft.server.MinecraftServer;

public final class EconomyCycleService {

    public static final long TICKS_PER_MC_DAY = 24_000L;
    private static final long MOMENTUM_INTERVAL = 1_200L;
    private static final long NOISE_INTERVAL = 3_600L;

    private EconomyCycleService() {
    }

    /**
     * 最近一次执行过 tickDay 的 mcDay。/time set 回拨会让同一天的 tickDay 被重复调用，
     * 幂等跳过可防止重复发布零季报等副作用（只防"同一天重复"，跨天回拨仍属时间旅行，另案处理）。
     */
    private static long lastTickDayProcessed = Long.MIN_VALUE;

    /** 世界切换时重置日结算幂等标记，避免新世界的同一天被误判为已处理。 */
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
        if (marketClosed) finance.futures.FuturesRiskService.notifyOnline(server, FinancialCycleService.lastClosedMarketDay());
        if (gameTime <= 0) {
            return;
        }

        if (gameTime % TICKS_PER_MC_DAY == 0) {
            tickDay(server, mcDay);
        }

        FinancialCycleService.advanceTo(mcDay);

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
        finance.event.EventContractService.processDay(server,mcDay);
        finance.settlement.SettlementService.processDay(server, mcDay);
        // 宏观经济：福利 → 村庄集市（按类别需求采购并回报供需）→ 价格/人口/政治收盘
        finance.macro.MacroEconomyService.beforeVillages(mcDay);
        finance.village.VillageService.processDay(server, mcDay);
        finance.macro.MacroEconomyService.afterVillages(mcDay);
        finance.exploration.ExplorationService.processDay(mcDay);
        NpcMarketMaker.naturalConsumeAll();
        NpcMarketMaker.centralBankIntervention();
        // 招工和发工资必须在设施生产之前
        finance.macro.MacroLaborService.processDay(mcDay);
        CompanyManager.tickAll(mcDay);
        CompanyManager.settleDailyProfits(mcDay);
        finance.earnings.EarningsSeasonService.processDay(server, mcDay);
        finance.npcinvestor.NpcInvestorService.processDay(mcDay);
        // NPC 投票必须在当天 CompanyProposalManager.tick 结算提案之前
        finance.npcinvestor.NpcGovernanceService.processDay(mcDay);
        StockMarketManager.tickNewDay();
        CompanyManager.tryDividends(mcDay);
        CompanyFinancingManager.tick(mcDay);
        CompanyProposalManager.tick(mcDay);
        CompanyBankruptcyManager.tick(server, mcDay);
        finance.gameplay.company.CompanyFacilityWorldFeedbackService.refresh(server, mcDay);
        finance.feedback.WorldTerminalStateService.auditDay(server, mcDay);
        finance.policy.PolicyMeetingService.onDayTick(server, mcDay);
        finance.caravan.CaravanService.processDay(server, mcDay);
    }

    private static void tickNoiseAndMomentum(MinecraftServer server) {
        NpcMarketMaker.tickAllMomentum();
        NpcMarketMaker.tickAllNoise();
        NpcMarketMaker.recalculateAll();
        StockMarketManager.tickMomentum();
        StockMarketManager.tickNoise();
        StockMarketManager.recalculateAllPrices();
        ConditionalStockOrderManager.checkOrders(server);
        PriceAlertManager.checkAlerts(server);
    }

    private static void tickMomentum(MinecraftServer server) {
        NpcMarketMaker.tickAllMomentum();
        NpcMarketMaker.recalculateAll();
        StockMarketManager.tickMomentum();
        StockMarketManager.recalculateAllPrices();
        ConditionalStockOrderManager.checkOrders(server);
        PriceAlertManager.checkAlerts(server);
    }
}
