package finance.company;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CompanyFinancingProject {

    private final UUID projectId;
    private final UUID companyId;
    private final String symbol;
    private final long issueQuantity;
    private final long issuePrice;
    private final long fundingTarget;
    private final long deadlineMcDay;
    private final LocalDateTime createdAt;
    private final Map<UUID, Long> subscriptions = new LinkedHashMap<>();

    public CompanyFinancingProject(UUID companyId, String symbol, long issueQuantity, long issuePrice,
                                   long fundingTarget, long deadlineMcDay) {
        this(UUID.randomUUID(), companyId, symbol, issueQuantity, issuePrice,
                fundingTarget, deadlineMcDay, LocalDateTime.now());
    }

    public CompanyFinancingProject(UUID projectId, UUID companyId, String symbol, long issueQuantity,
                                   long issuePrice, long fundingTarget, long deadlineMcDay,
                                   LocalDateTime createdAt) {
        this.projectId = projectId;
        this.companyId = companyId;
        this.symbol = symbol;
        this.issueQuantity = issueQuantity;
        this.issuePrice = issuePrice;
        this.fundingTarget = fundingTarget;
        this.deadlineMcDay = deadlineMcDay;
        this.createdAt = createdAt;
    }

    public UUID getProjectId() { return projectId; }
    public UUID getCompanyId() { return companyId; }
    public String getSymbol() { return symbol; }
    public long getIssueQuantity() { return issueQuantity; }
    public long getIssuePrice() { return issuePrice; }
    public long getFundingTarget() { return fundingTarget; }
    public long getDeadlineMcDay() { return deadlineMcDay; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Map<UUID, Long> getSubscriptions() { return subscriptions; }

    public long getSubscribedShares() {
        long total = 0;
        for (long shares : subscriptions.values()) {
            total += shares;
        }
        return total;
    }

    public long getRaisedAmount() {
        try {
            return Math.multiplyExact(getSubscribedShares(), issuePrice);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    public long getRemainingShares() {
        return Math.max(0, issueQuantity - getSubscribedShares());
    }

    public boolean isFunded() {
        return getRaisedAmount() >= fundingTarget;
    }

    public void addSubscription(UUID playerId, long shares) {
        if (playerId != null && shares > 0) {
            subscriptions.merge(playerId, shares, Long::sum);
        }
    }
}
