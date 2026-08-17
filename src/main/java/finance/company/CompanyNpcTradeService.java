package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.market.CommodityTradeRecorder;
import finance.market.CommodityTradeSource;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.MathUtil;

public final class CompanyNpcTradeService {

    private CompanyNpcTradeService() {
    }

    public record CommoditySettlementResult(boolean success, long payment, int quantity, String reason) {
        static CommoditySettlementResult fail(String reason) {
            return new CommoditySettlementResult(false, 0, 0, reason);
        }
    }

    public static CommoditySettlementResult buyForCompany(Company company, String commodityId, int quantity) {
        MarketPrice price = NpcMarketMaker.getMarketPrice(commodityId);
        return price == null ? CommoditySettlementResult.fail("商品没有市场报价")
                : buyForCompany(company, commodityId, quantity, price.getAskPrice());
    }

    static CommoditySettlementResult buyForCompany(Company company, String commodityId,
                                                    int quantity, long unitPrice) {
        long payment = validate(company, commodityId, quantity, unitPrice);
        if (payment <= 0) return CommoditySettlementResult.fail("交易参数或金额无效");
        if (company.getCash() < payment) return CommoditySettlementResult.fail("公司现金不足");
        if (!company.canAddInventory(commodityId, quantity)) return CommoditySettlementResult.fail("公司库存已满");
        if (CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodityId) < quantity) {
            return CommoditySettlementResult.fail("国际市场库存不足");
        }
        if (!AccountManager.canDeposit(NpcMarketMaker.NPC_UUID, payment)) {
            return CommoditySettlementResult.fail("国际市场账户已达到容量上限");
        }

        if (!company.withdraw(payment)) return CommoditySettlementResult.fail("公司扣款失败");
        if (!AccountManager.deposit(NpcMarketMaker.NPC_UUID, payment)) {
            company.deposit(payment);
            return CommoditySettlementResult.fail("国际市场入账失败");
        }
        if (!CommodityInventoryManager.removeCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity)) {
            AccountManager.withdraw(NpcMarketMaker.NPC_UUID, payment);
            company.deposit(payment);
            return CommoditySettlementResult.fail("国际市场库存扣除失败");
        }
        if (!company.addInventory(commodityId, quantity)) {
            CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity);
            AccountManager.withdraw(NpcMarketMaker.NPC_UUID, payment);
            company.deposit(payment);
            return CommoditySettlementResult.fail("公司库存入账失败");
        }

        company.recordPurchaseCost(payment);
        AccountManager.addTransactionRecord(new TransactionRecord(
                company.getCompanyId(), NpcMarketMaker.NPC_UUID, payment, TransactionType.NPC_SELL,
                company.getOwnerId(), company.getName() + "/" + commodityId, quantity));
        CommodityTradeRecorder.recordCompletedTrade(company.getCompanyId(), NpcMarketMaker.NPC_UUID,
                commodityId, unitPrice, quantity, CommodityTradeSource.COMPANY_NPC, false);
        return new CommoditySettlementResult(true, payment, quantity, "公司采购成功");
    }

    public static CommoditySettlementResult sellForCompany(Company company, String commodityId, int quantity) {
        MarketPrice price = NpcMarketMaker.getMarketPrice(commodityId);
        return price == null ? CommoditySettlementResult.fail("商品没有市场报价")
                : sellForCompany(company, commodityId, quantity, price.getBidPrice());
    }

    static CommoditySettlementResult sellForCompany(Company company, String commodityId,
                                                     int quantity, long unitPrice) {
        long payment = validate(company, commodityId, quantity, unitPrice);
        if (payment <= 0) return CommoditySettlementResult.fail("交易参数或金额无效");
        if (company.getInventoryAmount(commodityId) < quantity) return CommoditySettlementResult.fail("公司库存不足");
        if (!company.canDeposit(payment)) return CommoditySettlementResult.fail("公司现金已达到容量上限");
        if (AccountManager.getBalance(NpcMarketMaker.NPC_UUID) < payment) {
            return CommoditySettlementResult.fail("国际市场现金不足");
        }
        if (!CommodityInventoryManager.canAddCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity)) {
            return CommoditySettlementResult.fail("国际市场库存已满");
        }

        if (!AccountManager.withdraw(NpcMarketMaker.NPC_UUID, payment)) {
            return CommoditySettlementResult.fail("国际市场扣款失败");
        }
        if (!company.deposit(payment)) {
            AccountManager.deposit(NpcMarketMaker.NPC_UUID, payment);
            return CommoditySettlementResult.fail("公司入账失败");
        }
        if (!company.removeInventory(commodityId, quantity)) {
            company.withdraw(payment);
            AccountManager.deposit(NpcMarketMaker.NPC_UUID, payment);
            return CommoditySettlementResult.fail("公司库存扣除失败");
        }
        if (!CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity)) {
            company.addInventory(commodityId, quantity);
            company.withdraw(payment);
            AccountManager.deposit(NpcMarketMaker.NPC_UUID, payment);
            return CommoditySettlementResult.fail("国际市场库存入账失败");
        }

        company.recordSalesRevenue(payment);
        AccountManager.addTransactionRecord(new TransactionRecord(
                NpcMarketMaker.NPC_UUID, company.getCompanyId(), payment, TransactionType.NPC_BUY,
                company.getOwnerId(), company.getName() + "/" + commodityId, quantity));
        CommodityTradeRecorder.recordCompletedTrade(NpcMarketMaker.NPC_UUID, company.getCompanyId(),
                commodityId, unitPrice, quantity, CommodityTradeSource.COMPANY_NPC, true);
        return new CommoditySettlementResult(true, payment, quantity, "公司出售成功");
    }

    private static long validate(Company company, String commodityId, int quantity, long unitPrice) {
        if (company == null || commodityId == null || commodityId.isBlank() || quantity <= 0 || unitPrice <= 0) {
            return -1;
        }
        return MathUtil.multiplyExactOrNegative1(unitPrice, quantity);
    }
}
