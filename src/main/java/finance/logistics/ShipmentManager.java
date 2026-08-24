package finance.logistics;

import finance.data.EconomySavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShipmentManager {
    public static final int MAX_RECORDS = 2_048;
    public static final int MAX_TERMINAL_HISTORY = 512;
    public static final int MAX_ACTIVE_PER_PLAYER = 8;
    public static final int MAX_ACTIVE_PER_COMPANY = 32;
    public static final int MAX_LOAD_KEYS = 4_096;
    private static final Map<UUID, Shipment> SHIPMENTS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, UUID> LOAD_KEYS = new LinkedHashMap<>();

    private ShipmentManager() {}
    public static Shipment get(UUID id) { return id == null ? null : SHIPMENTS.get(id); }
    public static Map<UUID, Shipment> all() { return Map.copyOf(SHIPMENTS); }
    public static Map<String, UUID> loadKeys() { return Map.copyOf(LOAD_KEYS); }
    public static List<Shipment> relatedTo(UUID playerId, boolean admin) {
        if (playerId == null) return List.of();
        return SHIPMENTS.values().stream().filter(s -> admin || playerId.equals(s.creatorId())
                        || playerId.equals(s.carrierId())).sorted(Comparator.comparingLong(Shipment::createdDay)
                        .reversed().thenComparing(Shipment::id)).limit(64).toList();
    }
    public static int activeForPlayer(UUID playerId) {
        return (int) SHIPMENTS.values().stream().filter(s -> !s.status().terminal()
                && (playerId.equals(s.creatorId()) || playerId.equals(s.carrierId()))).count();
    }
    public static int activeForCompany(UUID companyId) {
        return companyId == null ? 0 : (int) SHIPMENTS.values().stream().filter(s -> !s.status().terminal()
                && companyId.equals(s.companyId())).count();
    }
    static boolean canCreate(UUID playerId, UUID companyId) {
        pruneHistory();
        return SHIPMENTS.size() < MAX_RECORDS
                && activeForPlayer(playerId) < finance.config.FinanceConfig.logisticsMaxActivePerPlayer()
                && (companyId == null || activeForCompany(companyId)
                < finance.config.FinanceConfig.logisticsMaxActivePerCompany());
    }
    static Shipment byLoadKey(String key) {
        UUID id = key == null ? null : LOAD_KEYS.get(key);
        return id == null ? null : SHIPMENTS.get(id);
    }
    static boolean addLoaded(Shipment shipment, TransportCargo cargo, String loadKey) {
        pruneHistory();
        if (shipment == null || cargo == null || loadKey == null || loadKey.isBlank()
                || SHIPMENTS.containsKey(shipment.id()) || !shipment.id().equals(cargo.shipmentId())) return false;
        if (!TransportCustodyManager.create(cargo)) return false;
        SHIPMENTS.put(shipment.id(), shipment);
        LOAD_KEYS.put(loadKey, shipment.id());
        trimLoadKeys();
        EconomySavedData.markDirty();
        return true;
    }
    public static boolean restore(Shipment shipment) {
        if (shipment == null || SHIPMENTS.size() >= MAX_RECORDS || SHIPMENTS.containsKey(shipment.id())) return false;
        SHIPMENTS.put(shipment.id(), shipment);
        return true;
    }
    public static void restoreLoadKey(String key, UUID shipmentId) {
        if (key == null || key.isBlank() || key.length() > 128 || shipmentId == null || !SHIPMENTS.containsKey(shipmentId)) return;
        LOAD_KEYS.put(key, shipmentId);
        trimLoadKeys();
    }
    public static List<Shipment> lossPendingAt(UUID sourceWarehouseId, UUID actorId) {
        List<Shipment> matches = new ArrayList<>();
        for (Shipment shipment : SHIPMENTS.values()) if (shipment.status() == ShipmentStatus.LOSS_PENDING
                && sourceWarehouseId.equals(shipment.sourceWarehouseId())
                && (actorId.equals(shipment.creatorId()) || actorId.equals(shipment.carrierId()))) matches.add(shipment);
        matches.sort(Comparator.comparingLong(Shipment::createdDay).thenComparing(Shipment::id));
        return List.copyOf(matches);
    }
    private static void trimLoadKeys() {
        while (LOAD_KEYS.size() > MAX_LOAD_KEYS) LOAD_KEYS.remove(LOAD_KEYS.keySet().iterator().next());
    }
    private static void pruneHistory() {
        List<Shipment> terminal = SHIPMENTS.values().stream().filter(s -> s.status().terminal())
                .sorted(Comparator.comparingLong(Shipment::createdDay).thenComparing(Shipment::id)).toList();
        int remove = Math.max(0, terminal.size() - MAX_TERMINAL_HISTORY);
        for (int i = 0; i < remove; i++) {
            UUID id = terminal.get(i).id();
            if (TransportCustodyManager.get(id) != null) continue;
            SHIPMENTS.remove(id);
            LOAD_KEYS.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        }
    }
    public static void clearDirect() {
        SHIPMENTS.clear();
        LOAD_KEYS.clear();
        TransportCustodyManager.clearDirect();
    }
}
