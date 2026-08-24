package finance.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class SettlementTradeStationItem extends BlockItem {
    public SettlementTradeStationItem(net.minecraft.world.level.block.Block block,Properties properties){super(block,properties);}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> tooltip,TooltipFlag flag){tooltip.add(Component.translatable("tooltip.finance.settlement_trade_station"));}
}
