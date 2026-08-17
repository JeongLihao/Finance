package finance.data.serializer;

import finance.debt.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigInteger;
import finance.company.CompanyManager;

/** Bounded and corruption-tolerant debt contract persistence. */
public final class DebtDataSerializer {
    private static final int MAX_CONTRACTS = 10_000;
    private static final int MAX_HOLDERS = 10_000;
    private DebtDataSerializer() { }

    public static void save(CompoundTag root) {
        ListTag bonds = new ListTag();
        for (CorporateBond b : CorporateBondManager.bonds().values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", b.id()); t.putUUID("Company", b.companyId()); t.putString("Code", b.code());
            t.putLong("Face", b.faceValue()); t.putLong("Quantity", b.totalQuantity()); t.putInt("CouponBps", b.couponBasisPoints());
            t.putLong("IssueDay", b.issueDay()); t.putLong("SubscriptionEnd", b.subscriptionEndDay()); t.putLong("Maturity", b.maturityDay());
            t.putInt("CouponInterval", b.couponIntervalDays()); t.putLong("NextCoupon", b.nextCouponDay());
            t.putLong("LastCoupon", b.lastCouponDay());
            t.putString("Status", b.status().name()); t.putLong("Escrow", b.escrowCash());
            ListTag holders = new ListTag();
            int hc = 0;
            for (var h : b.holdings().entrySet()) { if (++hc > MAX_HOLDERS) break; CompoundTag ht = new CompoundTag(); ht.putUUID("Player", h.getKey()); ht.putLong("Quantity", h.getValue()); holders.add(ht); }
            t.put("Holders", holders);
            ListTag recovered = new ListTag();
            for (var recovery : b.recoveredPrincipal().entrySet()) {
                CompoundTag rt = new CompoundTag(); rt.putUUID("Player", recovery.getKey());
                rt.putLong("Amount", recovery.getValue()); recovered.add(rt);
            }
            t.put("RecoveredPrincipal", recovered);
            bonds.add(t);
        }
        root.put("CorporateBonds", bonds);
        ListTag loans = new ListTag();
        for (CompanyLoan l : CompanyLoanManager.loans().values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", l.id()); t.putUUID("Company", l.companyId()); t.putLong("Original", l.originalPrincipal());
            t.putInt("RateBps", l.annualRateBasisPoints()); t.putLong("IssueDay", l.issueDay()); t.putLong("Maturity", l.maturityDay());
            t.putInt("PaymentInterval", l.paymentIntervalDays()); t.putLong("Outstanding", l.outstandingPrincipal());
            t.putLong("Interest", l.accruedInterest()); t.putLong("LastAccrual", l.lastAccrualDay()); t.putLong("NextPayment", l.nextPaymentDay());
            t.putLong("DelinquentSince", l.delinquentSinceDay()); t.putString("Status", l.status().name());
            t.putString("LenderType",l.lenderType().name());t.putUUID("LenderId",l.lenderId()); loans.add(t);
        }
        root.put("CompanyLoans", loans);
    }

    public static void load(CompoundTag root) {
        CorporateBondManager.clearDirect(); CompanyLoanManager.clearDirect();
        int count = 0;
        for (Tag raw : root.getList("CorporateBonds", Tag.TAG_COMPOUND)) {
            if (++count > MAX_CONTRACTS) break;
            try { CorporateBond bond = readBond((CompoundTag) raw); if (bond != null) CorporateBondManager.putDirect(bond); }
            catch (RuntimeException ignored) { }
        }
        count = 0;
        for (Tag raw : root.getList("CompanyLoans", Tag.TAG_COMPOUND)) {
            if (++count > MAX_CONTRACTS) break;
            try { CompanyLoan loan = readLoan((CompoundTag) raw); if (loan != null) CompanyLoanManager.putDirect(loan); }
            catch (RuntimeException ignored) { }
        }
    }

