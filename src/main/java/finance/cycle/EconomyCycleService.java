package finance.cycle;

import finance.alert.PriceAlertManager;
import finance.company.CompanyBankruptcyManager;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyManager;
import finance.company.CompanyProposalManager;
import finance.event.EventManager;
import finance.market.NpcMarketMaker;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.StockMarketManager;
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
        if (gameTime <= 0) {
            return;
        }

        if (gameTime % TICKS_PER_MC_DAY == 0) {
            tickDay(server, gameTime / TICKS_PER_MC_DAY);
        }

        if (gameTime % NOISE_INTERVAL == 0) {
            tickNoiseAndMomentum(server);
        } else if (gameTime % MOMENTUM_INTERVAL == 0) {
            tickMomentum(server);
        }
    }

    private static void tickDay(MinecraftServer server, long mcDay) {
        NpcMarketMaker.resetAllDayStats();
        EventManager.onDayTick(server);
        NpcMarketMaker.naturalConsumeAll();
        NpcMarketMaker.centralBankIntervention();
        CompanyManager.tickAll();
        CompanyManager.settleDailyProfits(mcDay);
        StockMarketManager.updateFairValuesAndResetDay();
        CompanyManager.tryDividends(mcDay);
        CompanyFinancingManager.tick(mcDay);
        CompanyProposalManager.tick(mcDay);
        CompanyBankruptcyManager.tick(mcDay);
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
