package finance.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SettlementMenuCodecTest {
    @Test void publicRowsCarryNoSettlementBudgetOrOtherPlayerIdentity(){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeVarInt(1);b.writeUUID(UUID.randomUUID());b.writeUtf("wheat",64);b.writeUtf("food",32);b.writeVarInt(16);b.writeLong(200);b.writeLong(7);b.writeUtf("OPEN",16);b.writeBoolean(false);SettlementMenu.DemandRow row=SettlementMenu.readRows(b).get(0);assertEquals("wheat",row.commodity());assertEquals(200,row.reward());assertFalse(row.mine());}
    @Test void oversizedAndInvalidRowsAreRejectedBeforeDisplay(){FriendlyByteBuf tooMany=new FriendlyByteBuf(Unpooled.buffer());tooMany.writeVarInt(SettlementMenu.MAX_ROWS+1);assertThrows(IllegalArgumentException.class,()->SettlementMenu.readRows(tooMany));FriendlyByteBuf invalid=new FriendlyByteBuf(Unpooled.buffer());invalid.writeVarInt(1);invalid.writeUUID(UUID.randomUUID());invalid.writeUtf("wheat",64);invalid.writeUtf("food",32);invalid.writeVarInt(0);invalid.writeLong(0);invalid.writeLong(-1);invalid.writeUtf("OPEN",16);invalid.writeBoolean(false);assertThrows(IllegalArgumentException.class,()->SettlementMenu.readRows(invalid));}
}
