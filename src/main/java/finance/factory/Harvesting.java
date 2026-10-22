package finance.factory;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 采集机在世界里干的活：每次只做一件（收一株作物、砍一段原木、挖一块方块、剪一只羊），
 * 收获进机器的仓里；仓放不下就不动世界。
 */
public final class Harvesting {

    /** 一次干活的结果。 */
    public enum Outcome {
        WORKED,
        NOTHING,
        FULL,
        /** 采矿机挖到底了。 */
        DONE
    }

    /** 农场、伐木、剪毛的范围：机器周围水平 4 格（9×9）。 */
    public static final int RADIUS = 4;
    /** 采矿机的竖井：机器正下方 5×5。 */
    public static final int SHAFT_RADIUS = 2;

    private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);

    private Harvesting() {}

    // ---------------------------------------------------------------- 农场

    public static Outcome farm(ServerLevel level, BlockPos origin, IItemHandler bin) {
        for (BlockPos p :
                BlockPos.betweenClosed(origin.offset(-RADIUS, -1, -RADIUS), origin.offset(RADIUS, 1, RADIUS))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                List<ItemStack> drops = new java.util.ArrayList<>(Block.getDrops(state, level, p, null));
                ItemStack seed = crop.getCloneItemStack(level, p, state);
                takeOne(drops, seed);
                if (!fits(bin, drops)) return Outcome.FULL;
                insertAll(bin, drops);
                level.setBlock(p, crop.getStateForAge(0), Block.UPDATE_ALL);
                return Outcome.WORKED;
            }
            boolean caneTop = state.is(Blocks.SUGAR_CANE)
                    && level.getBlockState(p.below()).is(Blocks.SUGAR_CANE);
            if (caneTop || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
                List<ItemStack> drops = Block.getDrops(state, level, p, null);
                if (!fits(bin, drops)) return Outcome.FULL;
                insertAll(bin, drops);
                // 甘蔗上面那几节会掉成物品，下次捡起来
                level.destroyBlock(p.immutable(), false);
                return Outcome.WORKED;
            }
        }
        return Outcome.NOTHING;
    }

    // ---------------------------------------------------------------- 伐木

    public static Outcome lumber(ServerLevel level, BlockPos origin, IItemHandler bin) {
        for (BlockPos p :
                BlockPos.betweenClosed(origin.offset(-RADIUS, 0, -RADIUS), origin.offset(RADIUS, 0, RADIUS))) {
            BlockPos root = p.immutable();
            if (!level.getBlockState(root).is(BlockTags.LOGS)
                    || !level.getBlockState(root.below()).is(BlockTags.DIRT)) continue;
            BlockPos top = root;
            while (top.getY() - root.getY() < 32
                    && level.getBlockState(top.above()).is(BlockTags.LOGS)) top = top.above();
            if (!hasNaturalLeaves(level, top)) continue; // 只砍真正的树，不拆玩家用原木盖的房子
            BlockState log = level.getBlockState(top);
            List<ItemStack> drops = Block.getDrops(log, level, top, null);
            if (!fits(bin, drops)) return Outcome.FULL;
            insertAll(bin, drops);
            level.destroyBlock(top, false);
            if (top.equals(root)) replant(level, root, bin);
            return Outcome.WORKED;
        }
        return Outcome.NOTHING;
    }

    private static boolean hasNaturalLeaves(ServerLevel level, BlockPos top) {
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    /** 树根砍掉后用仓里的树苗补种。 */
    private static void replant(ServerLevel level, BlockPos root, IItemHandler bin) {
        for (int slot = 0; slot < bin.getSlots(); slot++) {
            ItemStack stack = bin.getStackInSlot(slot);
            if (!stack.is(ItemTags.SAPLINGS) || !(stack.getItem() instanceof BlockItem sapling)) continue;
            BlockState state = sapling.getBlock().defaultBlockState();
            if (!state.canSurvive(level, root)) continue;
            bin.extractItem(slot, 1, false);
            level.setBlock(root, state, Block.UPDATE_ALL);
            return;
        }
    }

    // ---------------------------------------------------------------- 采矿

    /**
     * 挖竖井里下一块能挖的方块。cursor[0] = 当前层的 y，cursor[1] = 这一层挖到第几格（0～24）。
     * 空气、液体、带方块实体的、基岩这类挖不动的、铁镐挖不出东西的都跳过。
     */
    public static Outcome mine(ServerLevel level, BlockPos origin, IItemHandler bin, int[] cursor) {
        int side = SHAFT_RADIUS * 2 + 1;
        for (int tries = 0; tries < side * side * 4; tries++) {
            if (cursor[0] < level.getMinBuildHeight()) return Outcome.DONE;
            int dx = cursor[1] % side - SHAFT_RADIUS, dz = cursor[1] / side - SHAFT_RADIUS;
            BlockPos p = new BlockPos(origin.getX() + dx, cursor[0], origin.getZ() + dz);
            BlockState state = level.getBlockState(p);
            boolean minable = !state.isAir()
                    && state.getFluidState().isEmpty()
                    && !state.hasBlockEntity()
                    && state.getDestroySpeed(level, p) >= 0
                    && (!state.requiresCorrectToolForDrops() || PICKAXE.isCorrectToolForDrops(state));
            if (minable) {
                List<ItemStack> drops = Block.getDrops(state, level, p, null, null, PICKAXE);
                if (!fits(bin, drops)) return Outcome.FULL;
                insertAll(bin, drops);
                level.removeBlock(p, false);
                advance(cursor, side);
                return Outcome.WORKED;
            }
            advance(cursor, side);
        }
        return Outcome.NOTHING;
    }

    private static void advance(int[] cursor, int side) {
        if (++cursor[1] >= side * side) {
            cursor[1] = 0;
            cursor[0]--;
        }
    }

    // ---------------------------------------------------------------- 剪毛

    public static Outcome shear(ServerLevel level, BlockPos origin, IItemHandler bin) {
        AABB area = new AABB(origin).inflate(RADIUS, 2, RADIUS);
        for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, area, Sheep::readyForShearing)) {
            List<ItemStack> wool = sheep.onSheared(null, new ItemStack(Items.SHEARS), level, sheep.blockPosition(), 0);
            level.playSound(null, sheep, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            insertAll(bin, wool); // 放不下的掉在地上，下次再捡
            return Outcome.WORKED;
        }
        return Outcome.NOTHING;
    }

    // ---------------------------------------------------------------- 捡东西、放进仓

    /** 把周围地上的掉落物捡进仓，返回有没有捡到。 */
    public static boolean collect(ServerLevel level, BlockPos origin, IItemHandler bin) {
        boolean any = false;
        AABB area = new AABB(origin).inflate(RADIUS + 1, 3, RADIUS + 1);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            ItemStack stack = entity.getItem();
            ItemStack left = ItemHandlerHelper.insertItemStacked(bin, stack.copy(), false);
            if (left.getCount() == stack.getCount()) continue;
            any = true;
            if (left.isEmpty()) entity.discard();
            else entity.setItem(left);
        }
        return any;
    }

    static boolean fits(IItemHandler bin, List<ItemStack> drops) {
        // 逐个模拟放进去；同一种东西多次放时按累计量判断，宁可保守
        net.minecraftforge.items.ItemStackHandler copy = new net.minecraftforge.items.ItemStackHandler(bin.getSlots());
        for (int i = 0; i < bin.getSlots(); i++)
            copy.setStackInSlot(i, bin.getStackInSlot(i).copy());
        for (ItemStack drop : drops)
            if (!ItemHandlerHelper.insertItemStacked(copy, drop.copy(), false).isEmpty()) return false;
        return true;
    }

    static void insertAll(IItemHandler bin, List<ItemStack> drops) {
        for (ItemStack drop : drops) ItemHandlerHelper.insertItemStacked(bin, drop.copy(), false);
    }

    private static void takeOne(List<ItemStack> drops, ItemStack seed) {
        for (ItemStack drop : drops) {
            if (ItemStack.isSameItem(drop, seed)) {
                drop.shrink(1);
                break;
            }
        }
        drops.removeIf(ItemStack::isEmpty);
    }
}
