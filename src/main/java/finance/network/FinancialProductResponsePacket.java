package finance.network;

import finance.debt.BondStatus;
import finance.debt.LoanStatus;
import finance.bondmarket.BondOrderSide;
import finance.fixedincome.CentralBankBillStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record FinancialProductResponsePacket(long requestId, int benchmarkRateBps, String riskSummary,
                                              int yield7Bps, int yield30Bps, int yield90Bps,
                                              List<IndexRow> indices, List<BondRow> bonds, List<LoanRow> loans,
                                              List<BondOrderRow> bondOrders, List<BillRow> bills) {
    public static final int MAX_ROWS = 64;
    public record IndexRow(String id, double value, double changePercent) { }
    public record BondRow(UUID id, String code, UUID companyId, BondStatus status, long faceValue,
                          long subscribed, long total, int couponBps, long maturityDay,
                          long referencePrice, long marketPrice, int referenceYieldBps, int marketYieldBps, long accruedInterest,
                          long playerQuantity, long availableQuantity, long frozenQuantity,
                          long averageCost, long unrealizedProfit) { }
    public record LoanRow(UUID id, UUID companyId, LoanStatus status, long outstanding, long interest,
                          int rateBps, long maturityDay) { }
    public record BondOrderRow(UUID orderId, UUID bondId, BondOrderSide side, long price,
                               long quantity, boolean ownedByPlayer) { }
    public record BillRow(UUID id, int termDays, int rateBps, long maturityDay,
                          long principal, long expectedValue, CentralBankBillStatus status) { }
    public FinancialProductResponsePacket {
        if (requestId <= 0) throw new IllegalArgumentException("invalid request id");
        riskSummary = riskSummary == null ? "" : riskSummary.substring(0, Math.min(256, riskSummary.length()));
        indices = bounded(indices); bonds = bounded(bonds); loans = bounded(loans);
        bondOrders = bounded(bondOrders); bills = bounded(bills);
    }
    private static <T> List<T> bounded(List<T> source) { return source == null ? List.of() : List.copyOf(source.subList(0, Math.min(MAX_ROWS, source.size()))); }
    public static void encode(FinancialProductResponsePacket p, FriendlyByteBuf b) {
        b.writeLong(p.requestId); b.writeVarInt(p.benchmarkRateBps); b.writeUtf(p.riskSummary, 256);
        b.writeVarInt(p.yield7Bps); b.writeVarInt(p.yield30Bps); b.writeVarInt(p.yield90Bps);
        b.writeVarInt(p.indices.size()); for (IndexRow r : p.indices) { b.writeUtf(r.id, 64); b.writeDouble(r.value); b.writeDouble(r.changePercent); }
        b.writeVarInt(p.bonds.size()); for (BondRow r : p.bonds) { b.writeUUID(r.id); b.writeUtf(r.code, 16); b.writeUUID(r.companyId); b.writeEnum(r.status); b.writeLong(r.faceValue); b.writeLong(r.subscribed); b.writeLong(r.total); b.writeVarInt(r.couponBps); b.writeLong(r.maturityDay); b.writeLong(r.referencePrice); b.writeLong(r.marketPrice); b.writeVarInt(r.referenceYieldBps); b.writeVarInt(r.marketYieldBps); b.writeLong(r.accruedInterest); b.writeLong(r.playerQuantity); b.writeLong(r.availableQuantity); b.writeLong(r.frozenQuantity); b.writeLong(r.averageCost); b.writeLong(r.unrealizedProfit); }
        b.writeVarInt(p.loans.size()); for (LoanRow r : p.loans) { b.writeUUID(r.id); b.writeUUID(r.companyId); b.writeEnum(r.status); b.writeLong(r.outstanding); b.writeLong(r.interest); b.writeVarInt(r.rateBps); b.writeLong(r.maturityDay); }
        b.writeVarInt(p.bondOrders.size()); for (BondOrderRow r : p.bondOrders) { b.writeUUID(r.orderId); b.writeUUID(r.bondId); b.writeEnum(r.side); b.writeLong(r.price); b.writeLong(r.quantity); b.writeBoolean(r.ownedByPlayer); }
        b.writeVarInt(p.bills.size()); for (BillRow r : p.bills) { b.writeUUID(r.id); b.writeVarInt(r.termDays); b.writeVarInt(r.rateBps); b.writeLong(r.maturityDay); b.writeLong(r.principal); b.writeLong(r.expectedValue); b.writeEnum(r.status); }
    }
    public static FinancialProductResponsePacket decode(FriendlyByteBuf b) {
        long id = b.readLong(); int rate = b.readVarInt(); String risk = b.readUtf(256);
        int yield7 = b.readVarInt(), yield30 = b.readVarInt(), yield90 = b.readVarInt();
        int ic = count(b); List<IndexRow> indices = new ArrayList<>(ic); for (int i=0;i<ic;i++) indices.add(new IndexRow(b.readUtf(64), b.readDouble(), b.readDouble()));
        int bc = count(b); List<BondRow> bonds = new ArrayList<>(bc); for (int i=0;i<bc;i++) bonds.add(new BondRow(b.readUUID(), b.readUtf(16), b.readUUID(), b.readEnum(BondStatus.class), b.readLong(), b.readLong(), b.readLong(), b.readVarInt(), b.readLong(), b.readLong(), b.readLong(), b.readVarInt(), b.readVarInt(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readLong()));
        int lc = count(b); List<LoanRow> loans = new ArrayList<>(lc); for (int i=0;i<lc;i++) loans.add(new LoanRow(b.readUUID(), b.readUUID(), b.readEnum(LoanStatus.class), b.readLong(), b.readLong(), b.readVarInt(), b.readLong()));
        int oc = count(b); List<BondOrderRow> orders = new ArrayList<>(oc); for (int i=0;i<oc;i++) orders.add(new BondOrderRow(b.readUUID(), b.readUUID(), b.readEnum(BondOrderSide.class), b.readLong(), b.readLong(), b.readBoolean()));
        int billCount = count(b); List<BillRow> bills = new ArrayList<>(billCount); for (int i=0;i<billCount;i++) bills.add(new BillRow(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readLong(), b.readLong(), b.readLong(), b.readEnum(CentralBankBillStatus.class)));
        return new FinancialProductResponsePacket(id, rate, risk, yield7, yield30, yield90, indices, bonds, loans, orders, bills);
    }
    private static int count(FriendlyByteBuf b) { int n=b.readVarInt(); if(n<0||n>MAX_ROWS) throw new IllegalArgumentException("financial row limit exceeded"); return n; }
    public static void handle(FinancialProductResponsePacket p, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> finance.client.FinancialProductClientCache.accept(p)));
        supplier.get().setPacketHandled(true);
    }
}
