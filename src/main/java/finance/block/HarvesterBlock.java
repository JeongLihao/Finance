package finance.block;

import finance.block.entity.HarvesterBlockEntity;
import finance.company.Company;
import finance.factory.HarvestMode;
import finance.factory.MachineOwnership;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** 采集机（农场、伐木、采矿、剪毛共用）：放下时归属放置者的公司；右键打开收获仓；破坏时掉落里面的东西。 */
public final class HarvesterBlock extends BaseEntityBlock {
    private final HarvestMode mode;

    public HarvesterBlock(HarvestMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    public HarvestMode mode() {
        return mode;
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof HarvesterBlockEntity machine) {
            if (MachineOwnership.mayUse(server, machine.companyId()))
                NetworkHooks.openScreen(server, machine, buffer -> buffer.writeBlockPos(pos));
            else server.displayClientMessage(Component.translatable("block.finance.factory.not_member"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof HarvesterBlockEntity machine)) return;
        Company company = machine.bindCompany(MachineOwnership.companyForPlacer(placer));
        MachineOwnership.announce(placer, company, "block.finance." + mode.blockId());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HarvesterBlockEntity machine) {
            Containers.dropContents(level, pos, machine.inventorySnapshot());
            machine.clearInventory();
            machine.unregister();
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
        return level.getBlockEntity(pos) instanceof HarvesterBlockEntity machine
                ? AbstractContainerMenu.getRedstoneSignalFromContainer(machine.inventorySnapshot())
                : 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
