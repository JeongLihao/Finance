package finance.fund;

public record FundNavPoint(long mcDay, long nav, long netAssets, long totalShareUnits,
                           long benchmarkLevel, boolean degradedPrice) { }
