package finance.gameplay.company;

import java.util.UUID;

public record CompanyInvite(UUID playerId, CompanyMemberRole role, UUID invitedBy,
                            long createdDay, long expiresDay) {
    public CompanyInvite {
        if (playerId == null || role == null || role == CompanyMemberRole.OWNER || invitedBy == null
                || createdDay < 0 || expiresDay <= createdDay) throw new IllegalArgumentException("invalid company invite");
    }
}
