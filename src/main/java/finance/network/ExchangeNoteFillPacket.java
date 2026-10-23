package finance.network;

import finance.cash.CashService;
import finance.cycle.EconomyCycleService;
import finance.item.ExchangeNoteItem;
import finance.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * 在空白兑换钞上写金额（客户端 → 服务端）。服务端确认手里拿的是空白兑换钞，从账户扣款，
 * 收走一张空白钞并发一张写好金额的兑换钞。
 */
public record ExchangeNoteFillPacket(boolean offhand, long amount) {
    public static void encode(ExchangeNoteFillPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.offhand);
        buf.writeLong(p.amount);
    }

    public static ExchangeNoteFillPacket decode(FriendlyByteBuf buf) {
        return new ExchangeNoteFillPacket(buf.readBoolean(), buf.readLong());
    }

    public static void handle(ExchangeNoteFillPacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> run(p, c.getSender()));
        c.setPacketHandled(true);
    }

    private static void run(ExchangeNoteFillPacket p, ServerPlayer player) {
        if (player == null) return;
        if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "exchange-note")) {
            reply(player, false, "操作过于频繁");
            return;
        }
        InteractionHand hand = p.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack blank = player.getItemInHand(hand);
        if (!blank.is(ModItems.BLANK_EXCHANGE_NOTE.get())) {
            reply(player, false, "手里要拿着空白兑换钞。");
            return;
        }
        java.util.UUID serial = java.util.UUID.randomUUID();
        CashService.Result r = CashService.issueExchangeNote(player.getUUID(), p.amount, serial);
        reply(player, r.success(), r.message());
        if (!r.success()) return;
        ItemStack note = ExchangeNoteItem.create(
                ModItems.EXCHANGE_NOTE.get(),
                serial,
                p.amount,
                player.getGameProfile().getName(),
                EconomyCycleService.currentMcDay(player.server));
        blank.shrink(1);
        if (blank.isEmpty()) {
            player.setItemInHand(hand, note);
        } else if (!player.getInventory().add(note)) {
            player.drop(note, false);
        }
        player.getInventory().setChanged();
    }

    private static void reply(ServerPlayer player, boolean ok, String message) {
        player.displayClientMessage(
                Component.literal(message).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
    }
}
