package finance.gameplay.company;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CompanyGameplayProfile {
    public static final int MAX_MEMBERS = 64;
    public static final int MAX_INVITES = 32;
    public static final int MAX_WAREHOUSES = 8;
    public static final int MAX_DESKS = 8;
    public static final int MAX_OPERATION_KEYS = 256;

    private final UUID companyId;
    private CompanyOperatingMode operatingMode;
    private final Map<UUID, CompanyMemberRecord> members = new LinkedHashMap<>();
    private final Map<UUID, CompanyInvite> invites = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> warehouseIds = new LinkedHashSet<>();
    private final LinkedHashSet<String> deskKeys = new LinkedHashSet<>();
    private final LinkedHashSet<String> operationKeys = new LinkedHashSet<>();
    private long lastLegacyFallbackDay = -1;

    public CompanyGameplayProfile(UUID companyId, CompanyOperatingMode operatingMode) {
        if (companyId == null || operatingMode == null) throw new IllegalArgumentException("invalid company gameplay profile");
        this.companyId = companyId;
        this.operatingMode = operatingMode;
    }

    public UUID companyId() { return companyId; }
    public CompanyOperatingMode operatingMode() { return operatingMode; }
    public void setOperatingMode(CompanyOperatingMode mode) { if (mode != null) operatingMode = mode; }
    public Map<UUID, CompanyMemberRecord> members() { return Map.copyOf(members); }
    public Map<UUID, CompanyInvite> invites() { return Map.copyOf(invites); }
    public Set<UUID> warehouseIds() { return Set.copyOf(warehouseIds); }
    public Set<String> deskKeys() { return Set.copyOf(deskKeys); }
    public Set<String> operationKeys() { return Set.copyOf(operationKeys); }
    public long lastLegacyFallbackDay() { return lastLegacyFallbackDay; }
    public void setLastLegacyFallbackDay(long day) { if (day >= -1) lastLegacyFallbackDay = day; }

    public boolean putMember(CompanyMemberRecord member) {
        if (member == null || (!members.containsKey(member.playerId()) && members.size() >= MAX_MEMBERS)) return false;
        members.put(member.playerId(), member); return true;
    }
    public CompanyMemberRecord removeMember(UUID player) { return members.remove(player); }
    public boolean putInvite(CompanyInvite invite) {
        if (invite == null || (!invites.containsKey(invite.playerId()) && invites.size() >= MAX_INVITES)) return false;
        invites.put(invite.playerId(), invite); return true;
    }
    public CompanyInvite removeInvite(UUID player) { return invites.remove(player); }
    public void purgeExpiredInvites(long day) { invites.values().removeIf(invite -> day > invite.expiresDay()); }
    public boolean bindWarehouse(UUID id) { return id != null && (warehouseIds.contains(id)
            || warehouseIds.size() < MAX_WAREHOUSES && warehouseIds.add(id)); }
    public void unbindWarehouse(UUID id) { warehouseIds.remove(id); }
    public boolean addDesk(String key) { return key != null && !key.isBlank() && key.length() <= 160
            && (deskKeys.contains(key) || deskKeys.size() < MAX_DESKS && deskKeys.add(key)); }
    public void removeDesk(String key) { deskKeys.remove(key); }
    public boolean hasOperation(String key) { return key != null && operationKeys.contains(key); }
    public void recordOperation(String key) {
        if (key == null || key.isBlank() || key.length() > 96) return;
        operationKeys.add(key);
        while (operationKeys.size() > MAX_OPERATION_KEYS) operationKeys.remove(operationKeys.iterator().next());
    }
}
