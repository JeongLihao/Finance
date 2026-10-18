package finance.simple;

import finance.account.AccountManager;
import finance.bank.BankCustomerAccount;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.bank.TimeDeposit;
import finance.bank.TimeDepositStatus;
import finance.config.FinanceConfig;
import finance.cycle.EconomyCycleService;
import finance.fixedincome.TreasuryBond;
import finance.fixedincome.TreasuryBondManager;
import finance.fixedincome.TreasuryBondStatus;
import finance.fixedincome.YieldCurveService;
import finance.fund.FundDefinition;
import finance.fund.FundManager;
import finance.fund.FundState;
import finance.fund.FundType;
import finance.fund.PlayerFundPosition;
import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import finance.margin.MarginService;
import finance.policy.MonetaryPolicyService;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.ConditionalStockOrderType;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 投资页“理财”和“股票详情”的一键操作：把银行定期、央行债券、基金、融资融券、条件单包成玩家看得懂的按钮。
 * 真正的校验、扣款和记账仍在原来的服务里。
 */
public final class SimpleInvestService {

    private SimpleInvestService() {}

    /** 一种理财产品。key：bank / time:天数 / cbond:天数 / fund:基金ID。rateBps 为 -1 表示收益浮动。 */
    public record Product(String key, String name, String term, int rateBps, String risk, long minAmount) {}

    /** 玩家持有的一笔理财。key：bank / time:定期ID / cbond:债券ID / fund:基金ID。 */
    public record Holding(String key, String name, long amount, String status, String redeemLabel) {}

    /** 融资融券账户：保证金、借款、安全度（低于强平线会被强制卖出）、加倍买和借股卖出的仓位。 */
    public record MarginInfo(long collateral, long debt, int safetyPct, int liquidationPct,
                             Map<String, Long> levered, Map<String, Long> shorts) {
        public static final MarginInfo EMPTY = new MarginInfo(0, 0, -1, 0, Map.of(), Map.of());
    }

    public record Result(boolean success, String message, long delta) {}

    // ------------------------------------------------------------ 产品列表

    public static List<Product> products() {
        List<Product> out = new ArrayList<>();
        CommercialBank bank = SimpleCenterService.mainBank();
        if (bank != null) {
            out.add(new Product("bank", "活期存款", "随时取", finance.bank.PublicBankViewService.crop(bank, false).demandRateBps(), "低", 1));
            int benchmark = MonetaryPolicyService.benchmarkRateBasisPoints();
            for (int term : FinanceConfig.timeDepositTerms()) {
                int rate = Math.max(0, Math.min(20_000, benchmark - bank.policy().timeSpreadBps() + Math.min(500, term * 3)));
                out.add(new Product("time:" + term, "定期存款", term + " 天", rate, "低", FinanceConfig.timeDepositMinAmount()));
            }
        }
        for (int term : TreasuryBondManager.TERM_DAYS) {
            out.add(new Product("cbond:" + term, "央行债券", term + " 天", YieldCurveService.treasuryYieldBasisPoints(term), "低",
                    FinanceConfig.treasuryBondMinAmount()));
        }
        for (FundDefinition d : FundManager.definitions().values()) {
            String risk = d.type() == FundType.MONEY_MARKET ? "低" : d.type() == FundType.BROAD_STOCK_INDEX ? "中" : "高";
            out.add(new Product("fund:" + d.id(), d.displayName(), "随时赎回", -1, risk, d.minimumSubscription()));
        }
        return out;
    }

