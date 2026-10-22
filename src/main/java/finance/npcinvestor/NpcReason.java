package finance.npcinvestor;

/** NPC 决策理由代码。展示文本由客户端按代码本地化，这里只保留中文兜底。 */
public enum NpcReason {
    NO_PRICE("价格异常，暂不交易"),
    BANKRUPTCY_RISK("公司有破产风险"),
    NO_DIVIDEND("近两个周期未分红"),
    DIVIDEND_YIELD("股息率"),
    INSUFFICIENT_HISTORY("经营历史不足"),
    PROFIT_DECLINE("利润转负"),
    PROFIT_FLAT("利润没有增长"),
    PROFIT_GROWTH("利润增长"),
    MOMENTUM_DOWN("股价下跌"),
    MOMENTUM_UP("股价上涨"),
    NO_FUNDAMENTAL("缺少基本面数据"),
    OVERVALUED("股价高于基本面"),
    UNDERVALUED("股价低于基本面"),
    COMBINED_CAP("NPC 合计持股已达上限"),
    NO_CASH("资金不足"),
    SHARES_LOCKED("可卖持股不足（已被要约或回购锁定）"),
    SELL_REJECTED("卖单被拒"),
    FUNDING("从 NPC 资金池注资");

    private final String fallbackText;

    NpcReason(String fallbackText) {
        this.fallbackText = fallbackText;
    }

    public String fallbackText() {
        return fallbackText;
    }
}
