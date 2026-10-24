package finance.company;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一次进行中的上市认购。认购款放在每次认购独立的托管账户（{@link #escrowAccount()}）里，
 * 认购期结束时按实际认到的股数发股：认购人拿认到的，创始人拿剩下的；认不到最低比例就取消，原路退款。
 */
public final class IpoSubscription {
    public enum Status {
        OPEN,
        LISTED,
        CANCELLED
    }

    private final UUID id;
    private final UUID companyId;
    /** 创始股的去向：玩家公司是所有者，无主（NPC 竞争）公司是它的投资账户。 */
    private final UUID founderAccount;

    private final long issuePrice;
    private final long issueQuantity;
    private final long totalShares;
    private final long startDay;
    private final long endDay;
    private final Map<UUID, Long> subscriptions = new LinkedHashMap<>();
    private Status status;

    public IpoSubscription(
            UUID id,
            UUID companyId,
            UUID founderAccount,
            long issuePrice,
            long issueQuantity,
            long totalShares,
            long startDay,
            long endDay,
            Status status) {
        if (id == null
                || companyId == null
                || founderAccount == null
                || issuePrice <= 0
                || issueQuantity <= 0
                || totalShares < issueQuantity
                || endDay < startDay
                || status == null) throw new IllegalArgumentException("invalid ipo subscription");
        this.id = id;
        this.companyId = companyId;
        this.founderAccount = founderAccount;
        this.issuePrice = issuePrice;
        this.issueQuantity = issueQuantity;
        this.totalShares = totalShares;
        this.startDay = startDay;
        this.endDay = endDay;
        this.status = status;
    }

    /** 认购托管账户：每次认购一个，按认购 id 确定。 */
    public static UUID escrowAccount(UUID subscriptionId) {
        return UUID.nameUUIDFromBytes(("finance-ipo-escrow-" + subscriptionId).getBytes(StandardCharsets.UTF_8));
    }

    public UUID escrowAccount() {
        return escrowAccount(id);
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID founderAccount() {
        return founderAccount;
    }

    public long issuePrice() {
        return issuePrice;
    }

    public long issueQuantity() {
        return issueQuantity;
    }

    public long totalShares() {
        return totalShares;
    }

    public long startDay() {
        return startDay;
    }

    public long endDay() {
        return endDay;
    }

    public Status status() {
        return status;
    }

    void setStatus(Status status) {
        if (status != null) this.status = status;
    }

    public Map<UUID, Long> subscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    public long subscribedBy(UUID account) {
        return account == null ? 0 : subscriptions.getOrDefault(account, 0L);
    }

    public long subscribedShares() {
        long sum = 0;
        for (long v : subscriptions.values()) sum += v;
        return sum;
    }

    public long remainingShares() {
        return Math.max(0, issueQuantity - subscribedShares());
    }

    /** 认购期结束时要达到的最少股数。 */
    public long minimumShares(int minFillBps) {
        long bps = Math.max(1, Math.min(10_000, minFillBps));
        return Math.max(1, (issueQuantity * bps + 9_999) / 10_000);
    }

    void addSubscription(UUID account, long shares) {
        if (account != null && shares > 0) subscriptions.merge(account, shares, Long::sum);
    }

    /** 读档用。 */
    public void restoreSubscription(UUID account, long shares) {
        addSubscription(account, shares);
    }
}