    public static List<Holding> holdings(ServerPlayer player) {
        UUID id = player.getUUID();
        long day = EconomyCycleService.currentMcDay(player.server);
        List<Holding> out = new ArrayList<>();
        CommercialBank bank = SimpleCenterService.mainBank();
        BankCustomerAccount demand = SimpleCenterService.demandAccount(id, bank, day, false);
        if (demand != null && demand.available() > 0) out.add(new Holding("bank", "活期存款", demand.available(), "随时可取", "取回"));
        for (TimeDeposit t : BankingManager.timeDeposits().values()) {
            if (!t.ownerId().equals(id)) continue;
            if (t.status() == TimeDepositStatus.MATURED) {
                out.add(new Holding("time:" + t.id(), "定期存款", t.principal() + t.accruedInterest(), "已到期", "取回"));
            } else if (t.status() == TimeDepositStatus.ACTIVE) {
                out.add(new Holding("time:" + t.id(), "定期存款", t.principal(), "第 " + t.maturityDay() + " 天到期", "提前取（没利息）"));
            }
        }
        for (TreasuryBond b : TreasuryBondManager.bonds().values()) {
            Long principal = b.principalByPlayer().get(id);
            if (principal == null || principal <= 0 || b.status() != TreasuryBondStatus.ACTIVE) continue;
            out.add(new Holding("cbond:" + b.id(), "央行债券 " + b.termDays() + " 天", principal,
                    "第 " + b.maturityDay() + " 天到期，自动还本付息", "提前取（扣手续费）"));
        }
        Map<String, FundState> states = FundManager.states();
        for (FundDefinition d : FundManager.definitions().values()) {
            PlayerFundPosition p = FundManager.position(id, d.id());
            FundState s = states.get(d.id());
            if (p == null || p.availableShareUnits() <= 0 || s == null) continue;
            long value = p.availableShareUnits() * s.currentNav() / FundManager.SHARE_SCALE;
            long change = value - p.totalCost();
            out.add(new Holding("fund:" + d.id(), d.displayName(), value, "成本 " + MoneyFormat.pounds(p.totalCost())
                    + "，" + (change >= 0 ? "赚 " : "亏 ") + MoneyFormat.pounds(Math.abs(change)), "全部赎回"));
        }
        return out;
    }

    // ------------------------------------------------------------ 买入 / 取回

    public static Result buy(ServerPlayer player, String key, long amount) {
        UUID id = player.getUUID();
        long day = EconomyCycleService.currentMcDay(player.server);
        if (amount <= 0) return fail("金额要大于 0。");
        if (AccountManager.getBalance(id) < amount) return fail("钱包里没有这么多钱。");
        if ("bank".equals(key)) {
            SimpleCenterService.Result r = SimpleCenterService.bankDeposit(player, amount);
            return new Result(r.success(), r.message(), r.moneyDelta());
        }
        if (key.startsWith("time:")) {
            int term = parseInt(key.substring(5));
            CommercialBank bank = SimpleCenterService.mainBank();
            if (bank == null) return fail("现在没有银行营业。");
            if (amount < FinanceConfig.timeDepositMinAmount()) return fail("定期最少存 " + MoneyFormat.pounds(FinanceConfig.timeDepositMinAmount()) + "。");
            BankingManager.Result deposited = BankingManager.depositPlayer(id, bank.id(), amount, day);
            if (!deposited.success()) return fail(deposited.message());
            BankingManager.Result opened = BankingManager.openTimeDeposit(id, deposited.id(), amount, term, day);
            if (!opened.success()) {
                BankCustomerAccount demand = SimpleCenterService.demandAccount(id, bank, day, false);
                if (demand != null) BankingManager.withdrawPlayer(id, demand.id(), amount, day);
                return fail(opened.message());
            }
            return new Result(true, "存了 " + term + " 天定期 " + MoneyFormat.pounds(amount) + "，到期连本带息自动转回活期。", -amount);
        }
        if (key.startsWith("cbond:")) {
            int term = parseInt(key.substring(6));
            var r = TreasuryBondManager.subscribe(id, term, amount, day);
            return r.success() ? new Result(true, "买了 " + term + " 天央行债券 " + MoneyFormat.pounds(amount) + "，到期自动还本付息。", -amount)
                    : fail(r.message());
        }
        if (key.startsWith("fund:")) {
            String fund = key.substring(5);
            FundDefinition d = FundManager.definitions().get(FundDefinition.normalize(fund));
            if (d == null) return fail("没有这只基金。");
            // 产品表里已经写明了风险等级和“收益浮动”，这里代玩家确认一次风险提示
            FundManager.acknowledgeRisk(id, d.type());
            var r = FundManager.subscribe(id, fund, amount, day, null);
            return r.success() ? new Result(true, "买入基金「" + d.displayName() + "」" + MoneyFormat.pounds(amount) + "，每天按净值涨跌。", -amount)
                    : fail(r.message());
        }
        return fail("没有这种产品。");
    }

