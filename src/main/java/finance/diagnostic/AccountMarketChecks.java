package finance.diagnostic;

import static finance.diagnostic.EconomyConsistencyService.add;

import finance.account.Account;
import finance.account.AccountManager;
import finance.bank.*;
import finance.bondmarket.BondMarketManager;
import finance.chart.Candlestick;
import finance.chart.CandlestickService;
import finance.commodity.CommodityInventoryManager;
import finance.debt.*;
import finance.futures.*;
import finance.market.MarketManager;
import finance.market.Order;
import finance.stock.StockOrder;
import finance.stock.StockOrderManager;
import finance.stock.StockPortfolioManager;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 账户、商品库存、挂单、K 线历史和金融周期的一致性检查。只读，从 {@link EconomyConsistencyService} 拆出。 */
final class AccountMarketChecks {

    private AccountMarketChecks() {}

    static void checkAccounts(List<DiagnosticIssue> out) {
        for (Map.Entry<UUID, Account> entry : AccountManager.getAccounts().entrySet()) {
            Account account = entry.getValue();
            if (entry.getKey() == null
                    || account == null
                    || account.getPlayerId() == null
                    || !entry.getKey().equals(account.getPlayerId()))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "ACCOUNT",
                        "IDENTITY_MISMATCH",
                        String.valueOf(entry.getKey()),
                        "账户表的键和账户所有者不一致");
            else if (account.getBalance() < 0 || account.getFrozenBalance() < 0)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "ACCOUNT",
                        "NEGATIVE_BALANCE",
                        entry.getKey().toString(),
                        "可用余额或冻结余额为负数");
            else if (BigInteger.valueOf(account.getBalance())
                            .add(BigInteger.valueOf(account.getFrozenBalance()))
                            .compareTo(BigInteger.valueOf(Long.MAX_VALUE))
                    > 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "ACCOUNT",
                        "TOTAL_OVERFLOW",
                        entry.getKey().toString(),
                        "可用余额加冻结余额超出 long 范围");
        }
    }

    static void checkInventories(List<DiagnosticIssue> out) {
        CommodityInventoryManager.getInventories().forEach((owner, inventory) -> {
            if (owner == null || inventory == null)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "MARKET",
                        "BAD_INVENTORY_OWNER",
                        String.valueOf(owner),
                        "库存的所有者或内容为空");
            else
                inventory.getAllCommodities().forEach((id, amount) -> {
                    if (id == null || id.isBlank() || amount == null || amount < 0)
                        add(
                                out,
                                DiagnosticSeverity.ERROR,
                                "MARKET",
                                "INVALID_COMMODITY_HOLDING",
                                owner.toString(),
                                "商品 ID 为空或数量为负数");
                });
        });
        StockPortfolioManager.getPortfolios()
                .forEach((owner, portfolio) -> portfolio.forEach((symbol, holding) -> {
                    if (owner == null
                            || symbol == null
                            || symbol.isBlank()
                            || holding == null
                            || holding.getQuantity() < 0
                            || holding.getAverageCost() < 0)
                        add(
                                out,
                                DiagnosticSeverity.ERROR,
                                "STOCK",
                                "INVALID_STOCK_HOLDING",
                                String.valueOf(owner),
                                "股票持仓的身份、数量或成本无效");
                }));
    }

    static void checkOrders(List<DiagnosticIssue> out) {
        Set<UUID> ids = new HashSet<>();
        for (Order order : MarketManager.getOrders()) {
            if (order == null
                    || order.getOrderId() == null
                    || !ids.add(order.getOrderId())
                    || order.getPlayerId() == null
                    || order.getCommodityId() == null
                    || order.getCommodityId().isBlank()
                    || order.getType() == null
                    || order.getPrice() <= 0
                    || order.getQuantity() <= 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "MARKET",
                        "INVALID_ORDER",
                        order == null ? "null" : String.valueOf(order.getOrderId()),
                        "商品订单重复，或剩余数量状态无效");
        }
        ids.clear();
        for (StockOrder order : StockOrderManager.getOrders()) {
            if (order == null
                    || order.getOrderId() == null
                    || !ids.add(order.getOrderId())
                    || order.getPlayerId() == null
                    || order.getSymbol() == null
                    || order.getSymbol().isBlank()
                    || order.getType() == null
                    || order.getPrice() <= 0
                    || order.getQuantity() <= 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "STOCK",
                        "INVALID_ORDER",
                        order == null ? "null" : String.valueOf(order.getOrderId()),
                        "股票订单重复，或剩余数量状态无效");
        }
        ids.clear();
        BondMarketManager.orders().forEach(order -> {
            if (order.orderId() == null
                    || !ids.add(order.orderId())
                    || order.playerId() == null
                    || order.bondId() == null
                    || order.side() == null
                    || order.limitPricePerUnit() <= 0
                    || order.remainingQuantity() <= 0
                    || order.createdSequence() <= 0)
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "DEBT",
                        "INVALID_BOND_ORDER",
                        String.valueOf(order.orderId()),
                        "债券订单重复或无效");
        });
    }

    static void checkHistory(List<DiagnosticIssue> out) {
        CandlestickService.getSeriesDirect().forEach((key, series) -> {
            long previous = -1;
            for (Candlestick bar : series.getBars(Integer.MAX_VALUE)) {
                if (bar.mcDay() <= previous
                        || bar.high() < Math.max(bar.open(), bar.close())
                        || bar.low() > Math.min(bar.open(), bar.close())
                        || bar.high() < bar.low()
                        || bar.volume() < 0)
                    add(out, DiagnosticSeverity.WARN, "HISTORY", "INVALID_CANDLE", key.toString(), "K 线日期或开高低收量关系无效");
                previous = bar.mcDay();
            }
        });
    }

    static void checkCycle(List<DiagnosticIssue> out, long mcDay) {
        long processed = finance.cycle.FinancialCycleService.lastProcessedDay();
        long closed = finance.cycle.FinancialCycleService.lastClosedMarketDay();
        long observed = finance.cycle.FinancialCycleService.observedMarketDay();
        if (closed > observed && observed >= 0)
            add(
                    out,
                    DiagnosticSeverity.ERROR,
                    "CYCLE",
                    "CLOSED_AFTER_OBSERVED",
                    "financial-cycle",
                    "已收盘的市场日期晚于观测到的市场日期");
        if (mcDay >= 0 && (processed > mcDay || observed > mcDay))
            add(out, DiagnosticSeverity.WARN, "CYCLE", "CLOCK_ROLLBACK", "financial-cycle", "记录的周期日期超前于当前世界日期");
    }
}
