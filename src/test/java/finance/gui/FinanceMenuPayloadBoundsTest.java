package finance.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceMenuPayloadBoundsTest {
    @Test
    void oversizedCollectionCountIsRejectedBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(FinanceMenu.MAX_MARKET_ROWS + 1);
        assertThrows(IllegalArgumentException.class, () -> FinanceMenu.readMarketData(buffer));
    }

    @Test
    void marketWriterCapsRowsAndStringsToProtocolLimits() {
        List<FinanceMenu.MarketRow> rows = new ArrayList<>();
        for (int index = 0; index < FinanceMenu.MAX_MARKET_ROWS + 10; index++) {
            rows.add(new FinanceMenu.MarketRow("x".repeat(80), 1, 1, 1,
                    0, 0, 0, 1, 1, List.of(1L)));
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        FinanceMenu.writeMarketData(buffer, rows);

        List<FinanceMenu.MarketRow> decoded = FinanceMenu.readMarketData(buffer);
        assertEquals(FinanceMenu.MAX_MARKET_ROWS, decoded.size());
        assertEquals(64, decoded.get(0).commodityId().length());
    }

    @Test
    void negativeCollectionCountIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(-1);
        assertThrows(IllegalArgumentException.class,
                () -> FinanceMenu.readBoundedSize(buffer, "test", 4));
    }
}
