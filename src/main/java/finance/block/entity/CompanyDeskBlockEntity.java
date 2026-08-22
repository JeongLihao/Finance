package finance.block.entity;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyGameplayProfile;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class CompanyDeskBlockEntity extends BlockEntity {
    private UUID deskId = UUID.randomUUID();
    private UUID companyId;
    private UUID boundBy;
    public CompanyDeskBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COMPANY_DESK.get(), pos, state); }
    public UUID deskId() { return deskId; }
    public UUID companyId() { return companyId; }
    public UUID boundBy() { return boundBy; }

    public boolean bindOrValidate(ServerPlayer player) {
        if (player == null || level == null) return false;
        if (companyId != null) { CompanyGameplayProfile profile=CompanyGameplayManager.get(companyId); return CompanyMembershipService.hasPermission(companyId, player.getUUID(), CompanyPermission.VIEW_COMPANY) || profile != null && profile.invites().containsKey(player.getUUID()); }
        Company company = CompanyManager.getCompanyByOwner(player.getUUID());
        if (company == null) return false;
        CompanyGameplayProfile profile = CompanyGameplayManager.profileFor(company);
        String key = locationKey();
        if (!profile.addDesk(key)) return false;
        companyId = company.getCompanyId(); boundBy = player.getUUID(); setChanged(); return true;
    }
    public void unbindLocation() {
        CompanyGameplayProfile profile = CompanyGameplayManager.get(companyId);
        if (profile != null) profile.removeDesk(locationKey());
    }
    private String locationKey() { return level == null ? "unknown:" + worldPosition.asLong()
            : level.dimension().location() + ":" + worldPosition.asLong(); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putUUID("DeskId", deskId); if (companyId != null) tag.putUUID("CompanyId", companyId); if (boundBy != null) tag.putUUID("BoundBy", boundBy); }
    @Override public void load(CompoundTag tag) { super.load(tag); deskId = tag.hasUUID("DeskId") ? tag.getUUID("DeskId") : UUID.randomUUID(); companyId = tag.hasUUID("CompanyId") ? tag.getUUID("CompanyId") : null; boundBy = tag.hasUUID("BoundBy") ? tag.getUUID("BoundBy") : null; }
}
