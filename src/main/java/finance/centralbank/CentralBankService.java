package finance.centralbank;

import finance.bank.BankLedgerAccount;
import finance.bank.BankLedgerReason;
import finance.bank.BankRegulatoryService;
import finance.bank.BankStatus;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.bank.PlayerBankService;
import finance.bank.TimeDeposit;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.fixedincome.TreasuryBond;
import finance.fixedincome.TreasuryBondManager;
import finance.macro.EconomyAchievements;
import finance.policy.MonetaryPolicyService;
import finance.policy.PolicyMeetingService;
import finance.policy.PolicyRuleService;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 中央银行：模组里最高的经济主体，只管货币和物价，不收税、不发福利。
 *
 * <ul>
 *   <li><b>物价目标</b>：议息规则按“实际通胀 − 目标通胀”调利率（见 {@link PolicyRuleService}）；</li>
 *   <li><b>存款准备金率</b>：议息日通胀明显高于目标时上调，明显低于目标或经济疲软时下调；</li>
 *   <li><b>公开市场操作</b>：每天向商业银行卖出（吸收超额准备金）或买回央行票据（投放），
 *       通胀越高，超额准备金目标越低，银行可放贷的余钱越少；</li>
 *   <li><b>商品平准</b>：见 {@link PriceStabilizationService}。</li>
 * </ul>
 *
 * <p>公开市场操作只在商业银行的复式总账里记账（准备金 ⇄ 持有的央行票据 / 欠央行的回购款），
 * 和准备金付息一样，不经过玩家钱包。</p>
 */
public final class CentralBankService {
    /** 一条议息记录（面板和周报用）。 */
    public record Decision(
            long day,
            int rateBefore,
            int rateAfter,
            int reserveBefore,
            int reserveAfter,
            int inflationBps,
            String note) {}

    public static final int MAX_DECISIONS = 24;
    public static final int MAX_POSITIONS = 1_024;
    /** 物价连续稳定多少天发“物价稳定”成就。 */
    static final int STABLE_DAYS_FOR_ACHIEVEMENT = 30;
    /** 通胀高出目标多少基点算“失控”。 */
    static final int RUNAWAY_GAP_BPS = 500;
    /** 衰退指标（风险公司占比）达到多少时下调准备金率。 */
    static final double SLACK_FOR_RESERVE_CUT = 0.2;

    private static int demandReserveBps = -1;
    /** 银行 id → 公开市场操作头寸：正数 = 银行持有的央行票据（已吸收），负数 = 欠央行的回购款（已投放）。 */
    private static final Map<UUID, Long> POSITIONS = new LinkedHashMap<>();

    private static final ArrayDeque<Decision> DECISIONS = new ArrayDeque<>();
    private static long lastProcessedDay = Long.MIN_VALUE;
    private static long absorbedToday, injectedToday, absorbedTotal, injectedTotal;
    private static int stableStreak;
    private static boolean runaway;

    private CentralBankService() {}

    // ---------------------------------------------------------------- 查询

    public static int inflationTargetBps() {
        return FinanceConfig.cbInflationTargetBps();
    }

    /** 当前年化通胀（基点）；没有足够数据时按 0。 */
    public static int inflationBps() {
        PolicyRuleService.Evaluation eval = PolicyMeetingService.currentEvaluation();
        return eval == null
                ? 0
                : (int) Math.max(-1_000_000, Math.min(1_000_000, Math.round(eval.annualizedInflationPct() * 100)));
    }

    /** 活期存款法定准备金率（基点）。未调过时等于配置的初始值。 */
    public static int demandReserveBps() {
        int v = demandReserveBps >= 0 ? demandReserveBps : FinanceConfig.bankDemandReserveBps();
        return Math.max(0, Math.min(10_000, v));
    }

    /** 定期存款准备金率：活期的一半（不超过配置的定期率上限之外的比例关系）。 */
    public static int timeReserveBps() {
        return demandReserveBps() / 2;
    }

    public static Map<UUID, Long> positions() {
        return Collections.unmodifiableMap(POSITIONS);
    }

    public static List<Decision> decisions() {
        return new ArrayList<>(DECISIONS);
    }

    public static long absorbedToday() {
        return absorbedToday;
    }

    public static long injectedToday() {
        return injectedToday;
    }

    public static long absorbedTotal() {
        return absorbedTotal;
    }

