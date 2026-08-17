package finance.debt;

import finance.bondmarket.BondMarketManager;
import finance.bondmarket.BondPortfolioManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.policy.MonetaryPolicyService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.UUID;

/** Linear-accrual, simple-discount reference model for Minecraft-day bonds. */
public final class FixedIncomeValuationService {
    private static final MathContext MC = new MathContext(24, RoundingMode.HALF_EVEN);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private FixedIncomeValuationService() { }

    public static BondValuation value(CorporateBond bond, UUID playerId, long currentDay) {
        if (bond == null || playerId == null || currentDay < 0) return zero();
        long quantity = bond.holdings().getOrDefault(playerId, 0L);
        BigInteger gross = BigInteger.valueOf(bond.faceValue()).multiply(BigInteger.valueOf(quantity));
        BigInteger recovered = BigInteger.valueOf(bond.recoveredPrincipal().getOrDefault(playerId, 0L));
        long remainingPrincipal = cap(gross.subtract(recovered).max(BigInteger.ZERO));
        long accrualEnd = Math.min(currentDay, bond.maturityDay());
        long accruedDays = Math.max(0, accrualEnd - bond.lastCouponDay());
        long accrued = cap(coupon(gross, bond.couponBasisPoints(), accruedDays));
        long daysToNext = Math.max(0, Math.min(bond.nextCouponDay(), bond.maturityDay()) - currentDay);
        int referenceYield = referenceYieldBasisPoints(bond, currentDay);
        long referenceUnit = referencePricePerUnit(bond, currentDay, referenceYield);
        long marketUnit = BondMarketManager.lastPrice(bond.id(), referenceUnit);
        int marketYield = marketYieldBasisPoints(bond, currentDay, marketUnit);
        long marketValue = cap(BigInteger.valueOf(marketUnit).multiply(BigInteger.valueOf(quantity)));
        var position = BondPortfolioManager.position(bond.id(), playerId);
        long totalCost = position == null ? 0 : position.totalCost();
        long unrealized = clampSigned(BigInteger.valueOf(marketValue).subtract(BigInteger.valueOf(totalCost)));
        long remainingCashFlows = cap(remainingCashFlows(bond, quantity, currentDay));
        return new BondValuation(remainingPrincipal, daysToNext, accrued, remainingCashFlows,
                referenceYield, referenceUnit, marketUnit, marketYield, marketValue, unrealized);
    }

    public static int referenceYieldBasisPoints(CorporateBond bond, long currentDay) {
        if (bond == null) return 0;
        Company company = CompanyManager.getCompany(bond.companyId());
        CreditRating rating = company == null ? CreditRating.D : CompanyCreditService.rate(company);
        long remainingDays = Math.max(0, bond.maturityDay() - currentDay);
        long result = (long) MonetaryPolicyService.benchmarkRateBasisPoints()
                + rating.spreadBasisPoints() + Math.min(500, remainingDays * 5);
        return (int) Math.min(FinanceConfig.maxContractRateBasisPoints(), Math.max(0, result));
    }

