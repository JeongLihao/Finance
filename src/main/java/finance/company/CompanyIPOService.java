package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.MarketMakerLedger;
import finance.market.NpcMarketMaker;
import finance.money.MoneyEndpoint;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferService;
import finance.npcinvestor.NpcInvestorArchetype;
import finance.npcinvestor.NpcInvestorManager;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.stock.StockValuation;
import finance.util.MathUtil;
import finance.util.MoneyFormat;
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

    /** 所有认购（进行中 + 最近结束的），按认购 id 索引；LinkedHashMap 保持发起顺序，裁剪时先丢最旧的。 */
    private static final Map<UUID, IpoSubscription> SUBSCRIPTIONS = new LinkedHashMap<>();
    /** 已结束的认购最多留这么多条（面板和测试用），之后按时间丢掉最旧的。 */
    static final int MAX_CLOSED = 64;

    private CompanyIPOService() {}

    /** 发起 / 认购的结果：message 直接显示给玩家，subscription 失败时为 null。 */
    public record Result(boolean success, String message, IpoSubscription subscription) {
        static Result fail(String message) {
            return new Result(false, message, null);
        }

        /** 发起成功：提示里带上发行量、价格、认购期和最低认购股数。 */
        static Result started(IpoSubscription s, long fee) {
            return new Result(
                    true,
                    "上市认购开始了：发行 " + s.issueQuantity() + " 股，每股 " + MoneyFormat.pounds(s.issuePrice())
                            + "，认购期 " + (s.endDay() - s.startDay()) + " 天。认满 "
                            + s.minimumShares(FinanceConfig.ipoMinFillBasisPoints()) + " 股才上市，不够就退款"
                            + (fee > 0 ? "（上市费 " + MoneyFormat.pounds(fee) + " 不退）" : "") + "。",
                    s);
        }

        /** 认购成功：提示由调用方拼好。 */
        static Result subscribed(IpoSubscription s, String message) {
            return new Result(true, message, s);
        }
    }

    // ---------------------------------------------------------------- 查询

    /** 全部认购（只读视图），存档用。 */
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

    /** 所有进行中的认购（新的列表，可随意修改），界面和机会卡用。 */
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
        // 估值门槛只管玩家公司；NPC 竞争公司由 RivalService 按自己的条件（现金、利润、年龄）决定
        long minValuation = FinanceConfig.simpleIpoMinValuation();
        long valuation = StockValuation.companyValue(company);
        if (valuation < minValuation) {
            return Result.fail("公司估值要达到 " + MoneyFormat.pounds(minValuation) + " 才能上市，现在是 "
                    + MoneyFormat.pounds(valuation) + "（家底加最近的利润，借来的钱不算）。");
        }
        // 先查余额再开认购，避免开了认购才发现付不起上市费、又得撤销
        long fee = Math.max(0, FinanceConfig.ipoFee());
        if (fee > 0 && AccountManager.getBalance(requesterId) < fee)
            return Result.fail("上市费不够，需要 " + MoneyFormat.pounds(fee) + "。");
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
            Company company, UUID founder, long price, long quantity, long day, MoneyEndpoint payer, long fee) {
        IpoSubscription s = open(company, founder, price, quantity, day);
        if (fee > 0 && !payFee(payer, founder, company, fee)) {
            cancel(s, "上市费扣款失败");
            return Result.fail("上市费扣款失败。");
        }
        return Result.started(s, fee);
    }

    // ---------------------------------------------------------------- 定价与金额

    /** 发行 {@code issueQuantity} 股时的总股本：发行量 ÷ 流通比例，至少等于发行量。 */
    public static long totalSharesFor(long issueQuantity) {
        return Math.max(issueQuantity, Math.round(issueQuantity / DEFAULT_FLOAT_RATIO));
    }

    /** 建议发行价：公司估值 ÷ 总股本（即每股估值），限制在 1～{@link #MAX_ISSUE_PRICE}。一键上市和竞争公司共用。 */
    public static long suggestedPrice(Company company, long issueQuantity) {
        long perShare = StockValuation.companyValue(company) / Math.max(1, totalSharesFor(issueQuantity));
        return Math.max(1, Math.min(MAX_ISSUE_PRICE, perShare));
    }

    /** 价格 × 股数；股数超过 int 范围按 int 上限算，乘法溢出返回 −1。 */
    static long amount(long price, long shares) {
        return MathUtil.multiplyExactOrNegative1(price, (int) Math.min(Integer.MAX_VALUE, shares));
    }

    /** 玩家和无主公司共用的参数检查；返回错误提示，合法时返回 null。 */
    private static String validate(Company company, long issuePrice, long issueQuantity) {
        if (company.isPublic()) return "公司已上市，无法重复上市。";
        if (openFor(company.getCompanyId()) != null) return "公司已经在上市认购中了。";
        if (issuePrice <= 0 || issueQuantity <= 0) return "发行价和数量必须为正。";
        if (issueQuantity > MAX_ISSUE_QUANTITY) return "发行数量过多（最多 " + MAX_ISSUE_QUANTITY + "）。";
        if (issuePrice > MAX_ISSUE_PRICE) return "发行价过高（上限 " + MAX_ISSUE_PRICE + "）。";
        // 乘法溢出时 amount 返回 −1
        if (amount(issuePrice, issueQuantity) <= 0) return "募资金额过大。";
        return null;
    }

    /** 上市费从 {@code from}（玩家钱包或公司现金）付给 NPC 资金池，并记一条流水；转账失败返回 false。 */
    private static boolean payFee(MoneyEndpoint from, UUID payer, Company company, long fee) {
        if (!MoneyTransferService.transfer(from, MoneyEndpoints.account(NpcMarketMaker.NPC_UUID), fee)
                .success()) return false;
        AccountManager.addTransactionRecord(new TransactionRecord(
                payer, NpcMarketMaker.NPC_UUID, fee, TransactionType.IPO_FEE, payer, company.getName(), 0));
        return true;
    }

    /** 建一条认购并登记：总股本 = 发行量 ÷ 流通比例，认购期从今天起算；做市商随即认购预留部分。 */
    private static IpoSubscription open(Company company, UUID founder, long price, long quantity, long day) {
        long totalShares = totalSharesFor(quantity);
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
        // 托管账户以系统账户建立，不会领新手资金
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
        long cost = amount(s.issuePrice(), qty);
        if (cost <= 0 || AccountManager.getBalance(MarketMakerLedger.MASTER_UUID) < cost) return;
        // 做市商的钱同样进托管：上市成功转给公司，取消时和别人一样原路退回
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
        // 只认剩余额度以内的部分；调用方要按 accepted（或余额变化）算花了多少，不能按请求的 shares
        long accepted = Math.min(shares, s.remainingShares());
        if (accepted <= 0) return Result.fail("这次发行已经认满了。");
        long cost = amount(s.issuePrice(), accepted);
        if (cost <= 0) return Result.fail("认购金额过大。");
        if (AccountManager.getBalance(subscriber) < cost)
            return Result.fail("钱不够，认购 " + accepted + " 股要 " + MoneyFormat.pounds(cost) + "。");
        // 先扣钱成功再记股数，保证“托管余额 = 发行价 × 已认股数”
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
        return Result.subscribed(s, "认购了 " + accepted + " 股，付了 " + MoneyFormat.pounds(cost) + "，钱先放在托管里，上市成功才转给公司。");
    }

    // ---------------------------------------------------------------- 日结

    /** 每天：NPC 投资者认购，然后结算期满或已认满的认购。必须在 NPC 投资者补足资金之后调用。 */
    public static void processDay(long day) {
        boolean changed = false;
        // 遍历副本：结算时会改状态，trimClosed 还会删条目
        for (IpoSubscription s : new ArrayList<>(SUBSCRIPTIONS.values())) {
            if (s.status() != IpoSubscription.Status.OPEN) continue;
            Company company = CompanyManager.getCompany(s.companyId());
            // 公司被删或已经从别的路上市了，这次认购作废，钱退回
            if (company == null || company.isPublic()) {
                changed |= cancel(s, "公司已不存在或已上市");
                continue;
            }
            // 认购期内且没认满：NPC 投资者认购当天的份额
            if (day < s.endDay() && s.remainingShares() > 0) npcDemand(s, company, day);
            // 到期或已认满：结算（认满可以提前上市）
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
        // 每股价值按总股本摊（不是按发行量），和上市后的估值口径一致
        long perShare = StockValuation.companyValue(company) / Math.max(1, s.totalShares());
        List<NpcInvestorArchetype> willing = new ArrayList<>();
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values())
            if (s.issuePrice() <= perShare * valuationMultiple(a, company)) willing.add(a);
        if (willing.isEmpty()) return 0;
        // 按天轮换先后顺序，避免某个原型总是先认、把额度占光
        willing.sort(Comparator.comparingInt(
                a -> Math.floorMod(a.ordinal() - (int) day, NpcInvestorArchetype.values().length)));
        // 今天的目标量 = 剩余股数 ÷ 剩余天数（向上取整），最后一天就是全部剩余
        long daysLeft = Math.max(1, s.endDay() - day);
        long today = (s.remainingShares() + daysLeft - 1) / daysLeft;
        // NPC 合计持股上限，以及已经认了多少
        long combinedCap = s.totalShares() * Math.max(0, FinanceConfig.npcMaxCombinedHoldingPercent()) / 100;
        long combined = 0;
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) combined += s.subscribedBy(a.accountId());
        long bought = 0;
        for (int i = 0; i < willing.size() && today > 0; i++) {
            NpcInvestorArchetype a = willing.get(i);
            // 剩下的量在还没轮到的原型间平分（向上取整）；前面的认不满，差额自动留给后面的
            long share = (today + (willing.size() - i) - 1) / (willing.size() - i);
            // 单个原型的持股上限：总股本 × 单个上限百分比 × 原型系数
            long eachCap = Math.round(s.totalShares()
                    * Math.max(0, FinanceConfig.npcMaxHoldingPercentEach())
                    / 100.0
                    * a.holdingCapFactor());
            // 当天预算能买几股（发行价已保证 > 0）
            long affordable = AccountManager.getBalance(a.accountId()) / NPC_DAILY_BUDGET_DIVISOR / s.issuePrice();
            // 取 当天份额 / 预算 / 单个剩余上限 / 合计剩余上限 四者最小；上限已满时为负数，下面 qty > 0 会跳过
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
        // 上市步骤里任何一步不成（持股上限、公司现金上限、转账失败）都整笔取消退款，不留半成品
        return list(s, company, day) || cancel(s, "上市结算失败");
    }

    /**
     * 真正上市：托管的钱转给公司，按认购发股，建股票。先把能失败的检查全做完再动钱和股，
     * 中途失败返回 false（已转的钱会转回托管），由 {@link #settle} 取消退款。
     */
    private static boolean list(IpoSubscription s, Company company, long day) {
        long sold = s.subscribedShares();
        long raised = amount(s.issuePrice(), sold);
        // 创始人拿总股本里没卖出去的全部
        long founderShares = s.totalShares() - sold;
        if (raised <= 0 || founderShares < 0 || AccountManager.getBalance(s.escrowAccount()) < raised) return false;
        String symbol = generateUniqueSymbol(company.getName());
        // 做市商认的部分进做市商库存，不进普通持仓
        long marketMakerShares = s.subscribedBy(MarketMakerLedger.MASTER_UUID);
        Map<UUID, Long> holders = new LinkedHashMap<>(s.subscriptions());
        holders.remove(MarketMakerLedger.MASTER_UUID);
        // 预检：每个认购人和创始人都能加这么多持仓、公司现金放得下这笔钱
        for (Map.Entry<UUID, Long> e : holders.entrySet())
            if (!StockPortfolioManager.canAddHolding(e.getKey(), symbol, e.getValue())) return false;
        if (founderShares > 0 && !StockPortfolioManager.canAddHolding(s.founderAccount(), symbol, founderShares))
            return false;
        if (!company.canDeposit(raised)) return false;
        // 动钱：托管 → 公司现金
        if (!MoneyTransferService.transfer(
                        MoneyEndpoints.account(s.escrowAccount()), MoneyEndpoints.company(company), raised)
                .success()) return false;
        // 发股：认购人一次性全发，失败就把钱转回托管
        if (!holders.isEmpty() && !StockPortfolioManager.addHoldingsAtomically(holders, symbol, s.issuePrice())) {
            MoneyTransferService.transfer(
                    MoneyEndpoints.company(company), MoneyEndpoints.account(s.escrowAccount()), raised);
            return false;
        }
        // 创始人和做市商已经预检过，这里不会失败
        if (founderShares > 0)
            StockPortfolioManager.addHolding(s.founderAccount(), symbol, founderShares, s.issuePrice());
        if (marketMakerShares > 0)
            MarketMakerLedger.addInventory(MarketMakerLedger.SubMarket.STOCK, symbol, marketMakerShares);
        // 建股票：流通股 = 卖出去的（含做市商库存），创始股单独记并锁仓
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
            // 每人退 发行价 × 他认的股数，正好是他当初付进托管的钱
            long refund = amount(s.issuePrice(), e.getValue());
            // 托管不够退（正常不会发生）就跳过这一笔，钱留在托管里，不会凭空造钱
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

    /** 已结束的认购超过 {@link #MAX_CLOSED} 条时，从最早的开始删，进行中的不动。 */
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

    /** 读档时放回一条认购，不做任何校验和扣款。 */
    public static void restoreDirect(IpoSubscription s) {
        if (s != null) SUBSCRIPTIONS.put(s.id(), s);
    }

    /** 清空全部认购：读档前和服务器关闭时调用（静态状态不能带到下一个存档）。 */
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

    /** 代码重复时把末尾换成数字：ABCD → ABC1、ABC2 …… AB10，直到没被占用。 */
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
