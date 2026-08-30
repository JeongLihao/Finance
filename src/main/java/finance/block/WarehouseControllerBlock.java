package finance.block;

import finance.block.entity.WarehouseControllerBlockEntity;
import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class WarehouseControllerBlock extends BaseEntityBlock {
    public enum Indicator implements StringRepresentable { NORMAL("normal"),FULL("full"),OVER_CAPACITY("over_capacity"),PROJECT_PENDING("project_pending");private final String name;Indicator(String name){this.name=name;}public String getSerializedName(){return name;}}
    public static final EnumProperty<Indicator> INDICATOR=EnumProperty.create("indicator",Indicator.class);
    public WarehouseControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(INDICATOR,Indicator.NORMAL));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // Let the cargo item own bind/load/unload interaction instead of opening the warehouse menu first.
        if (player.getItemInHand(hand).is(finance.registry.ModItems.SEALED_CARGO_CRATE.get()))
            return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var exploration = finance.exploration.ExplorationService.verifyAt(serverPlayer,pos,
                    finance.exploration.ExplorationTargetType.WAREHOUSE);
            if(exploration.success())serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    exploration.messageKey(),exploration.assignment().reward()),true);
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof WarehouseControllerBlockEntity warehouse) {
                warehouse.claimIfNeeded(player.getUUID());
                WarehouseRecord record = WarehouseManager.registerOrRecover(serverPlayer, warehouse);
                if (player.isShiftKeyDown() && record != null) {
                    var requirement = finance.warehouse.WarehouseUpgradeRequirementService.requirement(record.tier());
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "finance.warehouse.inspect", record.tier().level(), record.capacityUnits(),
                            record.transferLimit(), finance.warehouse.WarehouseUpgradeRequirementService.summary(requirement)), true);
                    return InteractionResult.CONSUME;
                }
            }
            FinanceGameplayOpener.openTerminal(serverPlayer, pos, FinanceTerminalType.WAREHOUSE_CONTROLLER);
            if (entity instanceof WarehouseControllerBlockEntity warehouse) updateIndicator(level,pos,warehouse.warehouseId());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void updateIndicator(Level level,BlockPos pos,java.util.UUID warehouseId){if(level==null||level.isClientSide)return;var record=WarehouseManager.get(warehouseId);if(record==null)return;boolean pending=record.companyId()!=null&&finance.gameplay.company.capital.CapitalProjectManager.forCompany(record.companyId()).stream().anyMatch(project->warehouseId.equals(project.targetId())&&!project.status().terminal());java.util.UUID custody=finance.warehouse.WarehouseService.custodyOwner(record);long used=WarehouseManager.usedCapacity(custody),capacity=WarehouseManager.totalCapacity(custody);Indicator indicator=pending?Indicator.PROJECT_PENDING:used>capacity?Indicator.OVER_CAPACITY:used==capacity&&capacity>0?Indicator.FULL:Indicator.NORMAL;BlockState state=level.getBlockState(pos);if(state.getBlock() instanceof WarehouseControllerBlock&&state.getValue(INDICATOR)!=indicator)level.setBlock(pos,state.setValue(INDICATOR,indicator),3);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(INDICATOR);}

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarehouseControllerBlockEntity(pos, state);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WarehouseControllerBlockEntity warehouse) {
            WarehouseManager.disable(warehouse.warehouseId());
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "finance.warehouse.removed_warning"), true);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        // playerWillDestroy is not called for explosions, pistons/commands, or direct block replacement.
        // Disable the physical endpoint before BaseEntityBlock removes its block entity; custody remains recoverable.
        if (!level.isClientSide && state.getBlock() != replacement.getBlock()
                && level.getBlockEntity(pos) instanceof WarehouseControllerBlockEntity warehouse) {
            WarehouseManager.disable(warehouse.warehouseId());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
