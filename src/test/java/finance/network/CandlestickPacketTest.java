package finance.network;

import finance.chart.Candlestick;
import finance.chart.MarketInstrumentType;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandlestickPacketTest {

    @Test
    void onlyDocumentedTimeWindowsAreAccepted() {
        assertTrue(CandlestickRequestPacket.isAllowedLimit(30));
        assertTrue(CandlestickRequestPacket.isAllowedLimit(60));
        assertTrue(CandlestickRequestPacket.isAllowedLimit(120));
        assertFalse(CandlestickRequestPacket.isAllowedLimit(0));
        assertFalse(CandlestickRequestPacket.isAllowedLimit(128));
    }

    @Test
    void responseCannotContainMoreThanOneHundredTwentyEightBars() {
        List<Candlestick> bars = new ArrayList<>();
        for (int day = 0; day < 140; day++) bars.add(Candlestick.carry(day, 10));

        CandlestickResponsePacket packet = new CandlestickResponsePacket(
                1, MarketInstrumentType.STOCK, "ABC", 120, 140, bars);

        assertEquals(120, packet.bars().size());
        assertEquals(20, packet.bars().get(0).mcDay());
    }

    @Test
    void requestAndEmptyResponseRoundTrip() {
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(Unpooled.buffer());
        CandlestickRequestPacket.encode(
                new CandlestickRequestPacket(42, MarketInstrumentType.COMMODITY, "iron", 60), requestBuffer);
        CandlestickRequestPacket request = CandlestickRequestPacket.decode(requestBuffer);
        assertEquals(MarketInstrumentType.COMMODITY, request.type());
        assertEquals("iron", request.id());
        assertEquals(60, request.limit());
        assertEquals(42, request.requestId());

        FriendlyByteBuf responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
        CandlestickResponsePacket.encode(
                new CandlestickResponsePacket(42, MarketInstrumentType.STOCK, "ABC", 60, 9, List.of()), responseBuffer);
        CandlestickResponsePacket response = CandlestickResponsePacket.decode(responseBuffer);
        assertEquals("ABC", response.id());
        assertTrue(response.bars().isEmpty());
        assertEquals(60, response.limit());
        assertEquals(9, response.serverCurrentMcDay());
    }

    @Test
    void overlongIdAndOversizedOrInvalidPayloadAreRejected() {
        FriendlyByteBuf overlong = new FriendlyByteBuf(Unpooled.buffer());
        assertThrows(RuntimeException.class, () -> CandlestickRequestPacket.encode(
                new CandlestickRequestPacket(1, MarketInstrumentType.STOCK, "A".repeat(17), 30), overlong));

        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeLong(1);
        oversized.writeEnum(MarketInstrumentType.STOCK);
        oversized.writeUtf("ABC", 128);
        oversized.writeVarInt(120);
        oversized.writeLong(5);
        oversized.writeBoolean(true);
        oversized.writeVarInt(129);
        assertThrows(IllegalArgumentException.class, () -> CandlestickResponsePacket.decode(oversized));

        FriendlyByteBuf invalidEnum = new FriendlyByteBuf(Unpooled.buffer());
        invalidEnum.writeVarInt(99);
        assertThrows(RuntimeException.class, () -> CandlestickRequestPacket.decode(invalidEnum));
    }
}
