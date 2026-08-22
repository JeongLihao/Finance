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
        // At the first tick of a new MC day, the previous day's counters are
        // still intact. Persist them before individual price engines reset.
        EconomyMetricsService.closeDay(mcDay - 1);
        CandlestickService.closeDay(mcDay - 1);
        NpcMarketMaker.resetAllDayStats();
        EventManager.onDayTick(server);
        finance.event.EventContractService.processDay(server,mcDay);
        NpcMarketMaker.naturalConsumeAll();
        NpcMarketMaker.centralBankIntervention();
        CompanyManager.tickAll(mcDay);
        CompanyManager.settleDailyProfits(mcDay);
        StockMarketManager.updateFairValuesAndResetDay();
        CompanyManager.tryDividends(mcDay);
        CompanyFinancingManager.tick(mcDay);
        CompanyProposalManager.tick(mcDay);
        CompanyBankruptcyManager.tick(server, mcDay);
        finance.gameplay.company.CompanyFacilityWorldFeedbackService.refresh(server, mcDay);
        finance.feedback.WorldTerminalStateService.auditDay(server, mcDay);
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
