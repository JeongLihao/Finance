package finance.playerservice;

import finance.account.AccountManager;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.bank.PlayerBankService;
import finance.bank.PublicBankViewService;
import finance.bank.TimeDeposit;
import finance.bank.TimeDepositStatus;
import finance.cash.CashPurse;
import finance.cash.CashService;
import finance.cash.PlayerCashPurse;
import finance.command.EarningsCommand;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.diagnostic.ModuleHealthRegistry;
import finance.fixedincome.TreasuryBond;
import finance.fixedincome.TreasuryBondManager;
import finance.fixedincome.TreasuryBondStatus;
import finance.fixedincome.YieldCurveService;
import finance.governance.CorporateActionManager;
import finance.governance.TakeoverService;
import finance.governance.TenderOffer;
import finance.macro.EconomyAchievements;
import finance.macro.MacroEconomyService;
import finance.macro.MacroLaborService;
import finance.macro.MacroRadar;
import finance.macro.MacroSnapshotBuilder;
import finance.macro.NewsService;
import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import finance.margin.MarginService;
import finance.network.PlayerServicesResponsePacket;
import finance.network.PlayerServicesResponsePacket.Bank;
import finance.network.PlayerServicesResponsePacket.BondHolding;
import finance.network.PlayerServicesResponsePacket.BondOffer;
import finance.network.PlayerServicesResponsePacket.Cash;
import finance.network.PlayerServicesResponsePacket.CentralBankPage;
import finance.network.PlayerServicesResponsePacket.DepositRow;
import finance.network.PlayerServicesResponsePacket.Macro;
import finance.network.PlayerServicesResponsePacket.Margin;
import finance.network.PlayerServicesResponsePacket.SqueezeRow;
import finance.network.PlayerServicesResponsePacket.TenderRow;
import finance.npcinvestor.HolderLabels;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * “更多操作”界面的服务端逻辑：把原来只能用命令完成的玩家操作（融资融券、玩家银行、要约的撤回与强制私有化、
 * 央行债券认购赎回与定存提前支取、宏观经济的工资/报纸/商情、财报查询、实体现金存取与绿宝石兑换）汇总成一份快照，并按动作调用与命令相同的服务方法。
 * 校验、扣费和留痕都在原服务里，这里只做参数解析和转发。
 */
public final class PlayerServicesService {
    public enum Action {
        MARGIN_DEPOSIT,
        MARGIN_WITHDRAW,
        MARGIN_BUY,
        MARGIN_REPAY,
        MARGIN_SHORT,
        MARGIN_COVER,
        MARGIN_REPAY_BAD_DEBT,
        BANK_CHARTER,
        BANK_RATES,
        BANK_DIVIDEND,
        TENDER_OFFER,
        TENDER_ACCEPT,
        TENDER_CANCEL,
        SQUEEZE_OUT,
        CB_BOND_SUBSCRIBE,
        CB_BOND_REDEEM,
        TIME_DEPOSIT_EARLY,
        MACRO_WAGE,
        MACRO_NEWS,
        MACRO_RADAR,
        EARNINGS_REPORT,
        CASH_DEPOSIT,
        CASH_WITHDRAW,
        EMERALD_SELL,
        EMERALD_BUY
    }

    public record Result(boolean success, String message) {
        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /** 查询类操作（商情调查、季报）的结果文字，按玩家保存到下一次查询；只在内存里，不存档。 */
    private record Detail(String title, List<String> lines) {}

