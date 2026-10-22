package finance.block;

import finance.block.entity.FactoryBlockEntity;
import finance.gui.FactoryGuiOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 工厂方块 —— 产业链工厂的简化第一版。
 *
 * <p>右键打开仿熔炉 GUI；服务端每 tick 推进生产；破坏时掉落机内物品（不 dup）。</p>
 */
public final class FactoryBlock extends BaseEntityBlock {
    public FactoryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof FactoryBlockEntity factory) {
            FactoryGuiOpener.open(server, factory);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FactoryBlockEntity factory) {
            Containers.dropContents(level, pos, factory.inventorySnapshot());
            // 掉落后立即清空 BE 内物品：堵住开着 GUI 的玩家在 stillValid 失效前
            // 点走槽内真物品的窄窗口 dup（vanilla 熔炉同款）。
            factory.clearInventory();
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FactoryBlockEntity factory) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(factory.inventorySnapshot());
        }
        return 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactoryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(
                        type, finance.registry.ModBlockEntities.FACTORY.get(), FactoryBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }
}
