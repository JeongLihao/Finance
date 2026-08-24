package finance.logistics;

public record ShipmentActionResult(boolean success, String messageKey, Shipment shipment) {
    public static ShipmentActionResult success(String key, Shipment shipment) {
        return new ShipmentActionResult(true, key, shipment);
    }
    public static ShipmentActionResult failure(String key) {
        return new ShipmentActionResult(false, key, null);
    }
}
