package finance.bank;

import java.util.UUID;

public final class BankCustomerAccount {
    private final UUID id,bankId,ownerId;private final CustomerType ownerType;private final BankAccountType type;private final long openedDay;
    private long balance,frozen;private BankAccountStatus status;
    public BankCustomerAccount(UUID id,UUID bankId,UUID ownerId,CustomerType ownerType,BankAccountType type,long balance,long frozen,long openedDay,BankAccountStatus status){if(id==null||bankId==null||ownerId==null||ownerType==null||type==null||balance<0||frozen<0||frozen>balance||openedDay<0||status==null)throw new IllegalArgumentException();this.id=id;this.bankId=bankId;this.ownerId=ownerId;this.ownerType=ownerType;this.type=type;this.balance=balance;this.frozen=frozen;this.openedDay=openedDay;this.status=status;}
    public UUID id(){return id;}public UUID bankId(){return bankId;}public UUID ownerId(){return ownerId;}public CustomerType ownerType(){return ownerType;}public BankAccountType type(){return type;}public long balance(){return balance;}public long frozen(){return frozen;}public long available(){return balance-frozen;}public long openedDay(){return openedDay;}public BankAccountStatus status(){return status;}
    boolean canCredit(long v){return v>0&&balance<=Long.MAX_VALUE-v;}boolean credit(long v){if(!canCredit(v))return false;balance+=v;return true;}boolean debit(long v){if(v<=0||available()<v)return false;balance-=v;return true;}void close(){status=BankAccountStatus.CLOSED;}
}
