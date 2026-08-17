package finance.client;

import finance.network.FundResponsePacket;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class FundClientCache {
    public enum State{NOT_REQUESTED,LOADING,READY,SLOW}
    public record Entry(State state,long requestId,long requestedAt,List<FundResponsePacket.FundRow>funds,List<FundResponsePacket.PlanRow>plans,List<FundResponsePacket.RedemptionRow>redemptions){}
    private static final AtomicLong SEQUENCE=new AtomicLong();private static Entry entry=empty();private FundClientCache(){}
    public static long begin(){long id=SEQUENCE.updateAndGet(v->v==Long.MAX_VALUE?1:v+1);entry=new Entry(State.LOADING,id,System.currentTimeMillis(),List.of(),List.of(),List.of());return id;}
    public static void accept(FundResponsePacket p){if(p.requestId()==entry.requestId())entry=new Entry(State.READY,p.requestId(),0,p.funds(),p.plans(),p.redemptions());}
    public static Entry get(){if(entry.state()==State.LOADING&&System.currentTimeMillis()-entry.requestedAt()>3000)return new Entry(State.SLOW,entry.requestId(),entry.requestedAt(),entry.funds(),entry.plans(),entry.redemptions());return entry;}
    public static void clear(){entry=empty();}private static Entry empty(){return new Entry(State.NOT_REQUESTED,0,0,List.of(),List.of(),List.of());}
}
