package finance.client;

import finance.FinanceMod;
import finance.network.PriceTablePacket;
import finance.util.MoneyFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 客户端的商品价格表（服务端登录时和每分钟推一次），用来在物品悬停提示里写“能卖多少”。
 * 退出服务器时清空，不会把上一个服务器的价格带到下一个。
 */
@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, value = Dist.CLIENT)
public final class ClientPriceCache {

    private ClientPriceCache() {}

    private static final Map<String, PriceTablePacket.Row> PRICES = new HashMap<>();

    public static void accept(List<PriceTablePacket.Row> rows) {
        PRICES.clear();
        for (PriceTablePacket.Row r : rows) PRICES.put(r.itemId(), r);
    }

    public static void clear() {
        PRICES.clear();
    }

    /** 这个物品每个能卖多少；不是市场商品或还没收到价格返回 0。 */
    public static long bid(ItemStack stack) {
        PriceTablePacket.Row row = row(stack);
        return row == null ? 0 : row.bid();
    }

    static PriceTablePacket.Row row(ItemStack stack) {
        if (stack == null || stack.isEmpty() || PRICES.isEmpty()) return null;
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : PRICES.get(key.toString());
    }

    /** 悬停提示末尾加一行：“金融：卖 1.20 磅/个，这组 76.80 磅”。 */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        PriceTablePacket.Row row = row(event.getItemStack());
        if (row == null || row.bid() <= 0) return;
        int count = event.getItemStack().getCount();
        String text = "金融：卖 " + MoneyFormat.pounds(row.bid()) + "/个"
                + (count > 1 ? "，这组 " + MoneyFormat.pounds(row.bid() * count) : "");
        event.getToolTip().add(Component.literal(text).withStyle(ChatFormatting.GOLD));
    }
}
