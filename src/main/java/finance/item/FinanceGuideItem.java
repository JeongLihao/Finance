package finance.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Dependency-free handbook. Rendering is isolated in a client-only handler. */
public final class FinanceGuideItem extends Item {
    public FinanceGuideItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide)DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->finance.client.FinanceGuideClientHandler::open);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}
