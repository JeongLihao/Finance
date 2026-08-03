package finance.alert;

import finance.data.EconomySavedData;
import finance.config.FinanceConfig;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class PriceAlertManager {

    private static final List<PriceAlert> ALERTS = new ArrayList<>();
    private PriceAlertManager() {
    }

    public static AddResult addAlert(UUID playerId, PriceAlertType type, String targetId,
                                     PriceAlertDirection direction, long targetPrice) {
        if (playerId == null || type == null || direction == null || targetId == null
                || targetId.isBlank() || targetPrice <= 0) {
            return new AddResult(false, "提醒参数无效。", null);
        }
        long count = ALERTS.stream().filter(alert -> alert.getPlayerId().equals(playerId)).count();
        int limit = FinanceConfig.maxPriceAlertsPerPlayer();
        if (count >= limit) {
            return new AddResult(false, "提醒数量已达上限 " + limit + "。", null);
        }
        String normalized = normalizeTarget(type, targetId);
        if (!exists(type, normalized)) {
            return new AddResult(false, "提醒对象不存在。", null);
        }
        PriceAlert alert = new PriceAlert(playerId, type, normalized, direction, targetPrice);
        ALERTS.add(alert);
        EconomySavedData.markDirty();
        return new AddResult(true, "提醒已添加。", alert);
    }

    public static boolean cancelAlert(UUID playerId, UUID alertId) {
        Iterator<PriceAlert> iterator = ALERTS.iterator();
        while (iterator.hasNext()) {
            PriceAlert alert = iterator.next();
            if (alert.getAlertId().equals(alertId) && alert.getPlayerId().equals(playerId)) {
                iterator.remove();
                EconomySavedData.markDirty();
                return true;
            }
        }
        return false;
    }

    public static List<PriceAlert> getAlertsForPlayer(UUID playerId) {
        List<PriceAlert> result = new ArrayList<>();
        for (PriceAlert alert : ALERTS) {
            if (alert.getPlayerId().equals(playerId)) {
                result.add(alert);
            }
        }
        return result;
    }

    public static List<PriceAlert> getAlerts() {
        return ALERTS;
    }

    public static void addAlertDirect(PriceAlert alert) {
        if (alert != null) {
            ALERTS.add(alert);
        }
    }

    public static void clearAlertsDirect() {
        ALERTS.clear();
    }

    public static void checkAlerts(MinecraftServer server) {
        if (server == null || ALERTS.isEmpty()) {
            return;
        }
        Iterator<PriceAlert> iterator = ALERTS.iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            PriceAlert alert = iterator.next();
            long currentPrice = currentPrice(alert);
            if (currentPrice <= 0 || !alert.shouldTrigger(currentPrice)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(alert.getPlayerId());
            if (player == null) {
                continue;
            }
            player.sendSystemMessage(Component.literal(
                    "§e[金融提醒] " + displayType(alert.getType()) + " " + alert.getTargetId()
                            + " 已" + displayDirection(alert.getDirection()) + " "
                            + alert.getTargetPrice() + "，当前价 " + currentPrice + "。"));
            iterator.remove();
            changed = true;
        }
        if (changed) {
            EconomySavedData.markDirty();
        }
    }

    public static int checkAlertsForTest() {
        int triggered = 0;
        Iterator<PriceAlert> iterator = ALERTS.iterator();
        while (iterator.hasNext()) {
            PriceAlert alert = iterator.next();
            long currentPrice = currentPrice(alert);
            if (currentPrice > 0 && alert.shouldTrigger(currentPrice)) {
                iterator.remove();
                triggered++;
            }
        }
        if (triggered > 0) {
            EconomySavedData.markDirty();
        }
        return triggered;
    }

    private static long currentPrice(PriceAlert alert) {
        return switch (alert.getType()) {
            case COMMODITY -> {
                MarketPrice price = NpcMarketMaker.getMarketPrice(alert.getTargetId());
                yield price != null ? price.getMidPrice() : 0;
            }
            case STOCK -> {
                Stock stock = StockMarketManager.getStock(alert.getTargetId());
                yield stock != null ? stock.getLastPrice() : 0;
            }
        };
    }

    private static boolean exists(PriceAlertType type, String targetId) {
        return switch (type) {
            case COMMODITY -> NpcMarketMaker.getMarketPrice(targetId) != null;
            case STOCK -> StockMarketManager.getStock(targetId) != null;
        };
    }

    private static String normalizeTarget(PriceAlertType type, String targetId) {
        return switch (type) {
            case COMMODITY -> targetId.trim().toLowerCase(java.util.Locale.ROOT);
            case STOCK -> StockMarketManager.normalizeSymbol(targetId);
        };
    }

    private static String displayType(PriceAlertType type) {
        return type == PriceAlertType.COMMODITY ? "商品" : "股票";
    }

    private static String displayDirection(PriceAlertDirection direction) {
        return direction == PriceAlertDirection.ABOVE ? "涨到" : "跌到";
    }

    public record AddResult(boolean success, String message, PriceAlert alert) {
    }
}
