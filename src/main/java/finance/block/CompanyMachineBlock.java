package finance.block;

import finance.block.entity.CompanyMachineBlockEntity;
import finance.company.Company;
import finance.factory.MachineOwnership;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 公司机器（加工机、采集机）共用的方块行为：放下时记下放置者、归属放置者的公司并告诉他；
 * 右键时公司成员能打开界面；拆掉时掉落机内物品并注销机器记录；比较器读机内物品。
 */
public abstract class CompanyMachineBlock extends BaseEntityBlock {

    protected CompanyMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof CompanyMachineBlockEntity machine) {
            if (MachineOwnership.mayUse(server, machine.companyId())) machine.openMenu(server);
            else server.displayClientMessage(Component.translatable("block.finance.factory.not_member"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof CompanyMachineBlockEntity machine)) return;
        machine.setPlacer(placer);
        Company company = machine.bindCompany(MachineOwnership.companyForPlacer(placer));
        MachineOwnership.announce(placer, company, getDescriptionId());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CompanyMachineBlockEntity machine) {
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
        return level.getBlockEntity(pos) instanceof CompanyMachineBlockEntity machine
                ? AbstractContainerMenu.getRedstoneSignalFromContainer(machine.inventorySnapshot())
                : 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
