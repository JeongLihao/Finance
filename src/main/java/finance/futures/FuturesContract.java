package finance.futures;

import java.util.UUID;

/** Quote is the smallest currency unit per commodity unit; contract size is commodity units per lot. */
public final class FuturesContract {
    private final UUID id;
    private final String code;
    private final String commodityId;
    private final long contractSize;
    private final long listingDay;
    private final long lastTradingDay;
    private final long maturityDay;
    private final FuturesSettlementType settlementType;
    private FuturesContractStatus status;
    private long finalSettlementPrice;

    public FuturesContract(UUID id, String code, String commodityId, long contractSize, long listingDay,
                           long lastTradingDay, long maturityDay, FuturesSettlementType settlementType,
                           FuturesContractStatus status) {
        if (id == null || code == null || code.isBlank() || code.length() > 16 || commodityId == null
                || commodityId.isBlank() || commodityId.length() > 64 || contractSize <= 0 || listingDay < 0
                || lastTradingDay < listingDay || maturityDay <= lastTradingDay || settlementType == null || status == null) {
            throw new IllegalArgumentException("invalid futures contract");
        }
        this.id = id; this.code = code.trim().toUpperCase(java.util.Locale.ROOT);
        this.commodityId = commodityId.trim().toLowerCase(java.util.Locale.ROOT);
        this.contractSize = contractSize; this.listingDay = listingDay; this.lastTradingDay = lastTradingDay;
        this.maturityDay = maturityDay; this.settlementType = settlementType; this.status = status;
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public String commodityId() { return commodityId; }
    public long contractSize() { return contractSize; }
    public long listingDay() { return listingDay; }
    public long lastTradingDay() { return lastTradingDay; }
    public long maturityDay() { return maturityDay; }
    public FuturesSettlementType settlementType() { return settlementType; }
    public FuturesContractStatus status() { return status; }
    public long finalSettlementPrice() { return finalSettlementPrice; }
    public boolean canTrade() { return status == FuturesContractStatus.TRADING || status == FuturesContractStatus.LAST_TRADING_DAY; }
    void setStatus(FuturesContractStatus value) { if (value != null) status = value; }
    void setFinalSettlementPrice(long value) { finalSettlementPrice = Math.max(0, value); }
    public void restoreFinalSettlementPrice(long value) { finalSettlementPrice = Math.max(0, value); }
}
