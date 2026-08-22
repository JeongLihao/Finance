package finance.warehouse;

public record WarehouseActionResult(boolean success, String messageKey, int amount) {
    public static WarehouseActionResult success(String key, int amount) {
        return new WarehouseActionResult(true, key, amount);
    }
    public static WarehouseActionResult failure(String key) {
        return new WarehouseActionResult(false, key, 0);
    }
}
