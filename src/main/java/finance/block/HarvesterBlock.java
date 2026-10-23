package finance.block;

import finance.block.entity.HarvesterBlockEntity;
import finance.factory.HarvestMode;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 采集机（农场、伐木、采矿、剪毛共用）：公司机器的通用行为见 {@link CompanyMachineBlock}，这里只记干哪种活。 */
public final class HarvesterBlock extends CompanyMachineBlock {
    private final HarvestMode mode;

    public HarvesterBlock(HarvestMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    public HarvestMode mode() {
        return mode;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HarvesterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModBlockEntities.HARVESTER.get(), HarvesterBlockEntity::serverTick);
    }
}
