package finance.simple;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.bank.BankAccountStatus;
import finance.bank.BankAccountType;
import finance.bank.BankCustomerAccount;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.bank.CustomerType;
import finance.bank.PublicBankViewService;
import finance.cash.CashService;
import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyIPOService;
import finance.company.CompanyManagementAction;
import finance.company.CompanyManagementService;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.cycle.EconomyCycleService;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.InventoryUtil;
import finance.util.MoneyFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 简单模式金融中心（首页、买卖、我的公司、投资）的服务端操作。
 * 只是把已有的市场、公司、股票、银行功能包成“一键”操作，并把失败原因说清楚。
 */
public final class SimpleCenterService {

    /** 三项升级各最多 5 级，合起来就是“工厂等级”0～15。 */
    public static final int MAX_FACTORY_LEVEL = 15;
    /** 一键上市：发行 4000 股（占总股本 40%），发行价按估值折算。 */
    static final long QUICK_IPO_SHARES = 4_000L;
    private static final String STARTER_TAG = "finance_starter_granted";

    private SimpleCenterService() {}

    /** 操作结果；moneyDelta 是钱包变化，客户端据此飘字和播放音效。 */
    public record Result(boolean success, String message, long moneyDelta) {
        static Result ok(String message, long delta) { return new Result(true, message, delta); }
        static Result fail(String message) { return new Result(false, message, 0); }
    }

    // ------------------------------------------------------------ 买卖

