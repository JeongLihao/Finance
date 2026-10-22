package finance.bank;

import finance.config.FinanceConfig;
import java.util.Random;

/**
 * 恐慌度纯计算：输入全部来自真实账本与监管指标。
 * 谣言只能放大真实风险——触发前提是准备金/LCR/CAR 任一真实跌破，
 * 文案引用当日真实数值，不存在玩家可调用的制造入口。
 */
public final class BankPanicService {
    private BankPanicService() {}

    public record PanicInputs(
            double reserveRatio, int carBps, long dailyWithdrawn, long totalDeposits, boolean contagion) {}

    public static PanicInputs inputsOf(CommercialBank bank, long dailyWithdrawn, boolean contagion) {
        BankBalanceSheet s = bank.ledger().balanceSheet();
        long required = BankRegulatoryService.requiredReserves(bank);
        // 有效准备金率：扣除央行紧急借款后的自有准备金覆盖率。
        // 日终既有修复逻辑会借钱补足名义准备金，但依赖最后贷款人本身就是压力信号（stigma）。
        long effectiveReserves = s.reserves() - Math.max(0, s.centralBankBorrowing());
        double reserveRatio = required <= 0 ? 1.0 : (double) effectiveReserves / (double) required;
        long totalDeposits = safeAdd(s.demandDeposits(), s.timeDeposits());
        return new PanicInputs(
                reserveRatio,
                BankRegulatoryService.capitalAdequacyBps(bank),
                Math.max(0, dailyWithdrawn),
                Math.max(0, totalDeposits),
                contagion);
    }

    /** 计算当日恐慌增量（不含谣言）；返回 >= 0 的 stress，调用方负责衰减与钳制。 */
    public static int stressOf(PanicInputs in) {
        int stress = 0;
        if (in.reserveRatio() < 1.0) stress += (int) Math.min(40, (1.0 - in.reserveRatio()) * 50);
        if (in.carBps() < FinanceConfig.bankMinimumCapitalBps()) stress += 15;
        if (in.totalDeposits() > 0 && in.dailyWithdrawn() > 0) {
            double share = (double) in.dailyWithdrawn() / (double) in.totalDeposits();
            if (share > 0.03) stress += (int) Math.min(30, share * 300);
        }
        if (in.contagion()) stress += 8;
        return stress;
    }

    /**
     * 谣言判定。仅当真实风险存在时才可能触发；文案引用真实数值。
     * @return 谣言文本（含真实数据），或 null 表示今日无谣言
     */
    public static BankRumor maybeRumor(CommercialBank bank, PanicInputs in, BankRunState state, long day) {
        double shortfall = 0;
        String text = null;
        if (in.reserveRatio() < 1.0) {
            shortfall = Math.max(shortfall, 1.0 - in.reserveRatio());
            int pct = (int) Math.round(in.reserveRatio() * 100);
            text = "市井传闻：" + bank.name() + "有效准备金率仅 " + pct + "%，靠央行续命";
        } else if (in.carBps() < FinanceConfig.bankMinimumCapitalBps()) {
            shortfall = Math.max(shortfall, 0.5);
            int pct = in.carBps() / 100;
            text = "市井传闻：" + bank.name() + "资本充足率跌至 " + pct + "%，坏账可能吞掉本金";
        }
        if (text == null) return null; // 无真实风险：绝不造谣
        if (day <= state.rumorCooldownDay()) return null;
        // 确定性随机：同一银行同一天结果可重放，玩家无法操纵
        long seed = bank.id().getMostSignificantBits() * 31L
                + bank.id().getLeastSignificantBits()
                + day * 0x9E3779B97F4A7C15L;
        if (new Random(seed).nextDouble() >= FinanceConfig.bankRunRumorChance()) return null;
        int bump = Math.min(15, 4 + (int) (shortfall * 8));
        return new BankRumor(text, bump, day + FinanceConfig.bankRunRumorCooldownDays(), day);
    }

    /** 日终恐慌更新：stress - decay，钳制 0~100。 */
    public static void updatePanic(BankRunState state, int stress, BankRumor rumor, long day) {
        int next = state.panic() + stress;
        if (stress == 0) next -= FinanceConfig.bankRunPanicDecay();
        if (rumor != null) {
            next += rumor.panicBump();
            state.setRumorCooldownDay(rumor.cooldownUntilDay());
            state.setLastRumor(rumor.day(), rumor.text());
        }
        state.setPanic(next, day);
    }

    public record BankRumor(String text, int panicBump, long cooldownUntilDay, long day) {
        public BankRumor(String text, int panicBump, long cooldownUntilDay) {
            this(text, panicBump, cooldownUntilDay, -1);
        }
    }

    private static long safeAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
