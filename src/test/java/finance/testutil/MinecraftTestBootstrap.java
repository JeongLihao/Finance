package finance.testutil;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Initializes vanilla registries for plain JUnit tests that touch Items or Blocks. */
public final class MinecraftTestBootstrap {
    private MinecraftTestBootstrap() {}

    public static synchronized void ensureStarted() {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError forgeNetworkUnavailable) {
            // Forge's mapped unit-test classpath has no live FML network context. Vanilla
            // registries are already complete before that optional hook runs.
            if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse("minecraft:iron_ingot"))) {
                throw forgeNetworkUnavailable;
            }
        }
    }
}
