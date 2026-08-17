package finance.fund;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Immutable product terms. Rates are basis points; one whole share is {@link FundManager#SHARE_SCALE} units. */
public record FundDefinition(String id, String displayName, FundType type, String selectionRule,
                             int managementFeeBps, int subscriptionFeeBps, int redemptionFeeBps,
                             long minimumSubscription, int rebalanceIntervalDays, int maxConstituentWeightBps) {
    public FundDefinition {
        id = normalize(id);
        displayName = displayName == null ? "" : displayName.trim();
        selectionRule = selectionRule == null ? "" : selectionRule.trim();
        if (id.isBlank() || id.length() > 48 || displayName.isBlank() || displayName.length() > 64
                || type == null || managementFeeBps < 0 || subscriptionFeeBps < 0 || redemptionFeeBps < 0
                || managementFeeBps > 10_000 || subscriptionFeeBps > 10_000 || redemptionFeeBps > 10_000
                || minimumSubscription <= 0 || rebalanceIntervalDays <= 0
                || maxConstituentWeightBps <= 0 || maxConstituentWeightBps > 10_000) {
            throw new IllegalArgumentException("Invalid fund definition");
        }
    }

    public UUID custodyAccountId() {
        return UUID.nameUUIDFromBytes(("finance-fund-custody:" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
