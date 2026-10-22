package finance.block;

import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FinanceTerminalBlock extends Block {
    public enum Indicator implements StringRepresentable {
        NORMAL("normal"),
        SHORTAGE("shortage"),
        OFFLINE("offline"),
        RESTRICTED("restricted"),
        RESOLUTION("resolution");
        private final String name;

        Indicator(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Indicator> INDICATOR = EnumProperty.create("indicator", Indicator.class);
    private static final FacingShapes MARKET_SHAPE = FacingShapes.of(
            new double[] {2, 0, 3, 14, 2, 13}, new double[] {6, 2, 6, 10, 7, 10}, new double[] {1, 7, 6, 15, 16, 10});
    private static final FacingShapes SECURITIES_SHAPE = FacingShapes.of(
            new double[] {0, 8, 0, 16, 10, 16},
            new double[] {1, 0, 1, 3, 8, 15},
            new double[] {13, 0, 1, 15, 8, 15},
            new double[] {2, 10, 10, 14, 16, 12});
    private static final FacingShapes BANK_COUNTER_SHAPE =
            FacingShapes.of(new double[] {0, 0, 0, 16, 9, 16}, new double[] {0, 9, 10.5, 16, 16, 11.5});
    private static final FacingShapes CENTRAL_BANK_SHAPE =
            FacingShapes.of(new double[] {0, 0, 0, 16, 12, 16}, new double[] {2, 12, 2, 14, 15, 14});
    private final FinanceTerminalType terminalType;
    private final FacingShapes shape;

    public FinanceTerminalBlock(Properties properties, FinanceTerminalType terminalType) {
        super(properties);
        this.terminalType = terminalType;
        this.shape = switch (terminalType) {
            case MARKET_TERMINAL -> MARKET_SHAPE;
            case SECURITIES_TERMINAL -> SECURITIES_SHAPE;
            case BANK_COUNTER -> BANK_COUNTER_SHAPE;
            case CENTRAL_BANK_CONSOLE -> CENTRAL_BANK_SHAPE;
            default -> FacingShapes.of(new double[] {0, 0, 0, 16, 16, 16});
        };
        registerDefaultState(stateDefinition
                .any()
                .setValue(INDICATOR, Indicator.NORMAL)
                .setValue(FacingShapes.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(INDICATOR, FacingShapes.FACING);
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
        return shape.get(state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide)
            finance.gameplay.WorldTerminalRegistry.register(new finance.gameplay.WorldTerminalRegistry.TerminalRecord(
                    level.dimension().location().toString(), pos, terminalType));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && state.getBlock() != replacement.getBlock())
            finance.gameplay.WorldTerminalRegistry.remove(
                    level.dimension().location().toString(), pos);
        super.onRemove(state, level, pos, replacement, moving);
    }

    public FinanceTerminalType terminalType() {
        return terminalType;
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            FinanceGameplayOpener.openTerminal(serverPlayer, pos, terminalType);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
