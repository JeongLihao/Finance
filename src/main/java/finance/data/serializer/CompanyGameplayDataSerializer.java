package finance.data.serializer;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.gameplay.company.*;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class CompanyGameplayDataSerializer {
    public static final String ROOT = "CompanyGameplay";
    private CompanyGameplayDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag(); data.putInt("Version", 1);
        ListTag profiles = new ListTag();
        for (CompanyGameplayProfile profile : CompanyGameplayManager.profiles().values()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Company", profile.companyId());
            tag.putString("Mode", profile.operatingMode().name()); tag.putLong("LastFallback", profile.lastLegacyFallbackDay());
            ListTag members = new ListTag();
            for (CompanyMemberRecord member : profile.members().values()) { CompoundTag m = new CompoundTag(); m.putUUID("Player", member.playerId()); m.putString("Role", member.role().name()); m.putLong("Joined", member.joinedDay()); members.add(m); }
            tag.put("Members", members);
            ListTag invites = new ListTag();
            for (CompanyInvite invite : profile.invites().values()) { CompoundTag i = new CompoundTag(); i.putUUID("Player", invite.playerId()); i.putString("Role", invite.role().name()); i.putUUID("InvitedBy", invite.invitedBy()); i.putLong("Created", invite.createdDay()); i.putLong("Expires", invite.expiresDay()); invites.add(i); }
            tag.put("Invites", invites);
            tag.put("Warehouses", uuidList(profile.warehouseIds()));
            tag.put("Desks", stringList(profile.deskKeys()));
            tag.put("Operations", stringList(profile.operationKeys()));
            profiles.add(tag);
        }
        data.put("Profiles", profiles);
        ListTag facilities = new ListTag();
        for (CompanyFacilityRecord facility : CompanyFacilityManager.all()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Id", facility.facilityId()); tag.putUUID("Company", facility.companyId());
            tag.putString("Dimension", facility.dimensionId()); tag.putLong("Pos", facility.blockPos().asLong());
            tag.putString("Type", facility.type().name()); tag.putInt("Level", facility.productionLevel());
            tag.putString("Status", facility.status().name()); tag.putLong("LastDay", facility.lastProcessedDay());
            tag.putLong("StatusSince",facility.statusSinceDay());
            if (facility.boundWarehouseId() != null) tag.putUUID("Warehouse", facility.boundWarehouseId());
            tag.put("Operations", stringList(facility.operationKeys())); facilities.add(tag);
        }
        data.put("Facilities", facilities); root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        CompanyGameplayManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) { CompanyGameplayManager.ensureLegacyProfiles(); return; }
        CompoundTag data = root.getCompound(ROOT); ListTag profiles = data.getList("Profiles", Tag.TAG_COMPOUND);
        // Profiles are one-to-one with companies, whose authoritative serializer is
        // already loaded. Do not truncate this dependent list and silently discard
        // member/warehouse references for otherwise valid companies.
        for (int p = 0; p < profiles.size(); p++) {
            CompoundTag tag = profiles.getCompound(p);
            try {
                UUID companyId = NbtDataSupport.readUuidOrNull(tag, "Company"); Company company = CompanyManager.getCompany(companyId);
                CompanyOperatingMode mode = NbtDataSupport.safeEnum(CompanyOperatingMode.class, tag.getString("Mode"), null);
                if (company == null || mode == null) continue;
                CompanyGameplayProfile profile = new CompanyGameplayProfile(companyId, mode);
                profile.setLastLegacyFallbackDay(Math.max(-1, tag.getLong("LastFallback")));
                ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(CompanyGameplayProfile.MAX_MEMBERS, members.size()); i++) {
                    CompoundTag m = members.getCompound(i); UUID player = NbtDataSupport.readUuidOrNull(m, "Player");
                    CompanyMemberRole role = NbtDataSupport.safeEnum(CompanyMemberRole.class, m.getString("Role"), null);
                    if (player != null && !player.equals(company.getOwnerId()) && role != null && role != CompanyMemberRole.OWNER)
                        profile.putMember(new CompanyMemberRecord(player, role, Math.max(0, m.getLong("Joined"))));
                }
                ListTag invites = tag.getList("Invites", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(CompanyGameplayProfile.MAX_INVITES, invites.size()); i++) {
                    CompoundTag in = invites.getCompound(i); UUID player = NbtDataSupport.readUuidOrNull(in, "Player"); UUID invitedBy = NbtDataSupport.readUuidOrNull(in, "InvitedBy");
                    CompanyMemberRole role = NbtDataSupport.safeEnum(CompanyMemberRole.class, in.getString("Role"), null);
                    if (player != null && invitedBy != null && role != null && role != CompanyMemberRole.OWNER)
                        profile.putInvite(new CompanyInvite(player, role, invitedBy, in.getLong("Created"), in.getLong("Expires")));
                }
                ListTag warehouses = tag.getList("Warehouses", Tag.TAG_STRING);
                for (int i = 0; i < Math.min(CompanyGameplayProfile.MAX_WAREHOUSES, warehouses.size()); i++) try {
                    UUID id = UUID.fromString(warehouses.getString(i)); WarehouseRecord record = WarehouseManager.get(id);
                    if (record != null && companyId.equals(record.companyId())) profile.bindWarehouse(id);
                } catch (IllegalArgumentException ignored) {}
                ListTag desks = tag.getList("Desks", Tag.TAG_STRING);
                for (int i = 0; i < Math.min(CompanyGameplayProfile.MAX_DESKS, desks.size()); i++) profile.addDesk(desks.getString(i));
                ListTag ops = tag.getList("Operations", Tag.TAG_STRING);
                for (int i = Math.max(0, ops.size() - CompanyGameplayProfile.MAX_OPERATION_KEYS); i < ops.size(); i++) profile.recordOperation(ops.getString(i));
                CompanyGameplayManager.restore(profile);
            } catch (RuntimeException ignored) {}
        }
        CompanyGameplayManager.ensureLegacyProfiles();
        ListTag facilities = data.getList("Facilities", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(CompanyFacilityManager.MAX_FACILITIES, facilities.size()); i++) {
            CompoundTag tag = facilities.getCompound(i);
            try {
                UUID id = NbtDataSupport.readUuidOrNull(tag, "Id"), company = NbtDataSupport.readUuidOrNull(tag, "Company");
                String dimension = tag.getString("Dimension"); BlockPos pos = BlockPos.of(tag.getLong("Pos"));
                CompanyFacilityType type = NbtDataSupport.safeEnum(CompanyFacilityType.class, tag.getString("Type"), null);
                CompanyFacilityStatus status = NbtDataSupport.safeEnum(CompanyFacilityStatus.class, tag.getString("Status"), null);
                UUID warehouse = NbtDataSupport.readUuidOrNull(tag, "Warehouse");
                if (id == null || company == null || ResourceLocation.tryParse(dimension) == null || type == null || status == null
                        || warehouse != null && (WarehouseManager.get(warehouse) == null || !company.equals(WarehouseManager.get(warehouse).companyId()))) continue;
                CompanyFacilityRecord facility = new CompanyFacilityRecord(id, company, dimension, pos, type,
                        tag.getInt("Level"), status, tag.getLong("LastDay"), warehouse);
                facility.restoreStatusSinceDay(tag.contains("StatusSince")?tag.getLong("StatusSince"):tag.getLong("LastDay"));
                ListTag ops = tag.getList("Operations", Tag.TAG_STRING);
                for (int op = Math.max(0, ops.size() - CompanyFacilityRecord.MAX_OPERATION_KEYS); op < ops.size(); op++) facility.recordOperation(ops.getString(op));
                CompanyFacilityManager.restore(facility);
            } catch (RuntimeException ignored) {}
        }
    }

    private static ListTag uuidList(Iterable<UUID> values) { ListTag list = new ListTag(); for (UUID id : values) list.add(StringTag.valueOf(id.toString())); return list; }
    private static ListTag stringList(Iterable<String> values) { ListTag list = new ListTag(); for (String value : values) list.add(StringTag.valueOf(value)); return list; }
}
