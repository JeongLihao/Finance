package finance.debt;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.money.MoneyEndpoint;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferResult;
import finance.money.MoneyTransferService;
import finance.policy.MonetaryPolicyService;
import finance.bondmarket.BondMarketManager;
import finance.bondmarket.BondPortfolioManager;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import finance.util.ProportionalAllocator;

public final class CorporateBondManager {
    private static final Map<UUID, CorporateBond> BONDS = new LinkedHashMap<>();
    private CorporateBondManager() { }

    public static Result issue(UUID ownerId, UUID companyId, String code, long faceValue, long quantity,
                               int couponBps, long currentDay, int subscriptionDays, int termDays, int couponIntervalDays) {
        Company company = CompanyManager.getCompany(companyId);
        if (company == null || ownerId == null || !ownerId.equals(company.getOwnerId())) return Result.fail("无权发行");
        if (BONDS.size() >= FinanceConfig.maxCorporateBonds() || code == null || code.isBlank() || code.length() > 16
                || faceValue <= 0 || quantity <= 0 || currentDay < 0 || subscriptionDays < 1 || termDays <= subscriptionDays
                || termDays > FinanceConfig.maxBondTermDays()
                || couponIntervalDays < 1) return Result.fail("发行参数无效");
        if (BONDS.values().stream().anyMatch(b -> b.code().equalsIgnoreCase(code.trim()))) return Result.fail("债券代码重复");
        CreditRating rating = CompanyCreditService.rate(company);
        int minimumCoupon = MonetaryPolicyService.benchmarkRateBasisPoints() + rating.spreadBasisPoints();
        if (couponBps < minimumCoupon || couponBps > FinanceConfig.maxContractRateBasisPoints()) return Result.fail("票息超出信用风险范围");
        BigInteger principal = BigInteger.valueOf(faceValue).multiply(BigInteger.valueOf(quantity));
        long assets = Math.max(1, company.getReportBasedAssetValue());
        BigInteger ratingLimit = BigInteger.valueOf(assets).multiply(BigInteger.valueOf(rating.maxDebtPercent())).divide(BigInteger.valueOf(100));
        BigInteger configLimit = BigDecimal.valueOf(assets).multiply(BigDecimal.valueOf(FinanceConfig.maxBondFinancingRatio())).toBigInteger();
        BigInteger debtLimit = ratingLimit.min(configLimit);
        if (principal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                || principal.add(BigInteger.valueOf(CompanyCreditService.totalDebt(companyId))).compareTo(debtLimit) > 0)
            return Result.fail("融资额超过信用额度");
        long subscriptionEnd;
        long maturity;
        long firstCoupon;
        try {
            subscriptionEnd = Math.addExact(currentDay, subscriptionDays);
            maturity = Math.addExact(currentDay, termDays);
            firstCoupon = Math.addExact(subscriptionEnd, couponIntervalDays);
        } catch (ArithmeticException overflow) {
            return Result.fail("发行日期溢出");
        }
        UUID id = UUID.randomUUID();
        CorporateBond bond = new CorporateBond(id, companyId, code.trim().toUpperCase(), faceValue, quantity,
                couponBps, currentDay, subscriptionEnd, maturity, couponIntervalDays,
                firstCoupon, BondStatus.SUBSCRIPTION, 0);
        BONDS.put(id, bond); EconomySavedData.markDirty();
        record(bond, ownerId, 0, quantity, TransactionType.BOND_ISSUE, "公司债发行");
        return Result.ok(id, "债券进入认购期");
    }

