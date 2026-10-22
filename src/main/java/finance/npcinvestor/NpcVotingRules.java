package finance.npcinvestor;

import finance.company.CompanyProposalType;

/**
 * NPC 投票规则表（纯函数）：提案类型 × 原型 → 赞成 / 反对 / 弃权 + 理由。
 *
 * <p>弃权就是不投票，会拉低参与率。这是有意设计：玩家持股太少时，提案可能因参与率不足而失败。</p>
 */
public final class NpcVotingRules {
    private NpcVotingRules() {}

    public enum Choice {
        SUPPORT,
        OPPOSE,
        ABSTAIN
    }

    /** 投票理由代码，展示文本由客户端本地化，这里保留中文兜底。 */
    public enum VoteReason {
        DIVIDEND_RAISED("分红比例上调"),
        DIVIDEND_CUT("分红比例下调"),
        RETAINED_EARNINGS_LOW("留存收益不足，不宜提高分红"),
        PREFER_RETENTION("偏好留存利润用于增长"),
        ISSUE_BELOW_FUNDAMENTAL("发行价低于每股基本面，摊薄老股东"),
        ISSUE_FOR_GROWTH("利润增长中，支持融资扩张"),
        BUYBACK_SUPPORT("回购提振股价"),
        RETIREMENT_ACCRETIVE("注销库存股提高每股价值"),
        GROWTH_INVESTMENT("支持扩张性投资"),
        BANKRUPTCY_RISK_OPPOSE("公司有破产风险，反对大额支出"),
        BANKRUPTCY_RISK_RESCUE("公司有破产风险，支持紧急融资"),
        NEUTRAL("与本原型关注点无关");

        private final String fallbackText;

        VoteReason(String fallbackText) {
            this.fallbackText = fallbackText;
        }

        public String fallbackText() {
            return fallbackText;
        }
    }

    public record Decision(Choice choice, VoteReason reason) {
        static Decision support(VoteReason r) {
            return new Decision(Choice.SUPPORT, r);
        }

        static Decision oppose(VoteReason r) {
            return new Decision(Choice.OPPOSE, r);
        }

        static Decision abstain() {
            return new Decision(Choice.ABSTAIN, VoteReason.NEUTRAL);
        }
    }

    /**
     * 提案和公司的只读画像。
     *
     * @param type                 提案类型
     * @param proposedDividendPercent 分红提案的新比例（%），其他类型忽略
     * @param currentDividendPercent  公司当前生效的分红比例（%）
     * @param issuePrice           增发提案的发行价，其他类型忽略
     * @param fundamentalPerShare  每股基本面
     * @param retainedEarnings     留存收益
     * @param profitTrendPositive  近期利润为正且在增长
     * @param bankruptcyRisk       是否处于破产风险
     */
    public record Context(
            CompanyProposalType type,
            long proposedDividendPercent,
            long currentDividendPercent,
            long issuePrice,
            long fundamentalPerShare,
            long retainedEarnings,
            boolean profitTrendPositive,
            boolean bankruptcyRisk) {}

    public static Decision decide(NpcInvestorArchetype archetype, Context c) {
        if (archetype == null || c == null || c.type() == null) return Decision.abstain();
        return switch (c.type()) {
            case DIVIDEND -> dividend(archetype, c);
            case SHARE_ISSUE -> shareIssue(archetype, c);
            case SHARE_BUYBACK -> archetype == NpcInvestorArchetype.SPECULATOR
                            || archetype == NpcInvestorArchetype.VALUE
                    ? Decision.support(VoteReason.BUYBACK_SUPPORT)
                    : Decision.abstain();
            case TREASURY_RETIREMENT -> archetype == NpcInvestorArchetype.VALUE
                            || archetype == NpcInvestorArchetype.DIVIDEND
                    ? Decision.support(VoteReason.RETIREMENT_ACCRETIVE)
                    : Decision.abstain();
            case CAPITAL_PROJECT, MAJOR_ASSET_PURCHASE -> {
                if (c.bankruptcyRisk()) yield Decision.oppose(VoteReason.BANKRUPTCY_RISK_OPPOSE);
                yield archetype == NpcInvestorArchetype.GROWTH
                        ? Decision.support(VoteReason.GROWTH_INVESTMENT)
                        : Decision.abstain();
            }
            case EMERGENCY_RECAPITALIZATION -> c.bankruptcyRisk()
                    ? Decision.support(VoteReason.BANKRUPTCY_RISK_RESCUE)
                    : Decision.abstain();
            case RENAME, FUND_USAGE, TENDER_OFFER_RESPONSE, CONTROL_TRANSFER -> Decision.abstain();
        };
    }

    private static Decision dividend(NpcInvestorArchetype archetype, Context c) {
        long proposed = c.proposedDividendPercent();
        long current = c.currentDividendPercent();
        if (proposed == current) return Decision.abstain();
        boolean raise = proposed > current;
        return switch (archetype) {
            case DIVIDEND -> raise
                    ? Decision.support(VoteReason.DIVIDEND_RAISED)
                    : Decision.oppose(VoteReason.DIVIDEND_CUT);
            case GROWTH -> {
                if (!raise) yield Decision.support(VoteReason.PREFER_RETENTION);
                yield c.retainedEarnings() <= 0 || !c.profitTrendPositive()
                        ? Decision.oppose(VoteReason.RETAINED_EARNINGS_LOW)
                        : Decision.abstain();
            }
            case SPECULATOR, VALUE -> Decision.abstain();
        };
    }

    private static Decision shareIssue(NpcInvestorArchetype archetype, Context c) {
        boolean belowFundamental = c.fundamentalPerShare() > 0 && c.issuePrice() < c.fundamentalPerShare();
        return switch (archetype) {
            case DIVIDEND, VALUE -> belowFundamental
                    ? Decision.oppose(VoteReason.ISSUE_BELOW_FUNDAMENTAL)
                    : Decision.abstain();
            case GROWTH -> c.profitTrendPositive() && !c.bankruptcyRisk()
                    ? Decision.support(VoteReason.ISSUE_FOR_GROWTH)
                    : Decision.abstain();
            case SPECULATOR -> Decision.abstain();
        };
    }
}
