package finance.fund;

import finance.account.AccountManager;
import finance.bank.BankAccountStatus;
import finance.bank.BankingManager;
import finance.bondmarket.BondPortfolioManager;
import finance.debt.CorporateBondManager;
import finance.debt.FixedIncomeValuationService;
import finance.fixedincome.CentralBankBillManager;
import finance.futures.MarginManager;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;

import java.math.BigInteger;
import java.util.UUID;

/** Server-side aggregate; callers may only request the authenticated player's UUID. */
public final class PlayerPortfolioService {
    private PlayerPortfolioService(){}
    public record Overview(long cash,long bankDeposits,long stocks,long commodities,long bonds,long bills,long funds,
                           long futuresEquity,long liabilities,long netAssets,long dayChange,String primaryRisk){}
    public static Overview calculate(UUID player,long day){if(player==null)return new Overview(0,0,0,0,0,0,0,0,0,0,0,"invalid player");
        BigInteger cash=BigInteger.valueOf(AccountManager.getBalance(player)),bank=BigInteger.ZERO,stocks=BigInteger.ZERO,bonds=BigInteger.ZERO,bills=BigInteger.ZERO,funds=BigInteger.ZERO,futures=BigInteger.ZERO;
        for(var a:BankingManager.accounts().values())if(a.ownerId().equals(player)&&a.status()!=BankAccountStatus.CLOSED)bank=bank.add(BigInteger.valueOf(a.balance()));
        for(var e:StockPortfolioManager.getPortfolio(player).entrySet()){var stock=StockMarketManager.getStock(e.getKey());if(stock!=null&&stock.getLastPrice()>0)stocks=stocks.add(BigInteger.valueOf(stock.getLastPrice()).multiply(BigInteger.valueOf(e.getValue().getQuantity())));}
        for(var bond:CorporateBondManager.bonds().values())if(bond.holdings().getOrDefault(player,0L)>0)bonds=bonds.add(BigInteger.valueOf(FixedIncomeValuationService.value(bond,player,day).marketValue()));
        for(var bill:CentralBankBillManager.bills().values())if(bill.principalByPlayer().getOrDefault(player,0L)>0)bills=bills.add(BigInteger.valueOf(CentralBankBillManager.expectedMaturityValue(bill,player)));
        var fundPositions=FundManager.positions().get(player);if(fundPositions!=null)for(var e:fundPositions.entrySet()){FundState s=FundManager.states().get(e.getKey());if(s!=null)funds=funds.add(BigInteger.valueOf(FundMath.ratioFloor(e.getValue().shareUnits(),s.currentNav(),FundManager.SHARE_SCALE)));}
        var margin=MarginManager.accounts().get(player);if(margin!=null)futures=futures.add(BigInteger.valueOf(margin.cashBalance()));
        BigInteger total=cash.add(bank).add(stocks).add(bonds).add(bills).add(funds).add(futures);long net=FundMath.cap(total);return new Overview(FundMath.cap(cash),FundMath.cap(bank),FundMath.cap(stocks),0,FundMath.cap(bonds),FundMath.cap(bills),FundMath.cap(funds),FundMath.cap(futures),0,net,0,futures.signum()!=0?"期货保证金和价格波动":"市场价格与流动性风险");}
}
