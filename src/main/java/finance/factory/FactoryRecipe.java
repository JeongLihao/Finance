package finance.factory;

import finance.config.FinanceConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工厂方块配方注册表 —— 第一期 3 条写死配方。
 *
 * <p>反 dup 铁律：输入/输出物品与数量全部写死，生产时严格按配方执行，
 * 不做任何倍率换算。耗时走 {@link FinanceConfig} 可调。</p>
 */
public final class FactoryRecipe {
    private final String id;
    private final Item input;
    private final int inputCount;
    private final Item output;
    private final int outputCount;
    private final int defaultCookTime;

    private FactoryRecipe(String id, Item input, int inputCount, Item output, int outputCount, int defaultCookTime) {
        this.id = id;
        this.input = input;
        this.inputCount = Math.max(1, inputCount);
        this.output = output;
        this.outputCount = Math.max(1, outputCount);
        this.defaultCookTime = Math.max(1, defaultCookTime);
    }

    public String id() { return id; }
    public Item input() { return input; }
    public int inputCount() { return inputCount; }
    public Item output() { return output; }
    public int outputCount() { return outputCount; }

    /** 实际耗时（tick），读配置，钳制 [1, 72000]。 */
    public int cookTime() {
        return Math.max(1, Math.min(72_000, FinanceConfig.factoryCookTime(id, defaultCookTime)));
    }

    public ItemStack resultStack() {
        return new ItemStack(output, outputCount);
    }

    /** 输入栈是否满足本配方（物品相同且数量足够）。 */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(input) && stack.getCount() >= inputCount;
    }

    private static final Map<Item, FactoryRecipe> BY_INPUT;
    static {
        Map<Item, FactoryRecipe> map = new LinkedHashMap<>();
        register(map, new FactoryRecipe("wheat_to_bread", Items.WHEAT, 3, Items.BREAD, 1, 200));
        register(map, new FactoryRecipe("iron_ore_to_ingot", Items.IRON_ORE, 1, Items.IRON_INGOT, 1, 200));
        register(map, new FactoryRecipe("oak_log_to_planks", Items.OAK_LOG, 1, Items.OAK_PLANKS, 4, 100));
        BY_INPUT = Collections.unmodifiableMap(map);
    }

    private static void register(Map<Item, FactoryRecipe> map, FactoryRecipe recipe) {
        map.put(recipe.input, recipe);
    }

    /** 按输入物品查找配方，无匹配返回 null。 */
    public static FactoryRecipe find(ItemStack input) {
        if (input == null || input.isEmpty()) return null;
        return BY_INPUT.get(input.getItem());
    }

    public static Map<Item, FactoryRecipe> all() {
        return BY_INPUT;
    }
}
