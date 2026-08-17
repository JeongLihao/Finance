package finance.network;

import finance.fund.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FundResponsePacketTest {
    @Test void responseRoundTripsBoundedPrivateRows(){var row=new FundResponsePacket.FundRow("money-short","货币与短债基金",FundType.MONEY_MARKET,FundStatus.ACTIVE,10_000,9_900,1_000,0,1_000,10,5,5,1,2,3,"A",true,"");var p=new FundResponsePacket(1,List.of(row),List.of(),List.of());FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());FundResponsePacket.encode(p,b);assertEquals(p,FundResponsePacket.decode(b));}
    @Test void oversizedListIsRejectedBeforeAllocation(){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeLong(1);b.writeVarInt(FundResponsePacket.MAX_ROWS+1);assertThrows(IllegalArgumentException.class,()->FundResponsePacket.decode(b));}
}
