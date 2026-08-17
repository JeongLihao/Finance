package finance.futures;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;

import java.math.BigInteger;
import java.util.*;

/** Separate collateral ledger. Ordinary cash moves only through explicit deposit/withdraw operations. */
public final class MarginManager {
    private static final Map<UUID, MarginAccount> ACCOUNTS = new LinkedHashMap<>();
    private static final Map<Key, FuturesPosition> POSITIONS = new LinkedHashMap<>();
    private static final Map<Key, Long> PENDING_VARIATION = new LinkedHashMap<>();
    private MarginManager() { }

    public static synchronized MarginAccount account(UUID owner) {
        if (owner == null) return null;
        return ACCOUNTS.computeIfAbsent(owner, id -> new MarginAccount(id, 0, 0, MarginRiskStatus.NORMAL, -1));
    }
    public static synchronized boolean deposit(UUID owner, long amount) {
        MarginAccount margin = account(owner);
        if (margin == null || amount <= 0 || !margin.canCredit(amount) || !AccountManager.withdraw(owner, amount)) return false;
        if (!margin.credit(amount)) { AccountManager.deposit(owner, amount); return false; }
        AccountManager.addTransactionRecord(new TransactionRecord(owner,FuturesClearingService.CLEARING_MEMBER_ID,amount,TransactionType.FUTURES_MARGIN_DEPOSIT,owner,"期货保证金",1));
        EconomySavedData.markDirty(); return true;
    }
    public static synchronized boolean withdraw(UUID owner, long amount) {
        MarginAccount margin = account(owner);
        if (margin == null || amount <= 0 || availableToWithdraw(owner) < amount || !AccountManager.canDeposit(owner, amount)) return false;
        if (!margin.debit(amount)) return false;
        if (!AccountManager.deposit(owner, amount)) { margin.credit(amount); return false; }
        AccountManager.addTransactionRecord(new TransactionRecord(FuturesClearingService.CLEARING_MEMBER_ID,owner,amount,TransactionType.FUTURES_MARGIN_WITHDRAW,owner,"期货保证金",1));
        EconomySavedData.markDirty(); return true;
    }
    public static synchronized long availableToWithdraw(UUID owner) {
        MarginAccount a = account(owner); if (a == null) return 0;
        return Math.max(0, a.cashBalance() - a.frozenForOrders() - initialRequirement(owner));
    }
    public static synchronized boolean freezeOrder(UUID owner, long amount) {
        MarginAccount a = account(owner); boolean ok = a != null && amount >= 0
                && availableToWithdraw(owner) >= amount && a.freeze(amount);
        if (ok) EconomySavedData.markDirty(); return ok;
    }
    public static synchronized boolean releaseOrder(UUID owner, long amount) {
        MarginAccount a = account(owner); boolean ok = a != null && a.unfreeze(amount);
        if (ok) EconomySavedData.markDirty(); return ok;
    }
    static synchronized boolean canCommit(UUID owner, UUID contract, FuturesPosition.Preview preview, long frozenRelease) {
        MarginAccount a = account(owner);
        if (a == null || preview == null || frozenRelease < 0 || a.frozenForOrders() < frozenRelease) return false;
        long pending=PENDING_VARIATION.getOrDefault(new Key(owner,contract),0L);
        try { Math.addExact(pending,preview.variationDelta()); } catch (ArithmeticException ex) { return false; }
        long required = initialRequirementWith(owner, contract, preview.signedQuantity());
        if (required < 0) return false;
        return BigInteger.valueOf(required).add(BigInteger.valueOf(a.frozenForOrders() - frozenRelease))
                .compareTo(BigInteger.valueOf(a.cashBalance())) <= 0;
    }
    static synchronized void commit(UUID owner, UUID contract, FuturesPosition.Preview preview, long frozenRelease) {
        MarginAccount a = account(owner); FuturesPosition position = position(owner, contract);
        if (!a.unfreeze(frozenRelease)) throw new IllegalStateException("margin reservation changed");
        position.apply(preview);
        Key key=new Key(owner,contract);long pending=Math.addExact(PENDING_VARIATION.getOrDefault(key,0L),preview.variationDelta());
        if(pending==0)PENDING_VARIATION.remove(key);else PENDING_VARIATION.put(key,pending);
        if (position.signedQuantity() == 0) POSITIONS.remove(new Key(owner, contract));
        EconomySavedData.markDirty();
    }
    static synchronized boolean canCommitRiskReduction(UUID owner,UUID contract,FuturesPosition.Preview preview){
        FuturesPosition old=findPosition(owner,contract);MarginAccount a=account(owner);if(old==null||preview==null||Math.abs(preview.signedQuantity())>=old.quantity())return false;
        long pending=PENDING_VARIATION.getOrDefault(new Key(owner,contract),0L);try{Math.addExact(pending,preview.variationDelta());}catch(ArithmeticException ex){return false;}return a!=null;
    }
    static synchronized void commitRiskReduction(UUID owner,UUID contract,FuturesPosition.Preview preview){commit(owner,contract,preview,0);}
    static synchronized boolean creditCollateral(UUID owner,long amount){MarginAccount a=account(owner);return amount>0&&a.canCredit(amount)&&a.credit(amount);}
    public static synchronized FuturesPosition position(UUID owner, UUID contract) {
        return POSITIONS.computeIfAbsent(new Key(owner, contract), k -> new FuturesPosition(owner, contract, 0, 0, 0, 0));
    }
    public static synchronized FuturesPosition findPosition(UUID owner, UUID contract) { return POSITIONS.get(new Key(owner, contract)); }
    public static synchronized long initialRequirement(UUID owner) { return requirement(owner, FinanceConfig.futuresInitialMarginBps(), null, null); }
    public static synchronized long maintenanceRequirement(UUID owner) { return requirement(owner, FinanceConfig.futuresMaintenanceMarginBps(), null, null); }
    public static synchronized long liquidationRequirement(UUID owner) { return requirement(owner, FinanceConfig.futuresLiquidationMarginBps(), null, null); }
    private static long initialRequirementWith(UUID owner, UUID contract, long signedQuantity) {
        return requirement(owner, FinanceConfig.futuresInitialMarginBps(), contract, signedQuantity);
    }
    private static long requirement(UUID owner, int bps, UUID replacementContract, Long replacementQuantity) {
        BigInteger total = BigInteger.ZERO; boolean replaced = false;
        for (FuturesPosition p : POSITIONS.values()) {
            if (!p.ownerId().equals(owner)) continue;
            long qty = p.contractId().equals(replacementContract) ? replacementQuantity : p.signedQuantity();
            if (p.contractId().equals(replacementContract)) replaced = true;
            if (qty == 0) continue;
            FuturesContract c = FuturesMarketManager.contract(p.contractId());
            long price = FuturesMarketManager.riskPrice(p.contractId());
            long margin = c == null ? -1 : FuturesMath.margin(price, c.contractSize(), Math.abs(qty), bps);
            if (margin < 0) return -1; total = total.add(BigInteger.valueOf(margin));
        }
        if (!replaced && replacementContract != null && replacementQuantity != null && replacementQuantity != 0) {
            FuturesContract c = FuturesMarketManager.contract(replacementContract); long price = FuturesMarketManager.riskPrice(replacementContract);
            long margin = c == null ? -1 : FuturesMath.margin(price, c.contractSize(), Math.abs(replacementQuantity), bps);
            if (margin < 0) return -1; total = total.add(BigInteger.valueOf(margin));
        }
        return total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? -1 : total.longValue();
    }
    static synchronized boolean canCredit(UUID owner, long amount) { return amount == 0 || account(owner).canCredit(amount); }
    static synchronized void applySettlement(UUID owner, UUID contract, long debit, long credit, long day) {
        MarginAccount a = account(owner); if (debit > 0) a.forceDebit(debit); if (credit > 0 && !a.credit(credit)) throw new IllegalStateException();
        a.setLastSettlementDay(day);
        PENDING_VARIATION.remove(new Key(owner,contract));
    }
    static synchronized long pendingVariation(UUID owner,UUID contract){return PENDING_VARIATION.getOrDefault(new Key(owner,contract),0L);}
    public static synchronized Map<UUID, MarginAccount> accounts() { return Collections.unmodifiableMap(ACCOUNTS); }
    public static synchronized Map<Key, FuturesPosition> positions() { return Collections.unmodifiableMap(POSITIONS); }
    static synchronized void closeContractPositions(UUID contract){POSITIONS.entrySet().removeIf(e->e.getKey().contractId().equals(contract));PENDING_VARIATION.entrySet().removeIf(e->e.getKey().contractId().equals(contract));EconomySavedData.markDirty();}
    public static synchronized void removeContractStateDirect(Set<UUID> contracts){if(contracts==null||contracts.isEmpty())return;POSITIONS.entrySet().removeIf(e->contracts.contains(e.getKey().contractId()));PENDING_VARIATION.entrySet().removeIf(e->contracts.contains(e.getKey().contractId()));}
    public static synchronized void putAccountDirect(MarginAccount value) { if (value != null) ACCOUNTS.put(value.ownerId(), value); }
    public static synchronized void putPositionDirect(FuturesPosition value) { if (value != null && value.signedQuantity() != 0) POSITIONS.put(new Key(value.ownerId(), value.contractId()), value); }
    public static synchronized Map<Key,Long> pendingVariations(){return Collections.unmodifiableMap(PENDING_VARIATION);}
    public static synchronized void putPendingDirect(Key key,long value){if(key!=null&&value!=0)PENDING_VARIATION.put(key,value);}
    public static synchronized void clearDirect() { ACCOUNTS.clear(); POSITIONS.clear(); PENDING_VARIATION.clear(); }
    public record Key(UUID ownerId, UUID contractId) { }
}
