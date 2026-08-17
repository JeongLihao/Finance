package finance.fund;

import finance.account.Account;
import finance.account.AccountManager;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import finance.debt.FixedIncomeValuationService;
import finance.fixedincome.CentralBankBill;
import finance.fixedincome.CentralBankBillManager;
import finance.fixedincome.CentralBankBillStatus;
import finance.stock.Stock;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.stock.StockOrderManager;
import finance.stock.StockOrderType;

import java.math.BigInteger;
import finance.config.FinanceConfig;

public final class FundValuationService {
    private FundValuationService() { }
    public record Valuation(long cash, long stockValue, long bondValue, long billValue, long netAssets,
                            long nav, boolean degraded, String riskReason) { }

    public static Valuation value(FundDefinition definition, FundState state, long day) {
        if (definition == null || state == null || day < 0) return new Valuation(0,0,0,0,0,0,true,"invalid valuation input");
        var custody = definition.custodyAccountId(); Account account = AccountManager.getAccounts().get(custody);
        BigInteger cash = account == null ? BigInteger.ZERO : BigInteger.valueOf(account.getBalance()).add(BigInteger.valueOf(account.getFrozenBalance()));
        BigInteger stocks = BigInteger.ZERO, bonds = BigInteger.ZERO, bills = BigInteger.ZERO; boolean degraded = false;
        for (var entry : StockPortfolioManager.getPortfolio(custody).entrySet()) {
            StockHolding holding = entry.getValue(); Stock stock = StockMarketManager.getStock(entry.getKey());
            if (stock == null || stock.getLastPrice() <= 0) { degraded = true; continue; }
            stocks = stocks.add(BigInteger.valueOf(stock.getLastPrice()).multiply(BigInteger.valueOf(holding.getQuantity())));
        }
        for (var order : StockOrderManager.getOrders()) {
            if (!custody.equals(order.getPlayerId()) || order.getType() != StockOrderType.SELL) continue;
            Stock stock = StockMarketManager.getStock(order.getSymbol());
            if (stock == null || stock.getLastPrice() <= 0) { degraded = true; continue; }
            stocks = stocks.add(BigInteger.valueOf(stock.getLastPrice()).multiply(BigInteger.valueOf(order.getQuantity())));
        }
        for (CorporateBond bond : CorporateBondManager.bonds().values()) {
            long quantity = bond.holdings().getOrDefault(custody, 0L); if (quantity <= 0) continue;
            bonds = bonds.add(BigInteger.valueOf(FixedIncomeValuationService.value(bond, custody, day).marketValue()));
        }
        for (CentralBankBill bill : CentralBankBillManager.bills().values()) {
            long principal = bill.principalByPlayer().getOrDefault(custody, 0L); if (principal <= 0) continue;
            if (bill.status() == CentralBankBillStatus.ACTIVE) {
                long accruedDays=Math.max(0,Math.min(day,bill.maturityDay())-bill.issueDay());
                BigInteger interest=BigInteger.valueOf(principal).multiply(BigInteger.valueOf(bill.annualRateBasisPoints())).multiply(BigInteger.valueOf(accruedDays)).divide(BigInteger.valueOf((long)FinanceConfig.annualMcDays()*10_000L));
                bills=bills.add(BigInteger.valueOf(principal).add(interest));
            }
        }
        BigInteger net = cash.add(stocks).add(bonds).add(bills);
        if (net.signum() < 0 || net.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return new Valuation(FundMath.cap(cash),FundMath.cap(stocks),FundMath.cap(bonds),FundMath.cap(bills),0,0,true,"net asset overflow");
        long assets = net.longValue(); long nav = state.totalShareUnits() == 0 ? FundManager.INITIAL_NAV
                : FundMath.ratioFloor(assets, FundManager.SHARE_SCALE, state.totalShareUnits());
        if (nav <= 0) return new Valuation(FundMath.cap(cash),FundMath.cap(stocks),FundMath.cap(bonds),FundMath.cap(bills),assets,0,true,"non-positive NAV");
        return new Valuation(FundMath.cap(cash),FundMath.cap(stocks),FundMath.cap(bonds),FundMath.cap(bills),assets,nav,degraded,degraded?"missing underlying price":"");
    }
}
