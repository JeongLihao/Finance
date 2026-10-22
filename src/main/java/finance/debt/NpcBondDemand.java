package finance.debt;

import finance.account.AccountManager;
import finance.config.FinanceConfig;
import finance.npcinvestor.NpcInvestorArchetype;
import finance.npcinvestor.NpcInvestorManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * NPC 分红派认购公司债：认购期内每天把剩余额度按剩余天数分摊着买，最后一天买满。
 * 单人游戏里没有别的玩家认购，没有这一步玩家公司根本发不出债。
 * 每天花的钱不超过分红派账户余额的四分之一，钱不够就少买，没认满的部分照常按认购规则处理。
 */
public final class NpcBondDemand {

    private NpcBondDemand() {}

    /** 在认购期结束判定之前调用。返回当天 NPC 认购的张数合计。 */
    static long processDay(long day) {
        if (!FinanceConfig.npcInvestorsEnabled()) return 0;
        NpcInvestorManager.ensureAccounts();
        var buyer = NpcInvestorArchetype.DIVIDEND.accountId();
        List<CorporateBond> open = new ArrayList<>();
        for (CorporateBond bond : CorporateBondManager.bonds().values())
            if (bond.status() == BondStatus.SUBSCRIPTION && day < bond.subscriptionEndDay()) open.add(bond);
        open.sort(Comparator.comparing(CorporateBond::id));
        long bought = 0;
        for (CorporateBond bond : open) {
            long remaining = bond.totalQuantity() - bond.subscribedQuantity();
            if (remaining <= 0 || bond.faceValue() <= 0) continue;
            long daysLeft = Math.max(1, bond.subscriptionEndDay() - day);
            long want = (remaining + daysLeft - 1) / daysLeft;
            long affordable = AccountManager.getBalance(buyer) / 4 / bond.faceValue();
            long quantity = Math.min(want, affordable);
            if (quantity > 0
                    && CorporateBondManager.subscribe(buyer, bond.id(), quantity)
                            .success()) bought += quantity;
        }
        return bought;
    }
}
