package finance.caravan;

import java.util.UUID;

/**
 * 在途货运：一次发车的货物抽象。
 *
 * <p>货物在发车时已从玩家资金中扣除买入款（钱→货），在途期间既不在玩家仓库、
 * 也不在任何聚落库存——杜绝"既在路上又在仓库"的双重态（反刷钱核心）。</p>
 */
public final class CaravanShipment {

    /** 在途状态。 */
    public enum Status {
        /** 运输中。 */
        IN_TRANSIT,
        /** 已到货结算。 */
        ARRIVED
    }

    private final UUID id;
    private final UUID routeId;
    private final UUID ownerId;
    private final String commodityId;
    private final int qty;
    /** 买入单价（锁定，用于保险赔付基准）。 */
    private final long buyUnitPrice;

    private final long departDay;
    private final long arriveDay;
    private final int guardLevel;
    /**
     * 发车时锁定的被劫概率快照（基点）。
     *
     * <p>发车时保费按此值计价，结算掷骰也必须用此值：途中 RAID_ALERT 开关
     * 不得改变已发车货运的结算概率，否则"保费恒＞期望赔付"的精算不变量被打破。</p>
     */
    private final int raidBpsSnapshot;
    /** 每单位保额（0 = 未投保）。 */
    private final long insuredValuePerUnit;
    /** 发车时支付的总成本（货款＋保镖工资＋保费），用于盈亏核算。 */
    private final long dispatchCost;

    private Status status;

    public CaravanShipment(
            UUID id,
            UUID routeId,
            UUID ownerId,
            String commodityId,
            int qty,
            long buyUnitPrice,
            long departDay,
            long arriveDay,
            int guardLevel,
            int raidBpsSnapshot,
            long insuredValuePerUnit,
            long dispatchCost) {
        if (id == null || routeId == null || ownerId == null || commodityId == null || commodityId.isBlank()) {
            throw new IllegalArgumentException("货运 id/路线/所有者/商品不能为空");
        }
        if (qty <= 0 || buyUnitPrice <= 0) {
            throw new IllegalArgumentException("数量与买入单价必须 > 0");
        }
        if (arriveDay <= departDay) {
            throw new IllegalArgumentException("到货日必须晚于发车日");
        }
        this.id = id;
        this.routeId = routeId;
        this.ownerId = ownerId;
        this.commodityId = commodityId;
        this.qty = qty;
        this.buyUnitPrice = buyUnitPrice;
        this.departDay = departDay;
        this.arriveDay = arriveDay;
        this.guardLevel = guardLevel;
        this.raidBpsSnapshot = Math.max(0, raidBpsSnapshot);
        this.insuredValuePerUnit = Math.max(0L, insuredValuePerUnit);
        this.dispatchCost = Math.max(0L, dispatchCost);
        this.status = Status.IN_TRANSIT;
    }

    public UUID id() {
        return id;
    }

    public UUID routeId() {
        return routeId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String commodityId() {
        return commodityId;
    }

    public int qty() {
        return qty;
    }

    public long buyUnitPrice() {
        return buyUnitPrice;
    }

    public long departDay() {
        return departDay;
    }

    public long arriveDay() {
        return arriveDay;
    }

    public int guardLevel() {
        return guardLevel;
    }

    public int raidBpsSnapshot() {
        return raidBpsSnapshot;
    }

    public long insuredValuePerUnit() {
        return insuredValuePerUnit;
    }

    public boolean insured() {
        return insuredValuePerUnit > 0;
    }

    public long dispatchCost() {
        return dispatchCost;
    }

    public Status status() {
        return status;
    }

    public void markArrived() {
        this.status = Status.ARRIVED;
    }
}
