package finance.governance;

import finance.account.*;
import finance.company.*;
import finance.data.EconomySavedData;
import finance.stock.*;
import finance.util.ProportionalAllocator;
import java.math.BigInteger;
import java.util.*;

/** 回购和要约收购的到期结算、失败退款和锁定股份释放。只在 CorporateActionManager 加锁的入口方法里调用，沿用调用方持有的锁。从 {@link CorporateActionManager} 拆出。 */
final class CorporateActionSettlement {

    private CorporateActionSettlement() {}

    static void settleBuyback(BuybackPlan p, long day) {
        Stock s = StockMarketManager.getStock(p.symbol());
        Company c = CompanyManager.getCompany(p.companyId());
        long total = CorporateActionManager.sum(p.accepted());
        long acquired = Math.min(total, p.maxShares());
        Map<UUID, Long> allocation = allocate(acquired, p.accepted());
        long spent = CorporateActionManager.multiply(p.price(), acquired);
        long refund = spent < 0 ? 0 : p.budget() - spent;
        if (s == null
                || c == null
                || spent < 0
                || acquired > s.getVotingShares()
                || !precheckSellers(allocation, p.price())
                || !canReleaseUnallocated(p.accepted(), allocation, p.symbol())
                || (refund > 0 && !c.canDeposit(refund))) {
            failBuyback(p, c);
            return;
        }
        for (var e : allocation.entrySet()) {
            long payment = CorporateActionManager.multiply(p.price(), e.getValue());
            if (!AccountManager.moveFunds(p.escrowId(), e.getKey(), payment))
                throw new IllegalStateException("validated seller payment failed");
            CorporateActionManager.record(
                    p.companyId(),
                    e.getKey(),
                    payment,
                    TransactionType.COMPANY_BUYBACK,
                    e.getKey(),
                    p.symbol(),
                    e.getValue());
        }
        returnUnallocated(p.accepted(), allocation, p.symbol());
        if (refund > 0) {
            if (!AccountManager.withdraw(p.escrowId(), refund) || !c.deposit(refund))
                throw new IllegalStateException("validated buyback refund failed");
        }
        if (acquired > 0 && !s.addTreasuryShares(acquired))
            throw new IllegalStateException("validated treasury settlement failed");
        p.status(CapitalActionStatus.COMPLETED);
        CorporateActionManager.announce(
                p.companyId(),
                CorporateAnnouncement.Type.BUYBACK,
                day,
                p.id(),
                "buyback.completed",
                "shares=" + acquired + ",spent=" + spent);
        EconomySavedData.markDirty();
    }

    static void settleTender(TenderOffer o, long day) {
        long accepted = CorporateActionManager.sum(o.accepted());
        if (accepted < o.minShares()) {
            failTender(o, day, "minimum not reached");
            return;
        }
        long acquired = Math.min(accepted, o.targetShares());
        Map<UUID, Long> allocation = allocate(acquired, o.accepted());
        long spent = CorporateActionManager.multiply(o.price(), acquired);
        Map<UUID, Long> divRefunds = tenderDividendRefunds(o, allocation);
        long divRefundTotal = CorporateActionManager.sum(divRefunds);
        long escrowBal = AccountManager.getBalance(o.escrowId());
        long buyerRefund = escrowBal - spent - divRefundTotal;
        if (spent < 0
                || buyerRefund < 0
                || !StockPortfolioManager.canAddHolding(o.buyerId(), o.symbol(), acquired)
                || !precheckSellers(allocation, o.price())
                || !canReleaseUnallocated(o.accepted(), allocation, o.symbol())
                || !canDepositAll(divRefunds)
                || (buyerRefund > 0 && !AccountManager.canDeposit(o.buyerId(), buyerRefund))) {
            failTender(o, day, "settlement precheck failed");
            return;
        }
        for (var e : allocation.entrySet()) {
            long payment = CorporateActionManager.multiply(o.price(), e.getValue());
            if (!AccountManager.moveFunds(o.escrowId(), e.getKey(), payment))
                throw new IllegalStateException("validated tender payment failed");
        }
        if (!StockPortfolioManager.addHolding(o.buyerId(), o.symbol(), acquired, o.price()))
            throw new IllegalStateException("validated tender credit failed");
        returnUnallocated(o.accepted(), allocation, o.symbol());
        for (var e : divRefunds.entrySet()) {
            if (!AccountManager.moveFunds(o.escrowId(), e.getKey(), e.getValue()))
                throw new IllegalStateException("validated tender dividend refund failed");
        }
        o.acceptedDividends().clear();
        long remaining = AccountManager.getBalance(o.escrowId());
        if (remaining > 0 && !AccountManager.moveFunds(o.escrowId(), o.buyerId(), remaining))
            throw new IllegalStateException("validated tender refund failed");
        o.status(CapitalActionStatus.COMPLETED);
        CorporateActionManager.updateControl(o.targetCompanyId(), day, o.id());
        if (o.isSqueezeOut()) {
            delistAfterSqueezeOut(o, day);
        } else {
            CorporateActionManager.refreshTradingHalt(o.targetCompanyId());
        }
        CorporateActionManager.announce(
                o.targetCompanyId(),
                CorporateAnnouncement.Type.TENDER,
                day,
                o.id(),
                "tender.completed",
                "shares=" + acquired + ",spent=" + spent);
        CorporateActionManager.record(
                o.buyerId(),
                o.targetCompanyId(),
                spent,
                TransactionType.COMPANY_TENDER,
                o.buyerId(),
                o.symbol(),
                acquired);
        EconomySavedData.markDirty();
    }

