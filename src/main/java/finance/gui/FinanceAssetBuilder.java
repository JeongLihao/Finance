package finance.gui;

import finance.account.Account;
import finance.account.AssetSnapshotManager;
import finance.commodity.Commodity;
import finance.cycle.EconomyCycleService;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** 玩家资产汇总和明细（现金、商品、股票、押注等）的计算。从 {@link FinanceGuiOpener} 拆出。 */
final class FinanceAssetBuilder {

    private FinanceAssetBuilder() {}

    static AssetBundle buildAssetBundle(ServerPlayer player, Account account, Map<String, Integer> inventory) {
        List<RecordRows.AssetRow> rows = new ArrayList<>();
        long cash = account.getBalance();
        long frozenCash = account.getFrozenBalance();
        long commodityValue = 0;
        long stockValue = 0;

        rows.add(new RecordRows.AssetRow("现金", "可用现金", 1, cash, 0, cash, 1, 0));
        if (frozenCash > 0) {
            rows.add(new RecordRows.AssetRow("现金", "冻结资金", 1, frozenCash, 0, frozenCash, 1, 0));
        }

        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            MarketPrice price = NpcMarketMaker.getMarketPrice(entry.getKey());
            Commodity commodity = finance.commodity.CommodityRegistry.getCommodity(entry.getKey());
            if (price == null || entry.getValue() <= 0) {
                continue;
            }
            long value = safeMultiply(price.getMidPrice(), entry.getValue());
            commodityValue = finance.util.MathUtil.saturatedAddNonNegative(commodityValue, value);
            String name = commodity != null ? commodity.getDisplayName() : entry.getKey();
            rows.add(new RecordRows.AssetRow("商品", name, entry.getValue(), value, 0, value, price.getMidPrice(), 0));
        }

        for (Map.Entry<String, StockHolding> entry :
                StockPortfolioManager.getPortfolio(player.getUUID()).entrySet()) {
            Stock stock = StockMarketManager.getStock(entry.getKey());
            StockHolding holding = entry.getValue();
            if (stock == null || holding.getQuantity() <= 0) {
                continue;
            }
            long value = safeMultiply(stock.getLastPrice(), holding.getQuantity());
            long cost = safeMultiply(holding.getAverageCost(), holding.getQuantity());
            long profit = value - cost;
            stockValue = finance.util.MathUtil.saturatedAddNonNegative(stockValue, value);
            rows.add(new RecordRows.AssetRow(
                    "股票", stock.getSymbol(), holding.getQuantity(), value, 0, cost, stock.getLastPrice(), profit));
        }

        long mcDay = EconomyCycleService.currentMcDay(player.getServer());
        long fixedIncomeValue = 0;
        for (finance.debt.CorporateBond bond :
                finance.debt.CorporateBondManager.bonds().values()) {
            long quantity = bond.holdings().getOrDefault(player.getUUID(), 0L);
            if (quantity <= 0) continue;
            finance.debt.BondValuation valuation =
                    finance.debt.FixedIncomeValuationService.value(bond, player.getUUID(), mcDay);
            fixedIncomeValue = finance.util.MathUtil.saturatedAddNonNegative(fixedIncomeValue, valuation.marketValue());
            long cost = finance.bondmarket.BondPortfolioManager.position(bond.id(), player.getUUID())
                    .totalCost();
            rows.add(new RecordRows.AssetRow(
                    "债券",
                    bond.code(),
                    quantity,
                    valuation.marketValue(),
                    0,
                    cost,
                    valuation.marketPricePerUnit(),
                    valuation.unrealizedProfit()));
        }
        for (finance.fixedincome.CentralBankBill bill :
                finance.fixedincome.CentralBankBillManager.bills().values()) {
            if (bill.status() != finance.fixedincome.CentralBankBillStatus.ACTIVE) continue;
            long principal = bill.principalByPlayer().getOrDefault(player.getUUID(), 0L);
            if (principal <= 0) continue;
            long expected = finance.fixedincome.CentralBankBillManager.expectedMaturityValue(bill, player.getUUID());
            fixedIncomeValue = finance.util.MathUtil.saturatedAddNonNegative(fixedIncomeValue, principal);
            rows.add(new RecordRows.AssetRow(
                    "票据",
                    bill.termDays() + "日央行票据",
                    principal,
                    principal,
                    0,
                    principal,
                    1,
                    Math.max(0, expected - principal)));
        }

        finance.futures.MarginAccount marginAccount =
                finance.futures.MarginManager.accounts().get(player.getUUID());
        long futuresEquity =
                marginAccount == null ? 0 : Math.max(0, finance.futures.FuturesRiskService.equity(player.getUUID()));
        if (marginAccount != null
                && (marginAccount.cashBalance() > 0
                        || finance.futures.MarginManager.positions().keySet().stream()
                                .anyMatch(k -> k.ownerId().equals(player.getUUID())))) {
            rows.add(new RecordRows.AssetRow(
                    "衍生品",
                    "期货保证金权益",
                    1,
                    futuresEquity,
                    0,
                    marginAccount.cashBalance(),
                    1,
                    futuresEquity - marginAccount.cashBalance()));
        }

        long fundValue = 0;
        Map<String, finance.fund.PlayerFundPosition> fundPositions =
                finance.fund.FundManager.positions().get(player.getUUID());
        if (fundPositions != null) {
            for (Map.Entry<String, finance.fund.PlayerFundPosition> entry : fundPositions.entrySet()) {
                finance.fund.FundState state = finance.fund.FundManager.states().get(entry.getKey());
                finance.fund.FundDefinition definition =
                        finance.fund.FundManager.definitions().get(entry.getKey());
                if (state == null || definition == null || entry.getValue().shareUnits() <= 0) continue;
                long value = finance.fund.FundMath.ratioFloor(
                        entry.getValue().shareUnits(), state.currentNav(), finance.fund.FundManager.SHARE_SCALE);
                fundValue = finance.util.MathUtil.saturatedAddNonNegative(fundValue, value);
                rows.add(new RecordRows.AssetRow(
                        "基金",
                        definition.displayName(),
                        entry.getValue().shareUnits(),
                        value,
                        0,
                        entry.getValue().totalCost(),
                        state.currentNav(),
                        value - entry.getValue().totalCost()));
            }
        }

        long totalAsset = finance.util.MathUtil.saturatedAddNonNegative(cash, frozenCash);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, commodityValue);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, stockValue);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, fixedIncomeValue);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, futuresEquity);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, fundValue);
        long todayProfit = AssetSnapshotManager.getTodayProfit(player.getUUID(), totalAsset, mcDay);
        List<RecordRows.AssetRow> withPercent = new ArrayList<>();
        for (RecordRows.AssetRow row : rows) {
            double percent = totalAsset > 0 ? (double) row.value() / totalAsset * 100 : 0;
            withPercent.add(new RecordRows.AssetRow(
                    row.category(),
                    row.name(),
                    row.quantity(),
                    row.value(),
                    percent,
                    row.cost(),
                    row.currentPrice(),
                    row.floatingProfit()));
        }

        return new AssetBundle(
                new RecordRows.AssetSummary(cash, frozenCash, commodityValue, stockValue, totalAsset, todayProfit),
                withPercent);
    }

    static long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    record AssetBundle(RecordRows.AssetSummary summary, List<RecordRows.AssetRow> rows) {}
}
