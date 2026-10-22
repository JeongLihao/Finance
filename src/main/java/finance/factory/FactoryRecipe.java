package finance.factory;

import finance.config.FinanceConfig;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 加工机配方：一到两种原料 → 一种成品。冶炼类（矿石、圆石、沙子）要烧燃料，和熔炉一样。
 * 输入输出数量写死，生产时严格按配方扣减和产出；耗时可在配置里调。
 *
 * @param second 第二种原料，没有为 null（只有书用到：纸 + 皮革）
 */
public record FactoryRecipe(
        String id,
        Item input,
        int inputCount,
        @Nullable Item second,
        int secondCount,
        Item output,
        int outputCount,
        int defaultCookTime,
        boolean needsFuel) {

    public static final List<FactoryRecipe> ALL = List.of(
            simple("bread", Items.WHEAT, 3, Items.BREAD, 1, 200, false),
            simple("iron", Items.RAW_IRON, 1, Items.IRON_INGOT, 1, 200, true),
            simple("copper", Items.RAW_COPPER, 1, Items.COPPER_INGOT, 1, 200, true),
            simple("gold", Items.RAW_GOLD, 1, Items.GOLD_INGOT, 1, 200, true),
            simple("stone", Items.COBBLESTONE, 1, Items.STONE, 1, 200, true),
            simple("glass", Items.SAND, 1, Items.GLASS, 1, 200, true),
            simple("wool", Items.STRING, 4, Items.WHITE_WOOL, 1, 100, false),
            simple("paper", Items.SUGAR_CANE, 3, Items.PAPER, 3, 100, false),
            new FactoryRecipe("book", Items.PAPER, 3, Items.LEATHER, 1, Items.BOOK, 1, 200, false));

    private static FactoryRecipe simple(
            String id, Item input, int count, Item output, int outputCount, int cookTime, boolean fuel) {
        return new FactoryRecipe(id, input, count, null, 0, output, outputCount, cookTime, fuel);
    }

    /** 实际耗时（tick），按配置的速度百分比缩放。 */
    public int cookTime() {
        return Math.max(1, defaultCookTime * 100 / Math.max(1, FinanceConfig.factorySpeedPercent()));
    }

    public ItemStack resultStack() {
        return new ItemStack(output, outputCount);
    }

    /** 两个原料槽里的东西够不够做一次。 */
    public boolean matches(ItemStack first, ItemStack secondStack) {
        if (first.isEmpty() || !first.is(input) || first.getCount() < inputCount) return false;
        return second == null || (secondStack.is(second) && secondStack.getCount() >= secondCount);
    }

    /** 按两个原料槽找能做的配方，没有返回 null。 */
    public static @Nullable FactoryRecipe find(ItemStack first, ItemStack secondStack) {
        for (FactoryRecipe recipe : ALL) if (recipe.matches(first, secondStack)) return recipe;
        return null;
    }

    public static @Nullable FactoryRecipe byId(String id) {
        for (FactoryRecipe recipe : ALL) if (recipe.id.equals(id)) return recipe;
        return null;
    }

    /** 能放进第一原料槽的物品。 */
    public static boolean isFirstInput(ItemStack stack) {
        return ALL.stream().anyMatch(r -> stack.is(r.input));
    }

    /** 能放进第二原料槽的物品。 */
    public static boolean isSecondInput(ItemStack stack) {
        return ALL.stream().anyMatch(r -> r.second != null && stack.is(r.second));
    }
}
