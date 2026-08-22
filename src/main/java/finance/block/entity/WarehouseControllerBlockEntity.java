package finance.block.entity;

import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class WarehouseControllerBlockEntity extends BlockEntity {
    private UUID warehouseId = UUID.randomUUID();
    private UUID ownerId;
    private boolean created;

    public WarehouseControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAREHOUSE_CONTROLLER.get(), pos, state);
    }

    public void claimIfNeeded(UUID playerId) {
        if (!created && playerId != null) {
            ownerId = playerId;
            created = true;
            setChanged();
        }
    }

    public void assignIdentity(UUID warehouseId, UUID ownerId) {
        if (warehouseId == null || ownerId == null) return;
        this.warehouseId = warehouseId;
        this.ownerId = ownerId;
        this.created = true;
        setChanged();
    }

    public UUID warehouseId() { return warehouseId; }
    public UUID ownerId() { return ownerId; }
    public boolean created() { return created; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID("WarehouseId", warehouseId);
        if (ownerId != null) tag.putUUID("OwnerId", ownerId);
        tag.putBoolean("Created", created);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        warehouseId = tag.hasUUID("WarehouseId") ? tag.getUUID("WarehouseId") : UUID.randomUUID();
        ownerId = tag.hasUUID("OwnerId") ? tag.getUUID("OwnerId") : null;
        created = tag.getBoolean("Created") && ownerId != null;
    }
}
