package finance.rival;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalStatus;
import finance.company.CompanyProposalType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.governance.CorporateActionManager;
import finance.governance.ShareholderRegistryService;
import finance.governance.TakeoverService;
import finance.npcinvestor.NpcFloatPlacementService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * NPC 竞争公司的敌对动作（单人经济 A2，默认关闭）：
 * <ul>
 *   <li>敌对要约：激进 / 投机型竞争公司挑一家股价明显低于基本面的上市公司，用投资账户建仓到要约门槛，
 *       再通过 {@link TakeoverService#offerTender} 发起要约；</li>
 *   <li>股东激进主义：投资账户持股达到提案资格（25%）后，被投公司亏损或长期不分红时，发起“提高分红”提案。</li>
 * </ul>
 * 资金从竞争公司现金转入投资账户并留痕；股份只通过残差配售、普通买单和要约交割获得。同一天只执行一次。
 */
public final class RivalHostileService {
    /** 目标股价至少比每股基本面低 20%。 */
    static final int UNDERVALUE_BPS = 8_000;
    /** 要约价 = 公允价 × 1.3（再受要约价上下限约束）。 */
    static final int OFFER_PREMIUM_BPS = 13_000;
    /** 每天建仓不超过流通股的 2%；超过 30 天还没建够就放弃。 */
    static final int STAKE_BPS_PER_DAY = 200, STAKE_GIVE_UP_DAYS = 30;
    static final int TENDER_DAYS = 7, COOLDOWN_DAYS = 30, ACTIVISM_GAP_DAYS = 30;
    /** 建仓与要约都要求竞争公司至少留 2 万现金。 */
    static final long RESERVE_CASH = 20_000L;
    /** 提案资格：单一持股者 ≥ 有表决权股份的 25%（见 GovernanceAuthorizationService）。 */
    static final int PROPOSAL_BPS = 2_500;

    private RivalHostileService() { }

    public record Settings(boolean hostileTakeovers, boolean activism) {
        public static Settings fromConfig() {
            return new Settings(FinanceConfig.rivalsHostileTakeovers(), FinanceConfig.rivalsActivismEnabled());
        }
    }

    public static void processDay(long day) {
        if (!FinanceConfig.rivalsEnabled()) return; // 竞争公司整体关闭时也不做敌对动作
        processDay(day, Settings.fromConfig());
    }

    public static synchronized void processDay(long day, Settings settings) {
        if (settings == null || (!settings.hostileTakeovers() && !settings.activism()) || day < 0) return;
        if (day <= RivalManager.lastHostileDay()) return;
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.RIVALS)) return;
        RivalManager.setLastHostileDay(day);
        List<RivalProfile> profiles = new ArrayList<>(RivalManager.all());
        profiles.sort(Comparator.comparing(p -> p.companyId().toString()));
        for (RivalProfile profile : profiles) {
            Company rival = CompanyManager.getCompany(profile.companyId());
            if (rival == null || rival.isPlayerOwned() || rival.isBankruptcyRisk()) continue;
            if (settings.hostileTakeovers()) hostile(rival, profile, day);
            if (settings.activism()) activism(rival, profile, day);
        }
        EconomySavedData.markDirty();
    }

    // ---------------------------------------------------------------- 敌对要约

    static void hostile(Company rival, RivalProfile profile, long day) {
        if (profile.personality() == RivalPersonality.CONSERVATIVE) return;
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        if (profile.hostileTarget() == null) {
            if (profile.hostileCooldownDay() != Long.MIN_VALUE && day - profile.hostileCooldownDay() < COOLDOWN_DAYS) return;
            Stock target = pickTarget(rival);
            if (target == null) return;
            profile.startHostile(target.getCompanyId(), day);
            profile.record(day, "HOSTILE_START", target.getSymbol());
        }
        Stock stock = StockMarketManager.getStockByCompanyId(profile.hostileTarget());
        Company target = CompanyManager.getCompany(profile.hostileTarget());
        if (stock == null || target == null || !target.isPublic()) {
            profile.endHostile(day);
            return;
        }
        if (CorporateActionManager.openTenderIdFor(target.getCompanyId()) != null) return; // 有要约在进行（可能就是自己的）
        double held = TakeoverService.votingPercent(account, target.getCompanyId(), day);
        double required = FinanceConfig.takeoverMinHoldingBps() / 100.0;
        if (held + 1e-9 < required) {
            if (day - profile.hostileStartDay() > STAKE_GIVE_UP_DAYS) {
                profile.record(day, "HOSTILE_ABANDON", stock.getSymbol());
                profile.endHostile(day);
                return;
            }
            buildStake(rival, account, stock, required, day);
            return;
        }
        offer(rival, profile, account, stock, target, day);
    }

    /** 挑股价比每股基本面低 20% 以上、折价最大的上市公司（不能是自己，不能停牌）。 */
    static Stock pickTarget(Company rival) {
        Stock best = null;
        double bestRatio = UNDERVALUE_BPS / 10_000.0;
        for (Stock s : StockMarketManager.getListedStocks()) {
            if (s.getCompanyId().equals(rival.getCompanyId()) || s.isTradingHalted() || s.getLastPrice() <= 0) continue;
            Company c = CompanyManager.getCompany(s.getCompanyId());
            if (c == null || c.isBankruptcyRisk() || CorporateActionManager.openTenderIdFor(c.getCompanyId()) != null) continue;
            long fps = c.getFundamentalAssetValue() / Math.max(1, s.getTotalShares());
            if (fps <= 0) continue;
            double ratio = s.getLastPrice() / (double) fps;
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = s;
            }
        }
        return best;
    }

    /** 建仓：先残差配售再普通买单，每天最多流通股的 2%，资金从公司现金转入投资账户。 */
    static void buildStake(Company rival, UUID account, Stock stock, double requiredPercent, long day) {
        long voting = stock.getVotingShares();
        long have = holding(account, stock, day);
        long need = (long) Math.ceil(voting * requiredPercent / 100.0) - have;
        long qty = Math.min(need, Math.max(1, stock.getFloatShares() * STAKE_BPS_PER_DAY / 10_000));
        if (qty <= 0) return;
        long price = Math.max(NpcFloatPlacementService.placementPrice(stock), stock.getLastPrice());
        long cost = safeMul(price, qty);
        if (!fund(rival, account, cost)) return;
        long residual = NpcFloatPlacementService.availableResidual(stock, day);
        long fromResidual = Math.min(qty, residual);
        if (fromResidual > 0) NpcFloatPlacementService.place(account, stock.getSymbol(), fromResidual, day);
        long rest = qty - Math.max(0, fromResidual);
        if (rest > 0 && rest <= Integer.MAX_VALUE) StockMarketManager.buy(account, stock.getSymbol(), rest);
    }

    /** 发起要约：价格 = 公允价 × 1.3（受上下限约束），目标凑到 51%，最低接受量为目标的一半。 */
    static void offer(Company rival, RivalProfile profile, UUID account, Stock stock, Company target, long day) {
        long fair = stock.getFairValue();
        if (fair <= 0) return;
        long price = Math.min((long) Math.floor(fair * FinanceConfig.takeoverPriceCapRatio()),
                Math.max((long) Math.ceil(fair * FinanceConfig.takeoverPriceFloorRatio()), (long) Math.ceil(fair * OFFER_PREMIUM_BPS / 10_000.0)));
        price = Math.min(price, finance.company.CompanyIPOService.MAX_ISSUE_PRICE);
        long voting = stock.getVotingShares();
        long targetShares = (long) Math.ceil(voting * 0.51) - holding(account, stock, day);
        if (price <= 0 || targetShares <= 0) {
            profile.endHostile(day);
            return;
        }
        long minShares = Math.max(1, targetShares / 2);
        long escrow = safeMul(price, targetShares);
        if (!fund(rival, account, escrow)) return;
        var result = TakeoverService.offerTender(account, target.getCompanyId(), price, targetShares, minShares,
                TENDER_DAYS, day, "rival-tender:" + rival.getCompanyId() + ":" + day);
        if (!result.success()) {
            profile.record(day, "HOSTILE_FAIL", result.message());
            profile.endHostile(day);
            return;
        }
        profile.record(day, "TENDER", stock.getSymbol() + " @" + price + " ×" + targetShares);
        profile.endHostile(day);
        news(70, rival.getName() + "对" + target.getName() + "发起敌对要约收购",
                rival.getName() + "已持有 " + stock.getSymbol() + " 三成股份，现以每股 " + price
                        + " 收购，目标拿下控股权。股东可以选择接受或拒绝。", true);
    }

    // ---------------------------------------------------------------- 股东激进主义

    /** 持股 ≥ 25% 的被投公司亏损或近两个周期没分红时，发起“提高分红”提案（每 30 天最多一次）。 */
    static void activism(Company rival, RivalProfile profile, long day) {
        if (profile.lastActivismDay() != Long.MIN_VALUE && day - profile.lastActivismDay() < ACTIVISM_GAP_DAYS) return;
        UUID account = RivalProfile.investmentAccount(rival.getCompanyId());
        for (Stock stock : StockMarketManager.getListedStocks()) {
            if (stock.getCompanyId().equals(rival.getCompanyId())) continue;
            Company target = CompanyManager.getCompany(stock.getCompanyId());
            if (target == null || !target.isPublic()) continue;
            long voting = stock.getVotingShares();
            if (voting <= 0 || holding(account, stock, day) * 10_000 < voting * PROPOSAL_BPS) continue;
            if (!underperforming(target, day)) continue;
            boolean active = CompanyProposalManager.getProposalsForCompany(target.getCompanyId()).stream()
                    .anyMatch(p -> p.getStatus() == CompanyProposalStatus.ACTIVE && account.equals(p.getCreatorId()));
            if (active) continue;
            long current = Math.round(target.effectiveDividendRatio(CompanyManager.getDividendRatio()) * 100);
            long proposed = Math.min(100, Math.max(30, current + 20));
            var result = CompanyProposalManager.createProposal(account, target.getCompanyId(), CompanyProposalType.DIVIDEND,
                    "", proposed, 0, 0, day, day + 7, 0.5);
            if (!result.success()) continue;
            profile.setLastActivismDay(day);
            profile.record(day, "ACTIVISM", stock.getSymbol() + " 分红 " + current + "% → " + proposed + "%");
            news(50, rival.getName() + "要求" + target.getName() + "提高分红",
                    rival.getName() + "作为大股东提出把分红比例从 " + current + "% 提到 " + proposed + "%，7 天内表决。", false);
            return;
        }
    }

    /** 近期亏损，或近两个分红周期没有分红。 */
    static boolean underperforming(Company c, long day) {
        if (c.getSmoothedDailyProfit() < 0) return true;
        int cycle = Math.max(1, c.effectiveDividendCycleDays(CompanyManager.getDividendCycleDays()));
        return c.getDividendHistory().stream().noneMatch(r -> day - r.mcDay() <= 2L * cycle);
    }

    // ---------------------------------------------------------------- 工具

    /** 投资账户对该股的持股（含挂单和要约锁定）。 */
    static long holding(UUID account, Stock stock, long day) {
        for (var h : ShareholderRegistryService.snapshot(stock.getCompanyId(), day).holders()) {
            if (h.id().equals(account)) return h.total();
        }
        return 0;
    }

    /** 把公司现金转入投资账户，补足到 amount；转完后公司至少留 RESERVE_CASH。 */
    static boolean fund(Company rival, UUID account, long amount) {
        if (amount <= 0) return false;
        AccountManager.getOrCreateSystemAccount(account); // 先以系统账户建立，避免领新手资金
        long have = AccountManager.getBalance(account);
        long missing = amount - have;
        if (missing <= 0) return true;
        if (rival.getCash() - missing < RESERVE_CASH || !rival.withdraw(missing)) return false;
        if (!AccountManager.deposit(account, missing)) {
            rival.deposit(missing);
            return false;
        }
        AccountManager.addTransactionRecord(new TransactionRecord(rival.getCompanyId(), account, missing,
                TransactionType.RIVAL_INVESTMENT, null, rival.getName(), 0));
        return true;
    }

    private static long safeMul(long a, long b) {
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    private static void news(int score, String headline, String body, boolean major) {
        if (finance.macro.NewsService.enabled()) finance.macro.NewsService.report(score, headline, body, major);
    }
}
