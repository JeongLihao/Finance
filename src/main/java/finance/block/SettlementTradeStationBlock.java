package finance.block;

import finance.block.entity.SettlementTradeStationBlockEntity;
import finance.gui.SettlementGuiOpener;
import finance.settlement.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class SettlementTradeStationBlock extends BaseEntityBlock {
    public enum Indicator implements StringRepresentable {
        GREEN("green"),
        YELLOW("yellow"),
        RED("red");
        private final String n;

        Indicator(String n) {
            this.n = n;
        }

        public String getSerializedName() {
            return n;
        }
    }

    public static final EnumProperty<Indicator> INDICATOR = EnumProperty.create("indicator", Indicator.class);
    private static final FacingShapes SHAPE = FacingShapes.of(
            new double[] {0, 0, 0, 16, 9, 7},
            new double[] {0, 0, 13, 2, 16, 15},
            new double[] {14, 0, 13, 16, 16, 15},
            new double[] {0, 14, 0, 16, 16, 16});

    public SettlementTradeStationBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition
                .any()
                .setValue(INDICATOR, Indicator.YELLOW)
                .setValue(FacingShapes.FACING, Direction.NORTH));
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
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            var result = finance.exploration.ExplorationService.verifyAt(
                    sp, pos, finance.exploration.ExplorationTargetType.SETTLEMENT);
            if (result.success())
                sp.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                result.messageKey(), result.assignment().reward()),
                        true);
            SettlementGuiOpener.open(sp, pos, "finance.settlement.ready");
            finance.macro.NewsService.claimOnVisit(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SettlementTradeStationBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(INDICATOR, FacingShapes.FACING);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide
                && state.getBlock() != replacement.getBlock()
                && level.getBlockEntity(pos) instanceof SettlementTradeStationBlockEntity be)
            SettlementManager.disable(be.settlementId());
        super.onRemove(state, level, pos, replacement, moving);
    }
}
