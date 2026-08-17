package finance.network;

import finance.insurance.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InsurancePacketTest {
 @Test void responseRoundTripPreservesBoundedRows(){
  var id=UUID.randomUUID();var risk=new CompanyRiskService.Summary(1,2,3,4,5,6,7,8,"none","LOW","ok");
  var packet=new InsuranceResponsePacket(1,true,10,20,30,List.of(new InsuranceResponsePacket.PolicyRow(id,InsuranceProduct.INVENTORY_DISASTER,id,id,PolicyStatus.ACTIVE,1,2,3,2,1)),List.of(new InsuranceResponsePacket.ClaimRow(id,id,id,ClaimStatus.APPROVED,4,3,1,"verified")),risk);
  var buffer=new FriendlyByteBuf(Unpooled.buffer());InsuranceResponsePacket.encode(packet,buffer);var decoded=InsuranceResponsePacket.decode(buffer);
  assertEquals(packet,decoded);
 }
 @Test void decoderRejectsOversizedCollections(){var b=new FriendlyByteBuf(Unpooled.buffer());b.writeLong(1);b.writeBoolean(false);b.writeLong(0);b.writeLong(0);b.writeLong(0);b.writeVarInt(InsuranceResponsePacket.MAX_ROWS+1);assertThrows(IllegalArgumentException.class,()->InsuranceResponsePacket.decode(b));}
}
