package finance.index;

public record MarketIndexPoint(long mcDay, double value) {
    public MarketIndexPoint {
        if (!Double.isFinite(value) || value < 0) value = 0;
    }
}