    static final int CALENDAR_DAYS = 14;
    private static final int MAX_DETAILS = 256;
    private static final Map<UUID, Detail> DETAILS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Detail> eldest) {
            return size() > MAX_DETAILS;
        }
    };

    private PlayerServicesService() {}

    // ---------------------------------------------------------------- 快照

    public static PlayerServicesResponsePacket snapshot(long requestId, UUID player, long day) {
        return snapshot(requestId, player, null, day);
    }

    /** purse 为 null 时（没有在线玩家）现金页只显示汇率，随身现金和绿宝石按 0 显示。 */
    public static PlayerServicesResponsePacket snapshot(long requestId, UUID player, CashPurse purse, long day) {
        Detail detail;
        synchronized (DETAILS) {
            detail = DETAILS.getOrDefault(player, new Detail("", List.of()));
        }
        return new PlayerServicesResponsePacket(
                requestId,
                margin(player),
                bank(player),
                tenders(player),
                squeezable(player, day),
                centralBank(player),
                macro(player),
                EarningsCommand.calendarLines(day, CALENDAR_DAYS),
                detail.title(),
                detail.lines(),
                cash(player, purse, day));
    }

    public static Cash cash(UUID player, CashPurse purse, long day) {
        CashService.Quote q = CashService.quote(purse, day);
        return new Cash(
                q.enabled(),
                AccountManager.getBalance(player),
                purse == null ? 0 : purse.cashTotal(),
                purse == null ? 0 : purse.emeralds(),
                q.emeraldToCash(),
                q.cashToEmerald(),
                q.sellLeft(),
                q.buyLeft(),
                q.sellLimit(),
                q.buyLimit());
    }

    static Margin margin(UUID player) {
        MarginAccount acct = MarginPoolManager.getAccount(player);
        double ratio = MarginService.marginRatio(player);
        String ratioText = ratio == Double.MAX_VALUE ? "—（无负债）" : String.format(Locale.ROOT, "%.1f%%", ratio);
        return new Margin(
                acct.getCashCollateral(),
                acct.getFinancedDebt(),
                acct.getFinancedInterestOwed(),
                acct.getBadDebt(),
                ratioText,
                MarginService.isWarning(player),
                positions(acct.getFinancedShares()),
                positions(acct.getShortPositions()),
                AccountManager.getBalance(player));
    }

    private static String positions(Map<String, Long> map) {
        if (map.isEmpty()) return "—";
        StringBuilder b = new StringBuilder();
        map.forEach((symbol, qty) -> b.append(symbol).append("×").append(qty).append(' '));
        return b.toString().trim();
    }

    public static Bank bank(UUID player) {
        UUID bankId = PlayerBankService.bankOfOwner(player);
        CommercialBank bank = bankId == null ? null : BankingManager.bank(bankId);
        long minCapital = Math.max(1, FinanceConfig.playerBankMinCapital());
        long fee = Math.max(0, FinanceConfig.playerBankCharterFee());
        if (bank == null) return new Bank(false, "名下没有银行", "", minCapital, fee);
        var v = PublicBankViewService.crop(bank, false);
        return new Bank(
                true,
                v.code() + "「" + v.name() + "」 " + v.status() + "  权益 " + v.equity() + "  存款 " + v.deposits()
                        + "  资本充足率 " + v.capitalBps() / 100.0 + "%",
                "活期利率 " + v.demandRateBps() / 100.0 + "%  定期利率 " + v.timeRateBps() / 100.0 + "%",
                minCapital,
                fee);
    }

    static List<TenderRow> tenders(UUID player) {
        List<TenderRow> rows = new ArrayList<>();
        for (TenderOffer o : TakeoverService.openTenders()) {
            Company target = CompanyManager.getCompany(o.targetCompanyId());
            if (target == null) continue;
            long accepted =
                    o.accepted().values().stream().mapToLong(Long::longValue).sum();
            String buyer = player.equals(o.buyerId()) ? "你" : HolderLabels.label(o.buyerId(), target);
            long mine = StockPortfolioManager.getHolding(player, o.symbol()).getQuantity();
            rows.add(new TenderRow(
                    o.id(),
                    target.getName(),
                    buyer,
                    o.price(),
                    accepted,
                    o.targetShares(),
                    o.endDay(),
                    player.equals(o.buyerId()),
                    o.isSqueezeOut(),
                    mine));
        }
        return rows;
    }

    /** 持有表决权 ≥ 90%、可以发起强制私有化的上市公司。 */
    static List<SqueezeRow> squeezable(UUID player, long day) {
        List<SqueezeRow> rows = new ArrayList<>();
        for (Stock stock : StockMarketManager.getListedStocks()) {
            if (StockPortfolioManager.getHolding(player, stock.getSymbol()).getQuantity() <= 0) continue;
            double pct = TakeoverService.votingPercent(player, stock.getCompanyId(), day);
            if (pct + 1e-9 < 90.0) continue;
            Company c = CompanyManager.getCompany(stock.getCompanyId());
            if (c != null) rows.add(new SqueezeRow(c.getCompanyId(), c.getName(), (int) Math.round(pct * 10)));
        }
        return rows;
    }

    static CentralBankPage centralBank(UUID player) {
        List<BondOffer> offers = new ArrayList<>();
        for (int term : TreasuryBondManager.TERM_DAYS)
            offers.add(new BondOffer(term, YieldCurveService.treasuryYieldBasisPoints(term)));
        List<BondHolding> bonds = new ArrayList<>();
        for (TreasuryBond bond : TreasuryBondManager.bonds().values()) {
            Long principal = bond.principalByPlayer().get(player);
            if (principal == null || principal <= 0 || bond.status() != TreasuryBondStatus.ACTIVE) continue;
            bonds.add(new BondHolding(
                    bond.id(), bond.termDays(), bond.annualRateBasisPoints(), principal, bond.maturityDay()));
        }
        bonds.sort(java.util.Comparator.comparingLong(BondHolding::maturityDay));
        List<DepositRow> deposits = new ArrayList<>();
        for (TimeDeposit d : BankingManager.timeDeposits().values()) {
            if (!player.equals(d.ownerId()) || d.status() != TimeDepositStatus.ACTIVE) continue;
            CommercialBank bank = BankingManager.bank(d.bankId());
            deposits.add(new DepositRow(
                    d.id(),
                    bank == null ? "?" : bank.name(),
                    d.principal(),
                    d.lockedRateBps(),
                    d.maturityDay(),
                    d.accruedInterest()));
        }
        deposits.sort(java.util.Comparator.comparingLong(DepositRow::maturityDay));
        return new CentralBankPage(
                MacroSnapshotBuilder.centralBankRow(),
                offers,
                bonds,
                deposits,
                Math.max(0, FinanceConfig.treasuryBondMinAmount()),
                Math.max(0, FinanceConfig.treasuryBondEarlyFee()));
    }

    public static Macro macro(UUID player) {
        Company own = CompanyManager.getCompanyByOwner(player);
        if (!MacroEconomyService.active())
            return new Macro(false, own == null ? "" : own.getName(), 0, 0, NewsService.enabled(), 0);
        long wage = own == null ? 0 : MacroLaborService.wageOf(own.getCompanyId());
        return new Macro(
                true,
                own == null ? "" : own.getName(),
                wage,
                MacroLaborService.reservationWage(),
                NewsService.enabled(),
                Math.max(0, FinanceConfig.macroRadarCost()));
    }

    // ---------------------------------------------------------------- 操作

    /**
     * 执行一个动作。text 是股票代码、公司名、要约/公司/债券/定存 ID；a～d 是金额、数量等数字参数。
     * ServerPlayer 只在领报纸时需要（物品放进背包），其余动作只用 UUID。
     */
    public static Result act(
            ServerPlayer serverPlayer,
            UUID player,
            Action action,
            String text,
            long a,
            long b,
            long c,
            long d,
            long day) {
        return act(
                serverPlayer,
                serverPlayer == null ? null : new PlayerCashPurse(serverPlayer),
                player,
                action,
                text,
                a,
                b,
                c,
                d,
                day);
    }

    /** purse 是现金动作用的背包；测试里传内存实现。 */
    public static Result act(
            ServerPlayer serverPlayer,
            CashPurse purse,
            UUID player,
            Action action,
            String text,
            long a,
            long b,
            long c,
            long d,
            long day) {
        if (player == null || action == null) return Result.fail("参数无效");
        String t = text == null ? "" : text.trim();
        return switch (action) {
            case MARGIN_DEPOSIT -> margin(MarginService.depositCollateral(player, a));
            case MARGIN_WITHDRAW -> margin(MarginService.withdrawCollateral(player, a));
            case MARGIN_BUY -> needSymbol(t) ? margin(MarginService.marginBuy(player, t, a)) : Result.fail("请输入股票代码");
            case MARGIN_REPAY -> margin(MarginService.repayFinancing(player, a));
            case MARGIN_SHORT -> needSymbol(t) ? margin(MarginService.shortSell(player, t, a)) : Result.fail("请输入股票代码");
            case MARGIN_COVER -> needSymbol(t)
                    ? margin(MarginService.coverShort(player, t, Math.max(0, a)))
                    : Result.fail("请输入股票代码");
            case MARGIN_REPAY_BAD_DEBT -> margin(MarginService.repayBadDebt(player, a));
            case BANK_CHARTER -> charter(player, t, a, day);
            case BANK_RATES -> bankWrite()
                    ? bank(PlayerBankService.setSpreads(
                            player, null, (int) clampBps(a), (int) clampBps(b), (int) clampBps(c)))
                    : bankPaused();
            case BANK_DIVIDEND -> bankWrite() ? bank(PlayerBankService.dividend(player, null, a, day)) : bankPaused();
            case TENDER_OFFER -> offer(player, t, a, b, c, d, day);
            case TENDER_ACCEPT -> withUuid(t, id -> corp(TakeoverService.acceptTender(player, id, a, day, null)));
            case TENDER_CANCEL -> withUuid(t, id -> corp(TakeoverService.cancelTender(player, id, day, null)));
            case SQUEEZE_OUT -> withUuid(t, id -> corp(TakeoverService.squeezeOut(player, id, day, null)));
            case CB_BOND_SUBSCRIBE -> bond(
                    TreasuryBondManager.subscribe(player, (int) Math.max(0, Math.min(Integer.MAX_VALUE, a)), b, day));
            case CB_BOND_REDEEM -> withUuid(
                    t, id -> earlyExit(player, bond(TreasuryBondManager.earlyRedeem(player, id, day))));
            case TIME_DEPOSIT_EARLY -> withUuid(
                    t, id -> earlyExit(player, bank(BankingManager.earlyRedeemTimeDeposit(player, id, day))));
            case MACRO_WAGE -> wage(player, a);
            case MACRO_NEWS -> news(serverPlayer);
            case MACRO_RADAR -> radar(player, day);
            case EARNINGS_REPORT -> report(player, t);
            case CASH_DEPOSIT -> cash(CashService.deposit(player, purse, a));
            case CASH_WITHDRAW -> cash(CashService.withdraw(player, purse, a));
            case EMERALD_SELL -> cash(CashService.sellEmeralds(player, purse, a, day));
            case EMERALD_BUY -> cash(CashService.buyEmeralds(player, purse, a, day));
        };
    }

    private static boolean needSymbol(String symbol) {
        return !symbol.isEmpty() && symbol.length() <= 16;
    }

    private static long clampBps(long v) {
        return Math.max(0, Math.min(10_000, v));
    }

    private static Result margin(MarginService.Result r) {
        return new Result(r.success(), r.message());
    }

    private static Result bank(BankingManager.Result r) {
        return new Result(r.success(), r.message());
    }

    private static Result cash(CashService.Result r) {
        return new Result(r.success(), r.message());
    }

    private static Result corp(CorporateActionManager.Result r) {
        return new Result(r.success(), r.message());
    }

    private static Result bond(TreasuryBondManager.Result r) {
        return new Result(r.success(), r.message());
    }

    /** 提前支取定存或提前赎回央行债券成功：发隐藏成就“割肉离场”。 */
    private static Result earlyExit(UUID player, Result r) {
        if (r.success()) EconomyAchievements.award(player, EconomyAchievements.EARLY_EXIT);
        return r;
    }

    private static boolean bankWrite() {
        return ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.BANKING);
    }

    private static Result bankPaused() {
        return Result.fail("银行模块已因一致性问题暂停");
    }

    private static Result withUuid(String text, java.util.function.Function<UUID, Result> fn) {
        try {
            return fn.apply(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Result.fail("请先在列表里选中一项");
        }
    }

    /** 银行牌照：text = “代码 名称”，代码是第一个空格前的部分。 */
    private static Result charter(UUID player, String text, long capital, long day) {
        if (!bankWrite()) return bankPaused();
        int space = text.indexOf(' ');
        if (space <= 0 || space == text.length() - 1) return Result.fail("请输入“代码 名称”，例如：MYBK 我的银行");
        return bank(PlayerBankService.charter(
                player, text.substring(0, space), text.substring(space + 1).trim(), capital, day));
    }

    /** 公开收购要约：text = 目标公司名称；a 价格、b 目标股数、c 最低接受股数（0 = 等于目标）、d 天数。 */
    private static Result offer(UUID player, String name, long price, long target, long minimum, long days, long day) {
        Company company = CompanyManager.getCompanyByName(name);
        if (company == null) return Result.fail("未找到公司：" + name);
        if (days < 1 || days > 90) return Result.fail("期限应在 1～90 天");
        long min = minimum <= 0 ? target : minimum;
        return corp(TakeoverService.offerTender(
                player,
                company.getCompanyId(),
                price,
                target,
                min,
                (int) days,
                day,
                UUID.randomUUID().toString()));
    }

    private static Result wage(UUID player, long wage) {
        Company company = CompanyManager.getCompanyByOwner(player);
        if (company == null) return Result.fail("你还没有公司");
        if (wage < 0 || wage > 1_000_000) return Result.fail("工资应在 0～1000000");
        if (!MacroLaborService.setWage(player, company.getCompanyId(), wage)) return Result.fail("设定失败：只有公司所有者可以设定工资。");
        String hint = wage < MacroLaborService.reservationWage()
                ? "（低于保留工资 " + MacroLaborService.reservationWage() + "，将招不到人）"
                : "";
        return Result.ok(company.getName() + " 日工资设为 " + wage + hint);
    }

    private static Result news(ServerPlayer player) {
        if (player == null) return Result.fail("只有玩家可以领报纸。");
        if (!NewsService.enabled()) return Result.fail("《金融周报》未启用（配置 macroEconomy.newsEnabled）。");
        int n = NewsService.claim(player);
        if (n > 0) return Result.ok("领到 " + n + " 份报纸，已放进背包。");
        String next = NewsService.lastWeeklyDay() == Long.MIN_VALUE
                ? "?"
                : String.valueOf(NewsService.lastWeeklyDay() + FinanceConfig.macroNewsIntervalDays());
        return Result.ok((NewsService.archive().isEmpty() ? "还没有出过报纸。" : "最近的报纸你都领过了。") + "下一期约在第 " + next + " 天出版。");
    }

    private static Result radar(UUID player, long day) {
        MacroRadar.Purchase r = MacroRadar.purchase(player, day);
        if (!r.success()) return Result.fail(r.message());
        List<String> lines = new ArrayList<>();
        if (r.opportunities().isEmpty()) lines.add("调查员转了一圈，暂时没发现明显的商机。");
        for (MacroRadar.Opportunity o : r.opportunities()) lines.add("· " + o.text());
        lines.add("情报会过时：价格会被商队和村民拉平，两三天后最好重新调查。");
        putDetail(player, r.message(), lines);
        return Result.ok(r.message());
    }

    private static Result report(UUID player, String symbol) {
        if (!needSymbol(symbol)) return Result.fail("请输入股票代码");
        EarningsCommand.Report r = EarningsCommand.report(symbol);
        List<String> lines = r.lines();
        String title = lines.isEmpty() ? "季报" : lines.get(0);
        putDetail(player, title, lines.size() > 1 ? lines.subList(1, lines.size()) : List.of());
        return new Result(r.found(), r.found() ? "已查询 " + symbol.toUpperCase(Locale.ROOT) + " 的季报" : title);
    }

    static void putDetail(UUID player, String title, List<String> lines) {
        synchronized (DETAILS) {
            DETAILS.put(player, new Detail(title, List.copyOf(lines)));
        }
    }

    public static void clearDirect() {
        synchronized (DETAILS) {
            DETAILS.clear();
        }
    }
}
