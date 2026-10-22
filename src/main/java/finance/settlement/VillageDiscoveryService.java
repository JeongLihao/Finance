package finance.settlement;

import finance.FinanceMod;
import finance.config.FinanceConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家走进原版村庄时自动登记聚落，这样村民集市、人口、工资和本地物价不用等玩家放贸易站就能运转。
 * 每个玩家每 5 秒检查一次；村子附近已经有聚落就跳过。之后在村里放的贸易站会接管这个聚落。
 */
@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID)
public final class VillageDiscoveryService {

    private static final int INTERVAL_TICKS = 100;

    private VillageDiscoveryService() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof ServerPlayer player
                && player.tickCount % INTERVAL_TICKS == 0
                && FinanceConfig.settlementAutoDiscover()) {
            discover(player);
        }
    }

    static SettlementRecord discover(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        StructureStart village =
                level.structureManager().getStructureWithPieceAt(player.blockPosition(), StructureTags.VILLAGE);
        if (village == null || !village.isValid()) return null;
        BlockPos center = village.getBoundingBox().getCenter();
        if (!level.hasChunkAt(center)) return null;
        BlockPos anchor = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
        String dimension = level.dimension().location().toString();
        if (SettlementManager.anyNear(dimension, anchor)) return null;
        UUID id = UUID.randomUUID();
        SettlementRecord record = SettlementManager.registerOrRecover(id, dimension, anchor, SettlementNames.forId(id));
        if (record != null)
            player.sendSystemMessage(Component.translatable("finance.settlement.discovered", record.displayName()));
        return record;
    }
}
