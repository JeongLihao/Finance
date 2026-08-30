package finance.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SettlementMenuCodecTest {
    @Test void publicRowsCarryNoSettlementBudgetOrOtherPlayerIdentity(){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());writeRow(b,16,200,7,10_000,0);SettlementMenu.DemandRow row=SettlementMenu.readRows(b).get(0);assertEquals("wheat",row.commodity());assertEquals(200,row.reward());assertFalse(row.mine());assertEquals(10_000,row.localPremiumBps());}
    @Test void oversizedAndInvalidRowsAreRejectedBeforeDisplay(){FriendlyByteBuf tooMany=new FriendlyByteBuf(Unpooled.buffer());tooMany.writeVarInt(SettlementMenu.MAX_ROWS+1);assertThrows(IllegalArgumentException.class,()->SettlementMenu.readRows(tooMany));FriendlyByteBuf invalid=new FriendlyByteBuf(Unpooled.buffer());writeRow(invalid,0,0,-1,14_001,10_001);assertThrows(IllegalArgumentException.class,()->SettlementMenu.readRows(invalid));FriendlyByteBuf truncated=new FriendlyByteBuf(Unpooled.buffer());truncated.writeVarInt(1);truncated.writeUUID(UUID.randomUUID());assertThrows(IllegalArgumentException.class,()->SettlementMenu.readRows(truncated));}
    private static void writeRow(FriendlyByteBuf b,int quantity,long reward,long deadline,int premium,int shortage){b.writeVarInt(1);b.writeUUID(UUID.randomUUID());b.writeUtf("wheat",64);b.writeUtf("food",32);b.writeVarInt(quantity);b.writeLong(reward);b.writeLong(deadline);b.writeUtf("OPEN",16);b.writeBoolean(false);b.writeVarInt(premium);b.writeVarInt(shortage);b.writeUtf("BALANCED",16);b.writeBoolean(true);}
}
