package finance.fund;

import java.util.UUID;

public final class FundInvestmentPlan {
    public enum Status { ACTIVE, PAUSED, CANCELLED }
    private final UUID id; private final UUID playerId; private final String fundId; private final long amount; private final int intervalDays;
    private long nextExecutionDay; private long lastAttemptDay=-1; private int failureCount; private Status status;
    public FundInvestmentPlan(UUID id,UUID playerId,String fundId,long amount,int intervalDays,long nextExecutionDay,Status status){
        this.id=id;this.playerId=playerId;this.fundId=FundDefinition.normalize(fundId);this.amount=amount;this.intervalDays=intervalDays;this.nextExecutionDay=nextExecutionDay;this.status=status;
    }
    public UUID id(){return id;} public UUID playerId(){return playerId;} public String fundId(){return fundId;} public long amount(){return amount;}
    public int intervalDays(){return intervalDays;} public long nextExecutionDay(){return nextExecutionDay;} public long lastAttemptDay(){return lastAttemptDay;}
    public int failureCount(){return failureCount;} public Status status(){return status;}
    void attempted(long day,boolean success){lastAttemptDay=day;if(success)failureCount=0;else failureCount=Math.min(1_000_000,failureCount+1);nextExecutionDay=safeNext(day,intervalDays);}
    void status(Status value){status=value;}
    public void restore(long lastAttemptDay,int failureCount){this.lastAttemptDay=lastAttemptDay;this.failureCount=Math.max(0,failureCount);}
    private static long safeNext(long day,int interval){try{return Math.addExact(day,Math.max(1,interval));}catch(ArithmeticException ignored){return Long.MAX_VALUE;}}
}
