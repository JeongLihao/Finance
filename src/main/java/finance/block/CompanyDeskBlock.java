package finance.block;

import finance.block.entity.CompanyDeskBlockEntity;
import finance.gui.CompanyGameplayGuiOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class CompanyDeskBlock extends BaseEntityBlock {
    private static final FacingShapes SHAPE = FacingShapes.of(
            new double[] {0, 13, 0, 16, 15, 16}, new double[] {1, 0, 1, 3, 13, 15}, new double[] {9, 0, 1, 16, 13, 15});

    public CompanyDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FacingShapes.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FacingShapes.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return FacingShapes.facingPlacer(defaultBlockState(), context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return FacingShapes.rotate(state, rotation);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return FacingShapes.mirror(state, mirror);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state);
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof CompanyDeskBlockEntity desk) {
            if (desk.bindOrValidate(server)) CompanyGameplayGuiOpener.open(server, pos);
            else
                server.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("finance.company_gameplay.desk_denied"),
                        true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CompanyDeskBlockEntity desk)
            desk.unbindLocation();
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompanyDeskBlockEntity(pos, state);
    }
}