    public static long referencePricePerUnit(CorporateBond bond, long currentDay, int referenceYieldBps) {
        if (bond == null || currentDay < 0 || bond.faceValue() <= 0) return 0;
        if (bond.status() == BondStatus.DEFAULTED) return Math.max(0, bond.faceValue() * 40 / 100);
        if (bond.status() == BondStatus.MATURED || bond.status() == BondStatus.CANCELLED) return 0;
        BigDecimal present = BigDecimal.ZERO;
        long cursor = bond.lastCouponDay();
        while (cursor < bond.maturityDay()) {
            long next = Math.min(bond.maturityDay(), safeAdd(cursor, bond.couponIntervalDays()));
            long days = Math.max(0, next - cursor);
            BigInteger cash = coupon(BigInteger.valueOf(bond.faceValue()), bond.couponBasisPoints(), days);
            if (next == bond.maturityDay()) cash = cash.add(BigInteger.valueOf(bond.faceValue()));
            if (next > currentDay) present = present.add(discount(cash, referenceYieldBps, next - currentDay), MC);
            cursor = next;
            if (cursor == Long.MAX_VALUE) break;
        }
        if (currentDay >= bond.maturityDay()) return bond.faceValue();
        return cap(present.max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN).toBigInteger());
    }

    /** Simple annualized yield estimate: annual coupon plus annualized pull-to-par, divided by market price. */
    public static int marketYieldBasisPoints(CorporateBond bond, long currentDay, long marketPricePerUnit) {
        if (bond == null || marketPricePerUnit <= 0 || currentDay >= bond.maturityDay()
                || bond.status() != BondStatus.ACTIVE) return 0;
        long remainingDays = Math.max(1, bond.maturityDay() - currentDay);
        BigDecimal annualCoupon = BigDecimal.valueOf(bond.faceValue())
                .multiply(BigDecimal.valueOf(bond.couponBasisPoints()), MC)
                .divide(BigDecimal.valueOf(10_000), MC);
        BigDecimal pullToPar = BigDecimal.valueOf(bond.faceValue() - marketPricePerUnit)
                .multiply(BigDecimal.valueOf(FinanceConfig.annualMcDays()), MC)
                .divide(BigDecimal.valueOf(remainingDays), MC);
        BigDecimal yieldBps = annualCoupon.add(pullToPar, MC)
                .multiply(BigDecimal.valueOf(10_000), MC)
                .divide(BigDecimal.valueOf(marketPricePerUnit), MC);
        return yieldBps.max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(FinanceConfig.maxContractRateBasisPoints()))
                .setScale(0, RoundingMode.HALF_EVEN).intValue();
    }

    private static BigInteger remainingCashFlows(CorporateBond bond, long quantity, long currentDay) {
        if (bond.status() == BondStatus.DEFAULTED) {
            return BigInteger.valueOf(referencePricePerUnit(bond, currentDay,
                    referenceYieldBasisPoints(bond, currentDay))).multiply(BigInteger.valueOf(quantity));
        }
        BigInteger total = BigInteger.ZERO;
        long cursor = bond.lastCouponDay();
        while (cursor < bond.maturityDay()) {
            long next = Math.min(bond.maturityDay(), safeAdd(cursor, bond.couponIntervalDays()));
            if (next > currentDay) total = total.add(coupon(
                    BigInteger.valueOf(bond.faceValue()).multiply(BigInteger.valueOf(quantity)),
                    bond.couponBasisPoints(), next - cursor));
            cursor = next;
            if (cursor == Long.MAX_VALUE) break;
        }
        return total.add(BigInteger.valueOf(bond.faceValue()).multiply(BigInteger.valueOf(quantity)));
    }

    private static BigInteger coupon(BigInteger principal, int couponBps, long days) {
        if (principal.signum() <= 0 || couponBps <= 0 || days <= 0) return BigInteger.ZERO;
        return principal.multiply(BigInteger.valueOf(couponBps)).multiply(BigInteger.valueOf(days))
                .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L));
    }

    private static BigDecimal discount(BigInteger cash, int yieldBps, long days) {
        BigDecimal fraction = BigDecimal.valueOf(Math.max(0, yieldBps))
                .multiply(BigDecimal.valueOf(Math.max(0, days)), MC)
                .divide(BigDecimal.valueOf((long) FinanceConfig.annualMcDays() * 10_000L), MC);
        return new BigDecimal(cash).divide(BigDecimal.ONE.add(fraction), MC);
    }

    private static long safeAdd(long value, long increment) {
        try { return Math.addExact(value, increment); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }
    private static long cap(BigInteger value) { return value.max(BigInteger.ZERO).min(LONG_MAX).longValue(); }
    private static long clampSigned(BigInteger value) {
        return value.max(BigInteger.valueOf(Long.MIN_VALUE)).min(LONG_MAX).longValue();
    }
    private static BondValuation zero() { return new BondValuation(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
}
