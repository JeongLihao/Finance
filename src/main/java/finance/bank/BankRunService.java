package finance.bank;

import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 银行挤兑编排器：恐慌度更新 → NPC 挤兑流出 → 恐慌违约 → 非流动性死亡判定 → 自动桥接处置。
 * 所有资金流动走现有复式账本；本类只维护恐慌/排队等统计量。
 * 调用约定：仅服务端主线程调用（与全库单线程模型一致）；synchronized 为防御性，不依赖锁顺序。
 */
public final class BankRunService {
    /** 恐慌度达到此值时 NPC 零售池开始净流出；同时是 BankPanicTier.UNEASY 档位下限。 */
    public static final int PANIC_NPC_OUTFLOW_THRESHOLD = 30;

    private static final Map<UUID, BankRunState> STATES = new LinkedHashMap<>();
    private static final java.util.Set<UUID> KNOWN_FAILED = new java.util.HashSet<>();
    private static long lastProcessedDay = -1;
    private static long lastFailureDay = Long.MIN_VALUE / 4;

    private BankRunService() {}

    // ---------------- 查询 ----------------

    public static synchronized BankRunState stateOf(UUID bankId) {
        return STATES.computeIfAbsent(bankId, BankRunState::new);
    }

    public static int panicOf(UUID bankId) {
        BankRunState s = STATES.get(bankId);
        return s == null ? 0 : s.panic();
    }

    /** 同业冻结：恐慌过高的银行借不到同业拆借，只能找央行。 */
    public static boolean isInterbankFrozen(UUID bankId) {
        if (!active()) return false;
        BankRunState s = STATES.get(bankId);
        return s != null && s.panic() >= FinanceConfig.bankRunFreezePanicThreshold();
    }

    /** 是否处于排队限提档位。 */
    public static boolean withdrawalCapped(UUID bankId) {
        if (!active()) return false;
        BankRunState s = STATES.get(bankId);
        return s != null && s.panic() >= FinanceConfig.bankRunCapPanicThreshold();
    }

    /** NPC 聚合账户 ownerId（确定性派生）。 */
    public static UUID npcOwnerId(CommercialBank bank) {
        return UUID.nameUUIDFromBytes(("finance-npc-retail-" + bank.code()).getBytes(StandardCharsets.UTF_8));
    }

    // ---------------- 玩家行为钩子（BankingManager 调用） ----------------

    /** 记录玩家当日提现/转出（恐慌信号源之一）。 */
    public static void recordPlayerWithdrawal(UUID bankId, long amount) {
        if (!active() || bankId == null || amount <= 0) return;
        synchronized (BankRunService.class) {
            stateOf(bankId).addWithdrawn(amount);
        }
    }

    /**
     * 挤兑期每日提现预算。预算内返回 true 并扣减；超额返回 false（调用方拒绝提款）。
     * 恐慌未达限提档位时恒为 true。
     */
    public static synchronized boolean tryConsumeWithdrawalBudget(UUID bankId, long amount, long day) {
        if (!active() || bankId == null || amount <= 0) return amount > 0;
        CommercialBank bank = BankingManager.bank(bankId);
        if (bank == null) return false;
        BankRunState state = stateOf(bankId);
        if (state.panic() < FinanceConfig.bankRunCapPanicThreshold()) return true;
        if (state.budgetDay() != day) state.resetBudget(day);
        long budget = dailyBudget(bank);
        if (amount > budget || state.budgetUsed() > budget - amount) return false;
        state.addBudgetUsed(amount);
        return true;
    }

    /** 退还之前消费的预算（提款在账务阶段失败时调用，当日有效）。 */
    public static synchronized void refundWithdrawalBudget(UUID bankId, long amount) {
        if (bankId == null || amount <= 0) return;
        BankRunState state = STATES.get(bankId);
        if (state != null) state.addBudgetUsed(-amount);
    }

    public static synchronized long dailyBudget(CommercialBank bank) {
        if (bank == null) return 0;
        BankBalanceSheet s = bank.ledger().balanceSheet();
        long deposits = safeAdd(s.demandDeposits(), s.timeDeposits());
        return BigInteger.valueOf(deposits)
                .multiply(BigInteger.valueOf(FinanceConfig.bankRunDailyCapBps()))
                .divide(BigInteger.valueOf(10_000))
                .longValue();
    }

