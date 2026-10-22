package finance.data.serializer;

import finance.caravan.CaravanManager;
import finance.caravan.CaravanRoute;
import finance.caravan.CaravanShipment;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 商队持久化：路线与在途货运的存取。
 *
 * <p>沿用 {@code SettlementDataSerializer} 四步曲：损坏条目跳过＋计数，
 * 整体损坏时熔断对应模块。</p>
 */
public final class CaravanDataSerializer {

    public static final String ROOT = "CaravanRoutes";
    public static final int VERSION = 1;
    /** 防损坏存档无限膨胀。 */
    private static final int MAX_ROUTES = 4096;

    private static final int MAX_SHIPMENTS = 65536;

    private CaravanDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        ListTag routes = new ListTag();
        for (CaravanRoute r : CaravanManager.routes()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", r.id());
            t.putUUID("Owner", r.ownerId());
            t.putUUID("Source", r.sourceSettlementId());
            t.putUUID("Dest", r.destSettlementId());
            t.putString("Commodity", r.commodityId());
            t.putInt("QtyPerTrip", r.qtyPerTrip());
            t.putInt("Guard", r.guardLevel());
            t.putBoolean("Insured", r.insured());
            t.putString("Status", r.status().name());
            t.putLong("CreatedDay", r.createdDay());
            t.putLong("SaturationBps", r.saturationBps());
            t.putLong("TotalProfit", r.totalProfit());
            routes.add(t);
        }
        data.put("Routes", routes);
        ListTag shipments = new ListTag();
        for (CaravanShipment s : CaravanManager.allInTransit()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", s.id());
            t.putUUID("Route", s.routeId());
            t.putUUID("Owner", s.ownerId());
            t.putString("Commodity", s.commodityId());
            t.putInt("Qty", s.qty());
            t.putLong("BuyUnitPrice", s.buyUnitPrice());
            t.putLong("DepartDay", s.departDay());
            t.putLong("ArriveDay", s.arriveDay());
            t.putInt("Guard", s.guardLevel());
            t.putInt("RaidBps", s.raidBpsSnapshot()); // P1-5：发车日被劫概率快照，老存档缺此键读 0（不打劫）
            t.putLong("InsuredValuePerUnit", s.insuredValuePerUnit());
            t.putLong("DispatchCost", s.dispatchCost());
            t.putString("Status", s.status().name());
            shipments.add(t);
        }
        data.put("Shipments", shipments);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        CaravanManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag data = root.getCompound(ROOT);
        boolean invalid = data.getInt("Version") != VERSION;
        int skipped = 0;
        ListTag routes = data.getList("Routes", Tag.TAG_COMPOUND);
        int routeCount = 0;
        for (Tag raw : routes) {
            if (routeCount >= MAX_ROUTES) {
                // 截断不是静默丢弃：超限条目计入 skipped，可触发熔断/告警
                skipped += routes.size() - routeCount;
                break;
            }
            try {
                CompoundTag t = (CompoundTag) raw;
                UUID id = t.getUUID("Id");
                UUID owner = t.getUUID("Owner");
                UUID source = t.getUUID("Source");
                UUID dest = t.getUUID("Dest");
                String commodity = t.getString("Commodity");
                int qty = t.getInt("QtyPerTrip");
                int guard = t.getInt("Guard");
                boolean insured = t.getBoolean("Insured");
                CaravanRoute.Status status = CaravanRoute.Status.valueOf(t.getString("Status"));
                long createdDay = t.getLong("CreatedDay");
                CaravanRoute r = new CaravanRoute(id, owner, source, dest, commodity, qty, guard, insured, createdDay);
                r.setStatus(status);
                long sat = t.getLong("SaturationBps");
                if (sat > 0) {
                    r.addSaturation(sat);
                }
                r.addProfit(t.getLong("TotalProfit"));
                CaravanManager.putRouteDirect(r);
                routeCount++;
            } catch (Exception e) {
                skipped++;
            }
        }
        ListTag shipments = data.getList("Shipments", Tag.TAG_COMPOUND);
        int shipCount = 0;
        for (Tag raw : shipments) {
            if (shipCount >= MAX_SHIPMENTS) {
                // 截断不是静默丢弃：超限条目计入 skipped，可触发熔断/告警
                skipped += shipments.size() - shipCount;
                break;
            }
            try {
                CompoundTag t = (CompoundTag) raw;
                CaravanShipment s = new CaravanShipment(
                        t.getUUID("Id"),
                        t.getUUID("Route"),
                        t.getUUID("Owner"),
                        t.getString("Commodity"),
                        t.getInt("Qty"),
                        t.getLong("BuyUnitPrice"),
                        t.getLong("DepartDay"),
                        t.getLong("ArriveDay"),
                        t.getInt("Guard"),
                        t.getInt("RaidBps"),
                        t.getLong("InsuredValuePerUnit"),
                        t.getLong("DispatchCost"));
                if ("ARRIVED".equals(t.getString("Status"))) {
                    s.markArrived();
                }
                CaravanManager.putShipmentDirect(s);
                shipCount++;
            } catch (Exception e) {
                skipped++;
            }
        }
        if (invalid || skipped > 0) {
            ModuleHealthRegistry.restrict(
                    ModuleHealthRegistry.Module.CARAVAN, ModuleRunState.READ_ONLY, "商队存档 " + skipped + " 条损坏已跳过", -1);
        }
    }
}
