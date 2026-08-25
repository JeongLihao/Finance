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
 * 金融中心客户端快捷键。
 */
public class FinanceKeyMappings {

    public static final KeyMapping OPEN_FINANCE = new KeyMapping(
            "key.finance.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.finance"
    );

    public static final KeyMapping OPEN_GUIDE = new KeyMapping(
            "key.finance.open_guide", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.finance");
    public static final KeyMapping TOGGLE_TUTORIAL = new KeyMapping(
            "key.finance.toggle_tutorial", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.finance");

    @Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_FINANCE);
            event.register(OPEN_GUIDE);
            event.register(TOGGLE_TUTORIAL);
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
                FinancePacketHandler.CHANNEL.sendToServer(new OpenFinanceGuiPacket());
            }
            while (OPEN_GUIDE.consumeClick()) FinanceGuideClientHandler.open();
            while (TOGGLE_TUTORIAL.consumeClick()) TutorialClientState.toggleVisible();
        }
    }
}
