package finance.marketdata;

import finance.chart.MarketInstrumentType;

public record MarketRankingEntry(MarketInstrumentType type, String id, double changePercent,
                                 long volume, double volumeRatio) {}
