package finance.gameplay.company;

import java.util.UUID;

public record CompanyMemberRecord(UUID playerId, CompanyMemberRole role, long joinedDay) {
    public CompanyMemberRecord {
        if (playerId == null || role == null || joinedDay < 0) throw new IllegalArgumentException("invalid company member");
    }
}
