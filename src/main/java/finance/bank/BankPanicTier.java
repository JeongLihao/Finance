package finance.bank;

/** 恐慌档位：CALM 平静 / UNEASY 不安 / RUN 挤兑 / PANIC 恐慌。 */
public enum BankPanicTier {
    CALM,
    UNEASY,
    RUN,
    PANIC;

    public static BankPanicTier of(int panic, int capPanic, int failPanic) {
        if (panic >= failPanic) return PANIC;
        if (panic >= capPanic) return RUN;
        if (panic >= BankRunService.PANIC_NPC_OUTFLOW_THRESHOLD) return UNEASY;
        return CALM;
    }
}
