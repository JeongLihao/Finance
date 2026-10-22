package finance.npcinvestor;

import finance.account.AccountManager;
import finance.config.FinanceConfig;
import finance.margin.MarginPoolManager;
import finance.margin.MarginService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NPC 短线派每分钟追涨杀跌：一只股票 10 分钟内涨超 5% 就加杠杆（2 倍）买入 1% 流通股，
 * 跌超 5% 就把融资仓全部卖掉。它们本身就是泡沫的燃料，也是踩踏的推手——
 * 单人游戏里玩家一个人吹起的泡沫，要靠它们放大；抛售时它们和被强平的人一起砸盘。
 * 合计最多持有流通股的 20%。
 */
public final class SpeculatorService {

    static final int LOOKBACK_MINUTES = 10;
    static final double TRIGGER = 0.05;
    static final double ORDER_FLOAT_RATIO = 0.01;
    static final double MAX_FLOAT_RATIO = 0.20;

    private static final Map<String, ArrayDeque<Long>> HISTORY = new HashMap<>();

    private SpeculatorService() {}

    public static void tickMinute() {
        if (!FinanceConfig.npcInvestorsEnabled()) return;
        UUID id = NpcInvestorArchetype.SPECULATOR.accountId();
        for (Stock stock : StockMarketManager.getListedStocks()) {
            String symbol = stock.getSymbol();
            ArrayDeque<Long> history = HISTORY.computeIfAbsent(symbol, k -> new ArrayDeque<>());
            long now = stock.getAnchorPrice();
            history.addLast(now);
            while (history.size() > LOOKBACK_MINUTES + 1) history.removeFirst();
            if (history.size() <= LOOKBACK_MINUTES || stock.isTradingPaused() || stock.getFloatShares() <= 0) continue;
            double change = now / (double) Math.max(1, history.peekFirst()) - 1.0;
            long financed = MarginService.financedSharesOf(id, symbol);
            if (change > TRIGGER) {
                long cap = Math.round(stock.getFloatShares() * MAX_FLOAT_RATIO) - financed;
                long qty = Math.min(cap, Math.max(1, Math.round(stock.getFloatShares() * ORDER_FLOAT_RATIO)));
                long cash = AccountManager.getBalance(id)
                        + MarginPoolManager.getAccount(id).getCashCollateral();
                qty = Math.min(qty, cash * 2 / Math.max(1, now));
                if (qty <= 0) continue;
                long need = (qty * now + 1) / 2 + qty; // 首付一半，多留一点应对报价价差
                long topUp = need - MarginPoolManager.getAccount(id).getCashCollateral();
                if (topUp > 0
                        && !MarginService.depositCollateral(id, Math.min(topUp, AccountManager.getBalance(id)))
                                .success()) continue;
                MarginService.marginBuy(id, symbol, qty);
            } else if (change < -TRIGGER && financed > 0) {
                MarginService.closeFinanced(id, symbol);
            }
        }
    }

    public static void clearDirect() {
        HISTORY.clear();
    }
}