    private static CorporateBond readBond(CompoundTag t) {
        UUID id = NbtDataSupport.readUuidOrNull(t, "Id");
        UUID company = NbtDataSupport.readUuidOrNull(t, "Company");
        BondStatus status = NbtDataSupport.safeEnum(BondStatus.class, t.getString("Status"), null);
        String code = t.getString("Code").trim();
        long face = t.getLong("Face"), totalQuantity = t.getLong("Quantity");
        int couponBps = t.getInt("CouponBps"), interval = t.getInt("CouponInterval");
        long issue = t.getLong("IssueDay"), subscriptionEnd = t.getLong("SubscriptionEnd"), maturity = t.getLong("Maturity");
        long nextCoupon = t.getLong("NextCoupon"), escrow = t.getLong("Escrow");
        long lastCoupon = t.contains("LastCoupon") ? t.getLong("LastCoupon")
                : Math.max(subscriptionEnd, nextCoupon - Math.max(1, interval));
        if (id == null || company == null || status == null || code.isBlank() || code.length() > 16
                || face <= 0 || totalQuantity <= 0 || couponBps <= 0 || couponBps > 100_000
                || issue < 0 || subscriptionEnd <= issue || maturity <= subscriptionEnd
                || interval <= 0 || nextCoupon <= subscriptionEnd || lastCoupon < subscriptionEnd
                || lastCoupon > maturity || escrow < 0) return null;
        if (BigInteger.valueOf(face).multiply(BigInteger.valueOf(totalQuantity))
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return null;
        if (status != BondStatus.DEFAULTED && CompanyManager.getCompany(company) == null) return null;

        Map<UUID, Long> holdings = new LinkedHashMap<>();
        int hc = 0;
        for (Tag raw : t.getList("Holders", Tag.TAG_COMPOUND)) {
            if (++hc > MAX_HOLDERS) return null;
            CompoundTag h = (CompoundTag) raw; UUID player = NbtDataSupport.readUuidOrNull(h, "Player"); long quantity = h.getLong("Quantity");
            if (player == null || quantity <= 0) return null;
            long previous = holdings.getOrDefault(player, 0L);
            if (previous > Long.MAX_VALUE - quantity) return null;
            holdings.put(player, previous + quantity);
        }
        BigInteger subscribed = BigInteger.ZERO;
        for (long quantity : holdings.values()) subscribed = subscribed.add(BigInteger.valueOf(quantity));
        if (subscribed.compareTo(BigInteger.valueOf(totalQuantity)) > 0) return null;
        BigInteger expectedEscrow = BigInteger.valueOf(face).multiply(subscribed);
        if (status == BondStatus.SUBSCRIPTION) {
            if (expectedEscrow.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || escrow != expectedEscrow.longValue()) return null;
        } else if (escrow != 0) return null;
        if (status == BondStatus.ACTIVE && holdings.isEmpty()) return null;
        if ((status == BondStatus.MATURED || status == BondStatus.CANCELLED) && !holdings.isEmpty()) return null;

        Map<UUID, Long> recovered = new LinkedHashMap<>();
        int recoveryCount = 0;
        for (Tag raw : t.getList("RecoveredPrincipal", Tag.TAG_COMPOUND)) {
            if (++recoveryCount > MAX_HOLDERS) return null;
            CompoundTag r = (CompoundTag) raw; UUID player = NbtDataSupport.readUuidOrNull(r, "Player"); long amount = r.getLong("Amount");
            if (player == null || amount <= 0 || !holdings.containsKey(player) || recovered.putIfAbsent(player, amount) != null) return null;
            BigInteger gross = BigInteger.valueOf(face).multiply(BigInteger.valueOf(holdings.get(player)));
            if (BigInteger.valueOf(amount).compareTo(gross) > 0) return null;
        }
        if (!recovered.isEmpty() && status != BondStatus.DEFAULTED) return null;

        CorporateBond bond = new CorporateBond(id, company, code, face, totalQuantity, couponBps, issue,
                subscriptionEnd, maturity, interval, nextCoupon, status, escrow);
        bond.setLastCouponDayDirect(lastCoupon);
        holdings.forEach(bond::putHoldingDirect);
        recovered.forEach(bond::putRecoveredPrincipalDirect);
        return bond;
    }

    private static CompanyLoan readLoan(CompoundTag t) {
        UUID id = NbtDataSupport.readUuidOrNull(t, "Id"), company = NbtDataSupport.readUuidOrNull(t, "Company");
        LoanStatus status = NbtDataSupport.safeEnum(LoanStatus.class, t.getString("Status"), null);
        long original = t.getLong("Original"), issue = t.getLong("IssueDay"), maturity = t.getLong("Maturity");
        long outstanding = t.getLong("Outstanding"), interest = t.getLong("Interest"), lastAccrual = t.getLong("LastAccrual");
        long nextPayment = t.getLong("NextPayment"), delinquentSince = t.getLong("DelinquentSince");
        int rate = t.getInt("RateBps"), interval = t.getInt("PaymentInterval");
        LoanLenderType lenderType=t.contains("LenderType")?NbtDataSupport.safeEnum(LoanLenderType.class,t.getString("LenderType"),null):LoanLenderType.CENTRAL_BANK_DIRECT;
        UUID lenderId=t.contains("LenderId")?NbtDataSupport.readUuidOrNull(t,"LenderId"):finance.market.CentralBank.UUID;
        if (id == null || company == null || status == null || original <= 0 || rate < 0 || rate > 100_000
                || issue < 0 || maturity <= issue || interval <= 0 || interval >= maturity - issue
                || outstanding < 0 || outstanding > original || interest < 0 || lastAccrual < issue
                || nextPayment <= issue || lenderType==null||lenderId==null||lenderType==LoanLenderType.COMMERCIAL_BANK&&finance.bank.BankingManager.bank(lenderId)==null
                || (status != LoanStatus.DEFAULTED && CompanyManager.getCompany(company) == null)) return null;
        if (status == LoanStatus.ACTIVE && delinquentSince != -1) return null;
        if ((status == LoanStatus.DELINQUENT || status == LoanStatus.DEFAULTED) && delinquentSince < issue) return null;
        if ((status == LoanStatus.REPAID || status == LoanStatus.CANCELLED)
                && (outstanding != 0 || interest != 0)) return null;
        return new CompanyLoan(id, company, original, rate, issue, maturity, interval, outstanding,
                interest, lastAccrual, nextPayment, delinquentSince, status,lenderType,lenderId);
    }
}
