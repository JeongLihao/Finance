package finance.block.entity;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.gameplay.company.*;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class CompanyFactoryControllerBlockEntity extends BlockEntity {
    private UUID facilityId = UUID.randomUUID();
    private UUID companyId;
    public CompanyFactoryControllerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COMPANY_FACTORY_CONTROLLER.get(), pos, state); }
    public UUID facilityId() { return facilityId; }
    public UUID companyId() { return companyId; }
    public boolean registerOrValidate(ServerPlayer player) {
        if (player == null || level == null) return false;
        CompanyFacilityRecord existing = CompanyFacilityManager.get(facilityId);
        if (existing != null && (!existing.dimensionId().equals(level.dimension().location().toString())
                || !existing.blockPos().equals(worldPosition))) { facilityId = UUID.randomUUID(); existing = null; }
        if (existing != null) return CompanyMembershipService.hasPermission(existing.companyId(), player.getUUID(), CompanyPermission.VIEW_COMPANY);
        Company company = CompanyManager.getCompanyByOwner(player.getUUID());
        if (company == null) company = CompanyManager.getCompanies().stream()
                .filter(candidate -> CompanyMembershipService.hasPermission(candidate.getCompanyId(),
                        player.getUUID(), CompanyPermission.MANAGE_PRODUCTION))
                .findFirst().orElse(null);
        if (company == null) return false;
        CompanyGameplayProfile profile = CompanyGameplayManager.profileFor(company);
        UUID warehouse = profile.warehouseIds().stream().findFirst().orElse(null);
        if (warehouse == null) return false;
        CompanyFacilityRecord record = new CompanyFacilityRecord(facilityId, company.getCompanyId(),
                level.dimension().location().toString(), worldPosition, CompanyFacilityType.FACTORY_CONTROLLER,
                1, CompanyFacilityStatus.ACTIVE, -1, warehouse);
        if (!CompanyFacilityManager.register(record)) return false;
        companyId = company.getCompanyId(); setChanged(); return true;
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putUUID("FacilityId", facilityId); if (companyId != null) tag.putUUID("CompanyId", companyId); }
    @Override public void load(CompoundTag tag) { super.load(tag); facilityId = tag.hasUUID("FacilityId") ? tag.getUUID("FacilityId") : UUID.randomUUID(); companyId = tag.hasUUID("CompanyId") ? tag.getUUID("CompanyId") : null; }
}
