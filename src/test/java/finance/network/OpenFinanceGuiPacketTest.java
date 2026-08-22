package finance.network;

import finance.gameplay.FinanceScreenMode;
import finance.gameplay.FinanceTerminalType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenFinanceGuiPacketTest {

    @Test
    void entryTypeAndRequestedModeRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        OpenFinanceGuiPacket.encode(new OpenFinanceGuiPacket(
                FinanceTerminalType.SECURITIES_TERMINAL, FinanceScreenMode.ADVANCED), buffer);

        OpenFinanceGuiPacket decoded = OpenFinanceGuiPacket.decode(buffer);
        assertEquals(FinanceTerminalType.SECURITIES_TERMINAL, decoded.terminalType());
        assertEquals(FinanceScreenMode.ADVANCED, decoded.requestedMode());
    }

    @Test
    void legacyConstructorRemainsAdvancedFullScreenRequest() {
        OpenFinanceGuiPacket packet = new OpenFinanceGuiPacket();
        assertEquals(FinanceTerminalType.LEGACY_FULL_SCREEN, packet.terminalType());
        assertEquals(FinanceScreenMode.ADVANCED, packet.requestedMode());
    }

    @Test
    void invalidEnumOrdinalIsRejectedBeforeHandler() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(999);
        assertThrows(RuntimeException.class, () -> OpenFinanceGuiPacket.decode(buffer));
    }
}
