package finance.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class FinanceMenuPayloadBoundsTest {
    @Test
    void oversizedCollectionCountIsRejectedBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(MarketRows.MAX_MARKET_ROWS + 1);
        assertThrows(IllegalArgumentException.class, () -> MarketRows.readMarketData(buffer));
    }

    @Test
    void marketWriterCapsRowsAndStringsToProtocolLimits() {
        List<MarketRows.MarketRow> rows = new ArrayList<>();
        for (int index = 0; index < MarketRows.MAX_MARKET_ROWS + 10; index++) {
            rows.add(new MarketRows.MarketRow("x".repeat(80), 1, 1, 1, 0, 0, 0, 1, 1, List.of(1L)));
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        MarketRows.writeMarketData(buffer, rows);

        List<MarketRows.MarketRow> decoded = MarketRows.readMarketData(buffer);
        assertEquals(MarketRows.MAX_MARKET_ROWS, decoded.size());
        assertEquals(64, decoded.get(0).commodityId().length());
    }

    @Test
    void negativeCollectionCountIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(-1);
        assertThrows(IllegalArgumentException.class, () -> MenuCodec.readBoundedSize(buffer, "test", 4));
    }
}
