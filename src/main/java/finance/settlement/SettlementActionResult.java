package finance.settlement;
public record SettlementActionResult(boolean success,String messageKey) {
    public static SettlementActionResult ok(String key){return new SettlementActionResult(true,key);}
    public static SettlementActionResult fail(String key){return new SettlementActionResult(false,key);}
}
