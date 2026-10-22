package finance.gui;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class SettlementMenuCodecTest {
    @Test
    void publicRowsCarryNoSettlementBudgetOrOtherPlayerIdentity() {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        writeRow(b, 16, 200, 7, 10_000, 0);
        SettlementMenu.DemandRow row = SettlementMenu.readRows(b).get(0);
        assertEquals("wheat", row.commodity());
        assertEquals(200, row.reward());
        assertFalse(row.mine());
        assertEquals(10_000, row.localPremiumBps());
    }

    @Test
    void oversizedAndInvalidRowsAreRejectedBeforeDisplay() {
        FriendlyByteBuf tooMany = new FriendlyByteBuf(Unpooled.buffer());
        tooMany.writeVarInt(SettlementMenu.MAX_ROWS + 1);
        assertThrows(IllegalArgumentException.class, () -> SettlementMenu.readRows(tooMany));
        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        writeRow(invalid, 0, 0, -1, 14_001, 10_001);
        assertThrows(IllegalArgumentException.class, () -> SettlementMenu.readRows(invalid));
        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
        truncated.writeVarInt(1);
        truncated.writeUUID(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> SettlementMenu.readRows(truncated));
    }

    @Test
    void stallAndRouteExtrasRoundTrip() {
        UUID stall = UUID.randomUUID(), route = UUID.randomUUID(), dest = UUID.randomUUID();
        var extras = new SettlementMenu.Extras(
                -12,
                java.util.List.of(new SettlementMenu.WantRow("bread", "面包", 40, 15)),
                java.util.List.of(new SettlementMenu.StallRow(stall, "wheat", "小麦", 9, 30, 11)),
                java.util.List.of(new SettlementMenu.RouteRow(route, "河畔村", "煤", 32, 2, true, "PAUSED", -150)),
                java.util.List.of(new SettlementMenu.Choice(dest.toString(), "河畔村")),
                java.util.List.of(new SettlementMenu.Choice("coal", "煤")));
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        extras.write(b);
        assertEquals(extras, SettlementMenu.Extras.read(b));
    }

    @Test
    void oversizedExtrasAreRejected() {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        b.writeVarInt(1000);
        b.writeVarInt(SettlementMenu.MAX_ROWS + 1);
        assertThrows(IllegalArgumentException.class, () -> SettlementMenu.Extras.read(b));
    }

    private static void writeRow(
            FriendlyByteBuf b, int quantity, long reward, long deadline, int premium, int shortage) {
        b.writeVarInt(1);
        b.writeUUID(UUID.randomUUID());
        b.writeUtf("wheat", 64);
        b.writeUtf("food", 32);
        b.writeVarInt(quantity);
        b.writeLong(reward);
        b.writeLong(deadline);
        b.writeUtf("OPEN", 16);
        b.writeBoolean(false);
        b.writeVarInt(premium);
        b.writeVarInt(shortage);
        b.writeUtf("BALANCED", 16);
        b.writeBoolean(true);
    }
}
