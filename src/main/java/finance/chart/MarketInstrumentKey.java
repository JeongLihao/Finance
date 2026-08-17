package finance.chart;

import java.util.Locale;

public record MarketInstrumentKey(MarketInstrumentType type, String id) {

    public static final int MAX_ID_LENGTH = 128;

    public MarketInstrumentKey {
        if (type == null) throw new IllegalArgumentException("Instrument type is required");
        String normalized = id == null ? "" : id.trim();
        normalized = type == MarketInstrumentType.STOCK
                ? normalized.toUpperCase(Locale.ROOT)
                : normalized.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid instrument id");
        }
        id = normalized;
    }

    public static MarketInstrumentKey tryCreate(MarketInstrumentType type, String id) {
        try {
            return new MarketInstrumentKey(type, id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