    public static long injectedTotal() {
        return injectedTotal;
    }

    public static int stableStreak() {
        return stableStreak;
    }

    public static boolean runaway() {
        return runaway;
    }

    public static long lastProcessedDay() {
        return lastProcessedDay;
    }

    /** 银行持有的央行票据合计（公开市场操作吸收的准备金）。 */
    public static long outstandingAbsorbed() {
        long sum = 0;
        for (long v : POSITIONS.values()) if (v > 0) sum = sat(sum, v);
        return sum;
    }

    /** 银行欠央行的回购款合计（公开市场操作投放的准备金）。 */
    public static long outstandingInjected() {
        long sum = 0;
        for (long v : POSITIONS.values()) if (v < 0) sum = sat(sum, -v);
        return sum;
    }

    // ---------------------------------------------------------------- 议息

    /**
     * 议息日调用（利率决议之后）：按通胀缺口调整准备金率，记录决议，发相关成就。
     * MANUAL 模式只记录，不自动调准备金率。
     */
    public static void onMeeting(
            long day, PolicyRuleService.Evaluation eval, int rateBefore, int rateAfter, boolean manual) {
        int inflation = eval == null ? 0 : (int) Math.round(eval.annualizedInflationPct() * 100);
        double slack = eval == null ? 0 : eval.slackRatio();
        int reserveBefore = demandReserveBps();
        int reserveAfter = reserveBefore;
        if (!manual && FinanceConfig.cbReserveAuto())
            reserveAfter = nextReserve(reserveBefore, inflation - inflationTargetBps(), slack);
        if (reserveAfter != reserveBefore) demandReserveBps = reserveAfter;
        String note = eval == null ? "" : eval.explanation();
        DECISIONS.addLast(new Decision(day, rateBefore, rateAfter, reserveBefore, reserveAfter, inflation, note));
        while (DECISIONS.size() > MAX_DECISIONS) DECISIONS.removeFirst();
        if (rateAfter < rateBefore) awardRateCall(day);
        if (reserveAfter < reserveBefore) {
            for (UUID owner : new HashSet<>(PlayerBankService.ownersView().values()))
                EconomyAchievements.award(owner, EconomyAchievements.RESERVE_CUT);
        }
        EconomySavedData.markDirty();
    }

    /** 准备金率规则：通胀高出目标两倍容忍区间就上调一档，低于目标两倍区间或经济明显疲软就下调一档。 */
    static int nextReserve(int current, int gapBps, double slack) {
        int band = Math.max(0, FinanceConfig.cbInflationBandBps());
        int step = Math.max(1, FinanceConfig.cbReserveStepBps());
        int min = FinanceConfig.cbReserveMinBps(), max = FinanceConfig.cbReserveMaxBps();
        int next = current;
        if (gapBps > 2 * band) next = current + step;
        else if (gapBps < -2 * band || slack >= SLACK_FOR_RESERVE_CUT) next = current - step;
        return Math.max(min, Math.min(max, next));
    }

    /** 降息前的前瞻窗口里认购央行债券或开定存的玩家锁住了更高的利率：发“踩准议息”。 */
    private static void awardRateCall(long day) {
        long from = day - Math.max(1, FinanceConfig.policyGuidanceLeadDays()) - 1;
        Set<UUID> players = new HashSet<>();
        for (TreasuryBond bond : TreasuryBondManager.bonds().values()) {
            if (bond.issueDay() >= from && bond.issueDay() < day)
                players.addAll(bond.principalByPlayer().keySet());
        }
        for (TimeDeposit deposit : BankingManager.timeDeposits().values()) {
            if (deposit.startDay() >= from && deposit.startDay() < day && deposit.ownerId() != null)
                players.add(deposit.ownerId());
        }
        for (UUID p : players) EconomyAchievements.award(p, EconomyAchievements.RATE_CALL);
    }

    // ---------------------------------------------------------------- 每日

    /** 每天日结调用一次：公开市场操作、商品平准、物价稳定/失控判定。 */
    public static synchronized void processDay(long day) {
        if (day < 0 || day <= lastProcessedDay) return;
        lastProcessedDay = day;
        absorbedToday = 0;
        injectedToday = 0;
        int gap = inflationBps() - inflationTargetBps();
        if (FinanceConfig.cbOmoEnabled()) openMarket(day, gap);
        PriceStabilizationService.processDay(day);
        trackStability(gap);
        EconomySavedData.markDirty();
    }

