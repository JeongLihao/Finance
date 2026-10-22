package finance.caravan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商队注册表：路线与在途货运的内存登记簿（持久化见 {@code CaravanDataSerializer}）。
 */
public final class CaravanManager {

    private static final Map<UUID, CaravanRoute> ROUTES = new LinkedHashMap<>();
    private static final Map<UUID, CaravanShipment> SHIPMENTS = new LinkedHashMap<>();

    private CaravanManager() {}

    // ================================================================
    // 路线
    // ================================================================

    public static boolean addRoute(CaravanRoute route) {
        if (route == null || ROUTES.containsKey(route.id())) {
            return false;
        }
        ROUTES.put(route.id(), route);
        return true;
    }

    public static CaravanRoute route(UUID id) {
        return id == null ? null : ROUTES.get(id);
    }

    public static Collection<CaravanRoute> routes() {
        return Collections.unmodifiableCollection(new ArrayList<>(ROUTES.values()));
    }

    public static List<CaravanRoute> routesForOwner(UUID ownerId) {
        List<CaravanRoute> out = new ArrayList<>();
        for (CaravanRoute r : ROUTES.values()) {
            if (ownerId != null && ownerId.equals(r.ownerId())) {
                out.add(r);
            }
        }
        return out;
    }

    public static int activeRouteCountForOwner(UUID ownerId) {
        int n = 0;
        for (CaravanRoute r : ROUTES.values()) {
            if (ownerId != null && ownerId.equals(r.ownerId()) && r.status() == CaravanRoute.Status.ACTIVE) {
                n++;
            }
        }
        return n;
    }

    public static void putRouteDirect(CaravanRoute route) {
        if (route != null) {
            ROUTES.put(route.id(), route);
        }
    }

    // ================================================================
    // 在途货运
    // ================================================================

    public static boolean addShipment(CaravanShipment shipment) {
        if (shipment == null || SHIPMENTS.containsKey(shipment.id())) {
            return false;
        }
        SHIPMENTS.put(shipment.id(), shipment);
        return true;
    }

    public static List<CaravanShipment> arrivalsOn(long day) {
        List<CaravanShipment> out = new ArrayList<>();
        for (CaravanShipment s : SHIPMENTS.values()) {
            if (s.status() == CaravanShipment.Status.IN_TRANSIT && s.arriveDay() == day) {
                out.add(s);
            }
        }
        return out;
    }

    public static int inTransitCount() {
        int n = 0;
        for (CaravanShipment s : SHIPMENTS.values()) {
            if (s.status() == CaravanShipment.Status.IN_TRANSIT) {
                n++;
            }
        }
        return n;
    }

    /** 全部在途货运（供持久化遍历）。 */
    public static List<CaravanShipment> allInTransit() {
        List<CaravanShipment> out = new ArrayList<>();
        for (CaravanShipment s : SHIPMENTS.values()) {
            if (s.status() == CaravanShipment.Status.IN_TRANSIT) {
                out.add(s);
            }
        }
        return out;
    }

    /** 结算完的货运移出登记簿（ARRIVED 状态已落盘意义不大，防无限增长）。 */
    public static void removeShipment(UUID id) {
        SHIPMENTS.remove(id);
    }

    public static void putShipmentDirect(CaravanShipment shipment) {
        if (shipment != null) {
            SHIPMENTS.put(shipment.id(), shipment);
        }
    }

    /** 测试/重载用：清空全部。 */
    public static void clearDirect() {
        ROUTES.clear();
        SHIPMENTS.clear();
    }
}
