package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.MarketMakerLedger;
import finance.market.NpcMarketMaker;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferService;
import finance.npcinvestor.NpcInvestorArchetype;
import finance.npcinvestor.NpcInvestorManager;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.stock.StockValuation;
import finance.util.MathUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 公司上市：发起认购 → 认购期内 NPC 投资者和玩家真金白银认购（钱进独立托管）→ 期满按实际认到的股数发股。
 *
 * <ul>
 *   <li>不再凭空造募资款：公司拿到的钱全部来自认购人，认购托管 → 公司现金；</li>
 *   <li>认购不到 {@link FinanceConfig#ipoMinFillBasisPoints()} 就取消，托管原路退回，上市费不退；</li>
 *   <li>认购人拿认到的股，创始人拿总股本里剩下的（含没卖出去的那部分）并照常锁仓；</li>
 *   <li>做市商要的股票库存由它在发起认购时用自己的钱认购（{@link FinanceConfig#ipoMarketMakerReserveRatio()}）；</li>
 *   <li>NPC 竞争公司上市走同一条路（{@link #startOwnerless}）。</li>
 * </ul>
 */
public final class CompanyIPOService {

    /** 单股发行价上限。 */
    public static final long MAX_ISSUE_PRICE = 100L;

    /** 单次最多发行的股数。 */
    public static final long MAX_ISSUE_QUANTITY = 100_000L;

    /** 发行量占总股本的比例 —— 创始人至少保留 60%。 */
    public static final double DEFAULT_FLOAT_RATIO = 0.4;

    /** NPC 投资者每天最多花账户余额的这么多分之一认购（同 {@code NpcBondDemand}）。 */
    static final int NPC_DAILY_BUDGET_DIVISOR = 4;

    private static final Map<UUID, IpoSubscription> SUBSCRIPTIONS = new LinkedHashMap<>();
    /** 已结束的认购最多留这么多条（面板和测试用），之后按时间丢掉最旧的。 */
    static final int MAX_CLOSED = 64;

    private CompanyIPOService() {}

    public record Result(boolean success, String message, IpoSubscription subscription) {
        static Result fail(String message) {
            return new Result(false, message, null);
        }

        static Result ok(IpoSubscription s, long fee) {
            return new Result(
                    true,
                    "上市认购开始了：发行 " + s.issueQuantity() + " 股，每股 " + finance.util.MoneyFormat.pounds(s.issuePrice())
                            + "，认购期 " + (s.endDay() - s.startDay()) + " 天。认满 "
                            + s.minimumShares(FinanceConfig.ipoMinFillBasisPoints()) + " 股才上市，不够就退款"
                            + (fee > 0 ? "（上市费 " + finance.util.MoneyFormat.pounds(fee) + " 不退）" : "") + "。",
                    s);
        }

        static Result ok(IpoSubscription s, long cost, String message) {
            return new Result(true, message, s);
        }
    }

    // ---------------------------------------------------------------- 查询

    public static Collection<IpoSubscription> subscriptions() {
        return Collections.unmodifiableCollection(SUBSCRIPTIONS.values());
    }

    public static IpoSubscription get(UUID subscriptionId) {
        return subscriptionId == null ? null : SUBSCRIPTIONS.get(subscriptionId);
    }

    /** 公司进行中的认购；没有返回 null。 */
    public static IpoSubscription openFor(UUID companyId) {
        if (companyId == null) return null;
        for (IpoSubscription s : SUBSCRIPTIONS.values())
            if (s.status() == IpoSubscription.Status.OPEN && s.companyId().equals(companyId)) return s;
        return null;
    }

    public static List<IpoSubscription> open() {
        List<IpoSubscription> out = new ArrayList<>();
        for (IpoSubscription s : SUBSCRIPTIONS.values()) if (s.status() == IpoSubscription.Status.OPEN) out.add(s);
        return out;
    }

    // ---------------------------------------------------------------- 发起

    /** 玩家公司发起上市认购：只有所有者能发起，上市费从所有者钱包付。 */
    public static Result start(UUID requesterId, UUID companyId, long issuePrice, long issueQuantity, long day) {
        Company company = companyId == null ? null : CompanyManager.getCompany(companyId);
        if (company == null) return Result.fail("公司不存在。");
        if (requesterId == null || !requesterId.equals(company.getOwnerId())) return Result.fail("只有公司所有者可以发起上市。");
        String invalid = validate(company, issuePrice, issueQuantity);
        if (invalid != null) return Result.fail(invalid);
        long minValuation = FinanceConfig.simpleIpoMinValuation();
        long valuation = StockValuation.companyValue(company);
        if (valuation < minValuation) {
            return Result.fail("公司估值要达到 " + finance.util.MoneyFormat.pounds(minValuation) + " 才能上市，现在是 "
                    + finance.util.MoneyFormat.pounds(valuation) + "（家底加最近的利润，借来的钱不算）。");
        }
        long fee = Math.max(0, FinanceConfig.ipoFee());
        if (fee > 0 && AccountManager.getBalance(requesterId) < fee)
            return Result.fail("上市费不够，需要 " + finance.util.MoneyFormat.pounds(fee) + "。");
        return openAndCharge(
                company, requesterId, issuePrice, issueQuantity, day, MoneyEndpoints.account(requesterId), fee);
    }

    /** 无主公司（NPC 竞争公司）发起上市认购：规则和玩家一样，上市费从公司现金付，创始股记到 {@code founderAccount}。 */
    public static Result startOwnerless(
            UUID companyId, UUID founderAccount, long issuePrice, long issueQuantity, long day) {
        Company company = companyId == null ? null : CompanyManager.getCompany(companyId);
        if (company == null || founderAccount == null) return Result.fail("公司不存在。");
        if (company.getOwnerId() != null) return Result.fail("只有无主公司可以走这个入口。");
        String invalid = validate(company, issuePrice, issueQuantity);
        if (invalid != null) return Result.fail(invalid);
        // 投资账户先以系统账户建立：普通账户第一次被访问会领新手资金
        AccountManager.getOrCreateSystemAccount(founderAccount);
        long fee = Math.max(0, FinanceConfig.ipoFee());
        if (fee > 0 && company.getCash() < fee) return Result.fail("公司现金不够付上市费。");
        return openAndCharge(
                company, founderAccount, issuePrice, issueQuantity, day, MoneyEndpoints.company(company), fee);
    }

    /**
     * 先开认购（做市商在这一步用它自己原有的钱认购预留部分），再收上市费。上市费付给 NPC 资金池，
     * 而 NPC 资金池和做市商是同一个账户，先收费的话做市商会拿上市费去认购。收费失败就撤销认购并退款。
     */
    private static Result openAndCharge(
            Company company,
            UUID founder,
            long price,
            long quantity,
            long day,
            finance.money.MoneyEndpoint payer,
            long fee) {
        IpoSubscription s = open(company, founder, price, quantity, day);
        if (fee > 0 && !payFee(payer, founder, company, fee)) {
            cancel(s, "上市费扣款失败");
            return Result.fail("上市费扣款失败。");
        }
        return Result.ok(s, fee);
    }

    private static String validate(Company company, long issuePrice, long issueQuantity) {
        if (company.isPublic()) return "公司已上市，无法重复上市。";
        if (openFor(company.getCompanyId()) != null) return "公司已经在上市认购中了。";
        if (issuePrice <= 0 || issueQuantity <= 0) return "发行价和数量必须为正。";
        if (issueQuantity > MAX_ISSUE_QUANTITY) return "发行数量过多（最多 " + MAX_ISSUE_QUANTITY + "）。";
        if (issuePrice > MAX_ISSUE_PRICE) return "发行价过高（上限 " + MAX_ISSUE_PRICE + "）。";
        if (MathUtil.multiplyExactOrNegative1(issuePrice, (int) issueQuantity) <= 0) return "募资金额过大。";
        return null;
    }

    private static boolean payFee(finance.money.MoneyEndpoint from, UUID payer, Company company, long fee) {
        if (!MoneyTransferService.transfer(from, MoneyEndpoints.account(NpcMarketMaker.NPC_UUID), fee)
                .success()) return false;
        AccountManager.addTransactionRecord(new TransactionRecord(
                payer, NpcMarketMaker.NPC_UUID, fee, TransactionType.IPO_FEE, payer, company.getName(), 0));
        return true;
    }

    private static IpoSubscription open(Company company, UUID founder, long price, long quantity, long day) {
        long totalShares = Math.max(quantity, Math.round(quantity / DEFAULT_FLOAT_RATIO));
        long days = Math.max(1, FinanceConfig.ipoSubscriptionDays());
        IpoSubscription s = new IpoSubscription(
                UUID.randomUUID(),
                company.getCompanyId(),
                founder,
                price,
                quantity,
                totalShares,
                day,
                day + days,
                IpoSubscription.Status.OPEN);
        AccountManager.getOrCreateSystemAccount(s.escrowAccount());
        SUBSCRIPTIONS.put(s.id(), s);
        reserveForMarketMaker(s);
        EconomySavedData.markDirty();
        return s;
    }

    /** 做市商按预留比例用自己的钱认购，结算时这部分进做市商库存而不是普通持仓。余额不够就不认。 */
    private static void reserveForMarketMaker(IpoSubscription s) {
        double ratio = FinanceConfig.ipoMarketMakerReserveRatio();
        long qty = ratio <= 0 ? 0 : Math.min(s.remainingShares(), (long) Math.floor(s.issueQuantity() * ratio));
        if (qty <= 0) return;
        long cost = MathUtil.multiplyExactOrNegative1(s.issuePrice(), (int) qty);
        if (cost <= 0 || AccountManager.getBalance(MarketMakerLedger.MASTER_UUID) < cost) return;
        if (!AccountManager.moveFunds(MarketMakerLedger.MASTER_UUID, s.escrowAccount(), cost)) return;
        s.addSubscription(MarketMakerLedger.MASTER_UUID, qty);
        AccountManager.addTransactionRecord(new TransactionRecord(
                MarketMakerLedger.MASTER_UUID,
                s.escrowAccount(),
                cost,
                TransactionType.IPO_SUBSCRIBE,
                MarketMakerLedger.MASTER_UUID,
                "做市商预留",
                qty));
    }

    // ---------------------------------------------------------------- 认购

    /** 玩家（或 NPC）认购：钱从认购人账户进托管，超出剩余额度的部分不认。创始人不能认购自己的公司。 */
    public static Result subscribe(UUID subscriber, UUID subscriptionId, long shares, long day) {
        IpoSubscription s = get(subscriptionId);
        if (s == null || s.status() != IpoSubscription.Status.OPEN) return Result.fail("这次上市认购不存在或已结束。");
        if (subscriber == null || shares <= 0 || shares > Integer.MAX_VALUE) return Result.fail("认购股数无效。");
        if (day >= s.endDay()) return Result.fail("认购期已经结束，等结算。");
        if (subscriber.equals(s.founderAccount())) return Result.fail("创始人不能认购自己公司的新股。");
        long accepted = Math.min(shares, s.remainingShares());
        if (accepted <= 0) return Result.fail("这次发行已经认满了。");
        long cost = MathUtil.multiplyExactOrNegative1(s.issuePrice(), (int) accepted);
        if (cost <= 0) return Result.fail("认购金额过大。");
        if (AccountManager.getBalance(subscriber) < cost)
            return Result.fail("钱不够，认购 " + accepted + " 股要 " + finance.util.MoneyFormat.pounds(cost) + "。");
        if (!AccountManager.moveFunds(subscriber, s.escrowAccount(), cost)) return Result.fail("认购扣款失败。");
        s.addSubscription(subscriber, accepted);
        Company company = CompanyManager.getCompany(s.companyId());
        AccountManager.addTransactionRecord(new TransactionRecord(
                subscriber,
                s.escrowAccount(),
                cost,
                TransactionType.IPO_SUBSCRIBE,
                subscriber,
                company == null ? "" : company.getName(),
                accepted));
        EconomySavedData.markDirty();
        return Result.ok(
                s, cost, "认购了 " + accepted + " 股，付了 " + finance.util.MoneyFormat.pounds(cost) + "，钱先放在托管里，上市成功才转给公司。");
    }

    // ---------------------------------------------------------------- 日结

    /** 每天：NPC 投资者认购，然后结算期满或已认满的认购。必须在 NPC 投资者补足资金之后调用。 */
    public static void processDay(long day) {
        boolean changed = false;
        for (IpoSubscription s : new ArrayList<>(SUBSCRIPTIONS.values())) {
            if (s.status() != IpoSubscription.Status.OPEN) continue;
            Company company = CompanyManager.getCompany(s.companyId());
            if (company == null || company.isPublic()) {
                changed |= cancel(s, "公司已不存在或已上市");
                continue;
            }
            if (day < s.endDay() && s.remainingShares() > 0) npcDemand(s, company, day);
            if (day >= s.endDay() || s.remainingShares() == 0) settle(s, company, day);
            changed = true; // NPC 认购也改了状态
        }
        trimClosed();
        if (changed) EconomySavedData.markDirty();
    }

    /** NPC 投资者对每股价值的看法相对基本面的倍数：价值派要折价才买，短线派愿意多付一点。 */
    static double valuationMultiple(NpcInvestorArchetype archetype, Company company) {
        return switch (archetype) {
            case VALUE -> 0.9;
            case DIVIDEND -> 1.0;
            case GROWTH -> company.getSmoothedDailyProfit() > 0 ? 1.15 : 0.95;
            case SPECULATOR -> 1.1;
        };
    }

    /**
     * 按 {@code NpcBondDemand} 的写法：剩余股数按剩余天数分摊，最后一天认满；愿意认购的原型（发行价不高于它眼里的每股价值）
     * 平分当天的量，每个每天最多花余额的 1/{@value #NPC_DAILY_BUDGET_DIVISOR}，并守住单个和合计的持股上限。
     */
    static long npcDemand(IpoSubscription s, Company company, long day) {
        if (!FinanceConfig.npcInvestorsEnabled()) return 0;
        NpcInvestorManager.ensureAccounts();
        long perShare = StockValuation.companyValue(company) / Math.max(1, s.totalShares());
        List<NpcInvestorArchetype> willing = new ArrayList<>();
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values())
            if (s.issuePrice() <= perShare * valuationMultiple(a, company)) willing.add(a);
        if (willing.isEmpty()) return 0;
        willing.sort(Comparator.comparingInt(
                a -> Math.floorMod(a.ordinal() - (int) day, NpcInvestorArchetype.values().length)));
        long daysLeft = Math.max(1, s.endDay() - day);
        long today = (s.remainingShares() + daysLeft - 1) / daysLeft;
        long combinedCap = s.totalShares() * Math.max(0, FinanceConfig.npcMaxCombinedHoldingPercent()) / 100;
        long combined = 0;
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) combined += s.subscribedBy(a.accountId());
        long bought = 0;
        for (int i = 0; i < willing.size() && today > 0; i++) {
            NpcInvestorArchetype a = willing.get(i);
            long share = (today + (willing.size() - i) - 1) / (willing.size() - i);
            long eachCap = Math.round(s.totalShares()
                    * Math.max(0, FinanceConfig.npcMaxHoldingPercentEach())
                    / 100.0
                    * a.holdingCapFactor());
            long affordable = AccountManager.getBalance(a.accountId()) / NPC_DAILY_BUDGET_DIVISOR / s.issuePrice();
            long qty = Math.min(
                    Math.min(share, affordable),
                    Math.min(eachCap - s.subscribedBy(a.accountId()), combinedCap - combined));
            if (qty > 0 && subscribe(a.accountId(), s.id(), qty, day).success()) {
                bought += qty;
                combined += qty;
                today -= qty;
            }
        }
        return bought;
    }

    /** 期满结算：认够最低比例就上市，否则取消退款。返回状态是否改变。 */
    static boolean settle(IpoSubscription s, Company company, long day) {
        long sold = s.subscribedShares();
        if (sold < s.minimumShares(FinanceConfig.ipoMinFillBasisPoints()))
            return cancel(s, "认购不足 " + sold + "/" + s.issueQuantity());
        return list(s, company, day) || cancel(s, "上市结算失败");
    }

    private static boolean list(IpoSubscription s, Company company, long day) {
        long sold = s.subscribedShares();
        long raised = MathUtil.multiplyExactOrNegative1(s.issuePrice(), (int) Math.min(Integer.MAX_VALUE, sold));
        long founderShares = s.totalShares() - sold;
        if (raised <= 0 || founderShares < 0 || AccountManager.getBalance(s.escrowAccount()) < raised) return false;
        String symbol = generateUniqueSymbol(company.getName());
        long marketMakerShares = s.subscribedBy(MarketMakerLedger.MASTER_UUID);
        Map<UUID, Long> holders = new LinkedHashMap<>(s.subscriptions());
        holders.remove(MarketMakerLedger.MASTER_UUID);
        for (Map.Entry<UUID, Long> e : holders.entrySet())
            if (!StockPortfolioManager.canAddHolding(e.getKey(), symbol, e.getValue())) return false;
        if (founderShares > 0 && !StockPortfolioManager.canAddHolding(s.founderAccount(), symbol, founderShares))
            return false;
        if (!company.canDeposit(raised)) return false;
        if (!MoneyTransferService.transfer(
                        MoneyEndpoints.account(s.escrowAccount()), MoneyEndpoints.company(company), raised)
                .success()) return false;
        if (!holders.isEmpty() && !StockPortfolioManager.addHoldingsAtomically(holders, symbol, s.issuePrice())) {
            MoneyTransferService.transfer(
                    MoneyEndpoints.company(company), MoneyEndpoints.account(s.escrowAccount()), raised);
            return false;
        }
        if (founderShares > 0)
            StockPortfolioManager.addHolding(s.founderAccount(), symbol, founderShares, s.issuePrice());
        if (marketMakerShares > 0)
            MarketMakerLedger.addInventory(MarketMakerLedger.SubMarket.STOCK, symbol, marketMakerShares);
        Stock stock = new Stock(
                symbol,
                company.getName(),
                company.getCompanyId(),
                s.totalShares(),
                sold,
                founderShares,
                s.issuePrice(),
                s.issuePrice());
        stock.setFounderLockup(s.founderAccount(), Stock.FOUNDER_SHARE_LOCKUP_DAYS);
        company.setPublic(true);
        // 财报季：上市前累积的季度流水清零，避免上市后首份季报混入上市前经营数据
        company.drainQuarter();
        StockMarketManager.putStockDirect(stock);
        s.setStatus(IpoSubscription.Status.LISTED);
        AccountManager.addTransactionRecord(new TransactionRecord(
                s.escrowAccount(),
                company.getCompanyId(),
                raised,
                TransactionType.COMPANY_IPO,
                s.founderAccount(),
                company.getName() + "/" + symbol,
                sold));
        EconomySavedData.markDirty();
        return true;
    }

    /** 取消：托管里的钱按每人认购的股数原路退回。已经退过的不会再退（状态先改）。 */
    static boolean cancel(IpoSubscription s, String reason) {
        if (s.status() != IpoSubscription.Status.OPEN) return false;
        for (Map.Entry<UUID, Long> e : s.subscriptions().entrySet()) {
            long refund =
                    MathUtil.multiplyExactOrNegative1(s.issuePrice(), (int) Math.min(Integer.MAX_VALUE, e.getValue()));
            if (refund <= 0 || AccountManager.getBalance(s.escrowAccount()) < refund) continue;
            if (AccountManager.moveFunds(s.escrowAccount(), e.getKey(), refund))
                AccountManager.addTransactionRecord(new TransactionRecord(
                        s.escrowAccount(),
                        e.getKey(),
                        refund,
                        TransactionType.IPO_REFUND,
                        e.getKey(),
                        reason,
                        e.getValue()));
        }
        s.setStatus(IpoSubscription.Status.CANCELLED);
        EconomySavedData.markDirty();
        return true;
    }

    private static void trimClosed() {
        int closed = 0;
        for (IpoSubscription s : SUBSCRIPTIONS.values()) if (s.status() != IpoSubscription.Status.OPEN) closed++;
        var it = SUBSCRIPTIONS.values().iterator();
        while (closed > MAX_CLOSED && it.hasNext()) {
            if (it.next().status() != IpoSubscription.Status.OPEN) {
                it.remove();
                closed--;
            }
        }
    }

    // ---------------------------------------------------------------- 存档

    public static void restoreDirect(IpoSubscription s) {
        if (s != null) SUBSCRIPTIONS.put(s.id(), s);
    }

    public static void clearDirect() {
        SUBSCRIPTIONS.clear();
    }

    // ---------------------------------------------------------------- 股票代码

    /** 股票代码：公司名前 4 个字符里的字母大写；字母不够时补字母。 */
    private static String generateSymbol(String companyName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, companyName.length()); i++) {
            char c = companyName.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        while (sb.length() < 4) {
            sb.append((char) ('A' + (System.nanoTime() % 26)));
        }
        return sb.toString().substring(0, 4);
    }

    private static String generateUniqueSymbol(String companyName) {
        String base = generateSymbol(companyName);
        String symbol = base;
        int suffix = 1;
        while (StockMarketManager.getStock(symbol) != null) {
            String suffixText = String.valueOf(suffix++);
            int prefixLength = Math.max(1, 4 - suffixText.length());
            symbol = base.substring(0, Math.min(prefixLength, base.length())) + suffixText;
        }
        return symbol;
    }
}