    public static Result redeem(ServerPlayer player, String key) {
        UUID id = player.getUUID();
        long day = EconomyCycleService.currentMcDay(player.server);
        long before = AccountManager.getBalance(id);
        if ("bank".equals(key)) {
            SimpleCenterService.Result r = SimpleCenterService.bankWithdraw(player, Long.MAX_VALUE);
            return new Result(r.success(), r.message(), r.moneyDelta());
        }
        if (key.startsWith("time:")) {
            UUID depositId = parseUuid(key.substring(5));
            TimeDeposit t = depositId == null ? null : BankingManager.timeDeposits().get(depositId);
            if (t == null || !t.ownerId().equals(id)) return fail("找不到这笔定期。");
            BankingManager.Result r = t.status() == TimeDepositStatus.MATURED
                    ? BankingManager.redeemTimeDeposit(id, depositId, day)
                    : BankingManager.earlyRedeemTimeDeposit(id, depositId, day);
            if (!r.success()) return fail(r.message());
            // 钱先回到活期，再转回钱包
            CommercialBank bank = BankingManager.bank(t.bankId());
            BankCustomerAccount demand = SimpleCenterService.demandAccount(id, bank, day, false);
            if (demand != null && demand.available() > 0) BankingManager.withdrawPlayer(id, demand.id(), demand.available(), day);
            long got = AccountManager.getBalance(id) - before;
            return new Result(true, "定期已取回，钱包 " + MoneyFormat.signed(got) + "。", got);
        }
        if (key.startsWith("cbond:")) {
            UUID bondId = parseUuid(key.substring(6));
            var r = bondId == null ? null : TreasuryBondManager.earlyRedeem(id, bondId, day);
            if (r == null || !r.success()) return fail(r == null ? "找不到这笔债券。" : r.message());
            long got = AccountManager.getBalance(id) - before;
            return new Result(true, "央行债券已提前取回，钱包 " + MoneyFormat.signed(got) + "。", got);
        }
        if (key.startsWith("fund:")) {
            String fund = key.substring(5);
            PlayerFundPosition p = FundManager.position(id, fund);
            if (p == null || p.availableShareUnits() <= 0) return fail("你没有这只基金。");
            var r = FundManager.requestRedemption(id, fund, p.availableShareUnits(), day, null);
            if (!r.success()) return fail(r.message());
            long got = AccountManager.getBalance(id) - before;
            return new Result(true, got > 0 ? "基金已赎回，钱包 " + MoneyFormat.signed(got) + "。" : "已申请赎回，钱会在基金卖出持仓后到账。", got);
        }
        return fail("没有这种产品。");
    }

    // ------------------------------------------------------------ 融资融券、自动卖出

    public static MarginInfo marginInfo(UUID player) {
        MarginAccount a = MarginPoolManager.accounts().get(player);
        if (a == null) return MarginInfo.EMPTY;
        double ratio = MarginService.marginRatio(player);
        int safety = ratio == Double.MAX_VALUE || !Double.isFinite(ratio) ? -1 : (int) Math.min(100_000, Math.round(ratio));
        return new MarginInfo(a.getCashCollateral(), a.getFinancedDebt() + a.getFinancedInterestOwed(), safety,
                (int) Math.round(MarginService.maintenanceRatio()), new LinkedHashMap<>(a.getFinancedShares()),
                new LinkedHashMap<>(a.getShortPositions()));
    }

