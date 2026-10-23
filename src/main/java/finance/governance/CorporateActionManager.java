package finance.governance;

import finance.account.*;
import finance.company.*;
import finance.data.EconomySavedData;
import finance.stock.*;
import finance.util.Uuids;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CorporateActionManager {
    public static final int MAX_ACTIONS = 1024, MAX_ACCEPTANCES = 4096;
    private static final Map<UUID, BuybackPlan> BUYBACKS = new LinkedHashMap<>();
    private static final Map<UUID, TenderOffer> TENDERS = new LinkedHashMap<>();
    private static final List<CorporateAnnouncement> ANNOUNCEMENTS = new ArrayList<>();
    private static final Map<UUID, UUID> CONTROLLERS = new LinkedHashMap<>();
    private static final Set<String> KEYS = new HashSet<>();

    private CorporateActionManager() {}

    public static synchronized Result startBuyback(
            UUID requester, UUID companyId, long price, long maxShares, int duration, long day, String key) {
        Company c = CompanyManager.getCompany(companyId);
        Stock s = StockMarketManager.getStockByCompanyId(companyId);
        String k = "buyback:" + key;
        CompanyProposal authorization = approvedProposal(key, CompanyProposalType.SHARE_BUYBACK, companyId);
        if (requester == null
                || c == null
                || s == null
                || !GovernanceAuthorizationService.mayManage(requester, companyId)
                || authorization == null
                || authorization.getValue1() != price
                || authorization.getValue2() != maxShares
                || authorization.getValue3() != duration
                || price <= 0
                || maxShares <= 0
                || duration < 1
                || duration > 90
                || KEYS.contains(k)) return Result.fail("回购需要已通过的股东提案授权");
        if (!ensureCapacity(BUYBACKS)) return Result.fail("公司行动记录已满");
        if (BUYBACKS.values().stream()
                .anyMatch(x -> x.companyId().equals(companyId) && x.status() == CapitalActionStatus.OPEN))
            return Result.fail("公司已有进行中的回购");
        long budget = multiply(price, maxShares);
        long minimum = Math.max(1, c.estimateDailyOperatingCost());
        if (budget <= 0 || c.getCash() - minimum < budget) return Result.fail("扣除最低运营储备后，公司现金不足");
        UUID id = UUID.randomUUID(), escrow = escrow(id);
        ensureZeroEscrow(escrow);
        if (!c.withdraw(budget) || !AccountManager.deposit(escrow, budget)) {
            if (c.canDeposit(budget)) c.deposit(budget);
            return Result.fail("回购资金托管失败");
        }
        BUYBACKS.put(
                id,
                new BuybackPlan(
                        id,
                        companyId,
                        escrow,
                        s.getSymbol(),
                        price,
                        maxShares,
                        day,
                        day + duration,
                        budget,
                        CapitalActionStatus.OPEN));
        KEYS.add(k);
        announce(
                companyId,
                CorporateAnnouncement.Type.BUYBACK,
                day,
                id,
                "buyback.open",
                "price=" + price + ",shares=" + maxShares);
        record(companyId, escrow, budget, TransactionType.COMPANY_BUYBACK, requester, s.getSymbol(), maxShares);
        EconomySavedData.markDirty();
        return Result.ok(id, "回购已开启");
    }

    public static synchronized Result acceptBuyback(UUID holder, UUID planId, long shares, long day, String key) {
        BuybackPlan p = BUYBACKS.get(planId);
        String k = "buyback-accept:" + key;
        if (holder == null
                || p == null
                || p.status() != CapitalActionStatus.OPEN
                || day > p.endDay()
                || shares <= 0
                || key == null
                || key.isBlank()
                || KEYS.contains(k)
                || p.accepted().size() >= MAX_ACCEPTANCES) return Result.fail("无效的回购申报");
        if (lockedSharesFor(holder, p.symbol()) > 0) return Result.fail("股份已被其他公司行动锁定");
        Stock buybackStock = StockMarketManager.getStock(p.symbol());
        if (buybackStock != null && buybackStock.isFounderLocked(holder))
            return Result.fail("创始人股份锁定期内（剩余 " + buybackStock.getFounderLockupDaysRemaining() + " 天）");
        long cost = StockPortfolioManager.getHolding(holder, p.symbol()).getAverageCost();
        if (!StockPortfolioManager.removeHolding(holder, p.symbol(), shares) || !p.accept(holder, shares, cost)) {
            StockPortfolioManager.addHolding(holder, p.symbol(), shares, cost);
            return Result.fail("持股不足");
        }
        KEYS.add(k);
        EconomySavedData.markDirty();
        return Result.ok(planId, "股份已锁定参与回购");
    }

    public static synchronized Result startTender(
            UUID buyer, UUID companyId, long price, long target, long minimum, int duration, long day, String key) {
        Company c = CompanyManager.getCompany(companyId);
        Stock s = StockMarketManager.getStockByCompanyId(companyId);
        String k = "tender:" + key;
        if (buyer == null
                || c == null
                || s == null
                || price <= 0
                || target <= 0
                || minimum <= 0
                || minimum > target
                || duration < 1
                || duration > 90
                || key == null
                || key.isBlank()
                || KEYS.contains(k)) return Result.fail("无效的要约收购");
        if (!ensureCapacity(TENDERS)) return Result.fail("公司行动记录已满");
        long funds = multiply(price, target);
        UUID id = UUID.randomUUID(), escrow = escrow(id);
        ensureZeroEscrow(escrow);
        if (funds <= 0 || !AccountManager.moveFunds(buyer, escrow, funds)) return Result.fail("要约收购资金不足");
        TENDERS.put(
                id,
                new TenderOffer(
                        id,
                        buyer,
                        companyId,
                        escrow,
                        s.getSymbol(),
                        price,
                        target,
                        minimum,
                        day,
                        day + duration,
                        funds,
                        CapitalActionStatus.OPEN));
        KEYS.add(k);
        announce(
                companyId,
                CorporateAnnouncement.Type.TENDER,
                day,
                id,
                "tender.open",
                "price=" + price + ",target=" + target);
        setTradingHalted(companyId, true);
        EconomySavedData.markDirty();
        return Result.ok(id, "要约收购已发起");
    }

    public static synchronized Result acceptTender(UUID holder, UUID offerId, long shares, long day, String key) {
        TenderOffer o = TENDERS.get(offerId);
        String k = "tender-accept:" + key;
        if (holder == null
                || o == null
                || o.status() != CapitalActionStatus.OPEN
                || holder.equals(o.buyerId())
                || day > o.endDay()
                || shares <= 0
                || key == null
                || key.isBlank()
                || KEYS.contains(k)
                || o.accepted().size() >= MAX_ACCEPTANCES) return Result.fail("无效的要约申报");
        if (lockedSharesFor(holder, o.symbol()) > 0) return Result.fail("股份已被其他公司行动锁定");
        Stock tenderStock = StockMarketManager.getStock(o.symbol());
        if (tenderStock != null && tenderStock.isFounderLocked(holder))
            return Result.fail("创始人股份锁定期内不可接受要约（剩余 " + tenderStock.getFounderLockupDaysRemaining() + " 天）");
        long orderLocked = marketOrderLockedShares(holder, o.symbol());
        long freeShares = StockPortfolioManager.getHolding(holder, o.symbol()).getQuantity();
        if (shares > freeShares) return Result.fail("可接受份额不足（可用 " + freeShares + " 股，订单簿锁定 " + orderLocked + " 股）");
        long cost = StockPortfolioManager.getHolding(holder, o.symbol()).getAverageCost();
        if (!StockPortfolioManager.removeHolding(holder, o.symbol(), shares) || !o.accept(holder, shares, cost)) {
            StockPortfolioManager.addHolding(holder, o.symbol(), shares, cost);
            return Result.fail("持股不足");
        }
        KEYS.add(k);
        EconomySavedData.markDirty();
        return Result.ok(offerId, "股份已锁定参与要约");
    }

    public static synchronized Result cancelBuyback(UUID requester, UUID planId, long day, String key) {
        BuybackPlan p = BUYBACKS.get(planId);
        Company c = p == null ? null : CompanyManager.getCompany(p.companyId());
        String k = "buyback-cancel:" + key;
        if (p == null
                || c == null
                || p.status() != CapitalActionStatus.OPEN
                || !GovernanceAuthorizationService.mayManage(requester, p.companyId())
                || key == null
                || key.isBlank()
                || KEYS.contains(k)) return Result.fail("无效的回购取消请求");
        long cash = AccountManager.getBalance(p.escrowId());
        if ((cash > 0 && !c.canDeposit(cash)) || !CorporateActionSettlement.canRelease(p.accepted(), p.symbol()))
            return Result.fail("取消结算无法入账");
        CorporateActionSettlement.returnUnallocated(p.accepted(), Map.of(), p.symbol());
        if (cash > 0) {
            AccountManager.withdraw(p.escrowId(), cash);
            c.deposit(cash);
        }
        p.status(CapitalActionStatus.CANCELLED);
        KEYS.add(k);
        announce(p.companyId(), CorporateAnnouncement.Type.BUYBACK, day, p.id(), "buyback.cancelled", "refund=" + cash);
        EconomySavedData.markDirty();
        return Result.ok(planId, "回购已取消");
    }

    public static synchronized Result cancelTender(UUID requester, UUID offerId, long day, String key) {
        TenderOffer o = TENDERS.get(offerId);
        String k = "tender-cancel:" + key;
        if (o == null
                || o.status() != CapitalActionStatus.OPEN
                || !o.buyerId().equals(requester)
                || key == null
                || key.isBlank()
                || KEYS.contains(k)) return Result.fail("无效的要约取消请求");
        Map<UUID, Long> divs = new LinkedHashMap<>(o.acceptedDividends());
        long divTotal = sum(divs);
        long cash = AccountManager.getBalance(o.escrowId());
        long buyerCash = cash - divTotal;
        if (!CorporateActionSettlement.canRelease(o.accepted(), o.symbol())
                || !CorporateActionSettlement.canDepositAll(divs)
                || (buyerCash > 0 && !AccountManager.canDeposit(o.buyerId(), buyerCash)))
            return Result.fail("取消结算无法入账");
        CorporateActionSettlement.returnUnallocated(o.accepted(), Map.of(), o.symbol());
        for (var e : divs.entrySet()) {
            if (!AccountManager.moveFunds(o.escrowId(), e.getKey(), e.getValue()))
                throw new IllegalStateException("validated tender dividend restore failed");
        }
        o.acceptedDividends().clear();
        long rest = AccountManager.getBalance(o.escrowId());
        if (rest > 0 && !AccountManager.moveFunds(o.escrowId(), o.buyerId(), rest))
            throw new IllegalStateException("validated tender cancel refund failed");
        o.status(CapitalActionStatus.CANCELLED);
        KEYS.add(k);
        refreshTradingHalt(o.targetCompanyId());
        announce(
                o.targetCompanyId(),
                CorporateAnnouncement.Type.TENDER,
                day,
                o.id(),
                "tender.cancelled",
                "refund=" + cash);
        EconomySavedData.markDirty();
        return Result.ok(offerId, "要约已取消");
    }

    public static synchronized Result retireTreasury(
            UUID requester, UUID companyId, long shares, long day, String key) {
        Stock s = StockMarketManager.getStockByCompanyId(companyId);
        String k = "treasury-retire:" + key;
        CompanyProposal authorization = approvedProposal(key, CompanyProposalType.TREASURY_RETIREMENT, companyId);
        if (s == null
                || shares <= 0
                || !GovernanceAuthorizationService.mayManage(requester, companyId)
                || authorization == null
                || authorization.getValue1() != shares
                || KEYS.contains(k)
                || !s.retireTreasuryShares(shares)) return Result.fail("注销库存股需要已通过的股东提案授权");
        KEYS.add(k);
        announce(
                companyId,
                CorporateAnnouncement.Type.BUYBACK,
                day,
                authorization.getProposalId(),
                "treasury.retired",
                "shares=" + shares);
        record(companyId, companyId, 0, TransactionType.COMPANY_BUYBACK, requester, s.getSymbol(), shares);
        EconomySavedData.markDirty();
        return Result.ok(companyId, "库存股已注销");
    }

    public static synchronized boolean cancelForBankruptcy(UUID companyId, long day) {
        Company company = CompanyManager.getCompany(companyId);
        if (company == null) return false;
        List<BuybackPlan> buybacks = BUYBACKS.values().stream()
                .filter(p -> p.companyId().equals(companyId) && p.status() == CapitalActionStatus.OPEN)
                .toList();
        List<TenderOffer> tenders = TENDERS.values().stream()
                .filter(o -> o.targetCompanyId().equals(companyId) && o.status() == CapitalActionStatus.OPEN)
                .toList();
        long companyRefund = 0;
        for (BuybackPlan p : buybacks) {
            long cash = AccountManager.getBalance(p.escrowId());
            if (companyRefund > Long.MAX_VALUE - cash
                    || !CorporateActionSettlement.canRelease(p.accepted(), p.symbol())) return false;
            companyRefund += cash;
        }
        if (companyRefund > 0 && !company.canDeposit(companyRefund)) return false;
        for (TenderOffer o : tenders) {
            long cash = AccountManager.getBalance(o.escrowId());
            if ((cash > 0 && !AccountManager.canDeposit(o.buyerId(), cash))
                    || !CorporateActionSettlement.canRelease(o.accepted(), o.symbol())) return false;
        }
        for (BuybackPlan p : buybacks) {
            CorporateActionSettlement.failBuyback(p, company);
            p.status(CapitalActionStatus.CANCELLED);
            announce(companyId, CorporateAnnouncement.Type.BUYBACK, day, p.id(), "buyback.bankruptcy_cancelled", "");
        }
        for (TenderOffer o : tenders) {
            CorporateActionSettlement.failTender(o, day, "target company bankruptcy");
            o.status(CapitalActionStatus.CANCELLED);
        }
        refreshTradingHalt(companyId);
        return true;
    }

    public static synchronized void processDay(long day) {
        for (BuybackPlan p : new ArrayList<>(BUYBACKS.values()))
            if (p.status() == CapitalActionStatus.OPEN && day >= p.endDay())
                CorporateActionSettlement.settleBuyback(p, day);
        for (TenderOffer o : new ArrayList<>(TENDERS.values()))
            if (o.status() == CapitalActionStatus.OPEN && day >= o.endDay())
                CorporateActionSettlement.settleTender(o, day);
        for (Company c : CompanyManager.getCompanies())
            if (c.isPublic()) updateControl(c.getCompanyId(), day, c.getCompanyId());
    }

    static void updateControl(UUID companyId, long day, UUID business) {
        var snapshot = ShareholderRegistryService.snapshot(companyId, day);
        var top = snapshot.largest();
        UUID old = CONTROLLERS.get(companyId),
                next =
                        top != null
                                        && top.votingPercent() > 50
                                        && !top.id().equals(ShareholderRegistryService.SYSTEM_LIQUIDITY_HOLDER)
                                ? top.id()
                                : null;
        if (!Objects.equals(old, next)) {
            if (next == null) CONTROLLERS.remove(companyId);
            else CONTROLLERS.put(companyId, next);
            announce(
                    companyId,
                    CorporateAnnouncement.Type.CONTROL_CHANGE,
                    day,
                    business,
                    "control.changed",
                    "from=" + old + ",to=" + next);
        }
    }

    static Map<UUID, Long> costsFor(String symbol, Map<UUID, Long> accepted) {
        for (BuybackPlan p : BUYBACKS.values())
            if (p.symbol().equals(symbol) && p.accepted() == accepted) return p.acceptedCosts();
        for (TenderOffer o : TENDERS.values())
            if (o.symbol().equals(symbol) && o.accepted() == accepted) return o.acceptedCosts();
        return Map.of();
    }

    private static long lockedSharesFor(UUID h, String symbol) {
        long n = 0;
        for (var p : BUYBACKS.values())
            if (p.status() == CapitalActionStatus.OPEN && p.symbol().equals(symbol))
                n = add(n, p.accepted().getOrDefault(h, 0L));
        for (var o : TENDERS.values())
            if (o.status() == CapitalActionStatus.OPEN && o.symbol().equals(symbol))
                n = add(n, o.accepted().getOrDefault(h, 0L));
        return n;
    }

    public static synchronized Map<UUID, Long> lockedShares(String symbol) {
        Map<UUID, Long> m = new LinkedHashMap<>();
        for (var p : BUYBACKS.values())
            if (p.status() == CapitalActionStatus.OPEN && p.symbol().equals(symbol))
                p.accepted().forEach((k, v) -> m.merge(k, v, CorporateActionManager::add));
        for (var o : TENDERS.values())
            if (o.status() == CapitalActionStatus.OPEN && o.symbol().equals(symbol))
                o.accepted().forEach((k, v) -> m.merge(k, v, CorporateActionManager::add));
        return m;
    }

    public static Map<UUID, BuybackPlan> buybacks() {
        return Collections.unmodifiableMap(BUYBACKS);
    }

    public static Map<UUID, TenderOffer> tenders() {
        return Collections.unmodifiableMap(TENDERS);
    }

    public static List<CorporateAnnouncement> announcements() {
        return List.copyOf(ANNOUNCEMENTS);
    }

    public static Map<UUID, UUID> controllers() {
        return Collections.unmodifiableMap(CONTROLLERS);
    }

    public static Set<String> keys() {
        return Collections.unmodifiableSet(KEYS);
    }

    public static boolean putBuybackDirect(BuybackPlan p) {
        if (p == null) return false;
        BUYBACKS.put(p.id(), p);
        return true;
    }

    public static boolean putTenderDirect(TenderOffer o) {
        if (o == null) return false;
        TENDERS.put(o.id(), o);
        return true;
    }

    public static void putAnnouncementDirect(CorporateAnnouncement a) {
        if (a != null) {
            ANNOUNCEMENTS.add(a);
            while (ANNOUNCEMENTS.size() > 2048) ANNOUNCEMENTS.remove(0);
        }
    }

    public static void putControllerDirect(UUID c, UUID h) {
        if (c != null && h != null) CONTROLLERS.put(c, h);
    }

    public static void putKeyDirect(String k) {
        if (k != null && !k.isBlank()) KEYS.add(k);
    }

    public static void clearDirect() {
        BUYBACKS.clear();
        TENDERS.clear();
        ANNOUNCEMENTS.clear();
        CONTROLLERS.clear();
        KEYS.clear();
    }

    private static CompanyProposal approvedProposal(String key, CompanyProposalType type, UUID companyId) {
        if (key == null || !key.startsWith("proposal-")) return null;
        UUID proposalId = Uuids.parseOrNull(key.substring("proposal-".length()));
        CompanyProposal p = proposalId == null ? null : CompanyProposalManager.getProposal(proposalId);
        return p != null && p.isExecutableAuthorization(type, companyId) ? p : null;
    }

    private static boolean ensureCapacity(Map<UUID, ?> actions) {
        if (actions.size() < MAX_ACTIONS) return true;
        Iterator<? extends Map.Entry<UUID, ?>> it = actions.entrySet().iterator();
        while (actions.size() >= MAX_ACTIONS && it.hasNext()) {
            Object action = it.next().getValue();
            CapitalActionStatus status = action instanceof BuybackPlan p
                    ? p.status()
                    : action instanceof TenderOffer o ? o.status() : CapitalActionStatus.OPEN;
            if (status != CapitalActionStatus.OPEN) it.remove();
        }
        return actions.size() < MAX_ACTIONS;
    }

    private static UUID escrow(UUID id) {
        return UUID.nameUUIDFromBytes(("corporate-action-escrow:" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static void ensureZeroEscrow(UUID id) {
        if (!AccountManager.getAccounts().containsKey(id))
            AccountManager.getAccount(id).setBalance(0);
    }

    static long multiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    static long sum(Map<UUID, Long> m) {
        BigInteger n = BigInteger.ZERO;
        for (long v : m.values()) n = n.add(BigInteger.valueOf(v));
        return n.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    private static long add(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    static void announce(UUID c, CorporateAnnouncement.Type t, long d, UUID b, String key, String details) {
        putAnnouncementDirect(new CorporateAnnouncement(UUID.randomUUID(), c, t, d, b, key, details));
    }

    static void record(UUID from, UUID to, long amount, TransactionType type, UUID player, String object, long q) {
        AccountManager.addTransactionRecord(new TransactionRecord(from, to, amount, type, player, object, q));
    } // ================= 要约收购扩展（v0.5.0） =================
    /** 某股票在进行中要约里锁定的份额（holder → 股数），分红路由用 */
    public static synchronized Map<UUID, Long> tenderLockedShares(String symbol) {
        Map<UUID, Long> m = new LinkedHashMap<>();
        for (TenderOffer o : TENDERS.values())
            if (o.status() == CapitalActionStatus.OPEN && o.symbol().equals(symbol))
                o.accepted().forEach((k, v) -> m.merge(k, v, CorporateActionManager::add));
        return m;
    }
    /** 某股票在进行中回购里锁定的份额（holder → 股数） */
    public static synchronized Map<UUID, Long> buybackLockedShares(String symbol) {
        Map<UUID, Long> m = new LinkedHashMap<>();
        for (BuybackPlan x : BUYBACKS.values())
            if (x.status() == CapitalActionStatus.OPEN && x.symbol().equals(symbol))
                x.accepted().forEach((k, v) -> m.merge(k, v, CorporateActionManager::add));
        return m;
    }
    /** 进行中要约按 offer 分组的锁定份额（offerId → holder → shares），分红路由用 */
    public static synchronized Map<UUID, Map<UUID, Long>> tenderLockedByOffer(String symbol) {
        Map<UUID, Map<UUID, Long>> out = new LinkedHashMap<>();
        for (TenderOffer o : TENDERS.values())
            if (o.status() == CapitalActionStatus.OPEN
                    && o.symbol().equals(symbol)
                    && !o.accepted().isEmpty()) out.put(o.id(), new LinkedHashMap<>(o.accepted()));
        return out;
    }
    /** 某公司进行中的要约 ID（若有） */
    public static synchronized UUID openTenderIdFor(UUID companyId) {
        for (TenderOffer o : TENDERS.values())
            if (o.targetCompanyId().equals(companyId) && o.status() == CapitalActionStatus.OPEN) return o.id();
        return null;
    }
    /** 某玩家是否为某公司进行中要约的收购方 */
    public static synchronized boolean isTenderBuyer(UUID playerId, UUID companyId) {
        if (playerId == null || companyId == null) return false;
        for (TenderOffer o : TENDERS.values())
            if (o.targetCompanyId().equals(companyId)
                    && o.status() == CapitalActionStatus.OPEN
                    && playerId.equals(o.buyerId())) return true;
        return false;
    }
    /** 某股东被公司行动锁定的份额（公开版，供 TakeoverService 等调用） */
    public static long lockedSharesForHolder(UUID holder, String symbol) {
        return lockedSharesFor(holder, symbol);
    }
    /** 某股东在订单簿卖单中锁定的股数 —— 显式防一股两卖（订单锁定的份额已从持仓扣除，此处做口径校验） */
    public static long marketOrderLockedShares(UUID holder, String symbol) {
        long n = 0;
        if (holder == null || symbol == null) return 0;
        for (StockOrder o : StockOrderManager.getOrdersBySymbol(symbol))
            if (o.getType() == StockOrderType.SELL && holder.equals(o.getPlayerId()))
                n = add(n, (long) o.getQuantity());
        return n;
    }
    /** 设置某公司股票停牌状态 */
    private static void setTradingHalted(UUID companyId, boolean halted) {
        Stock s = StockMarketManager.getStockByCompanyId(companyId);
        if (s != null) s.setTradingHalted(halted);
    }
    /** 刷新某公司停牌状态：有进行中要约即停牌，否则复牌 */
    static void refreshTradingHalt(UUID companyId) {
        Stock s = StockMarketManager.getStockByCompanyId(companyId);
        if (s == null) return;
        boolean halted = TENDERS.values().stream()
                .anyMatch(o -> o.targetCompanyId().equals(companyId) && o.status() == CapitalActionStatus.OPEN);
        s.setTradingHalted(halted);
    }

    public record Result(boolean success, UUID id, String message) {
        static Result ok(UUID id, String m) {
            return new Result(true, id, m);
        }

        static Result fail(String m) {
            return new Result(false, null, m);
        }
    }
}
