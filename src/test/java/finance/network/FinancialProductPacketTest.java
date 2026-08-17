package finance.network;

import finance.client.FinancialProductClientCache;
import finance.debt.BondStatus;
import finance.debt.LoanStatus;
import finance.bondmarket.BondOrderSide;
import finance.fixedincome.CentralBankBillStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FinancialProductPacketTest {
    @Test void responseRoundTripsBoundedRows() {
        UUID id = UUID.randomUUID();
        FinancialProductResponsePacket packet = new FinancialProductResponsePacket(1, 500, "low", 525,600,700,
                List.of(new FinancialProductResponsePacket.IndexRow("stock:composite",1000,1.5)),
                List.of(new FinancialProductResponsePacket.BondRow(id,"B1",id,BondStatus.ACTIVE,100,2,10,800,20,
                        98,99,900,850,1,2,1,1,100,-2)),
                List.of(new FinancialProductResponsePacket.LoanRow(id,id,LoanStatus.ACTIVE,90,1,900,20)),
                List.of(new FinancialProductResponsePacket.BondOrderRow(id,id,BondOrderSide.BUY,99,1,true)),
                List.of(new FinancialProductResponsePacket.BillRow(id,7,525,10,100,101,CentralBankBillStatus.ACTIVE)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        FinancialProductResponsePacket.encode(packet, buffer);
        FinancialProductResponsePacket decoded = FinancialProductResponsePacket.decode(buffer);
        assertEquals(500, decoded.benchmarkRateBps());
        assertEquals("B1", decoded.bonds().get(0).code());
        assertEquals(90, decoded.loans().get(0).outstanding());
        assertEquals(600, decoded.yield30Bps());
        assertEquals(99, decoded.bondOrders().get(0).price());
    }

    @Test void oversizedResponseListIsRejectedOnDecode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeLong(1); buffer.writeVarInt(500); buffer.writeUtf("",256);
        buffer.writeVarInt(525); buffer.writeVarInt(600); buffer.writeVarInt(700);
        buffer.writeVarInt(FinancialProductResponsePacket.MAX_ROWS + 1);
        assertThrows(IllegalArgumentException.class, () -> FinancialProductResponsePacket.decode(buffer));
    }

    @Test void actionRoundTripsTargetAndNumericFields() {
        UUID id = UUID.randomUUID();
        var packet = new FinancialProductActionPacket(FinancialProductActionPacket.Action.PLACE_BOND_BUY,
                id, 99, 3, 0, 0, 0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        FinancialProductActionPacket.encode(packet, buffer);
        assertEquals(packet, FinancialProductActionPacket.decode(buffer));
    }

    @Test void invalidActionOrdinalIsRejectedDuringDecode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(Integer.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> FinancialProductActionPacket.decode(buffer));
    }

    @Test void staleFinancialResponseCannotOverwriteLatestRequest() {
        FinancialProductClientCache.clear();
        long oldRequest = FinancialProductClientCache.begin();
        long latestRequest = FinancialProductClientCache.begin();
        FinancialProductClientCache.accept(emptyResponse(oldRequest, 100));
        assertEquals(FinancialProductClientCache.State.LOADING, FinancialProductClientCache.get().state());
        FinancialProductClientCache.accept(emptyResponse(latestRequest, 200));
        assertEquals(FinancialProductClientCache.State.READY, FinancialProductClientCache.get().state());
        assertEquals(200, FinancialProductClientCache.get().benchmarkRateBps());
        FinancialProductClientCache.clear();
    }

    private static FinancialProductResponsePacket emptyResponse(long requestId, int benchmark) {
        return new FinancialProductResponsePacket(requestId, benchmark, "", 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
