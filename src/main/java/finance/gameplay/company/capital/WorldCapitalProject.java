package finance.gameplay.company.capital;

import net.minecraft.world.item.Item;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A capital project that turns real financing into a physical facility upgrade.
 * Budget and material requirements are frozen at creation; later configuration
 * changes never rewrite an existing project.
 */
public final class WorldCapitalProject {

    public static final int MAX_OPERATION_KEYS = 128;

    private final UUID projectId;
    private final UUID companyId;
    private final WorldCapitalProjectType type;
    private final UUID targetId;
    private final UUID creatorId;
    private final long createdDay;
    private final long deadlineDay;
    private final int targetLevel;
    private final CapitalFundingSource fundingSource;
    private final long budget;
    private final Map<Item, Integer> materials;
    private final boolean governanceRequired;

    private long fundedAmount;
    private UUID proposalId;
    private UUID loanId;
    private UUID bankId;
    private UUID bondId;
    private UUID financingProjectId;
    private boolean fundingSettled;
    private CapitalProjectStatus status;
    private long lastStatusChangeDay;
    private String failureKey;
    private final LinkedHashSet<String> operationKeys = new LinkedHashSet<>();

    public WorldCapitalProject(UUID projectId, UUID companyId, WorldCapitalProjectType type,
                               UUID targetId, UUID creatorId, long createdDay, long deadlineDay,
                               int targetLevel, CapitalFundingSource fundingSource, long budget,
                               Map<Item, Integer> materials, boolean governanceRequired,
                               CapitalProjectStatus status, long lastStatusChangeDay) {
        if (projectId == null || companyId == null || type == null || targetId == null
                || creatorId == null || fundingSource == null || materials == null
                || materials.isEmpty() || status == null
                || createdDay < 0 || deadlineDay <= createdDay || targetLevel < 1
                || budget <= 0) {
            throw new IllegalArgumentException("invalid capital project");
        }
        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("invalid capital project material");
            }
        }
        this.projectId = projectId;
        this.companyId = companyId;
        this.type = type;
        this.targetId = targetId;
        this.creatorId = creatorId;
        this.createdDay = createdDay;
        this.deadlineDay = deadlineDay;
        this.targetLevel = targetLevel;
        this.fundingSource = fundingSource;
        this.budget = budget;
        this.materials = new LinkedHashMap<>(materials);
        this.governanceRequired = governanceRequired;
        this.status = status;
        this.lastStatusChangeDay = Math.max(0, lastStatusChangeDay);
        this.failureKey = "";
    }

    public UUID projectId() { return projectId; }
    public UUID companyId() { return companyId; }
    public WorldCapitalProjectType type() { return type; }
    public UUID targetId() { return targetId; }
    public UUID creatorId() { return creatorId; }
    public long createdDay() { return createdDay; }
    public long deadlineDay() { return deadlineDay; }
    public int targetLevel() { return targetLevel; }
    public CapitalFundingSource fundingSource() { return fundingSource; }
    public long budget() { return budget; }
    public Map<Item, Integer> materials() { return Collections.unmodifiableMap(materials); }
    public boolean governanceRequired() { return governanceRequired; }
    public long fundedAmount() { return fundedAmount; }
    public UUID proposalId() { return proposalId; }
    public UUID loanId() { return loanId; }
    public UUID bankId() { return bankId; }
    public UUID bondId() { return bondId; }
    public UUID financingProjectId() { return financingProjectId; }
    public boolean fundingSettled() { return fundingSettled; }
    public CapitalProjectStatus status() { return status; }
    public long lastStatusChangeDay() { return lastStatusChangeDay; }
    public String failureKey() { return failureKey; }
    public Set<String> operationKeys() { return Set.copyOf(operationKeys); }

    /** Deterministic escrow account for this project's budget. */
    public UUID escrowAccountId() {
        return UUID.nameUUIDFromBytes(("capital-project-escrow:" + projectId).getBytes(StandardCharsets.UTF_8));
    }

    public boolean hasOperation(String key) { return key != null && operationKeys.contains(key); }

    public void recordOperation(String key) {
        if (key == null || key.isBlank() || key.length() > 96) return;
        operationKeys.add(key);
        while (operationKeys.size() > MAX_OPERATION_KEYS) {
            operationKeys.remove(operationKeys.iterator().next());
        }
    }

    void setStatus(CapitalProjectStatus next, long day) {
        if (next == null) return;
        this.status = next;
        this.lastStatusChangeDay = Math.max(0, day);
    }

    void setFailureKey(String key) { this.failureKey = key == null ? "" : key.substring(0, Math.min(96, key.length())); }
    void setFundedAmount(long amount) { this.fundedAmount = Math.max(0, amount); }
    void setProposalId(UUID id) { this.proposalId = id; }
    void setLoanId(UUID id) { this.loanId = id; }
    void setBankId(UUID id) { this.bankId = id; }
    void setBondId(UUID id) { this.bondId = id; }
    void setFinancingProjectId(UUID id) { this.financingProjectId = id; }
    void setFundingSettled(boolean value) { this.fundingSettled = value; }

    public void restoreOperation(String key) { recordOperation(key); }

    /** Serializer-only state restoration after constructor invariants have been checked. */
    public void restoreReferences(long fundedAmount, UUID proposalId, UUID loanId, UUID bankId,
                                  UUID bondId, UUID financingProjectId, boolean fundingSettled,
                                  String failureKey) {
        this.fundedAmount = Math.max(0, fundedAmount);
        this.proposalId = proposalId;
        this.loanId = loanId;
        this.bankId = bankId;
        this.bondId = bondId;
        this.financingProjectId = financingProjectId;
        this.fundingSettled = fundingSettled;
        this.failureKey = failureKey == null ? "" : failureKey.substring(0, Math.min(96, failureKey.length()));
    }
}