    public static Result subscribe(UUID playerId, UUID bondId, long quantity) {
        CorporateBond bond = BONDS.get(bondId);
        if (playerId == null || bond == null || bond.status() != BondStatus.SUBSCRIPTION || quantity <= 0
                || quantity > bond.totalQuantity() - bond.subscribedQuantity()) return Result.fail("认购无效或额度不足");
        long old = bond.holdings().getOrDefault(playerId, 0L);
        if (old > Long.MAX_VALUE - quantity) return Result.fail("持仓溢出");
        BigInteger paymentExact = BigInteger.valueOf(bond.faceValue()).multiply(BigInteger.valueOf(quantity));
        if (paymentExact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return Result.fail("认购金额溢出");
        long payment = paymentExact.longValue();
        if (!BondPortfolioManager.canAcquire(bondId, playerId, quantity, bond.faceValue())) return Result.fail("持仓成本或数量溢出");
        MoneyTransferResult transfer = MoneyTransferService.transfer(MoneyEndpoints.account(playerId), escrowEndpoint(bond), payment);
        if (!transfer.success()) return Result.fail("资金结算失败: " + transfer.failure());
        if (!BondPortfolioManager.acquire(bondId, playerId, quantity, bond.faceValue())) {
            MoneyTransferService.transfer(escrowEndpoint(bond), MoneyEndpoints.account(playerId), payment);
            return Result.fail("持仓结算失败");
        }
        record(bond, playerId, payment, quantity, TransactionType.BOND_SUBSCRIBE, "债券认购");
        return Result.ok(bond.id(), "认购成功");
    }

    public static void processDay(long day) {
        for (CorporateBond bond : new ArrayList<>(BONDS.values())) {
            if (bond.status() == BondStatus.SUBSCRIPTION && day >= bond.subscriptionEndDay()) activateOrCancel(bond);
            if (bond.status() != BondStatus.ACTIVE) continue;
            if (day >= bond.maturityDay()) {
                if (BondMarketManager.cancelOrdersForBond(bond.id())) settle(bond, true, day);
            }
            else if (day >= bond.nextCouponDay()) settle(bond, false, day);
        }
    }

    private static void activateOrCancel(CorporateBond bond) {
        Company company = CompanyManager.getCompany(bond.companyId());
        if (company == null || bond.subscribedQuantity() <= 0) { refundAndCancel(bond); return; }
        if (!MoneyTransferService.transfer(escrowEndpoint(bond), MoneyEndpoints.company(company), bond.escrowCash()).success()) {
            refundAndCancel(bond); return;
        }
        bond.setStatus(BondStatus.ACTIVE); EconomySavedData.markDirty();
    }

    private static boolean refundAndCancel(CorporateBond bond) {
        List<Map.Entry<UUID, Long>> holders = new ArrayList<>(bond.holdings().entrySet());
        for (Map.Entry<UUID, Long> h : holders) {
            long payment = exactProductOrNegative(bond.faceValue(), h.getValue());
            if (payment <= 0 || !AccountManager.canDeposit(h.getKey(), payment)) return false;
        }
        List<Map.Entry<UUID, Long>> refunded = new ArrayList<>();
        for (Map.Entry<UUID, Long> h : holders) {
            long payment = exactProductOrNegative(bond.faceValue(), h.getValue());
            if (!MoneyTransferService.transfer(escrowEndpoint(bond), MoneyEndpoints.account(h.getKey()), payment).success()) {
                for (Map.Entry<UUID, Long> previous : refunded) {
                    long rollback = exactProductOrNegative(bond.faceValue(), previous.getValue());
                    MoneyTransferService.transfer(MoneyEndpoints.account(previous.getKey()), escrowEndpoint(bond), rollback);
                }
                return false;
            }
            refunded.add(h);
            record(bond, h.getKey(), payment, h.getValue(), TransactionType.BOND_CANCEL, "债券取消退款");
        }
        bond.clearHoldings(); BondPortfolioManager.closeBond(bond.id());
        bond.setStatus(BondStatus.CANCELLED); EconomySavedData.markDirty();
        return true;
    }

    private static void settle(CorporateBond bond, boolean principal, long day) {
        Company company = CompanyManager.getCompany(bond.companyId());
        if (company == null) { markDefault(bond, "公司不存在"); return; }
        Map<UUID, Long> payouts = new LinkedHashMap<>();
        Map<UUID, Long> couponPayouts = new LinkedHashMap<>();
        BigInteger total = BigInteger.ZERO;
        boolean invalidPayout = false;
        long couponDays = principal
                ? Math.max(0, bond.maturityDay() - bond.lastCouponDay())
                : Math.max(0, day - bond.lastCouponDay());
        for (Map.Entry<UUID, Long> h : bond.holdings().entrySet()) {
            BigInteger principalAmount = BigInteger.valueOf(bond.faceValue()).multiply(BigInteger.valueOf(h.getValue()));
            BigInteger couponAmount = principalAmount.multiply(BigInteger.valueOf(bond.couponBasisPoints()))
                    .multiply(BigInteger.valueOf(couponDays))
                    .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L));
            BigInteger amount = principal ? principalAmount.add(couponAmount) : couponAmount;
            if (amount.signum() < 0 || amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                invalidPayout = true;
                continue;
            }
            if (amount.signum() > 0) payouts.put(h.getKey(), amount.longValue());
            if (couponAmount.signum() > 0 && couponAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                couponPayouts.put(h.getKey(), couponAmount.longValue());
            }
            total = total.add(amount);
        }
        if (invalidPayout || total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || company.getCash() < total.longValue()
                || payouts.entrySet().stream().anyMatch(e -> !AccountManager.canDeposit(e.getKey(), e.getValue()))) {
            markDefault(bond, "偿付资金不足或金额溢出"); return;
        }
        long totalLong = total.longValue();
        if (totalLong > 0 && !company.withdraw(totalLong)) { markDefault(bond, "公司扣款失败"); return; }
        List<Map.Entry<UUID, Long>> credited = new ArrayList<>();
        for (Map.Entry<UUID, Long> p : payouts.entrySet()) {
            if (!AccountManager.deposit(p.getKey(), p.getValue())) {
                for (Map.Entry<UUID, Long> rollback : credited) AccountManager.withdraw(rollback.getKey(), rollback.getValue());
                company.deposit(totalLong); markDefault(bond, "收款账户结算失败"); return;
            }
            credited.add(p);
        }
        for (Map.Entry<UUID, Long> p : payouts.entrySet()) {
            BondPortfolioManager.recordCoupon(bond.id(), p.getKey(), couponPayouts.getOrDefault(p.getKey(), 0L));
            record(bond, p.getKey(), p.getValue(), bond.holdings().getOrDefault(p.getKey(), 0L),
                    principal ? TransactionType.BOND_MATURITY : TransactionType.BOND_COUPON,
                    principal ? "债券到期兑付" : "债券付息");
        }
        bond.setLastCouponDay(principal ? bond.maturityDay() : day);
        if (principal) { bond.setStatus(BondStatus.MATURED); bond.clearHoldings(); BondPortfolioManager.closeBond(bond.id()); }
        else {
            long next;
            try { next = Math.addExact(day, bond.couponIntervalDays()); }
            catch (ArithmeticException overflow) { next = bond.maturityDay(); }
            bond.setNextCouponDay(Math.min(bond.maturityDay(), next));
        }
        EconomySavedData.markDirty();
    }

    public static long outstandingPrincipal(UUID companyId) {
        BigInteger total = BigInteger.ZERO;
        for (CorporateBond b : BONDS.values()) {
            if (!b.companyId().equals(companyId)) continue;
            if (b.status() == BondStatus.SUBSCRIPTION) {
                total = total.add(BigInteger.valueOf(b.faceValue()).multiply(BigInteger.valueOf(b.totalQuantity())));
            } else if (b.status() == BondStatus.ACTIVE || b.status() == BondStatus.DEFAULTED) {
                for (UUID holder : b.holdings().keySet()) total = total.add(BigInteger.valueOf(b.remainingPrincipalClaim(holder)));
            }
        }
        return total.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    public static boolean hasDefault(UUID companyId) { return BONDS.values().stream().anyMatch(b -> b.companyId().equals(companyId) && b.status() == BondStatus.DEFAULTED); }
    public static BankruptcyPlan planBankruptcyClaims(UUID companyId, long available) {
        if (companyId == null || available < 0) return new BankruptcyPlan(null, 0, 0, List.of());
        Map<UUID, Long> claims = new LinkedHashMap<>();
        for (CorporateBond bond : BONDS.values()) {
            if (!bond.companyId().equals(companyId) || (bond.status() != BondStatus.ACTIVE && bond.status() != BondStatus.DEFAULTED)) continue;
            for (UUID holder : bond.holdings().keySet()) {
                long claim = bond.remainingPrincipalClaim(holder);
                if (claim > 0) claims.merge(holder, claim, CorporateBondManager::saturatedAdd);
            }
        }
        for(var loanClaim:CompanyLoanManager.outstandingByLender(companyId).entrySet())if(loanClaim.getValue()>0)claims.merge(loanClaim.getKey(),loanClaim.getValue(),CorporateBondManager::saturatedAdd);
        BigInteger declared = BigInteger.ZERO;
        for (long value : claims.values()) declared = declared.add(BigInteger.valueOf(value));
        long declaredLong = declared.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        long creditorPool = Math.min(available, declaredLong);
        List<ProportionalAllocator.Allocation> allocations = ProportionalAllocator.allocate(creditorPool, claims, declaredLong);
        return new BankruptcyPlan(companyId, available, creditorPool, List.copyOf(allocations));
    }

    public static BankruptcySettlement settleBankruptcyClaims(UUID companyId, long available) {
        return settleBankruptcyClaims(planBankruptcyClaims(companyId, available), List.of());
    }

    public static BankruptcySettlement settleBankruptcyClaims(
            BankruptcyPlan plan, List<ProportionalAllocator.Allocation> additionalPayouts) {
        if (plan == null || plan.companyId() == null || additionalPayouts == null) {
            return new BankruptcySettlement(false, 0, 0);
        }
        BigInteger additionalTotal = BigInteger.ZERO;
        Map<UUID, BigInteger> combined = new LinkedHashMap<>();
        for (ProportionalAllocator.Allocation allocation : plan.creditorAllocations()) {
            if (allocation.id() == null || allocation.amount() < 0) {
                return new BankruptcySettlement(false, plan.reservedForCreditors(), 0);
            }
            if (allocation.amount() > 0) combined.merge(allocation.id(), BigInteger.valueOf(allocation.amount()), BigInteger::add);
        }
        for (ProportionalAllocator.Allocation allocation : additionalPayouts) {
            if (allocation == null || allocation.id() == null || allocation.amount() < 0) {
                return new BankruptcySettlement(false, plan.reservedForCreditors(), 0);
            }
            additionalTotal = additionalTotal.add(BigInteger.valueOf(allocation.amount()));
            if (allocation.amount() > 0) combined.merge(allocation.id(), BigInteger.valueOf(allocation.amount()), BigInteger::add);
        }
        long additionalLimit = Math.max(0, plan.available() - plan.reservedForCreditors());
        if (additionalTotal.compareTo(BigInteger.valueOf(additionalLimit)) > 0) {
            return new BankruptcySettlement(false, plan.reservedForCreditors(), 0);
        }
        Map<UUID, Long> deposits = new LinkedHashMap<>();
        for (Map.Entry<UUID, BigInteger> entry : combined.entrySet()) {
            if (entry.getValue().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                return new BankruptcySettlement(false, plan.reservedForCreditors(), 0);
            }
            long amount = entry.getValue().longValue();
            if(finance.bank.BankingManager.bank(entry.getKey())!=null){if(!CompanyLoanManager.canApplyBankruptcyRecovery(plan.companyId(),entry.getKey(),amount))return new BankruptcySettlement(false,plan.reservedForCreditors(),0);continue;}
            if (!AccountManager.canDeposit(entry.getKey(), amount)) {
                return new BankruptcySettlement(false, plan.reservedForCreditors(), 0);
            }
            deposits.put(entry.getKey(), amount);
        }
        List<Map.Entry<UUID, Long>> credited = new ArrayList<>();
        long paid = 0;
        for (Map.Entry<UUID, Long> deposit : deposits.entrySet()) {
            if (!AccountManager.deposit(deposit.getKey(), deposit.getValue())) {
                for (Map.Entry<UUID, Long> rollback : credited) {
                    AccountManager.withdraw(rollback.getKey(), rollback.getValue());
                }
                return new BankruptcySettlement(false, plan.reservedForCreditors(), 0);
            }
            credited.add(deposit);
        }
        for (ProportionalAllocator.Allocation allocation : plan.creditorAllocations()) {
            paid = saturatedAdd(paid, allocation.amount());
            long remaining = allocation.amount();
            for (CorporateBond bond : BONDS.values()) {
                if (remaining <= 0) break;
                if (!bond.companyId().equals(plan.companyId())
                        || (bond.status() != BondStatus.ACTIVE && bond.status() != BondStatus.DEFAULTED)) continue;
                remaining -= bond.applyRecovery(allocation.id(), remaining);
            }
            if (remaining > 0 && (allocation.id().equals(finance.market.CentralBank.UUID)||finance.bank.BankingManager.bank(allocation.id())!=null)){
                long applied=CompanyLoanManager.applyBankruptcyRecovery(plan.companyId(),allocation.id(),remaining);
                if(applied!=remaining)throw new IllegalStateException("prevalidated bankruptcy recovery failed");
            }
        }
        for (CorporateBond bond : BONDS.values()) if (bond.companyId().equals(plan.companyId())
                && (bond.status() == BondStatus.ACTIVE || bond.status() == BondStatus.DEFAULTED)) {
            bond.setStatus(BondStatus.DEFAULTED);
        }
        CompanyLoanManager.markBankruptcyDefault(plan.companyId());
        EconomySavedData.markDirty();
        return new BankruptcySettlement(true, plan.reservedForCreditors(), Math.min(plan.available(), paid));
    }
    public record BankruptcyPlan(UUID companyId, long available, long reservedForCreditors,
                                 List<ProportionalAllocator.Allocation> creditorAllocations) { }
    public record BankruptcySettlement(boolean complete, long reservedForCreditors, long paidToCreditors) { }
    public static Map<UUID, CorporateBond> bonds() { return java.util.Collections.unmodifiableMap(BONDS); }
    public static void putDirect(CorporateBond bond) { if (bond != null && BONDS.size() < FinanceConfig.maxCorporateBonds()) BONDS.put(bond.id(), bond); }
    public static void clearDirect() { BONDS.clear(); }

    private static MoneyEndpoint escrowEndpoint(CorporateBond bond) {
        return new MoneyEndpoint() {
            public String id() { return "bond-escrow:" + bond.id(); }
            public long balance() { return bond.escrowCash(); }
            public boolean canDebit(long amount) { return amount > 0 && bond.escrowCash() >= amount; }
            public boolean canCredit(long amount) { return bond.canCreditEscrow(amount); }
            public boolean debit(long amount) { return bond.debitEscrow(amount); }
            public boolean credit(long amount) { return bond.creditEscrow(amount); }
        };
    }
    private static long exactProductOrNegative(long a, long b) { try { return Math.multiplyExact(a, b); } catch (ArithmeticException e) { return -1; } }
    private static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private static void markDefault(CorporateBond bond, String reason) {
        if (bond.status() == BondStatus.DEFAULTED) return;
        if (!BondMarketManager.cancelOrdersForBond(bond.id())) return;
        bond.setStatus(BondStatus.DEFAULTED);
        Company company = CompanyManager.getCompany(bond.companyId());
        record(bond, company == null ? null : company.getOwnerId(), outstandingPrincipal(bond.companyId()),
                bond.subscribedQuantity(), TransactionType.BOND_DEFAULT, "债券违约:" + reason);
        EconomySavedData.markDirty();
    }
    private static void record(CorporateBond b, UUID player, long amount, long quantity, TransactionType type, String action) {
        AccountManager.addTransactionRecord(new TransactionRecord(b.companyId(), player, amount, type, player, action + "/" + b.code(), quantity));
    }
    public record Result(boolean success, UUID id, String message) {
        static Result ok(UUID id, String m) { return new Result(true, id, m); }
        static Result fail(String m) { return new Result(false, null, m); }
    }
}
