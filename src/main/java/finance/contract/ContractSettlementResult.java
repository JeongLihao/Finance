package finance.contract;

public record ContractSettlementResult(boolean success, String messageKey, long reward) {
    public static ContractSettlementResult success(String key, long reward) {
        return new ContractSettlementResult(true, key, reward);
    }
    public static ContractSettlementResult failure(String key) {
        return new ContractSettlementResult(false, key, 0);
    }
}