    private static void trackStability(int gapBps) {
        int band = Math.max(0, FinanceConfig.cbInflationBandBps());
        stableStreak = Math.abs(gapBps) <= band ? stableStreak + 1 : 0;
        if (stableStreak == STABLE_DAYS_FOR_ACHIEVEMENT)
            EconomyAchievements.awardOnline(EconomyAchievements.PRICE_STABILITY);
        boolean now = gapBps >= RUNAWAY_GAP_BPS;
        if (now && !runaway) EconomyAchievements.awardOnline(EconomyAchievements.RUNAWAY_INFLATION);
        runaway = now;
    }

    /** 超额准备金目标（占存款的基点）：通胀越高目标越低。 */
    static int excessTargetBps(int gapBps) {
        long target = Math.round(FinanceConfig.cbOmoExcessTargetBps() - FinanceConfig.cbOmoGapResponse() * gapBps);
        return (int) Math.max(0, Math.min(5_000, target));
    }

    static void openMarket(long day, int gapBps) {
        int targetBps = excessTargetBps(gapBps);
        int rate = Math.max(0, MonetaryPolicyService.benchmarkRateBasisPoints());
        POSITIONS.keySet().removeIf(id -> BankingManager.bank(id) == null);
        for (CommercialBank bank : new ArrayList<>(BankingManager.banks().values())) {
            if (bank.status() != BankStatus.ACTIVE && bank.status() != BankStatus.WATCH) continue;
            payInterest(bank, day, rate);
            var sheet = bank.ledger().balanceSheet();
            long deposits = sat(Math.max(0, sheet.demandDeposits()), Math.max(0, sheet.timeDeposits()));
            if (deposits <= 0) continue;
            long reserves = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
            long excess = reserves - BankRegulatoryService.requiredReserves(bank);
            long target = bps(deposits, targetBps);
            long cap = bps(deposits, FinanceConfig.cbOmoMaxDailyBps());
            if (cap <= 0) continue;
            if (excess > target) absorb(bank, day, Math.min(cap, excess - target));
            else if (excess < target) inject(bank, day, Math.min(cap, target - Math.max(0, excess)));
        }
    }

    /** 吸收：先收回回购投放，再卖出央行票据。只动超额准备金，不让银行低于法定准备金。 */
    private static void absorb(CommercialBank bank, long day, long amount) {
        long position = POSITIONS.getOrDefault(bank.id(), 0L);
        long done = 0;
        if (position < 0) {
            long repay = Math.min(amount, -position);
            if (post(
                    bank,
                    day,
                    "repo-repay",
                    BankLedgerAccount.LIABILITY_CENTRAL_BANK,
                    BankLedgerAccount.ASSET_RESERVE,
                    repay,
                    BankLedgerReason.OPEN_MARKET_REPO_REPAY)) {
                position += repay;
                done += repay;
            }
        }
        long rest = amount - done;
        if (rest > 0
                && (POSITIONS.containsKey(bank.id()) || POSITIONS.size() < MAX_POSITIONS)
                && post(
                        bank,
                        day,
                        "absorb",
                        BankLedgerAccount.ASSET_BOND,
                        BankLedgerAccount.ASSET_RESERVE,
                        rest,
                        BankLedgerReason.OPEN_MARKET_ABSORB)) {
            position += rest;
            done += rest;
        }
        setPosition(bank.id(), position);
        absorbedToday = sat(absorbedToday, done);
        absorbedTotal = sat(absorbedTotal, done);
    }

    /** 投放：先买回银行持有的央行票据，再做回购（借给银行准备金）。 */
    private static void inject(CommercialBank bank, long day, long amount) {
        long position = POSITIONS.getOrDefault(bank.id(), 0L);
        long done = 0;
        if (position > 0) {
            long redeem = Math.min(amount, position);
            if (post(
                    bank,
                    day,
                    "redeem",
                    BankLedgerAccount.ASSET_RESERVE,
                    BankLedgerAccount.ASSET_BOND,
                    redeem,
                    BankLedgerReason.OPEN_MARKET_REDEEM)) {
                position -= redeem;
                done += redeem;
            }
        }
        long rest = amount - done;
        if (rest > 0
                && bank.ledger().balanceSheet().equity() > 0
                && (POSITIONS.containsKey(bank.id()) || POSITIONS.size() < MAX_POSITIONS)
                && post(
                        bank,
                        day,
                        "repo",
                        BankLedgerAccount.ASSET_RESERVE,
                        BankLedgerAccount.LIABILITY_CENTRAL_BANK,
                        rest,
                        BankLedgerReason.OPEN_MARKET_REPO)) {
            position -= rest;
            done += rest;
        }
        setPosition(bank.id(), position);
        injectedToday = sat(injectedToday, done);
        injectedTotal = sat(injectedTotal, done);
    }

