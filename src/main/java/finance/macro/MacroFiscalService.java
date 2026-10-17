package finance.macro;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.market.NpcMarketMaker;
import finance.policy.MonetaryPolicyService;
import finance.util.MathUtil;
import finance.village.VillageManager;
import finance.village.VillageRecord;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

/**
 * 宏观经济第四步：国家财政。
 *
 * <p>收入：公司利润税（{@link #collectProfitTax}）、保护主义关税（{@link #collectTariff}）、提案费。
 * 支出：利息、行政开支、福利，都经 {@link #spend} 支付；余额不够时向央行借款，到债务上限仍不够就停发并进入紧缩。
 * 央行放款先用准备金，准备金不够才增发货币并记 {@code MACRO_DEBT_ISSUANCE}。</p>
 */
public final class MacroFiscalService {
    /** 国库保留几天的支出，超出部分自动还债。 */
    static final int CASH_BUFFER_DAYS = 7;

    private MacroFiscalService() { }

    public static boolean active() {
        return MacroEconomyService.active() && FinanceConfig.macroFiscalEnabled();
    }

    static void ensureAccount() {
        AccountManager.getOrCreateSystemAccount(Treasury.UUID);
    }

    public static long balance() {
        return AccountManager.getAccounts().containsKey(Treasury.UUID) ? AccountManager.getBalance(Treasury.UUID) : 0;
    }

    // ================================================================ 收入

