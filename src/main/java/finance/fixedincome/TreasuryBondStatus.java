package finance.fixedincome;

/**
 * 央行债券状态。
 *
 * <p>EARLY_REDEEMED 曾为死枚举值（全库无赋值引用，提前赎回直接移除持仓），已删除。</p>
 */
public enum TreasuryBondStatus {
    ACTIVE,
    MATURED,
    /** 兑付回滚失败：债券已部分兑付，暂停自动重试，待人工介入处置。 */
    SETTLEMENT_FAILED
}
