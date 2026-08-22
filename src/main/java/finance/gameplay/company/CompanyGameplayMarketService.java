package finance.gameplay.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.commodity.CommodityInventoryManager;
import finance.market.CommodityTradeRecorder;
import finance.market.CommodityTradeSource;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.MathUtil;

import java.util.UUID;

public final class CompanyGameplayMarketService {
    private CompanyGameplayMarketService() {}

    public static int autoSell(Company company) {
        int sold = 0;
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        for (String commodityId : company.getType().getDailyProduction().keySet()) {
            int available = CommodityInventoryManager.getCommodityAmount(custody, commodityId);
            int quantity = Math.min(available, Math.max(0, (int) Math.floor(available * company.getAutoSellRatio())));
            if (quantity > 0 && sell(company, commodityId, quantity)) sold += quantity;
        }
        return sold;
    }

    public static synchronized boolean sell(Company company, String commodityId, int quantity) {
        if (company == null || quantity <= 0) return false;
        MarketPrice price = NpcMarketMaker.getMarketPrice(commodityId);
        if (price == null || price.getBidPrice() <= 0) return false;
        long payment = MathUtil.multiplyExactOrNegative1(price.getBidPrice(), quantity);
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        if (payment <= 0 || CommodityInventoryManager.getCommodityAmount(custody, commodityId) < quantity
                || !company.canDeposit(payment)) return false;
        finance.account.Account npc = AccountManager.getAccounts().get(NpcMarketMaker.NPC_UUID);
        if (npc == null || npc.getBalance() < payment
                || !CommodityInventoryManager.canAddCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity)) return false;
        if (!npc.withdraw(payment)) return false;
        if (!company.deposit(payment)) { npc.deposit(payment); return false; }
        if (!CommodityInventoryManager.removeCommodity(custody, commodityId, quantity)) {
            company.withdraw(payment); npc.deposit(payment); return false;
        }
        if (!CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity)) {
            CommodityInventoryManager.addCommodity(custody, commodityId, quantity);
            company.withdraw(payment); npc.deposit(payment); return false;
        }
        company.recordGameplayRevenue(payment);
        AccountManager.addTransactionRecord(new TransactionRecord(NpcMarketMaker.NPC_UUID, company.getCompanyId(),
                payment, TransactionType.COMPANY_MARKET_SALE, company.getOwnerId(), company.getName() + "/" + commodityId, quantity));
        CommodityTradeRecorder.recordCompletedTrade(NpcMarketMaker.NPC_UUID, company.getCompanyId(), commodityId,
                price.getBidPrice(), quantity, CommodityTradeSource.COMPANY_NPC, true);
        return true;
    }
}
