package finance.centralbank;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.macro.NeedCategory;
import finance.market.CentralBank;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.MathUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品平准：消费品（人口需求的五类商品）的国际市场中间价偏离基准价超过区间时，
 * 央行用战略储备和国际市场做真实买卖——价格太高就把储备卖进市场，太低就从市场收购进储备。
 *
 * <p>每种商品每天最多动参考库存的一定比例（{@code stabilizationMaxDailyBps}），只把价格往区间里推，不一次打回基准。
 * 钱在央行账户和国际市场账户之间转移，商品在两边库存之间转移，都留流水。</p>
 */
public final class PriceStabilizationService {
    /** 一次平准动作（面板和周报用）。sold = true 表示央行卖出储备平抑高价。 */
    public record Action(String commodityId, boolean sold, int quantity, long price) {}

    private static final List<Action> LAST = new ArrayList<>();
    private static long lastDay = Long.MIN_VALUE;

    private PriceStabilizationService() {}

    public static List<Action> lastActions() {
        return List.copyOf(LAST);
    }

    public static long lastDay() {
        return lastDay;
    }

    static void processDay(long day) {
        LAST.clear();
        lastDay = day;
        if (!FinanceConfig.cbStabilizationEnabled()) return;
        int band = FinanceConfig.cbStabilizationBandBps();
        for (MarketPrice mp :
                new ArrayList<>(NpcMarketMaker.getAllMarketPrices().values())) {
            String id = mp.getCommodityId();
            int dailyCap = (int) Math.min(
                    Integer.MAX_VALUE,
                    CentralBankService.bps(mp.getReferenceStock(), FinanceConfig.cbStabilizationMaxDailyBps()));
            if (dailyCap <= 0) continue;
            if (!isConsumerGood(id) || CommodityRegistry.getCommodity(id) == null || mp.getBasePrice() <= 0) continue;
            long deviationBps = (mp.getMidPrice() - mp.getBasePrice()) * 10_000L / mp.getBasePrice();
            if (deviationBps > band) sell(mp, id, dailyCap);
            else if (deviationBps < -band) buy(mp, id, dailyCap);
        }
    }

    /** 人口需求里用得到的商品才算消费品。 */
    public static boolean isConsumerGood(String commodityId) {
        for (NeedCategory c : NeedCategory.values()) if (c.unitsOf(commodityId) > 0) return true;
        return false;
    }

    /** 价格太高：把央行储备卖给国际市场，市场库存增加、价格回落。国际市场按买价付钱，付不起的部分央行不收。 */
    private static void sell(MarketPrice mp, String id, int cap) {
        int reserve = CommodityInventoryManager.getCommodityAmount(CentralBank.UUID, id);
        int qty = Math.min(cap, reserve);
        if (qty <= 0 || !transfer(CentralBank.UUID, NpcMarketMaker.NPC_UUID, id, qty)) return;
        long marketStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, id);
        long price = Math.max(1, mp.getBidPrice());
        long value = MathUtil.multiplyExactOrNegative1(price, qty);
        long paid = Math.min(Math.max(0, AccountManager.getBalance(NpcMarketMaker.NPC_UUID)), Math.max(0, value));
        if (paid > 0 && AccountManager.moveFunds(NpcMarketMaker.NPC_UUID, CentralBank.UUID, paid)) {
            AccountManager.addTransactionRecord(new TransactionRecord(
                    NpcMarketMaker.NPC_UUID,
                    CentralBank.UUID,
                    paid,
                    TransactionType.CENTRAL_BANK_STABILIZATION,
                    CentralBank.UUID,
                    "平准抛售/" + id,
                    qty));
        }
        mp.recomputePrice(marketStock);
        LAST.add(new Action(id, true, qty, price));
    }

    /** 价格太低：央行从国际市场收购进储备，市场库存减少、价格回升。市场库存不足参考量一半时不收。 */
    private static void buy(MarketPrice mp, String id, int cap) {
        long marketStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, id);
        long spare = marketStock - mp.getReferenceStock() / 2;
        int qty = (int) Math.min(cap, Math.max(0, spare));
        if (qty <= 0) return;
        long price = Math.max(1, mp.getAskPrice());
        long cost = MathUtil.multiplyExactOrNegative1(price, qty);
        if (cost <= 0) return;
        CentralBank.ensureReserveCash(cost);
        if (!transfer(NpcMarketMaker.NPC_UUID, CentralBank.UUID, id, qty)) return;
        if (!AccountManager.moveFunds(CentralBank.UUID, NpcMarketMaker.NPC_UUID, cost)) {
            transfer(CentralBank.UUID, NpcMarketMaker.NPC_UUID, id, qty);
            return;
        }
        AccountManager.addTransactionRecord(new TransactionRecord(
                CentralBank.UUID,
                NpcMarketMaker.NPC_UUID,
                cost,
                TransactionType.CENTRAL_BANK_STABILIZATION,
                CentralBank.UUID,
                "平准收储/" + id,
                qty));
        mp.recomputePrice(marketStock - qty);
        LAST.add(new Action(id, false, qty, price));
    }

    private static boolean transfer(java.util.UUID from, java.util.UUID to, String id, int qty) {
        if (qty <= 0
                || CommodityInventoryManager.getCommodityAmount(from, id) < qty
                || !CommodityInventoryManager.canAddCommodity(to, id, qty)) return false;
        if (!CommodityInventoryManager.removeCommodity(from, id, qty)) return false;
        if (CommodityInventoryManager.addCommodity(to, id, qty)) return true;
        CommodityInventoryManager.addCommodity(from, id, qty);
        return false;
    }

    public static void clearDirect() {
        LAST.clear();
        lastDay = Long.MIN_VALUE;
    }
}
