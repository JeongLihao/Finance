package finance.item;

import finance.cash.CashService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 等值兑换钞（已填写）：金额、开票人和开票日存在 NBT 里，只有服务端在签发时写入。
 * 不记名：谁拿着右键，钱就存进谁的账户，钞票随即收回。
 */
public final class ExchangeNoteItem extends Item {
    private static final String AMOUNT = "Amount";
    private static final String ISSUER = "Issuer";
    private static final String DAY = "Day";

    public ExchangeNoteItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, long amount, String issuer, long day) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(AMOUNT, amount);
        tag.putString(ISSUER, issuer == null ? "" : issuer.substring(0, Math.min(32, issuer.length())));
        tag.putLong(DAY, day);
        return stack;
    }

    public static long amount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getLong(AMOUNT));
    }

    public static String issuer(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(ISSUER);
    }

    public static long day(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getLong(DAY);
    }

    @Override
    public Component getName(ItemStack stack) {
        long amount = amount(stack);
        return amount > 0
                ? Component.translatable("item.finance.exchange_note.valued", CashService.pounds(amount))
                : super.getName(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        CashService.Result r = CashService.redeemExchangeNote(player.getUUID(), amount(stack));
        player.displayClientMessage(
                Component.literal(r.message()).withStyle(r.success() ? ChatFormatting.GREEN : ChatFormatting.RED),
                false);
        if (!r.success()) return InteractionResultHolder.fail(stack);
        stack.shrink(1);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        long amount = amount(stack);
        if (amount <= 0) {
            tooltip.add(
                    Component.translatable("item.finance.exchange_note.invalid").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable("item.finance.cash.value", amount).withStyle(ChatFormatting.GOLD));
        String issuer = issuer(stack);
        if (!issuer.isEmpty()) {
            tooltip.add(Component.translatable("item.finance.exchange_note.issuer", issuer, day(stack))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.finance.exchange_note.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
