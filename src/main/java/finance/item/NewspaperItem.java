package finance.item;

import java.util.List;
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
import org.jetbrains.annotations.Nullable;

/** 《金融周报》与号外：右键打开报纸阅读界面。内容见 {@link NewspaperData}；渲染类只在客户端加载。 */
public final class NewspaperItem extends Item {
    public NewspaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            NewspaperData.Paper paper = NewspaperData.read(stack);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> finance.client.NewspaperClientHandler.open(paper));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        NewspaperData.Paper paper = NewspaperData.read(stack);
        if (paper == null) return super.getName(stack);
        return Component.translatable(
                paper.extra() ? "item.finance.newspaper.extra" : "item.finance.newspaper.weekly", paper.number());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        NewspaperData.Paper paper = NewspaperData.read(stack);
        if (paper == null) {
            tooltip.add(Component.translatable("item.finance.newspaper.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!paper.headline().isBlank()) {
            tooltip.add(Component.literal(paper.headline())
                    .withStyle(paper.extra() ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.finance.newspaper.day", paper.day())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
