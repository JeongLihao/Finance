package finance.marketdata;

import java.util.List;

public record MarketRankingSnapshot(
        List<MarketRankingEntry> commodityGainers,
        List<MarketRankingEntry> commodityLosers,
        List<MarketRankingEntry> stockGainers,
        List<MarketRankingEntry> stockLosers,
        List<MarketRankingEntry> commodityVolumeLeaders,
        List<MarketRankingEntry> stockVolumeLeaders,
        List<MarketRankingEntry> unusualVolume) {
    public MarketRankingSnapshot {
        commodityGainers = List.copyOf(commodityGainers);
        commodityLosers = List.copyOf(commodityLosers);
        stockGainers = List.copyOf(stockGainers);
        stockLosers = List.copyOf(stockLosers);
        commodityVolumeLeaders = List.copyOf(commodityVolumeLeaders);
        stockVolumeLeaders = List.copyOf(stockVolumeLeaders);
        unusualVolume = List.copyOf(unusualVolume);
    }
}
