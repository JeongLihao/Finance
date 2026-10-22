package finance.client;

import finance.FinanceMod;
import finance.gui.CompanyGameplayMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * 打开工厂控制器界面时，用粒子描出厂区（默认 9×9×5）的边框，持续 10 秒，方便知道设备要摆在哪里。
 * 纯客户端表现，不影响任何数据。
 */
@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, value = Dist.CLIENT)
public final class FactoryOutlineRenderer {
    static final int DURATION_TICKS = 200;
    static final int INTERVAL_TICKS = 10;
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.95f, 0.75f, 0.2f), 1.0f);

    private static BlockPos controller;
    private static int radius, below, above;
    private static int ticksLeft;

    private FactoryOutlineRenderer() {}

    static void show(CompanyGameplayMenu.FactoryView view, BlockPos pos) {
        if (view == null || pos == null) return;
        // 同一座厂重开界面（例如切换方式后）不重新计时，避免一直闪
        if (ticksLeft > 0 && pos.equals(controller)) return;
        controller = pos.immutable();
        radius = view.zoneRadius();
        below = view.zoneBelow();
        above = view.zoneAbove();
        ticksLeft = DURATION_TICKS;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticksLeft <= 0) return;
        ticksLeft--;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || controller == null) {
            ticksLeft = 0;
            return;
        }
        if (ticksLeft % INTERVAL_TICKS != 0) return;
        double x0 = controller.getX() - radius, x1 = controller.getX() + radius + 1;
        double z0 = controller.getZ() - radius, z1 = controller.getZ() + radius + 1;
        double y0 = controller.getY() - below, y1 = controller.getY() + above + 1;
        // 12 条棱，每格一个粒子
        for (double x = x0; x <= x1; x += 1) {
            spawn(level, x, y0, z0);
            spawn(level, x, y0, z1);
            spawn(level, x, y1, z0);
            spawn(level, x, y1, z1);
        }
        for (double z = z0; z <= z1; z += 1) {
            spawn(level, x0, y0, z);
            spawn(level, x1, y0, z);
            spawn(level, x0, y1, z);
            spawn(level, x1, y1, z);
        }
        for (double y = y0; y <= y1; y += 1) {
            spawn(level, x0, y, z0);
            spawn(level, x1, y, z0);
            spawn(level, x0, y, z1);
            spawn(level, x1, y, z1);
        }
    }

    private static void spawn(ClientLevel level, double x, double y, double z) {
        level.addParticle(DUST, x, y, z, 0, 0, 0);
    }
}