    /** 加倍买：自己出一半、券商借一半；保证金不够时自动从钱包补首付。 */
    public static Result leverBuy(ServerPlayer player, String symbol, long qty) {
        UUID id = player.getUUID();
        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) return fail("没有这只股票。");
        long total = Math.multiplyExact(Math.max(0, qty), Math.max(0, stock.getFairValue()));
        long down = (total + 1) / 2;
        long topUp = down - MarginPoolManager.getAccount(id).getCashCollateral();
        long before = AccountManager.getBalance(id);
        if (topUp > 0) {
            MarginService.Result deposit = MarginService.depositCollateral(id, topUp);
            if (!deposit.success()) return fail("钱不够付首付 " + MoneyFormat.pounds(down) + "。");
        }
        MarginService.Result r = MarginService.marginBuy(id, symbol, qty);
        if (!r.success()) return fail(r.message());
        return new Result(true, "加倍买入 " + qty + " 股「" + stock.getName() + "」：你出 " + MoneyFormat.pounds(down)
                + "，券商借你 " + MoneyFormat.pounds(total - down) + "。股价跌太多会被强制卖出。", AccountManager.getBalance(id) - before);
    }

    /** 还清加倍买借的钱：借来的股票变成你自己的，可以正常卖。 */
    public static Result leverRepay(ServerPlayer player) {
        UUID id = player.getUUID();
        MarginAccount a = MarginPoolManager.getAccount(id);
        long owed = a.getFinancedDebt() + a.getFinancedInterestOwed();
        if (owed <= 0) return fail("没有要还的借款。");
        MarginService.Result r = MarginService.repayFinancing(id, owed);
        return r.success() ? new Result(true, "已还清借款 " + MoneyFormat.pounds(owed) + "，加倍买的股票都变成你自己的了。", -owed)
                : fail(r.message());
    }

    /** 借股卖出（看跌）：先借股票卖掉，以后买回来还；要押一半的钱。 */
    public static Result shortSell(ServerPlayer player, String symbol, long qty) {
        UUID id = player.getUUID();
        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) return fail("没有这只股票。");
        long notional = Math.multiplyExact(Math.max(0, qty), Math.max(0, stock.getFairValue()));
        long need = (notional + 1) / 2;
        long topUp = need - MarginPoolManager.getAccount(id).getCashCollateral();
        long before = AccountManager.getBalance(id);
        if (topUp > 0) {
            MarginService.Result deposit = MarginService.depositCollateral(id, topUp);
            if (!deposit.success()) return fail("钱不够押保证金 " + MoneyFormat.pounds(need) + "。");
        }
        MarginService.Result r = MarginService.shortSell(id, symbol, qty);
        if (!r.success()) return fail(r.message());
        return new Result(true, "借了 " + qty + " 股「" + stock.getName() + "」卖掉。股价跌了再买回来就赚差价，涨了就亏。",
                AccountManager.getBalance(id) - before);
    }

    public static Result shortCover(ServerPlayer player, String symbol) {
        MarginService.Result r = MarginService.coverShort(player.getUUID(), symbol, 0);
        return r.success() ? new Result(true, "已买回还券。不用的保证金可以点“取回保证金”。", 0) : fail(r.message());
    }

    public static Result withdrawCollateral(ServerPlayer player) {
        UUID id = player.getUUID();
        long collateral = MarginPoolManager.getAccount(id).getCashCollateral();
        if (collateral <= 0) return fail("没有保证金。");
        MarginService.Result r = MarginService.withdrawCollateral(id, collateral);
        return r.success() ? new Result(true, "取回保证金 " + MoneyFormat.pounds(collateral) + "。", collateral)
                : fail("还有加倍买或借股卖出的仓位，保证金要留着。" );
    }

    /** 自动卖出：涨到（止盈）或跌到（止损）trigger 时卖出 qty 股。 */
    public static Result autoSell(ServerPlayer player, String symbol, boolean takeProfit, long trigger, long qty) {
        var r = ConditionalStockOrderManager.addOrder(player.getUUID(), symbol,
                takeProfit ? ConditionalStockOrderType.TAKE_PROFIT : ConditionalStockOrderType.STOP_LOSS, trigger, qty);
        if (!r.success()) return fail(r.message());
        return new Result(true, (takeProfit ? "涨到 " : "跌到 ") + MoneyFormat.pounds(trigger) + " 时自动卖出 " + qty + " 股。", 0);
    }

    public static Result cancelAuto(ServerPlayer player, UUID orderId) {
        return orderId != null && ConditionalStockOrderManager.cancelOrder(player.getUUID(), orderId)
                ? new Result(true, "已取消自动卖出。", 0) : fail("这条自动卖出已经执行或不存在。");
    }

    // ------------------------------------------------------------ 小工具

    private static Result fail(String message) { return new Result(false, message, 0); }

    private static int parseInt(String text) {
        try { return Integer.parseInt(text); } catch (NumberFormatException e) { return -1; }
    }

    private static UUID parseUuid(String text) {
        try { return UUID.fromString(text); } catch (RuntimeException e) { return null; }
    }
}
