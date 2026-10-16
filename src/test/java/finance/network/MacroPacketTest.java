package finance.network;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.macro.MacroEconomyService;
import finance.macro.MacroSnapshotBuilder;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageProfession;
import finance.village.VillageRecord;
import finance.village.VillageService;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MacroPacketTest {
    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "小麦", CommodityCategory.FOOD, 8));
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 10_000_000L);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wheat", 100_000);
        SettlementRecord s = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        EnumMap<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
        mix.put(VillageProfession.FARMER, 10);
        mix.put(VillageProfession.ARTISAN, 0);
        mix.put(VillageProfession.GUARD, 0);
        mix.put(VillageProfession.OTHER, 0);
        UUID budget = AccountManager.getOrCreateSystemAccount(UUID.randomUUID()).getPlayerId();
        assertTrue(VillageManager.restoreVillage(new VillageRecord(s.id(), 10, mix, budget, 0L, 0)));
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @Test void snapshotRoundTripsThroughTheWire() {
        MacroEconomyService.beforeVillages(1);
        VillageService.processDay(null, 1);
        MacroEconomyService.afterVillages(1);
        MacroResponsePacket built = MacroSnapshotBuilder.build(7);
        assertTrue(built.enabled());
        assertEquals(1, built.settlements().size());
        assertEquals("alpha", built.settlements().get(0).name());
        assertEquals("小麦", built.settlements().get(0).goods().get(0).commodity());
        assertEquals(6, built.groups().size());
        assertEquals(4, built.laws().size());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        MacroResponsePacket.encode(built, buf);
        MacroResponsePacket decoded = MacroResponsePacket.decode(buf);
        assertEquals(built, decoded);
    }

    @Test void oversizedRowCountsAreRejected() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(1);
        buf.writeBoolean(true);
        buf.writeLong(0);
        buf.writeVarInt(0);
        buf.writeLong(0);
        buf.writeVarInt(0);
        buf.writeLong(0);
        buf.writeLong(0);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeLong(0);
        buf.writeLong(0);
        buf.writeLong(0);
        buf.writeLong(0);
        buf.writeBoolean(false);
        buf.writeVarInt(1_000);
        assertThrows(IllegalArgumentException.class, () -> MacroResponsePacket.decode(buf));
    }
}
