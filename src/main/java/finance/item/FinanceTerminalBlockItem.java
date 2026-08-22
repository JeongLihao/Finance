package finance.item;

import finance.gameplay.FinanceTerminalType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FinanceTerminalBlockItem extends BlockItem {
    private final FinanceTerminalType type;

    public FinanceTerminalBlockItem(Block block, Properties properties, FinanceTerminalType type) {
        super(block, properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.finance.terminal." + type.name().toLowerCase()));
    }
}
