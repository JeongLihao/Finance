package finance.market;

import finance.account.AccountManager;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.util.MathUtil;

import java.util.UUID;

/**
 * 中央银行 —— 最后贷款人与最后库存缓冲池。
 *
 * <p>中央银行不直接替玩家成交，也不替代国际市场做市。
 * 正常波动由 {@link NpcMarketMaker} 自己吸收；只有市场现金、库存或价格
 * 进入失控区间时，中央银行才用准备金/储备库存进行调控。</p>
 */
public final class CentralBank {

    /** 中央银行账户 UUID，区别于国际市场 nil UUID。 */
    public static final UUID UUID = new UUID(0L, 1L);

    /** 基础准备金。央行承担战略兜底，而普通国际市场只承担日常交易。 */
    private static final long BASE_RESERVE_CASH = 50_000_000L;

    /** 每种商品战略储备目标。 */
    private static final int STRATEGIC_RESERVE_STOCK = 240_000;

    /** 国际市场现金下限，低于此值说明市场端货币不足。 */
    private static final double MARKET_CASH_FLOOR_RATIO = 0.25;

    /** 国际市场现金上限，高于此值说明市场端货币过剩。 */
    private static final double MARKET_CASH_CEILING_RATIO = 2.50;

    /** 库存进入危机区间才干预，软波动留给市场自己消化。 */
    private static final double STOCK_CRISIS_LOW = 0.35;
    private static final double STOCK_CRISIS_HIGH = 2.50;

    /** 单日最多修复偏离量的一部分，避免央行把价格一键打回基准。 */
    private static final double MAX_DAILY_STOCK_INTERVENTION = 0.35;

    private static int lastStockInterventions = 0;
    private static long lastCashDelta = 0;

    private CentralBank() {}

    public static void seedIfNeeded() {
        long current = AccountManager.getBalance(UUID);
        if (current < BASE_RESERVE_CASH) {
            AccountManager.deposit(UUID, BASE_RESERVE_CASH - current);
        }
        for (var commodity : CommodityRegistry.getAllCommodities()) {
            String commodityId = commodity.getId();
            int currentStock = CommodityInventoryManager.getCommodityAmount(UUID, commodityId);
            if (currentStock < STRATEGIC_RESERVE_STOCK) {
                CommodityInventoryManager.setCommodity(UUID, commodityId, STRATEGIC_RESERVE_STOCK);
            }
        }
    }

    /**
     * 每日政策操作：先调库存，再调市场端现金。
     *
     * <p>库存调控会实际在“国际市场库存池”和“央行储备库存池”之间转移商品；
     * 现金调控会在“国际市场账户”和“央行账户”之间转移货币。央行账户资金不足时，
     * 测试版会补足基础准备金，代表央行扩表。</p>
     */
    public static void dailyIntervention() {
        seedIfNeeded();
        lastStockInterventions = 0;
        lastCashDelta = 0;
        for (MarketPrice mp : NpcMarketMaker.getAllMarketPrices().values()) {
            interveneStock(mp);
        }
        interveneMarketCash();
        EconomySavedData.markDirty();
    }

    private static void interveneStock(MarketPrice mp) {
        String commodityId = mp.getCommodityId();
        long target = MarketPrice.REFERENCE_STOCK;
        long marketStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodityId);

        if (marketStock > Math.round(target * STOCK_CRISIS_HIGH)) {
            long surplus = marketStock - target;
            int qty = safeInt(Math.round(surplus * MAX_DAILY_STOCK_INTERVENTION));
            if (qty <= 0) return;

            long price = Math.max(1, mp.getBidPrice());
            long payment = MathUtil.multiplyExactOrNegative1(price, qty);
            ensureReserveCash(payment);
            if (payment > 0 && AccountManager.withdraw(UUID, payment)) {
                CommodityInventoryManager.removeCommodity(NpcMarketMaker.NPC_UUID, commodityId, qty);
                CommodityInventoryManager.addCommodity(UUID, commodityId, qty);
                AccountManager.deposit(NpcMarketMaker.NPC_UUID, payment);
                mp.recomputePrice(marketStock - qty);
                lastStockInterventions++;
                lastCashDelta -= payment;
            }
            return;
        }

        if (marketStock < Math.round(target * STOCK_CRISIS_LOW)) {
            long shortage = target - marketStock;
            int wanted = safeInt(Math.round(shortage * MAX_DAILY_STOCK_INTERVENTION));
            if (wanted <= 0) return;

            int reserve = CommodityInventoryManager.getCommodityAmount(UUID, commodityId);
            int qty = Math.min(wanted, reserve);
            if (qty <= 0) {
                // 极端短缺时建立少量战略储备投放，防止市场完全断货。
                qty = Math.max(1, wanted / 2);
            } else {
                CommodityInventoryManager.removeCommodity(UUID, commodityId, qty);
            }

            CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, qty);
            long price = Math.max(1, mp.getAskPrice());
            long receipt = MathUtil.multiplyExactOrNegative1(price, qty);
            if (receipt > 0) {
                long marketCash = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
                long paid = Math.min(marketCash, receipt);
                if (paid > 0 && AccountManager.withdraw(NpcMarketMaker.NPC_UUID, paid)) {
                    AccountManager.deposit(UUID, paid);
                    lastCashDelta += paid;
                }
            }
            mp.recomputePrice(marketStock + qty);
            lastStockInterventions++;
        }
    }

    private static void interveneMarketCash() {
        long targetCash = NpcMarketMaker.getInitialNpcBalance();
        long marketCash = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        long floor = Math.round(targetCash * MARKET_CASH_FLOOR_RATIO);
        long ceiling = Math.round(targetCash * MARKET_CASH_CEILING_RATIO);

        if (marketCash < floor) {
            long injection = Math.max(1, (targetCash - marketCash) / 3);
            ensureReserveCash(injection);
            if (AccountManager.withdraw(UUID, injection)) {
                AccountManager.deposit(NpcMarketMaker.NPC_UUID, injection);
                lastCashDelta -= injection;
            }
        } else if (marketCash > ceiling) {
            long withdrawal = Math.max(1, (marketCash - targetCash) / 3);
            if (AccountManager.withdraw(NpcMarketMaker.NPC_UUID, withdrawal)) {
                AccountManager.deposit(UUID, withdrawal);
                lastCashDelta += withdrawal;
            }
        }
    }

    public static String getLastInterventionSummary() {
        return "库存干预 " + lastStockInterventions + " 项，央行现金净变化 " + lastCashDelta;
    }

    private static void ensureReserveCash(long amount) {
        if (amount <= 0) return;
        long current = AccountManager.getBalance(UUID);
        if (current < amount) {
            AccountManager.deposit(UUID, Math.max(BASE_RESERVE_CASH, amount - current));
        }
    }

    private static int safeInt(long value) {
        if (value <= 0) return 0;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