    // ---------------- 日终主流程 ----------------

    public static synchronized void processDay(long day) {
        if (!active()) return;
        if (day <= lastProcessedDay) return;
        BankingManager.ensureDefaultBanks();
        for (long d = lastProcessedDay + 1; d <= day; d++) {
            processOneDay(d);
            lastProcessedDay = d;
            if (d == Long.MAX_VALUE) break;
        }
        EconomySavedData.markDirty();
    }

    private static void processOneDay(long day) {
        List<CommercialBank> banks = new ArrayList<>(BankingManager.banks().values());
        for (CommercialBank b : banks) ensureNpcPool(b, day);
        // 1. 恐慌度更新
        for (CommercialBank b : banks) {
            if (b.status() == BankStatus.FAILED || b.status() == BankStatus.MERGED) continue;
            BankRunState state = stateOf(b.id());
            BankPanicService.PanicInputs in =
                    BankPanicService.inputsOf(b, state.dailyWithdrawn(), contagionActive(day));
            int stress = BankPanicService.stressOf(in);
            BankPanicService.BankRumor rumor = BankPanicService.maybeRumor(b, in, state, day);
            BankPanicService.updatePanic(state, stress, rumor, day);
            state.resetDailyWithdrawn();
        }
        // 2. NPC 挤兑流出
        for (CommercialBank b : banks) {
            if (!operable(b)) continue;
            BankRunState state = stateOf(b.id());
            if (state.panic() >= PANIC_NPC_OUTFLOW_THRESHOLD || state.queued() > 0) processNpcOutflow(b, state, day);
        }
        // 3. 恐慌违约（偿付能力通道）
        for (CommercialBank b : banks) {
            if (!operable(b)) continue;
            applyPanicDefaults(b, stateOf(b.id()), day);
        }
        // 4. 非流动性死亡判定
        for (CommercialBank b : banks) {
            if (!operable(b)) continue;
            checkIlliquidityDeath(b, stateOf(b.id()), day);
        }
        // 5. 观察倒闭（传染源）
        for (CommercialBank b : banks) {
            if (b.status() == BankStatus.FAILED && KNOWN_FAILED.add(b.id())) lastFailureDay = day;
        }
        // 6. 自动桥接处置
        autoResolve(day);
    }

    private static boolean operable(CommercialBank b) {
        return b.status() != BankStatus.FAILED
                && b.status() != BankStatus.MERGED
                && b.status() != BankStatus.RESOLUTION;
    }

    private static boolean contagionActive(long day) {
        return lastFailureDay > Long.MIN_VALUE / 8 && day - lastFailureDay <= 7;
    }