    /** 头寸按基准利率计息：持有票据的银行收息，欠回购款的银行付息（只记银行总账）。 */
    private static void payInterest(CommercialBank bank, long day, int rateBps) {
        long position = POSITIONS.getOrDefault(bank.id(), 0L);
        if (position == 0 || rateBps <= 0) return;
        long interest = BigInteger.valueOf(Math.abs(position))
                .multiply(BigInteger.valueOf(rateBps))
                .divide(BigInteger.valueOf((long) Math.max(1, FinanceConfig.annualMcDays()) * 10_000L))
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
        if (interest <= 0) return;
        if (position > 0)
            post(
                    bank,
                    day,
                    "interest",
                    BankLedgerAccount.ASSET_RESERVE,
                    BankLedgerAccount.INCOME_INTEREST,
                    interest,
                    BankLedgerReason.OPEN_MARKET_INTEREST);
        else
            post(
                    bank,
                    day,
                    "interest",
                    BankLedgerAccount.EXPENSE_INTEREST,
                    BankLedgerAccount.ASSET_RESERVE,
                    interest,
                    BankLedgerReason.OPEN_MARKET_INTEREST);
    }

    private static boolean post(
            CommercialBank bank,
            long day,
            String kind,
            BankLedgerAccount debit,
            BankLedgerAccount credit,
            long amount,
            BankLedgerReason reason) {
        if (amount <= 0) return false;
        UUID ref = UUID.nameUUIDFromBytes((bank.id() + ":omo:" + kind + ":" + day).getBytes(StandardCharsets.UTF_8));
        return bank.ledger().post(day, ref, debit, credit, amount, reason);
    }

    private static void setPosition(UUID bankId, long position) {
        if (position == 0) POSITIONS.remove(bankId);
        else POSITIONS.put(bankId, position);
    }

    // ---------------------------------------------------------------- 存档与隔离

    public static void restore(
            int reserveBps,
            Map<UUID, Long> positions,
            List<Decision> decisions,
            long lastDay,
            long absorbed,
            long injected,
            int streak,
            boolean runawayNow) {
        clearDirect();
        demandReserveBps = reserveBps < 0 ? -1 : Math.min(10_000, reserveBps);
        if (positions != null) {
            for (Map.Entry<UUID, Long> e : positions.entrySet()) {
                if (POSITIONS.size() >= MAX_POSITIONS) break;
                if (e.getKey() != null && e.getValue() != null && e.getValue() != 0)
                    POSITIONS.put(e.getKey(), e.getValue());
            }
        }
        if (decisions != null) {
            for (Decision d : decisions) if (d != null) DECISIONS.addLast(d);
            while (DECISIONS.size() > MAX_DECISIONS) DECISIONS.removeFirst();
        }
        lastProcessedDay = lastDay;
        absorbedTotal = Math.max(0, absorbed);
        injectedTotal = Math.max(0, injected);
        stableStreak = Math.max(0, streak);
        runaway = runawayNow;
    }

    /** 存档里保存的准备金率原值（-1 = 未调过，跟随配置）。 */
    public static int storedReserveBps() {
        return demandReserveBps;
    }

    public static void clearDirect() {
        demandReserveBps = -1;
        POSITIONS.clear();
        DECISIONS.clear();
        lastProcessedDay = Long.MIN_VALUE;
        absorbedToday = injectedToday = absorbedTotal = injectedTotal = 0;
        stableStreak = 0;
        runaway = false;
    }

    // ---------------------------------------------------------------- 工具

    static long bps(long base, int bps) {
        if (base <= 0 || bps <= 0) return 0;
        return BigInteger.valueOf(base)
                .multiply(BigInteger.valueOf(bps))
                .divide(BigInteger.valueOf(10_000))
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
    }

    static long sat(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}
