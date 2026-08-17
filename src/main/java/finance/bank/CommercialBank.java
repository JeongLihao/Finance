package finance.bank;

import java.util.*;

public final class CommercialBank {
    public static final int MAX_SNAPSHOTS = 30;
    private final UUID id; private final String code; private final String name; private BankStatus status;
    private final long paidInCapital; private final BankPolicy policy; private final BankLedger ledger;
    private final List<BankBalanceSheetSnapshot> snapshots = new ArrayList<>();
    public CommercialBank(UUID id,String code,String name,BankStatus status,long paidInCapital,BankPolicy policy){
        if(id==null||code==null||code.isBlank()||code.length()>16||name==null||name.isBlank()||name.length()>64||status==null||paidInCapital<=0||policy==null)throw new IllegalArgumentException();
        this.id=id;this.code=code;this.name=name;this.status=status;this.paidInCapital=paidInCapital;this.policy=policy;this.ledger=new BankLedger(id);
    }
    public UUID id(){return id;}public String code(){return code;}public String name(){return name;}public BankStatus status(){return status;}public long paidInCapital(){return paidInCapital;}public BankPolicy policy(){return policy;}public BankLedger ledger(){return ledger;}
    public void setStatus(BankStatus v){if(v!=null)status=v;}public boolean acceptsNewBusiness(){return status==BankStatus.ACTIVE||status==BankStatus.WATCH;}
    public List<BankBalanceSheetSnapshot> snapshots(){return List.copyOf(snapshots);}public void addSnapshot(BankBalanceSheetSnapshot s){if(s!=null){snapshots.add(s);if(snapshots.size()>MAX_SNAPSHOTS)snapshots.subList(0,snapshots.size()-MAX_SNAPSHOTS).clear();}}
}