    /** 日利润结算前调用：按税制对当日营业毛利征税，计入公司成本。返回税额。 */
    public static long collectProfitTax(Company company) {
        if (!active() || company == null) return 0;
        long base = company.getDailyRevenue() - company.getDailyCost();
        int bps = FinanceConfig.macroPoliticsEnabled()
                ? MacroEconomyManager.law(LawGroup.TAXATION).profitTaxBps()
                : LawGroup.TAXATION.defaultLevel().profitTaxBps();
        if (base <= 0 || bps <= 0) return 0;
        long tax = BigInteger.valueOf(base).multiply(BigInteger.valueOf(bps)).divide(BigInteger.valueOf(10_000)).longValue();
        tax = Math.min(tax, Math.max(0, company.getCash()));
        if (tax <= 0) return 0;
        ensureAccount();
        if (!company.withdraw(tax)) return 0;
        if (!AccountManager.deposit(Treasury.UUID, tax)) {
            company.deposit(tax);
            return 0;
        }
        company.recordGameplayCost(tax);
        Treasury.book(Treasury.Item.TAX, tax);
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(), Treasury.UUID, tax,
                TransactionType.MACRO_TAX, company.getOwnerId(), company.getName(), 0));
        EconomySavedData.markDirty();
        return tax;
    }

    /**
     * NPC 行商卖给聚落后调用：保护主义下村民多付的部分（含税价 − 不含税价）由 NPC 资金池转入国库。
     */
    public static long collectTariff(String commodityId, long baseUnitPrice, long grossUnitPrice, int qty) {
        if (!active() || qty <= 0 || grossUnitPrice <= baseUnitPrice) return 0;
        long perUnit = grossUnitPrice - baseUnitPrice;
        long amount = MathUtil.multiplyExactOrNegative1(perUnit, qty);
        if (amount <= 0) return 0;
        ensureAccount();
        if (!AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, Treasury.UUID, amount)) return 0;
        Treasury.book(Treasury.Item.TARIFF, amount);
        AccountManager.addTransactionRecord(new TransactionRecord(NpcMarketMaker.NPC_UUID, Treasury.UUID, amount,
                TransactionType.MACRO_TARIFF, NpcMarketMaker.NPC_UUID, commodityId, qty));
        return amount;
    }

    /** 提案费等规费：玩家 → 国库。 */
    static boolean collectFee(UUID payer, long amount, String note) {
        return collectFee(payer, amount, note, TransactionType.MACRO_POLITICS);
    }

    /** 规费：玩家 → 国库，按给定流水类型记账。 */
    static boolean collectFee(UUID payer, long amount, String note, TransactionType type) {
        if (payer == null || amount <= 0) return amount == 0;
        ensureAccount();
        if (!AccountManager.moveFunds(payer, Treasury.UUID, amount)) return false;
        Treasury.book(Treasury.Item.FEES, amount);
        AccountManager.addTransactionRecord(new TransactionRecord(payer, Treasury.UUID, amount,
                type, payer, note, 0));
        return true;
    }

    // ================================================================ 支出

    /**
     * 从国库支付一笔开支；余额不够先借款，到债务上限仍不够就只付能付的部分并进入紧缩。返回实付金额。
     */
    static long spend(long amount, Treasury.Item item, UUID to, TransactionType type, String note) {
        if (amount <= 0 || to == null) return 0;
        ensureAccount();
        long balance = AccountManager.getBalance(Treasury.UUID);
        if (balance < amount) borrow(amount - balance);
        long pay = Math.min(amount, AccountManager.getBalance(Treasury.UUID));
        if (pay < amount) Treasury.setAusterity(true);
        if (pay <= 0 || !AccountManager.moveFunds(Treasury.UUID, to, pay)) return 0;
        Treasury.book(item, pay);
        AccountManager.addTransactionRecord(new TransactionRecord(Treasury.UUID, to, pay, type, to, note, 0));
        return pay;
    }

    /** 向央行借款，受债务上限约束；央行准备金不足时增发。返回实借金额。 */
    static long borrow(long wanted) {
        long room = Math.max(0, FinanceConfig.macroDebtCeiling() - Treasury.debt());
        long amount = Math.min(Math.max(0, wanted), room);
        if (amount <= 0) return 0;
        AccountManager.getOrCreateSystemAccount(CentralBank.UUID);
        long reserve = AccountManager.getBalance(CentralBank.UUID);
        long issuance = Math.max(0, amount - reserve);
        if (issuance > 0) {
            if (!AccountManager.deposit(CentralBank.UUID, issuance)) return 0;
            Treasury.addIssuance(issuance);
            AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, CentralBank.UUID, issuance,
                    TransactionType.MACRO_DEBT_ISSUANCE, CentralBank.UUID, "国库借款", 0));
        }
        if (!AccountManager.moveFunds(CentralBank.UUID, Treasury.UUID, amount)) return 0;
        Treasury.setDebt(Treasury.debt() + amount);
        Treasury.book(Treasury.Item.BORROW, amount);
        AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, Treasury.UUID, amount,
                TransactionType.MACRO_DEBT_BORROW, CentralBank.UUID, "", 0));
        return amount;
    }

    /** 当日利息 = 债务 × (基准利率 + 点差) / 年天数，向上取整（有债就至少 1）。 */
    public static long dailyInterest(long debt) {
        if (debt <= 0) return 0;
        long rate = Math.max(0, MonetaryPolicyService.benchmarkRateBasisPoints()) + Math.max(0, FinanceConfig.macroDebtSpreadBps());
        long denom = (long) Math.max(1, FinanceConfig.annualMcDays()) * 10_000L;
        BigInteger[] qr = BigInteger.valueOf(debt).multiply(BigInteger.valueOf(rate)).divideAndRemainder(BigInteger.valueOf(denom));
        long interest = qr[0].longValue() + (qr[1].signum() > 0 ? 1 : 0);
        return rate == 0 ? 0 : interest;
    }

    /** 当前福利人均额。 */
    static long welfarePerCapita() {
        if (!FinanceConfig.macroPoliticsEnabled()) return 0;
        return switch (MacroEconomyManager.law(LawGroup.WELFARE)) {
            case POOR_RELIEF -> FinanceConfig.macroWelfareReliefPerCapita();
            case PUBLIC_HEALTH -> FinanceConfig.macroWelfareHealthPerCapita();
            default -> 0L;
        };
    }

    // ================================================================ 日结

    /**
     * 每天集市之前：收盘昨天的账 → 付利息 → 行政开支 → 福利 → 结余超过缓冲时还债。
     */
    public static void processDay(long day) {
        if (!active() || day <= Treasury.lastProcessedDay()) return;
        ensureAccount();
        if (Treasury.lastProcessedDay() != Long.MIN_VALUE) Treasury.close(Treasury.lastProcessedDay(), balance());
        Treasury.setLastProcessedDay(day);
        Treasury.setAusterity(false);

        long spentToday = 0;
        spentToday += spend(dailyInterest(Treasury.debt()), Treasury.Item.INTEREST, CentralBank.UUID,
                TransactionType.MACRO_DEBT_INTEREST, "");
        long population = 0;
        for (VillageRecord v : VillageManager.villages()) population += v.population();
        long admin = MathUtil.saturatedMultiplyNonNegative(Math.max(0, FinanceConfig.macroAdminCostPerCapita()), population);
        spentToday += spend(admin, Treasury.Item.ADMIN, NpcMarketMaker.NPC_UUID, TransactionType.MACRO_ADMIN, "");
        spentToday += payWelfare();
        repaySurplus(spentToday);
        EconomySavedData.markDirty();
    }

    private static long payWelfare() {
        long perCapita = welfarePerCapita();
        if (perCapita <= 0) return 0;
        String law = MacroEconomyManager.law(LawGroup.WELFARE).name();
        long total = 0;
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            VillageRecord village = VillageManager.get(econ.settlementId);
            if (village == null || village.budgetAccountId() == null) continue;
            long amount = MathUtil.multiplyExactOrNegative1(perCapita, village.population());
            if (amount <= 0) continue;
            long paid = spend(amount, Treasury.Item.WELFARE, village.budgetAccountId(), TransactionType.MACRO_WELFARE, law);
            if (paid > 0) econ.addIncome(paid);
            total += paid;
            if (paid < amount) break;
        }
        return total;
    }

    /** 国库余额超过 {@value #CASH_BUFFER_DAYS} 天支出时，多出的部分还给央行。 */
    static void repaySurplus(long dailySpending) {
        long debt = Treasury.debt();
        if (debt <= 0) return;
        long buffer = MathUtil.saturatedMultiplyNonNegative(Math.max(1, dailySpending), CASH_BUFFER_DAYS);
        long surplus = balance() - buffer;
        long repay = Math.min(debt, surplus);
        if (repay <= 0 || !AccountManager.moveFunds(Treasury.UUID, CentralBank.UUID, repay)) return;
        Treasury.setDebt(debt - repay);
        Treasury.book(Treasury.Item.REPAY, repay);
        AccountManager.addTransactionRecord(new TransactionRecord(Treasury.UUID, CentralBank.UUID, repay,
                TransactionType.MACRO_DEBT_REPAY, CentralBank.UUID, "", 0));
    }

    /** 债务占上限的比例（基点）。 */
    public static int debtRatioBps() {
        long ceiling = FinanceConfig.macroDebtCeiling();
        if (ceiling <= 0) return Treasury.debt() > 0 ? 10_000 : 0;
        return (int) Math.min(10_000, Treasury.debt() * 10_000 / ceiling);
    }

    /** 昨日收入与支出合计（不含借还款）。 */
    public static long[] lastDayTotals() {
        Treasury.Day d = Treasury.lastDay();
        long in = 0, out = 0;
        if (d != null) {
            for (Map.Entry<Treasury.Item, Long> e : d.flows().entrySet()) {
                if (e.getKey() == Treasury.Item.BORROW || e.getKey() == Treasury.Item.REPAY) continue;
                if (e.getKey().inflow()) in += e.getValue(); else out += e.getValue();
            }
        }
        return new long[]{in, out};
    }
}
