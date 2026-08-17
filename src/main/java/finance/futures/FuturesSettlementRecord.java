package finance.futures;

import java.util.UUID;

public record FuturesSettlementRecord(UUID contractId,long day,long settlementPrice,long grossGain,long collectedLoss,
                                      long guaranteeFundUsed,long profitHaircut,boolean finalSettlement) {
    public FuturesSettlementRecord{if(contractId==null||day<0||settlementPrice<=0||grossGain<0||collectedLoss<0||guaranteeFundUsed<0||profitHaircut<0)throw new IllegalArgumentException();}
}