    private static boolean active() {
        return FinanceConfig.bankRunEnabled() && ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.BANK_RUN);
    }

    /** 日终前确保所有银行的 NPC 零售池已播种（幂等）。 */
    public static synchronized void ensureNpcPools(long day) {
        if (!active()) return;
        BankingManager.ensureDefaultBanks();
        for (CommercialBank b : BankingManager.banks().values()) ensureNpcPool(b, day);
        EconomySavedData.markDirty();
    }

    // ---------------- NPC 零售池 ----------------

    static BankCustomerAccount npcAccount(UUID bankId, UUID owner, BankAccountType type) {
        for (BankCustomerAccount a : BankingManager.accountsOfBank(bankId)) {
            if (a.ownerId().equals(owner)
                    && a.ownerType() == CustomerType.NPC
                    && a.type() == type
                    && a.status() != BankAccountStatus.CLOSED) return a;
        }
        return null;
    }

    /** NPC 零售池存款总额（保险缴费基数剔除用，与赔付口径对齐）。 */
    static long npcPoolDeposits(UUID bankId) {
        long sum = 0;
        for (BankCustomerAccount a : BankingManager.accountsOfBank(bankId)) {
            if (a.ownerType() == CustomerType.NPC && a.status() != BankAccountStatus.CLOSED && a.balance() > 0)
                sum = sum > Long.MAX_VALUE - a.balance() ? Long.MAX_VALUE : sum + a.balance();
        }
        return sum;
    }

    private static void ensureNpcPool(CommercialBank bank, long day) {
        if (bank == null || !bank.acceptsNewBusiness() || "BRIDGE".equals(bank.code())) return;
        BankRunState state = stateOf(bank.id());
        if (state.npcLoanBook() > 0) return; // 已播种（持久化）
        long seed = BigInteger.valueOf(FinanceConfig.bankInitialCapital())
                .multiply(BigInteger.valueOf(FinanceConfig.bankRunNpcSeedBps()))
                .divide(BigInteger.valueOf(10_000))
                .longValue();
        if (seed <= 0) return;
        long timePart = BigInteger.valueOf(seed)
                .multiply(BigInteger.valueOf(FinanceConfig.bankRunNpcTimeShareBps()))
                .divide(BigInteger.valueOf(10_000))
                .longValue();
        UUID owner = npcOwnerId(bank);
        BankCustomerAccount demand = npcAccount(bank.id(), owner, BankAccountType.DEMAND_DEPOSIT);
        BankCustomerAccount time = npcAccount(bank.id(), owner, BankAccountType.TIME_DEPOSIT);
        if (demand == null) {
            demand = new BankCustomerAccount(
                    UUID.randomUUID(),
                    bank.id(),
                    owner,
                    CustomerType.NPC,
                    BankAccountType.DEMAND_DEPOSIT,
                    0,
                    0,
                    Math.max(0, day),
                    BankAccountStatus.ACTIVE);
            BankingManager.putAccountDirect(demand);
        }
        if (time == null) {
            time = new BankCustomerAccount(
                    UUID.randomUUID(),
                    bank.id(),
                    owner,
                    CustomerType.NPC,
                    BankAccountType.TIME_DEPOSIT,
                    0,
                    0,
                    Math.max(0, day),
                    BankAccountStatus.ACTIVE);
            BankingManager.putAccountDirect(time);
        }
        // 贷款创造存款：Dr 贷款资产 / Cr 活期负债（复式平衡，不印钱）
        UUID ref = UUID.nameUUIDFromBytes(("finance-npc-seed-" + bank.id()).getBytes(StandardCharsets.UTF_8));
        if (!bank.ledger().hasReference(ref)) {
            List<BankLedger.Draft> drafts = List.of(new BankLedger.Draft(
                    BankLedgerAccount.ASSET_COMPANY_LOAN,
                    BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                    seed,
                    BankLedgerReason.COMPANY_LOAN_ISSUE));
            if (!bank.ledger().canPost(ref, drafts)) return;
            bank.ledger().postBatch(day, ref, drafts);
            if (!demand.credit(seed)) throw new IllegalStateException("npc seed credit failed");
            state.setNpcLoanBook(seed);
        }
        // 部分转定期
        if (timePart > 0) {
            UUID ref2 = UUID.nameUUIDFromBytes(("finance-npc-seed-time-" + bank.id()).getBytes(StandardCharsets.UTF_8));
            if (!bank.ledger().hasReference(ref2)) {
                List<BankLedger.Draft> move = List.of(new BankLedger.Draft(
                        BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                        BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                        timePart,
                        BankLedgerReason.TIME_DEPOSIT_OPEN));
                if (bank.ledger().canPost(ref2, move) && demand.debit(timePart)) {
                    bank.ledger().postBatch(day, ref2, move);
                    if (!time.credit(timePart)) throw new IllegalStateException("npc time seed credit failed");
                }
            }
        }
        EconomySavedData.markDirty();
    }

    // ---------------- 挤兑流出 ----------------

    private static void processNpcOutflow(CommercialBank bank, BankRunState state, long day) {
        UUID owner = npcOwnerId(bank);
        BankCustomerAccount demand = npcAccount(bank.id(), owner, BankAccountType.DEMAND_DEPOSIT);
        BankCustomerAccount time = npcAccount(bank.id(), owner, BankAccountType.TIME_DEPOSIT);
        long npcDemand = demand == null ? 0 : demand.available();
        long npcTime = time == null ? 0 : time.available();
        long pool = safeAdd(npcDemand, npcTime);
        long desired = BigInteger.valueOf(pool)
                .multiply(BigInteger.valueOf(state.panic()))
                .multiply(BigInteger.valueOf(FinanceConfig.bankRunMaxDailyShareBps()))
                .divide(BigInteger.valueOf(100L * 10_000L))
                .longValue();
        long total = safeAdd(desired, state.queued());
        if (total <= 0) return;
        // 限提预算（玩家已消耗部分优先）
        long budgetLeft = budgetRemaining(bank, state, day);
        long payable = Math.min(total, budgetLeft);
        // 流动性瀑布：自有准备金 → 同业（可能冻结） → 央行紧急贷款
        long reserves = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        if (payable > reserves) {
            BankLiquidityService.ensureReserves(bank.id(), payable, day);
            reserves = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        }
        long actual = Math.min(payable, reserves);
        long fromDemand = Math.min(actual, npcDemand);
        long fromTime = actual - fromDemand;
        UUID ref =
                UUID.nameUUIDFromBytes(("finance-bank-run-" + bank.id() + "-" + day).getBytes(StandardCharsets.UTF_8));
        List<BankLedger.Draft> drafts = new ArrayList<>();
        if (fromDemand > 0)
            drafts.add(new BankLedger.Draft(
                    BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                    BankLedgerAccount.ASSET_RESERVE,
                    fromDemand,
                    BankLedgerReason.BANK_RUN_WITHDRAWAL));
        if (fromTime > 0)
            drafts.add(new BankLedger.Draft(
                    BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                    BankLedgerAccount.ASSET_RESERVE,
                    fromTime,
                    BankLedgerReason.TIME_DEPOSIT_EARLY_REDEEM));
        boolean posted = false;
        if (!drafts.isEmpty()
                && !bank.ledger().hasReference(ref)
                && bank.ledger().canPost(ref, drafts)) {
            boolean ok = (fromDemand == 0 || demand.debit(fromDemand)) && (fromTime == 0 || time.debit(fromTime));
            if (ok && bank.ledger().postBatch(day, ref, drafts)) posted = true;
            else { // 回滚账户（账本未动）
                if (fromDemand > 0 && !demand.credit(fromDemand)) throw new IllegalStateException();
                if (fromTime > 0 && !time.credit(fromTime)) throw new IllegalStateException();
            }
        }
        long paid = posted ? actual : 0;
        if (paid > 0) {
            state.addOutflow(paid);
            state.addWithdrawn(paid);
            state.reduceQueued(paid);
            if (state.budgetDay() != day) state.resetBudget(day);
            state.addBudgetUsed(paid);
        }
        long unpaid = total - paid;
        state.reduceQueued(state.queued());
        if (unpaid > 0) {
            state.addQueued(unpaid);
            // 挤兑未满足 → 恐慌加剧
            BankBalanceSheet s = bank.ledger().balanceSheet();
            long deposits = Math.max(1, safeAdd(s.demandDeposits(), s.timeDeposits()));
            int bump = (int) Math.min(
                    10,
                    BigInteger.valueOf(unpaid)
                            .multiply(BigInteger.valueOf(200))
                            .divide(BigInteger.valueOf(deposits))
                            .longValue());
            state.setPanic(state.panic() + bump, day);
        }
        EconomySavedData.markDirty();
    }

    private static long budgetRemaining(CommercialBank bank, BankRunState state, long day) {
        if (state.panic() < FinanceConfig.bankRunCapPanicThreshold()) return Long.MAX_VALUE;
        if (state.budgetDay() != day) state.resetBudget(day);
        return Math.max(0, dailyBudget(bank) - state.budgetUsed());
    }

    // ---------------- 恐慌违约 ----------------

    private static void applyPanicDefaults(CommercialBank bank, BankRunState state, long day) {
        if (state.panic() < FinanceConfig.bankRunFreezePanicThreshold()) return;
        long book = Math.min(state.npcLoanBook(), bank.ledger().balance(BankLedgerAccount.ASSET_COMPANY_LOAN));
        if (book <= 0) return;
        // 日违约 = book × (panic/100)² × defaultBps/10000
        long defaulted = BigInteger.valueOf(book)
                .multiply(BigInteger.valueOf(state.panic()))
                .multiply(BigInteger.valueOf(state.panic()))
                .multiply(BigInteger.valueOf(FinanceConfig.bankRunNpcDefaultBps()))
                .divide(BigInteger.valueOf(100L * 100L * 10_000L))
                .longValue();
        defaulted = Math.min(defaulted, book);
        if (defaulted <= 0) return;
        UUID ref = UUID.nameUUIDFromBytes(
                ("finance-npc-default-" + bank.id() + "-" + day).getBytes(StandardCharsets.UTF_8));
        if (bank.ledger().hasReference(ref)) return;
        List<BankLedger.Draft> drafts = List.of(new BankLedger.Draft(
                BankLedgerAccount.EXPENSE_CREDIT_LOSS,
                BankLedgerAccount.ASSET_COMPANY_LOAN,
                defaulted,
                BankLedgerReason.NPC_LOAN_DEFAULT));
        if (bank.ledger().canPost(ref, drafts) && bank.ledger().postBatch(day, ref, drafts)) {
            state.reduceNpcLoanBook(defaulted);
            BankRegulatoryService.evaluate(bank);
            EconomySavedData.markDirty();
        }
    }

    // ---------------- 非流动性死亡 ----------------

    private static void checkIlliquidityDeath(CommercialBank bank, BankRunState state, long day) {
        long required = BankRegulatoryService.requiredReserves(bank);
        long reserves = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        boolean illiquid =
                reserves < required && state.queued() > 0 && state.panic() >= FinanceConfig.bankRunFailPanicThreshold();
        state.setIlliquidDays(illiquid ? state.illiquidDays() + 1 : 0);
        if (state.illiquidDays() >= FinanceConfig.bankRunIlliquidDays()) {
            bank.setStatus(BankStatus.RESOLUTION);
            state.setIlliquidDays(0);
            EconomySavedData.markDirty();
        }
    }

    // ---------------- 自动桥接处置 ----------------

    private static void autoResolve(long day) {
        if (!FinanceConfig.bankRunAutoResolve()) return;
        for (CommercialBank b : new ArrayList<>(BankingManager.banks().values())) {
            if (b.status() != BankStatus.RESOLUTION) continue;
            if (DepositInsuranceService.resolvedBanks().contains(b.id())) continue;
            if (DepositInsuranceService.resolve(b.id(), day)) {
                BankLiquidationService.settleUninsured(b.id(), day);
                KNOWN_FAILED.add(b.id());
                lastFailureDay = day;
            }
        }
    }

    // ---------------- 持久化 ----------------

    public static Map<UUID, BankRunState> states() {
        return Collections.unmodifiableMap(STATES);
    }

    public static long lastProcessedDay() {
        return lastProcessedDay;
    }

    public static long lastFailureDay() {
        return lastFailureDay;
    }

    public static synchronized void restoreState(
            UUID bankId,
            int panic,
            long lastPanicDay,
            long queued,
            int illiquidDays,
            long rumorCooldownDay,
            int peakPanic,
            long cumulativeOutflow,
            long lastRumorDay,
            String lastRumorText,
            long budgetDay,
            long budgetUsed,
            long npcLoanBook) {
        stateOf(bankId)
                .restore(
                        panic,
                        lastPanicDay,
                        queued,
                        illiquidDays,
                        rumorCooldownDay,
                        peakPanic,
                        cumulativeOutflow,
                        lastRumorDay,
                        lastRumorText,
                        budgetDay,
                        budgetUsed,
                        npcLoanBook);
    }

    public static synchronized void restoreFailureDay(long day) {
        lastFailureDay = day;
    }

    /** 持久化恢复：上次处理到的日终天数（仿 InterbankMarketService.restoreDay）。 */
    public static synchronized void restoreRunDay(long day) {
        lastProcessedDay = Math.max(-1, day);
    }

    /** 重启恢复：存档中已处于 FAILED 的银行视为已知倒闭，避免重启当日被误判为"新倒闭"而延长传染窗口。 */
    public static synchronized void markFailureKnown(UUID bankId) {
        if (bankId != null) KNOWN_FAILED.add(bankId);
    }

    public static synchronized void clearDirect() {
        STATES.clear();
        KNOWN_FAILED.clear();
        lastProcessedDay = -1;
        lastFailureDay = Long.MIN_VALUE / 4;
    }

    private static long safeAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
