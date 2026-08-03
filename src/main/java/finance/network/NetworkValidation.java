package finance.network;

import finance.stock.StockMarketManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class NetworkValidation {

    public static final int MAX_COMMODITY_ID_LENGTH = 64;
    public static final int MAX_ITEM_ID_LENGTH = 128;
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;
    public static final int MAX_SYMBOL_LENGTH = 16;

    private NetworkValidation() {
    }

    public static String normalizeCommodityId(String commodityId) {
        return commodityId == null ? "" : commodityId.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeSymbol(String symbol) {
        return StockMarketManager.normalizeSymbol(symbol);
    }

    public static boolean isValidCommodityId(String commodityId) {
        String normalized = normalizeCommodityId(commodityId);
        return !normalized.isEmpty()
                && normalized.length() <= MAX_COMMODITY_ID_LENGTH
                && normalized.matches("[a-z0-9_:.\\-]+");
    }

    public static boolean isValidSymbol(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return !normalized.isEmpty() && normalized.length() <= MAX_SYMBOL_LENGTH;
    }

    public static boolean isValidDisplayName(String displayName) {
        return displayName != null
                && !displayName.trim().isEmpty()
                && displayName.length() <= MAX_DISPLAY_NAME_LENGTH;
    }

    public static boolean isValidItemId(String itemId) {
        return itemId == null
                || (!itemId.trim().isEmpty()
                && itemId.length() <= MAX_ITEM_ID_LENGTH
                && ResourceLocation.tryParse(itemId.trim()) != null);
    }

    public static boolean isPositive(long value) {
        return value > 0;
    }

    public static boolean isPositive(int value) {
        return value > 0;
    }
}
