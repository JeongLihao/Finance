package finance.caravan;

import finance.util.MathUtil;
import java.util.UUID;

/**
 * 商队路线：玩家在两个聚落之间建立的抽象贸易路线。
 *
 * <p>纯数据对象，无世界实体。商队每天自动跑一趟（见 {@link CaravanService#processDay}），
 * 赚取两地价差。路线饱和度（saturationBps）随发车累积、每日衰减，是负反馈机制的一半
 * （另一半是 {@code MarketPrice} 双向动量，见设计文档）。</p>
 */
public final class CaravanRoute {

    /** 路线状态。 */
    public enum Status {
        /** 正常发车。 */
        ACTIVE,
        /** 玩家暂停，不发车；在途货运继续走完。 */
        PAUSED,
        /** 已关闭，不再发车；在途货运继续走完。 */
        CLOSED
    }

    /** 保镖等级合法范围。 */
    public static final int MIN_GUARD_LEVEL = 0, MAX_GUARD_LEVEL = 3;

    private final UUID id;
    private final UUID ownerId;
    private final UUID sourceSettlementId;
    private final UUID destSettlementId;
    private final String commodityId;
    private final int qtyPerTrip;
    private final int guardLevel;
    private final boolean insured;
    private Status status;
    private final long createdDay;
    /** 饱和度（基点）：跑得越勤，卖价折扣越深。 */
    private long saturationBps;
    /** 累计盈亏（记账用，可为负）。 */
    private long totalProfit;

    public CaravanRoute(
            UUID id,
            UUID ownerId,
            UUID sourceSettlementId,
            UUID destSettlementId,
            String commodityId,
            int qtyPerTrip,
            int guardLevel,
            boolean insured,
            long createdDay) {
        if (id == null
                || ownerId == null
                || sourceSettlementId == null
                || destSettlementId == null
                || commodityId == null
                || commodityId.isBlank()) {
            throw new IllegalArgumentException("路线 id/所有者/聚落/商品不能为空");
        }
        if (sourceSettlementId.equals(destSettlementId)) {
            throw new IllegalArgumentException("起点与终点不能是同一聚落");
        }
        if (qtyPerTrip <= 0) {
            throw new IllegalArgumentException("每趟数量必须 > 0");
        }
        if (guardLevel < MIN_GUARD_LEVEL || guardLevel > MAX_GUARD_LEVEL) {
            throw new IllegalArgumentException("保镖等级越界: " + guardLevel);
        }
        this.id = id;
        this.ownerId = ownerId;
        this.sourceSettlementId = sourceSettlementId;
        this.destSettlementId = destSettlementId;
        this.commodityId = commodityId;
        this.qtyPerTrip = qtyPerTrip;
        this.guardLevel = guardLevel;
        this.insured = insured;
        this.status = Status.ACTIVE;
        this.createdDay = createdDay;
        this.saturationBps = 0;
        this.totalProfit = 0;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID sourceSettlementId() {
        return sourceSettlementId;
    }

    public UUID destSettlementId() {
        return destSettlementId;
    }

    public String commodityId() {
        return commodityId;
    }

    public int qtyPerTrip() {
        return qtyPerTrip;
    }

    public int guardLevel() {
        return guardLevel;
    }

    public boolean insured() {
        return insured;
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status s) {
        if (s != null) {
            this.status = s;
        }
    }

    public long createdDay() {
        return createdDay;
    }

    public long saturationBps() {
        return saturationBps;
    }

    /** 饱和度钳制在 [0, 1_000_000]，防止溢出与过度折扣。 */
    public void addSaturation(long deltaBps) {
        if (deltaBps <= 0) {
            return;
        }
        long next = saturationBps + deltaBps;
        this.saturationBps = Math.min(1_000_000L, Math.max(0L, next));
    }

    /** 每日衰减：saturationBps ×= (10000 - decayBps) / 10000。 */
    public void decaySaturation(long decayBps) {
        if (decayBps <= 0 || saturationBps <= 0) {
            return;
        }
        long keep = Math.max(0L, 10_000L - Math.min(10_000L, decayBps));
        this.saturationBps = saturationBps * keep / 10_000L;
    }

    public long totalProfit() {
        return totalProfit;
    }

    public void addProfit(long delta) {
        // 饱和加法防 long 回绕；保留符号（盈亏可为负），不能用 NonNegative 变体（会吞掉亏损）
        this.totalProfit = MathUtil.saturatedAdd(this.totalProfit, delta);
    }
}
