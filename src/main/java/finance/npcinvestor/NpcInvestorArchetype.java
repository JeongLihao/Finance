package finance.npcinvestor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * NPC 投资者原型。每个原型对应一个固定的系统账户（确定性 UUID），
 * 持股、投票和接受要约都以该账户身份进行。
 */
public enum NpcInvestorArchetype {
    /** 分红派：看股息率和分红连续性，偏长期持有。 */
    DIVIDEND("分红派", 1.0D),
    /** 成长派：看近期利润趋势。 */
    GROWTH("成长派", 1.0D),
    /** 短线派：看价格动量，快进快出，持仓上限减半。 */
    SPECULATOR("短线派", 0.5D),
    /** 价值派：股价低于每股基本面时买入。 */
    VALUE("价值派", 1.0D);

    private final String displayName;
    private final double holdingCapFactor;
    private final UUID accountId;

    NpcInvestorArchetype(String displayName, double holdingCapFactor) {
        this.displayName = displayName;
        this.holdingCapFactor = holdingCapFactor;
        this.accountId = UUID.nameUUIDFromBytes(
                ("finance-npc-investor-" + name().toLowerCase(java.util.Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    public String displayName() { return displayName; }

    /** 相对 maxHoldingPercentEach 的持仓上限系数。 */
    public double holdingCapFactor() { return holdingCapFactor; }

    public UUID accountId() { return accountId; }

    /** 该 UUID 是否是某个 NPC 投资者的账户。 */
    public static boolean isInvestorAccount(UUID id) {
        return fromAccount(id) != null;
    }

    public static NpcInvestorArchetype fromAccount(UUID id) {
        if (id == null) return null;
        for (NpcInvestorArchetype a : values()) {
            if (a.accountId.equals(id)) return a;
        }
        return null;
    }
}
