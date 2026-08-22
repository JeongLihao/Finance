package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;

import java.util.UUID;

public final class CompanyMembershipService {
    public static final long INVITE_DURATION_DAYS = 7;
    private CompanyMembershipService() {}

    public static boolean hasPermission(UUID companyId, UUID playerId, CompanyPermission permission) {
        Company company = CompanyManager.getCompany(companyId);
        if (company == null || playerId == null || permission == null) return false;
        if (playerId.equals(company.getOwnerId())) return true;
        CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        CompanyMemberRecord member = profile == null ? null : profile.members().get(playerId);
        return member != null && member.role().allows(permission);
    }

    public static synchronized CompanyGameplayActionResult invite(UUID actor, UUID companyId, UUID player,
                                                                   CompanyMemberRole role, long day, String key) {
        CompanyGameplayProfile profile = validProfile(actor, companyId, CompanyPermission.MANAGE_MEMBERS, key);
        Company company = CompanyManager.getCompany(companyId);
        if (profile == null || company == null || player == null || player.equals(company.getOwnerId())
                || role == null || role == CompanyMemberRole.OWNER || day < 0)
            return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        if (profile.hasOperation(scoped(actor, key))) return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        profile.purgeExpiredInvites(day);
        if (profile.members().containsKey(player) || !profile.putInvite(new CompanyInvite(player, role, actor,
                day, day + INVITE_DURATION_DAYS))) return CompanyGameplayActionResult.fail("finance.company_gameplay.invite_limit");
        profile.recordOperation(scoped(actor, key)); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.invite_success");
    }

    public static synchronized CompanyGameplayActionResult acceptInvite(UUID player, UUID companyId,
                                                                          long day, String key) {
        CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (!validKey(key) || profile == null || player == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        if (profile.hasOperation(scoped(player, key))) return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        profile.purgeExpiredInvites(day);
        CompanyInvite invite = profile.invites().get(player);
        if (invite == null || day > invite.expiresDay() || !profile.putMember(new CompanyMemberRecord(player,
                invite.role(), day))) return CompanyGameplayActionResult.fail("finance.company_gameplay.invite_missing");
        profile.removeInvite(player); profile.recordOperation(scoped(player, key)); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.join_success");
    }

    public static synchronized CompanyGameplayActionResult rejectInvite(UUID player, UUID companyId, String key) {
        CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (!validKey(key) || profile == null || player == null)
            return CompanyGameplayActionResult.fail("finance.company_gameplay.invite_missing");
        if (profile.hasOperation(scoped(player, key)))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        if (profile.removeInvite(player) == null)
            return CompanyGameplayActionResult.fail("finance.company_gameplay.invite_missing");
        profile.recordOperation(scoped(player, key)); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.invite_rejected");
    }

    public static synchronized CompanyGameplayActionResult changeRole(UUID actor, UUID companyId, UUID player,
                                                                       CompanyMemberRole role, String key) {
        Company company = CompanyManager.getCompany(companyId);
        CompanyGameplayProfile profile = validProfile(actor, companyId, CompanyPermission.MANAGE_MEMBERS, key);
        if (company == null || profile == null || player == null || player.equals(company.getOwnerId())
                || role == null || role == CompanyMemberRole.OWNER || !profile.members().containsKey(player))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        if (!profile.putMember(new CompanyMemberRecord(player, role, profile.members().get(player).joinedDay())))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.member_limit");
        profile.recordOperation(scoped(actor, key)); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.role_success");
    }

    public static synchronized CompanyGameplayActionResult removeMember(UUID actor, UUID companyId, UUID player,
                                                                         String key) {
        Company company = CompanyManager.getCompany(companyId);
        CompanyGameplayProfile profile = validProfile(actor, companyId, CompanyPermission.MANAGE_MEMBERS, key);
        if (company == null || profile == null || player == null || player.equals(company.getOwnerId())
                || profile.removeMember(player) == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        profile.recordOperation(scoped(actor, key)); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.member_removed");
    }

    public static synchronized CompanyGameplayActionResult leaveCompany(UUID player, UUID companyId, String key) {
        Company company = CompanyManager.getCompany(companyId); CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (!validKey(key) || company == null || profile == null || player == null || player.equals(company.getOwnerId()))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        if (profile.hasOperation(scoped(player, key)))
            return CompanyGameplayActionResult.fail("finance.company_gameplay.duplicate_operation");
        if (profile.removeMember(player) == null) return CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
        profile.recordOperation(scoped(player, key)); EconomySavedData.markDirty();
        return CompanyGameplayActionResult.ok("finance.company_gameplay.left_company");
    }

    private static CompanyGameplayProfile validProfile(UUID actor, UUID companyId, CompanyPermission permission, String key) {
        if (!validKey(key) || !hasPermission(companyId, actor, permission)) return null;
        return CompanyGameplayManager.get(companyId);
    }
    private static boolean validKey(String key) { return key != null && !key.isBlank() && key.length() <= 64; }
    private static String scoped(UUID actor, String key) { return actor + ":" + key; }
}
