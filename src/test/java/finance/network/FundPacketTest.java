package finance.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FundPacketTest {
    @Test void actionRoundTrips(){var p=new FundActionPacket(FundActionPacket.Action.SUBSCRIBE,"money-short",UUID.randomUUID(),1000,0,7,"op");FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());FundActionPacket.encode(p,b);assertEquals(p,FundActionPacket.decode(b));}
    @Test void excessiveIntervalIsRejected(){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());FundActionPacket.encode(new FundActionPacket(FundActionPacket.Action.CREATE_PLAN,"money-short",null,1000,0,3651,"op"),b);assertThrows(IllegalArgumentException.class,()->FundActionPacket.decode(b));}
}
