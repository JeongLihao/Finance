package finance.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketOverviewCodecTest {
    @Test void snapshotWriterAndReaderAreBounded(){List<MarketSnapshot> rows=new ArrayList<>();for(int i=0;i<80;i++)rows.add(new MarketSnapshot("c"+i,1,1,2,0,0,3));FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());MarketSnapshot.writeList(b,rows);assertEquals(MarketSnapshot.MAX_ROWS,MarketSnapshot.readList(b).size());}
    @Test void oversizedCountIsRejectedBeforeAllocation(){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeVarInt(MarketSnapshot.MAX_ROWS+1);assertThrows(IllegalArgumentException.class,()->MarketSnapshot.readList(b));}
}
