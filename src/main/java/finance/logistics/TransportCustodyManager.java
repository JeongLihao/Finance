package finance.logistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The sole authority for commodity units between source and destination custody. */
public final class TransportCustodyManager {
    private static final Map<UUID, TransportCargo> CARGO = new LinkedHashMap<>();
    private TransportCustodyManager() {}

    public static TransportCargo get(UUID shipmentId) { return shipmentId == null ? null : CARGO.get(shipmentId); }
    public static Map<UUID, TransportCargo> all() { return Map.copyOf(CARGO); }
    static boolean create(TransportCargo cargo) {
        return cargo != null && !CARGO.containsKey(cargo.shipmentId())
                && CARGO.putIfAbsent(cargo.shipmentId(), cargo) == null;
    }
    static TransportCargo release(UUID shipmentId) { return shipmentId == null ? null : CARGO.remove(shipmentId); }
    public static boolean restore(TransportCargo cargo) { return create(cargo); }
    public static void clearDirect() { CARGO.clear(); }
}