    /** 把背包里的东西直接卖给市场。市场今天收不完的部分原样还给玩家。 */
    public static Result quickSell(ServerPlayer player, String commodityId, int quantity) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null || commodity.getItemId() == null || commodity.getItemId().isEmpty()) {
            return Result.fail("这种东西市场不收。");
        }
        String name = commodity.getDisplayName();
        int owned = InventoryUtil.countItemInInventory(player, commodity.getItemId());
        int wanted = Math.min(Math.max(0, quantity), owned);
        if (wanted <= 0) return Result.fail("背包里没有" + name + "。");
        String blocked = NpcMarketMaker.npcBuyBlockReason(commodityId);
        if (blocked != null) return Result.fail(name + "：" + blocked);
        UUID id = player.getUUID();
        if (!CommodityInventoryManager.canAddCommodity(id, commodityId, wanted)) {
            return Result.fail("一次卖太多了，少卖一点试试。");
        }
        int removed = InventoryUtil.removeFromInventory(player, commodity.getItemId(), wanted);
        if (removed <= 0) return Result.fail("背包里没有" + name + "。");
        if (!CommodityInventoryManager.addCommodity(id, commodityId, removed)) {
            InventoryUtil.addToInventory(player, commodity.getItemId(), removed);
            return Result.fail("一次卖太多了，少卖一点试试。");
        }
        long before = AccountManager.getBalance(id);
        int filled = NpcMarketMaker.npcBuyFilled(id, commodityId, removed);
        int leftover = removed - Math.max(0, filled);
        if (leftover > 0 && CommodityInventoryManager.removeCommodity(id, commodityId, leftover)) {
            InventoryUtil.addToInventory(player, commodity.getItemId(), leftover);
        }
        if (filled <= 0) return Result.fail(name + "：市场现在不收，东西已经还给你。");
        long earned = AccountManager.getBalance(id) - before;
        finance.advancement.FinanceAdvancementTriggers.trigger(player, "first_trade");
        String message = "卖出 " + filled + " 个" + name + "，得 " + MoneyFormat.pounds(earned);
        if (leftover > 0) message += "。市场今天只收这么多，剩下 " + leftover + " 个还给你";
        return Result.ok(message + "。", earned);
    }

    /** 从市场买东西，直接放进背包（背包满了掉在脚下）。 */
    public static Result quickBuy(ServerPlayer player, String commodityId, int quantity) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null || commodity.getItemId() == null || commodity.getItemId().isEmpty()) {
            return Result.fail("市场不卖这种东西。");
        }
        String name = commodity.getDisplayName();
        if (quantity <= 0) return Result.fail("数量必须大于 0。");
        String blocked = NpcMarketMaker.npcSellBlockReason(commodityId);
        if (blocked != null) return Result.fail(name + "：" + blocked);
        MarketPrice price = NpcMarketMaker.getMarketPrice(commodityId);
        UUID id = player.getUUID();
        if (price != null && AccountManager.getBalance(id) < price.getAskPrice()) {
            return Result.fail("钱不够，" + name + "一个要 " + MoneyFormat.pounds(price.getAskPrice()) + "。");
        }
        int affordable = price == null || price.getAskPrice() <= 0 ? quantity
                : (int) Math.min(quantity, AccountManager.getBalance(id) / price.getAskPrice());
        long before = AccountManager.getBalance(id);
        int filled = NpcMarketMaker.npcSellFilled(id, commodityId, Math.max(1, affordable));
        if (filled <= 0) return Result.fail(name + "：现在买不到，稍后再试。");
        if (CommodityInventoryManager.removeCommodity(id, commodityId, filled)) {
            InventoryUtil.addToInventory(player, commodity.getItemId(), filled);
        }
        long spent = before - AccountManager.getBalance(id);
        String message = "买入 " + filled + " 个" + name + "，花 " + MoneyFormat.pounds(spent);
        if (filled < quantity) message += "（钱或货只够这么多）";
        return Result.ok(message + "。", -spent);
    }

    // ------------------------------------------------------------ 挂单

    /** 挂单：卖单直接用背包里的货，买单先冻结要花的钱。市场价格到了就自动成交（见 MarketOrderFiller）。 */
    public static Result placeOrder(ServerPlayer player, String commodityId, boolean sell, long price, int quantity) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) return Result.fail("这种东西不能挂单。");
        if (price <= 0 || quantity <= 0) return Result.fail("价格和数量都要大于 0。");
        String name = commodity.getDisplayName();
        UUID id = player.getUUID();
        if (sell) {
            if (commodity.getItemId() == null || commodity.getItemId().isEmpty()) return Result.fail("这种东西不能挂单。");
            int owned = InventoryUtil.countItemInInventory(player, commodity.getItemId());
            if (owned < quantity) return Result.fail("背包里只有 " + owned + " 个" + name + "。");
            if (!CommodityInventoryManager.canAddCommodity(id, commodityId, quantity)) return Result.fail("一次挂太多了，少挂一点。");
            int removed = InventoryUtil.removeFromInventory(player, commodity.getItemId(), quantity);
            if (removed < quantity || !CommodityInventoryManager.addCommodity(id, commodityId, removed)) {
                InventoryUtil.addToInventory(player, commodity.getItemId(), removed);
                return Result.fail("挂单失败，东西已还给你。");
            }
            if (!finance.market.MarketManager.placeOrder(new finance.market.Order(id, commodityId, finance.market.OrderType.SELL, price, quantity))) {
                if (CommodityInventoryManager.removeCommodity(id, commodityId, quantity)) {
                    InventoryUtil.addToInventory(player, commodity.getItemId(), quantity);
                }
                return Result.fail("挂单失败，东西已还给你。");
            }
            return Result.ok("已挂卖单：" + quantity + " 个" + name + "，每个 " + MoneyFormat.pounds(price)
                    + "。市场收购价涨到这个数就自动卖出。", 0);
        }
        long total;
        try {
            total = Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException e) {
            return Result.fail("金额太大了。");
        }
        if (AccountManager.getBalance(id) < total) return Result.fail("钱不够，这张买单要先冻结 " + MoneyFormat.pounds(total) + "。");
        if (!finance.market.MarketManager.placeOrder(new finance.market.Order(id, commodityId, finance.market.OrderType.BUY, price, quantity))) {
            return Result.fail("挂单失败。");
        }
        return Result.ok("已挂买单：" + quantity + " 个" + name + "，每个 " + MoneyFormat.pounds(price)
                + "，先冻结 " + MoneyFormat.pounds(total) + "。市场出售价跌到这个数就自动买入。", 0);
    }

    public static Result cancelOrder(ServerPlayer player, UUID orderId) {
        if (orderId == null) return Result.fail("请求无效。");
        if (!finance.market.MarketManager.cancelOrder(orderId, player.getUUID())) return Result.fail("这张挂单已经成交或不存在。");
        return Result.ok("已撤单，冻结的钱或货已经退回。卖单的货在“暂存”里，可以取回背包。", 0);
    }

    /** 把暂存在市场账上的某种货全部取回背包（背包满了掉在脚下）。 */
    public static Result takeGoods(ServerPlayer player, String commodityId) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null || commodity.getItemId() == null || commodity.getItemId().isEmpty()) return Result.fail("这种东西取不出来。");
        UUID id = player.getUUID();
        int amount = Math.min(64 * 36, CommodityInventoryManager.getCommodityAmount(id, commodityId));
        if (amount <= 0) return Result.fail("没有暂存的" + commodity.getDisplayName() + "。");
        if (!CommodityInventoryManager.removeCommodity(id, commodityId, amount)) return Result.fail("取回失败。");
        InventoryUtil.addToInventory(player, commodity.getItemId(), amount);
        return Result.ok("取回 " + amount + " 个" + commodity.getDisplayName() + "。", 0);
    }

    // ------------------------------------------------------------ 锁价合同（期货）

    private static long listedDay = Long.MIN_VALUE;

    /** 每种市场商品都保持一份 7 天后到期的合约，玩家才有东西可押（以前要管理员手动开）。每个 MC 日只检查一次。 */
    public static synchronized void ensureFuturesListed(long day) {
        if (!FinanceConfig.futuresEnabled() || day < 0 || day == listedDay) return;
        listedDay = day;
        for (String commodityId : NpcMarketMaker.getAllMarketPrices().keySet()) {
            if (finance.futures.FuturesMarketManager.contracts().size() >= finance.futures.FuturesMarketManager.MAX_CONTRACTS - 8) return;
            if (!finance.futures.FuturesMarketManager.hasLiveContractForCommodity(commodityId)) {
                finance.futures.FuturesMarketManager.createStandard(commodityId, day, day + 6, day + 7);
            }
        }
    }

    /** 锁价：sell=true 锁定卖价（担心降价），false 锁定买价（担心涨价）。保证金不够时自动从钱包补。 */
    public static Result lockPrice(ServerPlayer player, UUID contractId, boolean sell, long quantity) {
        finance.futures.FuturesContract c = contractId == null ? null : finance.futures.FuturesMarketManager.contract(contractId);
        if (c == null || !c.canTrade()) return Result.fail("这份合同现在不能交易。");
        if (quantity <= 0) return Result.fail("份数要大于 0。");
        long[] quote = finance.futures.FuturesMarketMaker.getQuote(contractId);
        if (quote == null) return Result.fail("市场暂时不报价，稍后再试。");
        long price = sell ? quote[0] : quote[1];
        UUID id = player.getUUID();
        long need = finance.futures.FuturesMath.margin(price, c.contractSize(), quantity, FinanceConfig.futuresInitialMarginBps());
        if (need < 0) return Result.fail("金额太大了。");
        long shortfall = need - finance.futures.MarginManager.availableToWithdraw(id);
        long deposited = 0;
        if (shortfall > 0) {
            if (AccountManager.getBalance(id) < shortfall) return Result.fail("钱不够交保证金，需要 " + MoneyFormat.pounds(shortfall) + "。");
            if (!finance.futures.MarginManager.deposit(id, shortfall)) return Result.fail("保证金转入失败。");
            deposited = shortfall;
        }
        var result = finance.futures.FuturesMarketManager.place(id, contractId,
                sell ? finance.futures.FuturesOrderSide.SELL : finance.futures.FuturesOrderSide.BUY, price, quantity);
        if (!result.success()) return Result.fail(result.message());
        Commodity commodity = CommodityRegistry.getCommodity(c.commodityId());
        String name = commodity == null ? c.commodityId() : commodity.getDisplayName();
        String what = sell ? "锁定卖价" : "锁定买价";
        return Result.ok(what + "：" + quantity + " 份" + name + "（每份 " + c.contractSize() + " 个），每个 "
                + MoneyFormat.pounds(price) + "，第 " + c.maturityDay() + " 天按差价结算。"
                + (deposited > 0 ? "已从钱包转入保证金 " + MoneyFormat.pounds(deposited) + "。" : ""), -deposited);
    }

    /** 提前平掉一份合同：按当前报价反向成交。 */
    public static Result closeLock(ServerPlayer player, UUID contractId) {
        UUID id = player.getUUID();
        finance.futures.FuturesPosition position = contractId == null ? null : finance.futures.MarginManager.findPosition(id, contractId);
        if (position == null || position.signedQuantity() == 0) return Result.fail("你没有这份合同。");
        long[] quote = finance.futures.FuturesMarketMaker.getQuote(contractId);
        if (quote == null) return Result.fail("市场暂时不报价，稍后再试。");
        boolean wasSeller = position.signedQuantity() < 0;
        long quantity = Math.abs(position.signedQuantity());
        var result = finance.futures.FuturesMarketManager.place(id, contractId,
                wasSeller ? finance.futures.FuturesOrderSide.BUY : finance.futures.FuturesOrderSide.SELL,
                wasSeller ? quote[1] : quote[0], quantity);
        if (!result.success()) return Result.fail(result.message());
        return Result.ok("已平掉合同。不用的保证金可以点“取回保证金”。", 0);
    }

    public static Result withdrawMargin(ServerPlayer player) {
        UUID id = player.getUUID();
        long free = finance.futures.MarginManager.availableToWithdraw(id);
        if (free <= 0) return Result.fail("没有可以取回的保证金（还有合同在身上时要留着）。");
        if (!finance.futures.MarginManager.withdraw(id, free)) return Result.fail("取回失败。");
        return Result.ok("取回保证金 " + MoneyFormat.pounds(free) + "。", free);
    }

    // ------------------------------------------------------------ 公司

    /** 工厂等级 = 生产线 + 仓储 + 管理三项等级之和。 */
    public static int factoryLevel(Company company) {
        return company.getProductionLevel() + company.getStorageLevel() + company.getManagementLevel();
    }

    /** 下一次升级的是哪一项：等级最低的那项（同级时按生产线、仓储、管理的顺序）；已满级返回 null。 */
    static CompanyManagementAction nextUpgrade(Company company) {
        int p = company.getProductionLevel(), s = company.getStorageLevel(), m = company.getManagementLevel();
        int min = Math.min(p, Math.min(s, m));
        if (min >= MAX_FACTORY_LEVEL / 3) return null;
        if (p == min) return CompanyManagementAction.UPGRADE_PRODUCTION;
        if (s == min) return CompanyManagementAction.UPGRADE_STORAGE;
        return CompanyManagementAction.UPGRADE_MANAGEMENT;
    }

    /** 下一次升级要花多少；满级返回 0。 */
    public static long nextUpgradeCost(Company company) {
        CompanyManagementAction next = nextUpgrade(company);
        if (next == null) return 0;
        return company.getUpgradeCost(switch (next) {
            case UPGRADE_PRODUCTION -> "PRODUCTION";
            case UPGRADE_STORAGE -> "STORAGE";
            default -> "MANAGEMENT";
        });
    }

    public static Result upgradeFactory(UUID playerId) {
        Company company = CompanyManager.getCompanyByOwner(playerId);
        if (company == null) return Result.fail("你还没有公司。");
        CompanyManagementAction next = nextUpgrade(company);
        if (next == null) return Result.fail("工厂已经升到最高级了。");
        long cost = nextUpgradeCost(company);
        int before = factoryLevel(company);
        CompanyManagementService.Result result = CompanyManagementService.apply(playerId, company, next, null, 0, 0);
        if (!result.success()) {
            return Result.fail(AccountManager.getBalance(playerId) < cost
                    ? "钱不够，升级要 " + MoneyFormat.pounds(cost) + "。" : result.message());
        }
        return Result.ok("工厂升到 " + (before + 1) + " 级，花了 " + MoneyFormat.pounds(cost) + "。", -cost);
    }

    /** 一键上市：发行价按估值折算，玩家不用自己定价。 */
    public static Result quickIpo(UUID playerId) {
        Company company = CompanyManager.getCompanyByOwner(playerId);
        if (company == null) return Result.fail("你还没有公司。");
        if (company.isPublic()) return Result.fail("公司已经上市了。");
        long totalShares = Math.round(QUICK_IPO_SHARES / CompanyIPOService.DEFAULT_FLOAT_RATIO);
        long price = Math.max(1, Math.min(CompanyIPOService.MAX_ISSUE_PRICE, finance.stock.StockValuation.companyValue(company) / totalShares));
        long quantity = Math.min(QUICK_IPO_SHARES, Math.max(1, CompanyIPOService.MAX_TOTAL_RAISE / price));
        long before = AccountManager.getBalance(playerId);
        CompanyIPOService.IPOResult result = CompanyIPOService.ipo(playerId, company.getCompanyId(), price, quantity);
        if (!result.success()) return Result.fail(result.message());
        return Result.ok("公司上市了！卖出 " + quantity + " 股，每股 " + MoneyFormat.pounds(price)
                + "，募到的钱进了公司账上。", AccountManager.getBalance(playerId) - before);
    }

    // ------------------------------------------------------------ 银行

    /** 简单模式只用一家银行：第一家正常营业的。 */
    public static CommercialBank mainBank() {
        BankingManager.ensureDefaultBanks();
        return BankingManager.banks().values().stream().filter(CommercialBank::acceptsNewBusiness)
                .min(Comparator.comparing(CommercialBank::code)).orElse(null);
    }

    public static BankCustomerAccount demandAccount(UUID player, CommercialBank bank, long day, boolean create) {
        return bank == null ? null : BankingManager.demandAccount(bank.id(), player, CustomerType.PLAYER, day, create);
    }

    public static Result bankDeposit(ServerPlayer player, long amount) {
        CommercialBank bank = mainBank();
        if (bank == null) return Result.fail("现在没有银行营业。");
        if (amount <= 0) return Result.fail("存入金额要大于 0。");
        long day = EconomyCycleService.currentMcDay(player.server);
        BankingManager.Result result = BankingManager.depositPlayer(player.getUUID(), bank.id(), amount, day);
        if (!result.success()) return Result.fail(result.message());
        return Result.ok("存入银行 " + MoneyFormat.pounds(amount) + "，每天都有利息。", -amount);
    }

    public static Result bankWithdraw(ServerPlayer player, long amount) {
        CommercialBank bank = mainBank();
        long day = EconomyCycleService.currentMcDay(player.server);
        BankCustomerAccount account = demandAccount(player.getUUID(), bank, day, false);
        if (account == null || account.available() <= 0) return Result.fail("银行里没有你的存款。");
        long take = Math.min(amount, account.available());
        if (take <= 0) return Result.fail("取出金额要大于 0。");
        BankingManager.Result result = BankingManager.withdrawPlayer(player.getUUID(), account.id(), take, day);
        if (!result.success()) return Result.fail(result.message());
        return Result.ok("从银行取出 " + MoneyFormat.pounds(take) + "。", take);
    }

    // ------------------------------------------------------------ 启动资金

    /** 每名玩家第一次打开金融中心时送一次启动资金；返回实际送出的金额（没送为 0）。 */
    public static long grantStarterIfNeeded(ServerPlayer player) {
        long amount = FinanceConfig.simpleStarterGrant();
        CompoundTag data = player.getPersistentData();
        if (amount <= 0 || data.getBoolean(STARTER_TAG)) return 0;
        if (!AccountManager.deposit(player.getUUID(), amount)) return 0;
        data.putBoolean(STARTER_TAG, true);
        AccountManager.addTransactionRecord(new TransactionRecord(CashService.ISSUER, player.getUUID(), amount,
                TransactionType.STARTER_GRANT, player.getUUID(), "启动资金", 1));
        return amount;
    }

    // ------------------------------------------------------------ 首页数据

    /**
     * 首页和其他三页需要、但金融中心快照里没有的数据。
     * extras：这个玩家现在能用的进阶玩法（组名和单项名）；locked：还没解锁、做到进度就能解锁的组；
     * unlockedNow：刚解锁、要弹出介绍的组；cards：机会卡；bets：押注；futuresMarginBps：商品押注每份要交的保证金比例。
     */
    public record Status(long bankBalance, int bankRateBps, int factoryLevel, long nextUpgradeCost,
                         long companyValuation, long ipoMinValuation, long stockUnlockBalance,
                         String advancedMode, List<String> extras, List<String> locked, List<String> unlockedNow,
                         List<SimpleOpportunityService.Card> cards, List<String> weather,
                         List<String> summary, List<SimpleInvestService.Product> products,
                         List<SimpleInvestService.Holding> holdings, SimpleInvestService.MarginInfo margin,
                         List<SimpleBetService.Bet> bets, int futuresMarginBps,
                         SimpleBossService.BossInfo boss, finance.network.PlayerServicesResponsePacket.Cash cash,
                         List<Valuation> valuations, List<Long> worth, List<String> ranks, String betStats) {}

    /** 一只股票“股价怎么来的”：家底、日利润、倍数（× 10）、总股数。 */
    public record Valuation(String symbol, long netAssets, long dailyProfit, int multipleX10, long shares) {}

    private static List<Valuation> cachedValuations = List.of();
    private static long valuationsTick = Long.MIN_VALUE;

    /** 估值对所有玩家都一样，开着金融中心时每秒会刷新好几次：一秒（20 tick）内复用上次算的。 */
    static synchronized List<Valuation> valuations(long tick) {
        if (tick >= valuationsTick && tick - valuationsTick < 20) return cachedValuations;
        valuationsTick = tick;
        cachedValuations = valuations();
        return cachedValuations;
    }

    static List<Valuation> valuations() {
        List<Valuation> out = new ArrayList<>();
        for (finance.stock.Stock stock : finance.stock.StockMarketManager.getListedStocks()) {
            Company c = CompanyManager.getCompany(stock.getCompanyId());
            if (c == null) continue;
            var b = finance.stock.StockValuation.of(c);
            out.add(new Valuation(stock.getSymbol(), b.netAssets(), b.dailyProfit(), b.multipleX10(), stock.getTotalShares()));
            if (out.size() >= 32) break;
        }
        return out;
    }

    public static Status status(ServerPlayer player) {
        return status(player, List.of());
    }

    /** summary 只在打开金融中心时带上（“上次打开以来”的小结），刷新时为空。 */
    public static Status status(ServerPlayer player, List<String> summary) {
        UUID id = player.getUUID();
        long day = EconomyCycleService.currentMcDay(player.server);
        if (!SimpleUnlocks.disabled("futures")) ensureFuturesListed(day);
        // 离线时被强制结束、到期结算的押注先补记战绩，再把闲着的押金退回钱包
        SimpleBetService.applyPending(player);
        SimpleBetService.sweepIdle(id);
        CommercialBank bank = mainBank();
        BankCustomerAccount account = demandAccount(id, bank, day, false);
        long bankBalance = account != null && account.status() == BankAccountStatus.ACTIVE
                && account.type() == BankAccountType.DEMAND_DEPOSIT ? account.available() : 0;
        int rate = bank == null ? 0 : PublicBankViewService.crop(bank, false).demandRateBps();
        Company company = CompanyManager.getCompanyByOwner(id);

        java.util.EnumSet<SimpleUnlocks.Group> unlocked = SimpleUnlocks.unlocked(player);
        java.util.Set<String> instant = FinanceConfig.simpleExtras(), disabled = FinanceConfig.simpleDisabled();
        java.util.function.Predicate<String> allowed = name -> SimpleUnlocks.allowed(unlocked, instant, disabled, name);
        List<String> unlockedNow = SimpleUnlocks.takeNew(player, unlocked).stream().map(g -> g.id).toList();
        List<SimpleInvestService.Holding> holdings = SimpleInvestService.holdings(player);
        SimpleInvestService.MarginInfo margin = SimpleInvestService.marginInfo(id);
        SimpleBossService.BossInfo boss = SimpleBossService.info(player);
        return new Status(bankBalance, rate,
                company == null ? 0 : factoryLevel(company),
                company == null ? 0 : nextUpgradeCost(company),
                company == null ? 0 : finance.stock.StockValuation.companyValue(company),
                FinanceConfig.simpleIpoMinValuation(), FinanceConfig.simpleStockUnlockBalance(),
                FinanceConfig.simpleAdvancedMode(),
                SimpleUnlocks.allowedNames(unlocked, instant, disabled), SimpleUnlocks.lockedIds(unlocked, instant, disabled), unlockedNow,
                SimpleOpportunityService.cards(player, allowed, boss, margin, !holdings.isEmpty(), AccountManager.getBalance(id)),
                weather(player), summary,
                SimpleInvestService.products(allowed), holdings, margin,
                SimpleBetService.bets(player), FinanceConfig.futuresInitialMarginBps(),
                boss,
                finance.playerservice.PlayerServicesService.cash(id, new finance.cash.PlayerCashPurse(player), day),
                valuations(player.server.getTickCount()),
                SimpleWealthService.history(player, day, SimpleWealthService.netWorth(id)),
                SimpleWealthService.rankLines(player.server, id),
                SimpleBetService.stats(player).line());
    }

    // ------------------------------------------------------------ 经济天气

    /** 利率、物价、央行动向，每条都配一句“对你意味着什么”。 */
    static List<String> weather(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        int rate = finance.policy.MonetaryPolicyService.benchmarkRateBasisPoints();
        String rateMeaning = rate >= 500 ? "存钱利息高，借钱贵。" : rate <= 200 ? "借钱便宜，存钱利息少。" : "利率适中。";
        out.add("利率 " + pct(rate) + "：" + rateMeaning);
        int inflation = finance.centralbank.CentralBankService.inflationBps();
        int target = finance.centralbank.CentralBankService.inflationTargetBps();
        String priceMeaning = inflation > target + 100 ? "东西在变贵：手里的货会升值，放着的现金会变不值钱。"
                : inflation < 0 ? "东西在变便宜：现金更值钱，囤货可能亏。" : "物价稳定。";
        out.add("物价一年涨 " + pct(inflation) + "（央行目标 " + pct(target) + "）：" + priceMeaning);
        finance.policy.PolicyMeetingService.PolicyOutlook outlook = finance.policy.PolicyMeetingService.outlook();
        long today = EconomyCycleService.currentMcDay(player.server);
        String when = outlook.nextMeetingDay() < 0 ? "日程未定" : outlook.nextMeetingDay() <= today ? "今天"
                : (outlook.nextMeetingDay() - today) + " 天后";
        String lean = outlook.recommendedBps() > outlook.currentBps() ? "可能加息：存款利息会涨，贷款会变贵。"
                : outlook.recommendedBps() < outlook.currentBps() ? "可能降息：贷款会变便宜，存款利息会降。" : "大概率不变。";
        out.add("央行下次开会：" + when + "，" + lean);
        List<finance.centralbank.CentralBankService.Decision> decisions = finance.centralbank.CentralBankService.decisions();
        if (!decisions.isEmpty()) {
            finance.centralbank.CentralBankService.Decision d = decisions.get(decisions.size() - 1);
            out.add("上次开会（第 " + d.day() + " 天）：利率 " + pct(d.rateBefore()) + " → " + pct(d.rateAfter()) + "。");
        }
        out.add(reserveLine());
        out.add(moneyLine());
        return out;
    }

    /** 流通中的钱和最近 7 天的变化。 */
    static String moneyLine() {
        var m = finance.centralbank.MoneySupplyService.summary();
        String head = "流通中的钱 " + MoneyFormat.compact(m.circulating()) + "（账户 " + MoneyFormat.compact(m.accounts())
                + "、公司 " + MoneyFormat.compact(m.companies()) + "、央行钞票 " + MoneyFormat.compact(m.notes()) + "）";
        if (!m.hasHistory()) return head + "。钱都是央行发的，钱比货涨得快，物价就会涨。";
        return head + "，最近 7 天" + (m.change7d() >= 0 ? "多了 " : "少了 ") + MoneyFormat.compact(Math.abs(m.change7d()))
                + "。钱比货涨得快，物价就会涨，央行会加息把钱收回去。";
    }

    /** 央行储备投放到第几档、下一档还差多少成交额。 */
    static String reserveLine() {
        int tier = finance.centralbank.ReserveService.unlockedTier();
        List<String> next = tier >= 3 ? List.of() : finance.centralbank.ReserveService.namesOfTier(tier + 1);
        String now = "央行储备在供应" + (tier == 1 ? "第 1 档原材料" : tier == 2 ? "第 1、2 档原材料" : "全部三档原材料") + "，每天只补少量货。";
        if (next.isEmpty()) return now;
        return now + "全服再成交 " + MoneyFormat.pounds(finance.centralbank.ReserveService.valueToNextTier()) + "，央行开始供应"
                + String.join("、", next.subList(0, Math.min(4, next.size()))) + "。";
    }

    static String pct(int bps) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", bps / 100.0);
    }

    // ------------------------------------------------------------ 上次打开以来

    private static final String LAST_OPEN_TAG = "finance_last_open";

    /** “上次打开以来”收入和支出的小结；第一次打开或期间没有记账时返回空表。会把“上次打开时间”更新为现在。 */
    public static List<String> summarySinceLastOpen(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        long last = data.getLong(LAST_OPEN_TAG);
        data.putLong(LAST_OPEN_TAG, now.toEpochSecond(java.time.ZoneOffset.UTC));
        if (last <= 0) return List.of();
        java.time.LocalDateTime since = java.time.LocalDateTime.ofEpochSecond(last, 0, java.time.ZoneOffset.UTC);
        return summarize(player.getUUID(), AccountManager.getTransactions(), since);
    }

    /** 从最新的记录往前数到 since 为止，按“卖东西 / 分红 / 其他收入 / 支出”汇总。 */
    static List<String> summarize(UUID player, List<TransactionRecord> records, java.time.LocalDateTime since) {
        long sales = 0, dividends = 0, otherIn = 0, spent = 0;
        for (int i = records.size() - 1; i >= 0; i--) {
            TransactionRecord r = records.get(i);
            if (r.getTimestamp() == null || !r.getTimestamp().isAfter(since)) break;
            long amount = Math.max(0, r.getAmount());
            boolean in = player.equals(r.getTo()), out = player.equals(r.getFrom());
            if (in == out) continue;
            if (out) {
                spent += amount;
                continue;
            }
            switch (r.getType()) {
                case COMMODITY_SELL, NPC_BUY, MARKET_TRADE, STOCK_SELL -> sales += amount;
                case DIVIDEND, BANK_DIVIDEND -> dividends += amount;
                default -> otherIn += amount;
            }
        }
        if (sales + dividends + otherIn + spent == 0) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add("收入 " + MoneyFormat.signed(sales + dividends + otherIn) + "，支出 " + MoneyFormat.pounds(spent) + "。");
        if (sales > 0) lines.add("卖东西 +" + MoneyFormat.pounds(sales));
        if (dividends > 0) lines.add("分红 +" + MoneyFormat.pounds(dividends));
        if (otherIn > 0) lines.add("其他收入 +" + MoneyFormat.pounds(otherIn));
        return lines;
    }
}
