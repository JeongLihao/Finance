package finance.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Horizontal facing for the furniture-style blocks, and outline shapes that follow their models.
 * Models are authored with the front facing north; boxes are given in pixels for that orientation
 * and rotated the same way the blockstates rotate the model.
 */
public final class FacingShapes {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    private FacingShapes(double[][] boxes) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, rotated(b, facing));
            }
            shapes.put(facing, shape.optimize());
        }
    }

    /** Each box is {x1, y1, z1, x2, y2, z2} in pixels, front facing north. */
    public static FacingShapes of(double[]... boxes) {
        return new FacingShapes(boxes);
    }

    public VoxelShape get(BlockState state) {
        return shapes.get(state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
    }

    /** The front faces the player who places the block. */
    public static BlockState facingPlacer(BlockState state, BlockPlaceContext context) {
        return state.setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public static BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public static BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private static VoxelShape rotated(double[] b, Direction facing) {
        double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        // Same turn as the blockstate's y rotation: north -> east -> south -> west.
        return switch (facing) {
            case EAST -> Block.box(16 - z2, b[1], x1, 16 - z1, b[4], x2);
            case SOUTH -> Block.box(16 - x2, b[1], 16 - z2, 16 - x1, b[4], 16 - z1);
            case WEST -> Block.box(z1, b[1], 16 - x2, z2, b[4], 16 - x1);
            default -> Block.box(x1, b[1], z1, x2, b[4], z2);
        };
    }
}
