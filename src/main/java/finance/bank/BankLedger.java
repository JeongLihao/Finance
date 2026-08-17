package finance.bank;

import java.math.BigInteger;
import java.util.*;

public final class BankLedger {
    public static final int MAX_ENTRIES = 2_000;
    private final UUID bankId;
    private final EnumMap<BankLedgerAccount, Long> balances = new EnumMap<>(BankLedgerAccount.class);
    private final List<BankLedgerEntry> entries = new ArrayList<>();
    private final Set<UUID> references = new HashSet<>();

    public BankLedger(UUID bankId) { if (bankId == null) throw new IllegalArgumentException(); this.bankId = bankId; for (var a : BankLedgerAccount.values()) balances.put(a, 0L); }
    public record Draft(BankLedgerAccount debit, BankLedgerAccount credit, long amount, BankLedgerReason reason) { }

    public synchronized boolean canPost(UUID reference, List<Draft> drafts) { return preview(reference, drafts) != null; }
    public synchronized boolean post(long day, UUID reference, BankLedgerAccount debit, BankLedgerAccount credit,
                                     long amount, BankLedgerReason reason) {
        return postBatch(day, reference, List.of(new Draft(debit, credit, amount, reason)));
    }
    public synchronized boolean postBatch(long day, UUID reference, List<Draft> drafts) {
        EnumMap<BankLedgerAccount, Long> next = preview(reference, drafts);
        if (day < 0 || next == null) return false;
        for (Draft d : drafts) entries.add(new BankLedgerEntry(UUID.randomUUID(), bankId, day, d.debit, d.credit, d.amount, d.reason, reference));
        balances.clear(); balances.putAll(next); references.add(reference);if(entries.size()>MAX_ENTRIES)compact(day); return true;
    }
    private EnumMap<BankLedgerAccount, Long> preview(UUID reference, List<Draft> drafts) {
        if (reference == null || references.contains(reference) || drafts == null || drafts.isEmpty()) return null;
        EnumMap<BankLedgerAccount, BigInteger> exact = new EnumMap<>(BankLedgerAccount.class);
        for (var a : BankLedgerAccount.values()) exact.put(a, BigInteger.valueOf(balances.getOrDefault(a, 0L)));
        for (Draft d : drafts) {
            if (d == null || d.debit == null || d.credit == null || d.debit == d.credit || d.amount <= 0 || d.reason == null) return null;
            apply(exact, d.debit, d.amount, true); apply(exact, d.credit, d.amount, false);
        }
        EnumMap<BankLedgerAccount, Long> result = new EnumMap<>(BankLedgerAccount.class);
        for (var e : exact.entrySet()) if (e.getValue().signum() < 0 || e.getValue().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return null; else result.put(e.getKey(), e.getValue().longValue());
        return result;
    }
    private static void apply(Map<BankLedgerAccount, BigInteger> map, BankLedgerAccount account, long amount, boolean debit) {
        boolean increase = debit == account.debitIncreases(); BigInteger delta = BigInteger.valueOf(amount);
        map.put(account, increase ? map.get(account).add(delta) : map.get(account).subtract(delta));
    }
    public synchronized long balance(BankLedgerAccount account) { return balances.getOrDefault(account, 0L); }
    public synchronized boolean hasReference(UUID id) { return references.contains(id); }
    public synchronized List<BankLedgerEntry> entries() { return List.copyOf(entries); }
    public synchronized BankBalanceSheet balanceSheet() {
        BigInteger grossAssets = bi(BankLedgerAccount.ASSET_RESERVE).add(bi(BankLedgerAccount.ASSET_COMPANY_LOAN)).add(bi(BankLedgerAccount.ASSET_INTERBANK)).add(bi(BankLedgerAccount.ASSET_BOND)).add(bi(BankLedgerAccount.ASSET_OTHER));
        BigInteger reserve = bi(BankLedgerAccount.CONTRA_LOAN_LOSS_RESERVE); BigInteger assets = grossAssets.subtract(reserve);
        BigInteger liabilities = bi(BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT).add(bi(BankLedgerAccount.LIABILITY_TIME_DEPOSIT)).add(bi(BankLedgerAccount.LIABILITY_INTERBANK)).add(bi(BankLedgerAccount.LIABILITY_CENTRAL_BANK));
        BigInteger equity = bi(BankLedgerAccount.EQUITY_PAID_IN).add(bi(BankLedgerAccount.EQUITY_RETAINED)).add(bi(BankLedgerAccount.INCOME_INTEREST)).add(bi(BankLedgerAccount.INCOME_FEE)).subtract(bi(BankLedgerAccount.EXPENSE_INTEREST)).subtract(bi(BankLedgerAccount.EXPENSE_CREDIT_LOSS)).subtract(bi(BankLedgerAccount.EXPENSE_INSURANCE));
        boolean balanced = assets.equals(liabilities.add(equity));
        return new BankBalanceSheet(cap(assets), balance(BankLedgerAccount.ASSET_RESERVE), balance(BankLedgerAccount.ASSET_COMPANY_LOAN), balance(BankLedgerAccount.ASSET_INTERBANK), balance(BankLedgerAccount.ASSET_BOND), balance(BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT), balance(BankLedgerAccount.LIABILITY_TIME_DEPOSIT), balance(BankLedgerAccount.LIABILITY_INTERBANK), balance(BankLedgerAccount.LIABILITY_CENTRAL_BANK), signedCap(equity), balance(BankLedgerAccount.CONTRA_LOAN_LOSS_RESERVE), balanced);
    }
    private BigInteger bi(BankLedgerAccount a) { return BigInteger.valueOf(balance(a)); }
    private static long cap(BigInteger v) { return v.max(BigInteger.ZERO).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
    private static long signedCap(BigInteger v) { return v.max(BigInteger.valueOf(Long.MIN_VALUE)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
    private void compact(long day){record Node(BankLedgerAccount account,long amount){}List<Node>debits=new ArrayList<>(),credits=new ArrayList<>();for(var a:BankLedgerAccount.values()){long value=balances.getOrDefault(a,0L);if(value<=0)continue;if(a.debitIncreases())debits.add(new Node(a,value));else credits.add(new Node(a,value));}List<BankLedgerEntry>compacted=new ArrayList<>();int i=0,j=0;long dl=i<debits.size()?debits.get(i).amount:0,cl=j<credits.size()?credits.get(j).amount:0;while(i<debits.size()&&j<credits.size()){long amount=Math.min(dl,cl);UUID ref=UUID.randomUUID();compacted.add(new BankLedgerEntry(UUID.randomUUID(),bankId,day,debits.get(i).account,credits.get(j).account,amount,BankLedgerReason.LEDGER_COMPACTION,ref));dl-=amount;cl-=amount;if(dl==0&&++i<debits.size())dl=debits.get(i).amount;if(cl==0&&++j<credits.size())cl=credits.get(j).amount;}if(i==debits.size()&&j==credits.size()){entries.clear();entries.addAll(compacted);references.clear();for(var e:compacted)references.add(e.referenceId());}}
    public synchronized boolean restore(List<BankLedgerEntry> restored) {
        balances.replaceAll((k,v)->0L);entries.clear();references.clear();
        if (restored == null || restored.size() > MAX_ENTRIES) return false;
        LinkedHashMap<UUID,List<BankLedgerEntry>> batches=new LinkedHashMap<>();for(BankLedgerEntry e:restored){if(e==null||!e.bankId().equals(bankId))return false;batches.computeIfAbsent(e.referenceId(),x->new ArrayList<>()).add(e);}
        for(var batch:batches.entrySet()){List<Draft> drafts=batch.getValue().stream().map(e->new Draft(e.debit(),e.credit(),e.amount(),e.reason())).toList();long day=batch.getValue().get(0).mcDay();if(!postBatch(day,batch.getKey(),drafts))return false;}
        entries.clear();entries.addAll(restored);return balanceSheet().balanced();
    }
}
