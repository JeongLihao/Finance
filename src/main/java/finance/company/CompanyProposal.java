package finance.company;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CompanyProposal {

    private final UUID proposalId;
    private final UUID companyId;
    private final UUID creatorId;
    private final CompanyProposalType type;
    private final String title;
    private final String textValue;
    private final long value1;
    private final long value2;
    private final long value3;
    private final long startMcDay;
    private final long endMcDay;
    private final double passRatio;
    private final LocalDateTime createdAt;
    private final Map<UUID, VoteRecord> votes = new LinkedHashMap<>();
    private CompanyProposalStatus status = CompanyProposalStatus.ACTIVE;
    private String resultSummary = "";

    public CompanyProposal(UUID companyId, UUID creatorId, CompanyProposalType type, String title,
                           String textValue, long value1, long value2, long value3,
                           long startMcDay, long endMcDay, double passRatio) {
        this(UUID.randomUUID(), companyId, creatorId, type, title, textValue, value1, value2, value3,
                startMcDay, endMcDay, passRatio, LocalDateTime.now(), CompanyProposalStatus.ACTIVE, "");
    }

    public CompanyProposal(UUID proposalId, UUID companyId, UUID creatorId, CompanyProposalType type,
                           String title, String textValue, long value1, long value2, long value3,
                           long startMcDay, long endMcDay, double passRatio, LocalDateTime createdAt,
                           CompanyProposalStatus status, String resultSummary) {
        this.proposalId = proposalId;
        this.companyId = companyId;
        this.creatorId = creatorId;
        this.type = type;
        this.title = title == null ? "" : title;
        this.textValue = textValue == null ? "" : textValue;
        this.value1 = value1;
        this.value2 = value2;
        this.value3 = value3;
        this.startMcDay = startMcDay;
        this.endMcDay = endMcDay;
        this.passRatio = passRatio;
        this.createdAt = createdAt;
        this.status = status == null ? CompanyProposalStatus.ACTIVE : status;
        this.resultSummary = resultSummary == null ? "" : resultSummary;
    }

    public UUID getProposalId() { return proposalId; }
    public UUID getCompanyId() { return companyId; }
    public UUID getCreatorId() { return creatorId; }
    public CompanyProposalType getType() { return type; }
    public String getTitle() { return title; }
    public String getTextValue() { return textValue; }
    public long getValue1() { return value1; }
    public long getValue2() { return value2; }
    public long getValue3() { return value3; }
    public long getStartMcDay() { return startMcDay; }
    public long getEndMcDay() { return endMcDay; }
    public double getPassRatio() { return passRatio; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Map<UUID, VoteRecord> getVotes() { return votes; }
    public CompanyProposalStatus getStatus() { return status; }
    public String getResultSummary() { return resultSummary; }

    public long getYesVotes() {
        return votes.values().stream().filter(VoteRecord::support).mapToLong(VoteRecord::power).sum();
    }

    public long getNoVotes() {
        return votes.values().stream().filter(vote -> !vote.support()).mapToLong(VoteRecord::power).sum();
    }

    public void addVote(UUID playerId, boolean support, long power) {
        votes.put(playerId, new VoteRecord(support, power));
    }

    public void finish(CompanyProposalStatus status, String resultSummary) {
        this.status = status == null ? CompanyProposalStatus.FAILED : status;
        this.resultSummary = resultSummary == null ? "" : resultSummary;
    }

    public record VoteRecord(boolean support, long power) {}
}
