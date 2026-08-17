package finance.futures;

public record FuturesContractKey(String commodityId, long maturityDay) {
    public FuturesContractKey {
        commodityId = commodityId == null ? "" : commodityId.trim().toLowerCase(java.util.Locale.ROOT);
        if (commodityId.isBlank() || commodityId.length() > 64 || maturityDay < 0) {
            throw new IllegalArgumentException("invalid futures contract key");
        }
    }
}
