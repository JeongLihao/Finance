package finance.governance;

import finance.chart.Candlestick;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import finance.company.Company;
import finance.company.CompanyIPOService;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockOrderManager;
import finance.stock.StockPortfolioManager;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 要约收购玩法编排层（v0.5.0）。
 *
 * <p>在 {@link CorporateActionManager} 机械层之上叠加玩法规则：
 * <ul>
 *   <li>发起门槛：收购方事前持有目标公司有表决权股份 ≥ 30%（可配置）</li>
 *   <li>定价护栏：要约价 ∈ [0.8, 1.5] × fairValue，且 ≤ IPO 发行价上限</li>
 *   <li>反刷钱：收购方不得是目标 owner / 现任控制人（自我交易）</li>
 *   <li>要约期 3–14 MC 天；发起即停牌（停牌管理在 CorporateActionManager 内）</li>
 *   <li>强制私有化：持股 ≥ 90% 可按保护价挤出小股东并退市</li>
 * </ul>
 */
public final class TakeoverService {

    private TakeoverService() {}

    // ================================================================
    // 发起要约
    // ================================================================

    /**
     * 发起要约收购 —— 玩法入口（含全部规则校验）。
     *
     * @return CorporateActionManager.Result（success 时 id 为要约 ID）
     */
    public static synchronized CorporateActionManager.Result offerTender(
            UUID buyer,
            UUID companyId,
            long price,
            long targetShares,
            long minShares,
            int durationDays,
            long day,
            String key) {
        // ---- fail-fast 基础校验 ----
        if (buyer == null || companyId == null) {
            return CorporateActionManager.Result.fail("参数错误。");
        }
        Company company = CompanyManager.getCompany(companyId);
        if (company == null) {
            return CorporateActionManager.Result.fail("公司不存在。");
        }
        if (!company.isPublic()) {
            return CorporateActionManager.Result.fail("目标公司未上市，无法发起要约收购。");
        }
        Stock stock = StockMarketManager.getStockByCompanyId(companyId);
        if (stock == null) {
            return CorporateActionManager.Result.fail("目标公司没有流通股票。");
        }
        if (CorporateActionManager.openTenderIdFor(companyId) != null) {
            return CorporateActionManager.Result.fail("该公司已有进行中的要约收购。");
        }

        // ---- 反刷钱：自我交易 ----
        // 收购方不得是目标公司 owner —— 否则可用"自己收购自己"洗股、绕过创始人锁定
        if (buyer.equals(company.getOwnerId())) {
            return CorporateActionManager.Result.fail("不能对自己拥有的公司发起要约收购。");
        }
        // 收购方不得是目标公司现任实际控制人
        UUID controller = CorporateActionManager.controllers().get(companyId);
        if (buyer.equals(controller)) {
            return CorporateActionManager.Result.fail("实际控制人不能对自己控制的公司发起要约收购。");
        }

        // ---- 30% 事前持股门槛 ----
        // 防止无持仓者用纯现金发起骚扰要约、恶意停牌整蛊
        double votingPct = votingPercent(buyer, companyId, day);
        double requiredPct = FinanceConfig.takeoverMinHoldingBps() / 100.0;
        if (votingPct < requiredPct) {
            return CorporateActionManager.Result.fail(
                    String.format("发起要约需事前持有目标公司有表决权股份 ≥ %.1f%%（当前 %.2f%%）。", requiredPct, votingPct));
        }

        // ---- 定价护栏 ----
        long fairValue = stock.getFairValue();
        if (fairValue <= 0) {
            return CorporateActionManager.Result.fail("目标公司估值异常，暂不可发起要约。");
        }
        double floorRatio = FinanceConfig.takeoverPriceFloorRatio();
        double capRatio = FinanceConfig.takeoverPriceCapRatio();
        long minPrice = Math.max(1L, (long) Math.ceil(fairValue * floorRatio));
        long maxPrice = (long) Math.floor(fairValue * capRatio);
        if (price < minPrice || price > maxPrice) {
            return CorporateActionManager.Result.fail(String.format(
                    "要约价须在 %d ~ %d 之间（公允价值 %d 的 %.1f～%.1f 倍）。", minPrice, maxPrice, fairValue, floorRatio, capRatio));
        }
        // 与 IPO 发行价上限对齐，防止数值体系撕裂
        if (price > CompanyIPOService.MAX_ISSUE_PRICE) {
            return CorporateActionManager.Result.fail("要约价不得超过 " + CompanyIPOService.MAX_ISSUE_PRICE + "（与发行价上限对齐）。");
        }

        // ---- 资金规模上限：price × target ≤ MAX_ISSUE_PRICE × totalShares ----
        // 防止超大 escrow 成为资金黑洞
        long funds;
        try {
            funds = Math.multiplyExact(price, targetShares);
        } catch (ArithmeticException e) {
            return CorporateActionManager.Result.fail("要约资金规模过大。");
        }
        long maxFunds;
        try {
            maxFunds = Math.multiplyExact(CompanyIPOService.MAX_ISSUE_PRICE, stock.getTotalShares());
        } catch (ArithmeticException e) {
            maxFunds = Long.MAX_VALUE;
        }
        if (funds > maxFunds) {
            return CorporateActionManager.Result.fail("要约资金规模超过上限。");
        }

        // ---- 要约期 3–14 天 ----
        int minDays = FinanceConfig.takeoverMinDurationDays();
        int maxDays = FinanceConfig.takeoverMaxDurationDays();
        if (durationDays < minDays || durationDays > maxDays) {
            return CorporateActionManager.Result.fail("要约期须为 " + minDays + "～" + maxDays + " 天（MC 天）。");
        }

        String opKey = (key == null || key.isBlank()) ? UUID.randomUUID().toString() : key;
        CorporateActionManager.Result result = CorporateActionManager.startTender(
                buyer, companyId, price, targetShares, minShares, durationDays, day, opKey);
        if (result.success()) {
            EconomySavedData.markDirty();
        }
        return result;
    }

