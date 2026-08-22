package finance.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletMenuCodecTest {
    @Test
    void walletPayloadRetainsAtMostTenBoundedTransactions() {
        List<WalletMenu.WalletTransaction> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rows.add(new WalletMenu.WalletTransaction(i, "T".repeat(50), i * 10L, i, "O".repeat(100)));
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        WalletMenu.write(buffer, 10, 20, 30, 40, rows);
        assertEquals(10, buffer.readLong());
        assertEquals(20, buffer.readLong());
        assertEquals(30, buffer.readLong());
        assertEquals(40, buffer.readLong());
        List<WalletMenu.WalletTransaction> decoded = WalletMenu.readTransactions(buffer);
        assertEquals(10, decoded.size());
        assertEquals(WalletMenu.MAX_TYPE_LENGTH, decoded.get(0).type().length());
        assertEquals(WalletMenu.MAX_OBJECT_LENGTH, decoded.get(0).objectName().length());
    }

    @Test
    void oversizedTransactionCountIsRejectedBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(WalletMenu.MAX_TRANSACTIONS + 1);
        assertThrows(IllegalArgumentException.class, () -> WalletMenu.readTransactions(buffer));
    }
}
