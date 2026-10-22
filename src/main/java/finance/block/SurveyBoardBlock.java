package finance.block;

import finance.exploration.*;
import finance.item.SurveyNoteItem;
import finance.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SurveyBoardBlock extends Block {
    private static final FacingShapes SHAPE = FacingShapes.of(new double[] {0, 0, 6, 16, 16, 10});

    public SurveyBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FacingShapes.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ExplorationResult result = ExplorationService.request(sp, pos);
            ExplorationAssignment a = result.assignment();
            if (result.success() && a != null) {
                boolean has = sp.getInventory().items.stream()
                        .anyMatch(stack -> a.id().equals(SurveyNoteItem.assignmentId(stack)));
                if (!has) {
                    ItemStack note = new ItemStack(ModItems.SURVEY_NOTE.get());
                    SurveyNoteItem.bind(note, a.id());
                    if (!sp.addItem(note)) sp.drop(note, false);
                }
                sp.displayClientMessage(
                        Component.translatable(
                                result.messageKey(),
                                ExplorationService.direction(a, pos),
                                ExplorationService.distance(a, pos),
                                a.reward()),
                        true);
            } else sp.displayClientMessage(Component.translatable(result.messageKey()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
