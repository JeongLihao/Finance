package finance.client;

import finance.network.FinancialProductResponsePacket;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class FinancialProductClientCache {
    public enum State { NOT_REQUESTED, LOADING, READY, SLOW }
    public record Entry(State state, long requestId, long requestedAt, int benchmarkRateBps, String riskSummary,
                        List<FinancialProductResponsePacket.IndexRow> indices,
                        List<FinancialProductResponsePacket.BondRow> bonds,
                        List<FinancialProductResponsePacket.LoanRow> loans,
                        List<FinancialProductResponsePacket.BondOrderRow> bondOrders,
                        List<FinancialProductResponsePacket.BillRow> bills,
                        int yield7Bps, int yield30Bps, int yield90Bps) { }
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static Entry entry = empty();
    private FinancialProductClientCache() { }
    public static long begin() { long id=SEQUENCE.updateAndGet(v->v==Long.MAX_VALUE?1:v+1); entry=new Entry(State.LOADING,id,System.currentTimeMillis(),0,"",List.of(),List.of(),List.of(),List.of(),List.of(),0,0,0); return id; }
    public static void accept(FinancialProductResponsePacket p) { if(p.requestId()==entry.requestId()) entry=new Entry(State.READY,p.requestId(),0,p.benchmarkRateBps(),p.riskSummary(),p.indices(),p.bonds(),p.loans(),p.bondOrders(),p.bills(),p.yield7Bps(),p.yield30Bps(),p.yield90Bps()); }
    public static Entry get() { if(entry.state()==State.LOADING && System.currentTimeMillis()-entry.requestedAt()>3000) return new Entry(State.SLOW,entry.requestId(),entry.requestedAt(),0,"",List.of(),List.of(),List.of(),List.of(),List.of(),0,0,0); return entry; }
    public static void clear() { entry=empty(); }
    private static Entry empty() { return new Entry(State.NOT_REQUESTED,0,0,0,"",List.of(),List.of(),List.of(),List.of(),List.of(),0,0,0); }
}
