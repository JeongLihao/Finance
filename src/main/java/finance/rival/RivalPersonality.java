package finance.rival;

import java.util.UUID;

/** NPC 竞争公司的经营人格。 */
public enum RivalPersonality {
    /** 激进：盈利就扩产，现金够就升级，原料一紧就高价抢购。 */
    AGGRESSIVE("激进", 3, 13_000),
    /** 保守：亏损就控制成本，攒够大笔现金才升级，合同出价克制。 */
    CONSERVATIVE("保守", 6, 11_000),
    /** 投机：看自家产品价格，贵了快速变现，便宜了囤货。 */
    OPPORTUNIST("投机", 4, 12_000);

    private final String displayName;
    /** 现金达到"升级费 × 该倍数"才升级。 */
    private final int upgradeCashMultiple;
    /** 采购合同出价相对市场卖价的基点（11000 = 加价 10%）。 */
    private final int contractPremiumBps;

    RivalPersonality(String displayName, int upgradeCashMultiple, int contractPremiumBps) {
        this.displayName = displayName;
        this.upgradeCashMultiple = upgradeCashMultiple;
        this.contractPremiumBps = contractPremiumBps;
    }

    public String displayName() {
        return displayName;
    }

    public int upgradeCashMultiple() {
        return upgradeCashMultiple;
    }

    public int contractPremiumBps() {
        return contractPremiumBps;
    }

    /** 按公司 ID 确定性派生人格（同一存档结果可复现）。 */
    public static RivalPersonality forCompany(UUID companyId) {
        if (companyId == null) return CONSERVATIVE;
        RivalPersonality[] all = values();
        return all[
                (int) Math.floorMod(
                        companyId.getMostSignificantBits() ^ companyId.getLeastSignificantBits(), (long) all.length)];
    }

    public static RivalPersonality parse(String name) {
        try {
            return valueOf(name);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
