package finance.gameplay;

import net.minecraft.core.BlockPos;

/** 访问校验结果。模式由服务端返回，客户端声明不具有授权意义。 */
public record FinanceAccessDecision(
        boolean allowed,
        FinanceScreenMode screenMode,
        String messageKey,
        BlockPos sourcePos
) {
    public static FinanceAccessDecision allow(FinanceScreenMode mode, BlockPos sourcePos) {
        return new FinanceAccessDecision(true, mode, "finance.access.allowed", sourcePos);
    }

    public static FinanceAccessDecision deny(String messageKey) {
        return new FinanceAccessDecision(false, null, messageKey, null);
    }
}
