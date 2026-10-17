package finance.item;

import finance.cash.Denomination;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

/** 实体现金（硬币或纸币）。右键打开“更多操作 → 现金”页，可以存入账户或兑换绿宝石。 */
public final class CashItem extends Item {
    private final Denomination denomination;

    public CashItem(Denomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    public Denomination denomination() { return denomination; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    finance.client.PlayerServicesScreen.open(finance.client.PlayerServicesForm.Page.CASH));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        long value = denomination.value();
        tooltip.add(Component.translatable("item.finance.cash.value", value).withStyle(ChatFormatting.GOLD));
        if (stack.getCount() > 1) {
            tooltip.add(Component.translatable("item.finance.cash.stack_value", value * stack.getCount())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.finance.cash.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