    static boolean failBuyback(BuybackPlan p, Company c) {
        long cash = AccountManager.getBalance(p.escrowId());
        if (c == null || !canRelease(p.accepted(), p.symbol()) || (cash > 0 && !c.canDeposit(cash))) return false;
        returnUnallocated(p.accepted(), Map.of(), p.symbol());
        if (cash > 0 && (!AccountManager.withdraw(p.escrowId(), cash) || !c.deposit(cash)))
            throw new IllegalStateException("validated buyback failure refund failed");
        p.status(CapitalActionStatus.FAILED);
        EconomySavedData.markDirty();
        return true;
    }

    static boolean failTender(TenderOffer o, long day, String reason) {
        Map<UUID, Long> divs = new LinkedHashMap<>(o.acceptedDividends());
        long divTotal = CorporateActionManager.sum(divs);
        long cash = AccountManager.getBalance(o.escrowId());
        long buyerCash = cash - divTotal;
        if (!canRelease(o.accepted(), o.symbol())
                || !canDepositAll(divs)
                || (buyerCash > 0 && !AccountManager.canDeposit(o.buyerId(), buyerCash))) return false;
        returnUnallocated(o.accepted(), Map.of(), o.symbol());
        for (var e : divs.entrySet()) {
            if (!AccountManager.moveFunds(o.escrowId(), e.getKey(), e.getValue()))
                throw new IllegalStateException("validated tender dividend restore failed");
        }
        o.acceptedDividends().clear();
        long rest = AccountManager.getBalance(o.escrowId());
        if (rest > 0 && !AccountManager.moveFunds(o.escrowId(), o.buyerId(), rest))
            throw new IllegalStateException("validated tender failure refund failed");
        o.status(CapitalActionStatus.FAILED);
        CorporateActionManager.refreshTradingHalt(o.targetCompanyId());
        CorporateActionManager.announce(
                o.targetCompanyId(), CorporateAnnouncement.Type.TENDER, day, o.id(), "tender.failed", reason);
        EconomySavedData.markDirty();
        return true;
    }

    static boolean precheckSellers(Map<UUID, Long> a, long price) {
        for (var e : a.entrySet()) {
            long cash = CorporateActionManager.multiply(price, e.getValue());
            if (cash <= 0 || !AccountManager.canDeposit(e.getKey(), cash)) return false;
        }
        return true;
    }

    static void returnUnallocated(Map<UUID, Long> accepted, Map<UUID, Long> allocated, String symbol) {
        Map<UUID, Long> costs = CorporateActionManager.costsFor(symbol, accepted);
        for (var e : accepted.entrySet()) {
            long q = e.getValue() - allocated.getOrDefault(e.getKey(), 0L);
            if (q > 0 && !StockPortfolioManager.addHolding(e.getKey(), symbol, q, costs.getOrDefault(e.getKey(), 0L)))
                throw new IllegalStateException("locked share release failed");
        }
    }

    static boolean canRelease(Map<UUID, Long> accepted, String symbol) {
        for (var e : accepted.entrySet())
            if (e.getValue() > 0 && !StockPortfolioManager.canAddHolding(e.getKey(), symbol, e.getValue()))
                return false;
        return true;
    }

    static boolean canReleaseUnallocated(Map<UUID, Long> accepted, Map<UUID, Long> allocated, String symbol) {
        for (var e : accepted.entrySet()) {
            long quantity = e.getValue() - allocated.getOrDefault(e.getKey(), 0L);
            if (quantity > 0 && !StockPortfolioManager.canAddHolding(e.getKey(), symbol, quantity)) return false;
        }
        return true;
    }

    static Map<UUID, Long> allocate(long amount, Map<UUID, Long> weights) {
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (var a : ProportionalAllocator.allocate(amount, weights)) if (a.amount() > 0) out.put(a.id(), a.amount());
        return out;
    }

    /** 要约分红拆分：交割成功时，未分配份额对应的分红返还股东 */
    static Map<UUID, Long> tenderDividendRefunds(TenderOffer o, Map<UUID, Long> allocation) {
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (var e : o.accepted().entrySet()) {
            long div = o.acceptedDividends().getOrDefault(e.getKey(), 0L);
            if (div <= 0) continue;
            long acc = e.getValue(), alloc = allocation.getOrDefault(e.getKey(), 0L);
            long buyerPart = proRata(div, alloc, acc);
            long holderPart = div - buyerPart;
            if (holderPart > 0) out.put(e.getKey(), holderPart);
        }
        return out;
    }

    static long proRata(long amount, long part, long whole) {
        if (amount <= 0 || part <= 0 || whole <= 0) return 0;
        if (part >= whole) return amount;
        return BigInteger.valueOf(amount)
                .multiply(BigInteger.valueOf(part))
                .divide(BigInteger.valueOf(whole))
                .longValue();
    }

    static boolean canDepositAll(Map<UUID, Long> m) {
        for (var e : m.entrySet())
            if (e.getValue() > 0 && !AccountManager.canDeposit(e.getKey(), e.getValue())) return false;
        return true;
    }

    /** 强制私有化交割完成 → 目标公司退市：摘牌、撤单、转非公开 */
    static void delistAfterSqueezeOut(TenderOffer o, long day) {
        UUID companyId = o.targetCompanyId();
        Company c = CompanyManager.getCompany(companyId);
        Stock stock = StockMarketManager.getStockByCompanyId(companyId);
        if (stock != null) {
            StockOrderManager.cancelOrdersForSymbol(stock.getSymbol());
            StockMarketManager.removeStockByCompanyId(companyId);
        }
        if (c != null) c.setPublic(false);
        CorporateActionManager.announce(
                companyId,
                CorporateAnnouncement.Type.TENDER,
                day,
                o.id(),
                "tender.squeeze_out_delisted",
                "公司已退市，转为非公开");
    }
}
