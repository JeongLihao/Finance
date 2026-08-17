package finance.network;

import finance.client.FuturesClientCache;
import finance.futures.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FuturesPacketTest {
    @Test void responseRoundTripsBoundedPrivateState(){UUID id=UUID.randomUUID();var p=new FuturesResponsePacket(1,1000,200,900,300,180,500,MarginRiskStatus.NORMAL,10_000,0,List.of(new FuturesResponsePacket.ContractRow(id,"IRON10","iron",FuturesContractStatus.TRADING,10,9,10,100,99,98,5,7)),List.of(new FuturesResponsePacket.PositionRow(id,2,100,99,20,5)),List.of(new FuturesResponsePacket.OrderRow(id,id,FuturesOrderSide.BUY,90,1,true)),List.of(new FuturesResponsePacket.SettlementRow(id,1,99,0,0,false)));FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());FuturesResponsePacket.encode(p,b);var d=FuturesResponsePacket.decode(b);assertEquals(1000,d.marginCash());assertEquals("IRON10",d.contracts().get(0).code());assertEquals(7,d.contracts().get(0).dailyVolume());assertEquals(2,d.positions().get(0).signedQuantity());assertTrue(d.orders().get(0).owned());}
    @Test void oversizedListIsRejectedBeforeAllocation(){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeLong(1);for(int i=0;i<6;i++)b.writeLong(0);b.writeEnum(MarginRiskStatus.NORMAL);b.writeLong(0);b.writeLong(0);b.writeVarInt(FuturesResponsePacket.MAX_ROWS+1);assertThrows(IllegalArgumentException.class,()->FuturesResponsePacket.decode(b));}
    @Test void actionRoundTripsAndOverlongCommodityIsRejected(){var p=new FuturesActionPacket(FuturesActionPacket.Action.CREATE_CONTRACT,null,"iron",0,0,0,30);FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());FuturesActionPacket.encode(p,b);assertEquals(p,FuturesActionPacket.decode(b));FriendlyByteBuf bad=new FriendlyByteBuf(Unpooled.buffer());bad.writeVarInt(FuturesActionPacket.Action.CREATE_CONTRACT.ordinal());bad.writeBoolean(false);bad.writeUtf("x".repeat(65));assertThrows(RuntimeException.class,()->FuturesActionPacket.decode(bad));}
    @Test void staleResponseCannotOverwriteLatestRequest(){FuturesClientCache.clear();long old=FuturesClientCache.begin(),latest=FuturesClientCache.begin();FuturesClientCache.accept(empty(old,100));assertEquals(FuturesClientCache.State.LOADING,FuturesClientCache.get().state());FuturesClientCache.accept(empty(latest,200));assertEquals(200,FuturesClientCache.get().data().marginCash());FuturesClientCache.clear();}
    private static FuturesResponsePacket empty(long id,long cash){return new FuturesResponsePacket(id,cash,0,cash,0,0,cash,MarginRiskStatus.NORMAL,0,0,List.of(),List.of(),List.of(),List.of());}
}