    // ================================================================
    // 接受 / 撤销（委托机械层，安全检查在 CorporateActionManager 内）
    // ================================================================

    public static synchronized CorporateActionManager.Result acceptTender(
            UUID holder, UUID offerId, long shares, long day, String key) {
        String opKey = (key == null || key.isBlank()) ? UUID.randomUUID().toString() : key;
        return CorporateActionManager.acceptTender(holder, offerId, shares, day, opKey);
    }

    public static synchronized CorporateActionManager.Result cancelTender(
            UUID requester, UUID offerId, long day, String key) {
        String opKey = (key == null || key.isBlank()) ? UUID.randomUUID().toString() : key;
        return CorporateActionManager.cancelTender(requester, offerId, day, opKey);
    }

    // ================================================================
    // 强制私有化（squeeze-out）
    // ================================================================

    /**
     * 强制私有化：持股 ≥ 90% 的股东按保护价强制收购剩余小股东股份，
     * 交割完成后目标公司退市。
     *
     * <p>保护价 = max(最近一次要约价, 近 7 天 VWAP, 当前 fairValue)，
     * 防止控股股东先做低公司再低价挤出小股东。
     */
    public static synchronized CorporateActionManager.Result squeezeOut(
            UUID squeezer, UUID companyId, long day, String key) {
        if (squeezer == null || companyId == null) {
            return CorporateActionManager.Result.fail("参数错误。");
        }
        Company company = CompanyManager.getCompany(companyId);
        if (company == null) {
            return CorporateActionManager.Result.fail("公司不存在。");
        }
        if (!company.isPublic()) {
            return CorporateActionManager.Result.fail("公司未上市，无需私有化。");
        }
        Stock stock = StockMarketManager.getStockByCompanyId(companyId);
        if (stock == null) {
            return CorporateActionManager.Result.fail("目标公司没有流通股票。");
        }
        if (CorporateActionManager.openTenderIdFor(companyId) != null) {
            return CorporateActionManager.Result.fail("该公司已有进行中的要约，请先等待其结束。");
        }

        // 90% 持股检查（含要约锁定份额口径）
        double votingPct = votingPercent(squeezer, companyId, day);
        double requiredPct = FinanceConfig.takeoverSqueezeOutBps() / 100.0;
        if (votingPct < requiredPct) {
            return CorporateActionManager.Result.fail(
                    String.format("强制私有化需持有 ≥ %.1f%% 有表决权股份（当前 %.2f%%）。", requiredPct, votingPct));
        }

        // 先撤掉订单簿上的单子，把份额退回持仓，再统一强制收购
        // （订单锁定的份额已从持仓扣除，不先撤单就收不齐）
        StockOrderManager.cancelOrdersForSymbol(stock.getSymbol());

        Map<UUID, Long> holdings = StockPortfolioManager.getHoldingsForCompany(stock.getSymbol());
        long squeezerShares = holdings.getOrDefault(squeezer, 0L);
        long minorityTotal = 0L;
        for (Map.Entry<UUID, Long> e : holdings.entrySet()) {
            if (e.getKey().equals(squeezer)) {
                continue;
            }
            // 锁定期内的创始人股份不可被强制收购 —— 锁定期的意义是防创始人提前退出
            if (stock.isFounderLocked(e.getKey())) {
                return CorporateActionManager.Result.fail("仍有股东处于创始人锁定期内，请等待锁定期结束。");
            }
            // 被其他公司行动锁定的份额暂不可强制收购
            if (CorporateActionManager.lockedSharesForHolder(e.getKey(), stock.getSymbol()) > 0) {
                return CorporateActionManager.Result.fail("有股东份额被其他公司行动锁定，请稍后再试。");
            }
            minorityTotal = addCap(minorityTotal, e.getValue());
        }
        if (minorityTotal <= 0) {
            return CorporateActionManager.Result.fail("没有可收购的少数股东股份。");
        }
        long totalVoting = stock.getVotingShares();
        if (squeezerShares + minorityTotal > totalVoting) {
            return CorporateActionManager.Result.fail("持股数据异常，无法发起强制私有化。");
        }

        long price = protectionPrice(companyId, stock);
        if (price <= 0) {
            return CorporateActionManager.Result.fail("无法确定公允的强制收购价。");
        }

        // 用普通要约机制承载强制收购：目标 = 全部少数股，最低接受量 = 目标（必须全收）
        // duration 取 1 天，下一日结算；自动锁定全部少数股东份额，无需逐一接受
        String opKey = (key == null || key.isBlank()) ? UUID.randomUUID().toString() : key;
        CorporateActionManager.Result opened = CorporateActionManager.startTender(
                squeezer, companyId, price, minorityTotal, minorityTotal, 1, day, opKey);
        if (!opened.success()) {
            return opened;
        }
        TenderOffer offer = CorporateActionManager.tenders().get(opened.id());
        if (offer == null) {
            return CorporateActionManager.Result.fail("强制要约创建失败。");
        }
        // 自动锁定全部少数股东份额
        for (Map.Entry<UUID, Long> e : holdings.entrySet()) {
            if (e.getKey().equals(squeezer) || e.getValue() <= 0) {
                continue;
            }
            long cost = StockPortfolioManager.getHolding(e.getKey(), stock.getSymbol())
                    .getAverageCost();
            if (!StockPortfolioManager.removeHolding(e.getKey(), stock.getSymbol(), e.getValue())
                    || !offer.accept(e.getKey(), e.getValue(), cost)) {
                // 回滚：已锁定的返还（此时要约刚创建，直接取消最干净）
                CorporateActionManager.cancelTender(
                        squeezer, offer.id(), day, UUID.randomUUID().toString());
                return CorporateActionManager.Result.fail("锁定少数股东份额失败，已取消。");
            }
        }
        offer.setSqueezeOut(true);
        EconomySavedData.markDirty();
        return CorporateActionManager.Result.ok(
                offer.id(), "强制私有化要约已发起，保护价 " + price + "，将收购 " + minorityTotal + " 股，交割后公司退市。");
    }

