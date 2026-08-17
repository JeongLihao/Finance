package finance.chart;

import finance.util.MathUtil;

public record Candlestick(long mcDay, long open, long high, long low, long close, long volume) {

    public Candlestick {
        if (mcDay < 0 || open <= 0 || high <= 0 || low <= 0 || close <= 0 || volume < 0
                || high < Math.max(open, close) || low > Math.min(open, close) || high < low) {
            throw new IllegalArgumentException("Invalid candlestick");
        }
    }

    public static Candlestick trade(long mcDay, long price, long quantity) {
        return new Candlestick(mcDay, price, price, price, price, Math.max(0, quantity));
    }

    public static Candlestick carry(long mcDay, long previousClose) {
        return new Candlestick(mcDay, previousClose, previousClose, previousClose, previousClose, 0);
    }

    public Candlestick withTrade(long price, long quantity) {
        if (price <= 0 || quantity <= 0) return this;
        return new Candlestick(mcDay, open, Math.max(high, price), Math.min(low, price), price,
                MathUtil.saturatedAddNonNegative(volume, quantity));
    }
}
