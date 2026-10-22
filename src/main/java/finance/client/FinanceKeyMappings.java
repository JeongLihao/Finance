package finance.client;

import com.mojang.blaze3d.platform.InputConstants;
import finance.FinanceMod;
import finance.network.FinancePacketHandler;
import finance.network.OpenFinanceGuiPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 金融中心客户端快捷键：只占一个键（默认 K，原版和常见模组都不用）。
 * 教程中心在金融中心标题栏，教程目标的显示开关在教程中心里。
 */
public class FinanceKeyMappings {

    public static final KeyMapping OPEN_FINANCE = new KeyMapping(
            "key.finance.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.finance");

    @Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_FINANCE);
        }
    }

    @Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, value = Dist.CLIENT)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }
            while (OPEN_FINANCE.consumeClick()) {
                // 手上拿着市场商品时，打开后直接显示这件东西的买卖详情
                var commodity = finance.command.SellCommand.commodityOf(minecraft.player.getMainHandItem());
                finance.client.simple.SimpleClientState.setPendingCommodity(
                        commodity == null ? null : commodity.getId());
                FinancePacketHandler.CHANNEL.sendToServer(new OpenFinanceGuiPacket());
            }
        }
    }
}
