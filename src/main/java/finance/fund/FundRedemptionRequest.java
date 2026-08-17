package finance.fund;

import java.util.UUID;

public final class FundRedemptionRequest {
    public enum Status { PENDING, EXECUTING, PAID, CANCELLED, FAILED }
    private final UUID id; private final UUID playerId; private final String fundId;
    private final long shareUnits; private final long createdDay; private Status status;
    private String failureReason; private long paidAmount;
    public FundRedemptionRequest(UUID id, UUID playerId, String fundId, long shareUnits, long createdDay, Status status) {
        this.id=id; this.playerId=playerId; this.fundId=FundDefinition.normalize(fundId); this.shareUnits=shareUnits;
        this.createdDay=createdDay; this.status=status; this.failureReason="";
    }
    public UUID id(){return id;} public UUID playerId(){return playerId;} public String fundId(){return fundId;}
    public long shareUnits(){return shareUnits;} public long createdDay(){return createdDay;} public Status status(){return status;}
    public String failureReason(){return failureReason;} public long paidAmount(){return paidAmount;}
    void status(Status value,String reason,long paid){status=value;failureReason=reason==null?"":reason.substring(0,Math.min(256,reason.length()));paidAmount=Math.max(0,paid);}
    public void restore(Status value,String reason,long paid){status(value,reason,paid);}
}
