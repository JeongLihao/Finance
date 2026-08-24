package finance.block.entity;

import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class SettlementTradeStationBlockEntity extends BlockEntity {
    private UUID settlementId=UUID.randomUUID();
    public SettlementTradeStationBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.SETTLEMENT_TRADE_STATION.get(),pos,state);}
    public UUID settlementId(){return settlementId;}
    public void assignIdentity(UUID id){if(id!=null&&!id.equals(settlementId)){settlementId=id;setChanged();}}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putUUID("SettlementId",settlementId);}
    @Override public void load(CompoundTag tag){super.load(tag);settlementId=tag.hasUUID("SettlementId")?tag.getUUID("SettlementId"):UUID.randomUUID();}
}