    /**
     * 保护价 = max(最近一次要约价, 近 7 天 VWAP, 当前 fairValue)。
     */
    public static long protectionPrice(UUID companyId, Stock stock) {
        long fairValue = Math.max(0L, stock.getFairValue());
        long vwap = vwap7d(stock.getSymbol());
        long lastTender = lastTenderPrice(companyId);
        return Math.max(fairValue, Math.max(vwap, lastTender));
    }

    /** 近 7 天 VWAP（典型价加权）；无成交时返回 0 */
    static long vwap7d(String symbol) {
        List<Candlestick> bars = CandlestickService.getBars(MarketInstrumentType.STOCK, symbol, 7);
        BigInteger pxq = BigInteger.ZERO;
        BigInteger q = BigInteger.ZERO;
        for (Candlestick bar : bars) {
            if (bar.volume() <= 0) {
                continue;
            }
            long typical = (bar.high() + bar.low() + bar.close()) / 3;
            pxq = pxq.add(BigInteger.valueOf(typical).multiply(BigInteger.valueOf(bar.volume())));
            q = q.add(BigInteger.valueOf(bar.volume()));
        }
        if (q.signum() <= 0) {
            return 0L;
        }
        return pxq.divide(q).longValue();
    }

    /** 该公司最近一次要约的要约价；无则返回 0 */
    static long lastTenderPrice(UUID companyId) {
        return CorporateActionManager.tenders().values().stream()
                .filter(o -> o.targetCompanyId().equals(companyId))
                .max(Comparator.comparingLong(TenderOffer::startDay))
                .map(TenderOffer::price)
                .orElse(0L);
    }

    // ================================================================
    // 查询
    // ================================================================

    /** 某玩家对某公司的有表决权股份占比（0–100） */
    public static double votingPercent(UUID playerId, UUID companyId, long day) {
        var snapshot = ShareholderRegistryService.snapshot(companyId, day);
        for (var holder : snapshot.holders()) {
            if (holder.id().equals(playerId)) {
                // 融资买入的股份是"借钱买的"，不计入有表决权股份 —— 防"借钱举牌"操纵并购
                long financed = finance.margin.MarginService.financedSharesOf(playerId, snapshot.symbol());
                long voting = Math.max(0, holder.total() - financed);
                long votingShares = snapshot.votingShares();
                return votingShares <= 0 ? 0 : voting * 100.0 / votingShares;
            }
        }
        return 0.0;
    }

    /** 进行中的要约（全部公司） */
    public static List<TenderOffer> openTenders() {
        return CorporateActionManager.tenders().values().stream()
                .filter(o -> o.status() == CapitalActionStatus.OPEN)
                .sorted(Comparator.comparingLong(TenderOffer::startDay).reversed())
                .toList();
    }

    private static long addCap(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
