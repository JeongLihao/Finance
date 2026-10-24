package finance.diagnostic;

import static finance.diagnostic.EconomyConsistencyService.add;

import finance.account.AccountManager;
import finance.bank.*;
import finance.debt.*;
import finance.futures.*;
import finance.stock.StockPortfolioManager;
import java.util.List;

/** 竞争公司和 NPC 投资者的一致性检查。只读，从 {@link EconomyConsistencyService} 拆出。 */
final class NpcChecks {

    private NpcChecks() {}

    static void checkRivals(List<DiagnosticIssue> out) {
        // 竞争公司档案只能指向无主公司（破产公司的档案在日结时清理）
        for (finance.rival.RivalProfile p : finance.rival.RivalManager.all()) {
            finance.company.Company c = finance.company.CompanyManager.getCompany(p.companyId());
            if (c != null && c.isPlayerOwned()) {
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "RIVALS",
                        "PROFILE_ON_PLAYER_COMPANY",
                        p.companyId().toString(),
                        "竞争对手档案指向玩家公司");
            }
        }
    }

    static void checkNpcEconomy(List<DiagnosticIssue> out) {
        // NPC 投资者：账户不能为负，持股不能让单股合计超过有表决权股份（股本核对由 STOCK 模块负责）。
        for (finance.npcinvestor.NpcInvestorArchetype a : finance.npcinvestor.NpcInvestorArchetype.values()) {
            finance.account.Account account = AccountManager.getAccounts().get(a.accountId());
            if (account != null && (account.getBalance() < 0 || account.getFrozenBalance() < 0)) {
                add(out, DiagnosticSeverity.ERROR, "NPC_ECONOMY", "NEGATIVE_BALANCE", a.name(), "NPC 投资者余额为负数");
            }
            var portfolio = StockPortfolioManager.getPortfolios().get(a.accountId());
            if (portfolio == null) continue;
            for (var entry : portfolio.entrySet()) {
                finance.stock.Stock stock = finance.stock.StockMarketManager.getStock(entry.getKey());
                if (stock == null || entry.getValue().getQuantity() > stock.getVotingShares()) {
                    add(
                            out,
                            DiagnosticSeverity.ERROR,
                            "NPC_ECONOMY",
                            "HOLDING_INVALID",
                            a.name() + "/" + entry.getKey(),
                            "NPC 投资者持有不存在的股票，或持股超过有表决权股总数");
                }
            }
        }
    }
}
