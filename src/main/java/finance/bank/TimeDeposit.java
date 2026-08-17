package finance.bank;

import java.util.UUID;

public final class TimeDeposit {
    private final UUID id,bankId,ownerId,accountId;private final long principal,startDay,maturityDay;private final int lockedRateBps;
    private long accruedInterest,lastAccrualDay;private TimeDepositStatus status;
    public TimeDeposit(UUID id,UUID bankId,UUID ownerId,UUID accountId,long principal,int lockedRateBps,long startDay,long maturityDay,long accruedInterest,long lastAccrualDay,TimeDepositStatus status){if(id==null||bankId==null||ownerId==null||accountId==null||principal<=0||lockedRateBps<0||lockedRateBps>100_000||startDay<0||maturityDay<=startDay||accruedInterest<0||lastAccrualDay<startDay||status==null)throw new IllegalArgumentException();this.id=id;this.bankId=bankId;this.ownerId=ownerId;this.accountId=accountId;this.principal=principal;this.lockedRateBps=lockedRateBps;this.startDay=startDay;this.maturityDay=maturityDay;this.accruedInterest=accruedInterest;this.lastAccrualDay=lastAccrualDay;this.status=status;}
    public UUID id(){return id;}public UUID bankId(){return bankId;}public UUID ownerId(){return ownerId;}public UUID accountId(){return accountId;}public long principal(){return principal;}public int lockedRateBps(){return lockedRateBps;}public long startDay(){return startDay;}public long maturityDay(){return maturityDay;}public long accruedInterest(){return accruedInterest;}public long lastAccrualDay(){return lastAccrualDay;}public TimeDepositStatus status(){return status;}
    void addInterest(long v,long day){accruedInterest=Math.addExact(accruedInterest,v);lastAccrualDay=day;}void setStatus(TimeDepositStatus v){status=v;}
}
