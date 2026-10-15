package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.MarketMakerLedger;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;

import java.util.UUID;

/**
 * 公司 IPO 服务 —— 玩家公司上市和股票发行。
 */
public class CompanyIPOService {

    /** 上市费用默认值；实际费用从服务器配置读取。 */
    public static final long IPO_FEE = 5_000L;

    /**
     * 发行价上限 —— 单股发行价不得超过该值。
     * 经济量级参考：新手初始资金 1,000；建公司 10,000；系统公司初始现金 50,000。
     * 按最大发行量 100,000 股、总股本 250,000 股计，上限 100 对应总估值 2,500 万，
     * 为系统公司初始规模的 500 倍，覆盖正常游戏内估值空间。
     * 注意：仅靠单价上限挡不住刷钱（100 × 100,000 股仍可铸造 1,000 万），
     * 必须配合下方的募资总额上限。
     */
    public static final long MAX_ISSUE_PRICE = 100L;

    /**
     * 单次 IPO 募资总额上限 —— issuePrice × issueQuantity 不得超过该值。
     * 真实账本（P0-1 修正版，旧注释称"净亏 5000"是错的）：
     * 成本侧：建公司 10,000（玩家余额扣除）＋ 上市费 ipoFee（配置下限钳制 ≥5,000）＝ ≥15,000；
     * 收入侧：新公司自带 5,000 种子现金（INITIAL_PLAYER_COMPANY_CASH）＋ 本次募资 raisedCapital
     * （≤10,000，本上限卡死；IPO 资金为发行铸造，直接进公司现金）＝ ≤15,000；
     * 提现侧：公司现金提现对种子现金无限制，募资部分经所有者提现通道也可全额取出。
     * 因此 ipoFee=5,000 且募资拉满时，"建公司→IPO→提现"整条链净损益为 0——不亏，但也无利可图。
     * 安全边际真正来自两点：(1) ipoFee 下限钳制 ≥5000——旧下限允许配 0，此时成本只剩 10,000、
     * 可提 15,000，每小号净赚 5,000；(2) 每人限建一家公司，重复开小号也只能打平，时间成本下无利可图。
     * 正常小额募资不受影响。
     */
    public static final long MAX_TOTAL_RAISE = 10_000L;

    /** 默认发行比例 —— 创始人保留 60%，发行 40% */
    public static final double DEFAULT_FLOAT_RATIO = 0.4;

    public record IPOResult(boolean success, String message, Stock stock) {}

    /**
     * 玩家公司进行 IPO —— 发行股票。
     *
     * @param companyId       公司 UUID
     * @param issuePrice      发行价
     * @param issueQuantity   发行数量（流通股）
     * @return 结果和新生成的 Stock 对象
     */
    public static IPOResult ipo(UUID requesterId, UUID companyId, long issuePrice, long issueQuantity) {
        if (companyId == null) {
            return fail("缺少公司。");
        }

        Company company = CompanyManager.getCompany(companyId);
        if (company == null) {
            return fail("公司不存在。");
        }

        if (company.isPublic()) {
            return fail("公司已上市，无法重复 IPO。");
        }

        if (requesterId == null || company.getOwnerId() == null || !company.getOwnerId().equals(requesterId)) {
            return fail("只有公司所有者可以发起 IPO。");
        }

        if (issuePrice <= 0 || issueQuantity <= 0) {
            return fail("发行价和数量必须为正。");
        }

        if (issueQuantity > 100_000L) {
            return fail("发行数量过多（最多 100,000）。");
        }

        if (issuePrice > MAX_ISSUE_PRICE) {
            return fail("发行价过高（上限 " + MAX_ISSUE_PRICE + "）。");
        }

        long raisedCapital;
        try {
            raisedCapital = Math.multiplyExact(issuePrice, issueQuantity);
        } catch (ArithmeticException ex) {
            return fail("募资金额过大。");
        }

        if (raisedCapital > MAX_TOTAL_RAISE) {
            return fail("募资总额超过上限 " + MAX_TOTAL_RAISE + "（请降低发行价或发行数量）。");
        }

        String symbol = generateUniqueSymbol(company.getName());

        // 检查上市费用
        long ipoFee = FinanceConfig.ipoFee();
        if (!AccountManager.withdraw(requesterId, ipoFee)) {
            return fail("上市费用不足，需要 " + ipoFee + "。");
        }

        // 计算创始人持股和总股本
        // 发行数量 = 总股本 × 40%，所以总股本 = 发行数量 / 0.4
        long totalShares = Math.round(issueQuantity / DEFAULT_FLOAT_RATIO);
        long ownerShares = totalShares - issueQuantity;

        if (!company.canDeposit(raisedCapital)) {
            AccountManager.deposit(requesterId, ipoFee);
            return fail("公司现金余额无法容纳本次募资。");
        }
        if (!StockPortfolioManager.canAddHolding(requesterId, symbol, ownerShares)) {
            AccountManager.deposit(requesterId, ipoFee);
            return fail("创始人持仓无法容纳本次发行的股份。");
        }

        // 创建股票
        Stock stock = new Stock(
                symbol,
                company.getName(),
                companyId,
                totalShares,
                issueQuantity,      // floatShares
                ownerShares,         // ownerShares
                issuePrice,          // currentPrice
                issuePrice           // fairValue
        );

        // 募集资金进公司现金
        // IPO capital is an explicit issuance source; it is not transferred from the listing fee.
        if (!company.deposit(raisedCapital)) {
            AccountManager.deposit(requesterId, ipoFee);
            return fail("IPO 募资结算失败。");
        }
        if (!StockPortfolioManager.addHolding(requesterId, symbol, ownerShares, issuePrice)) {
            company.withdraw(raisedCapital);
            AccountManager.deposit(requesterId, ipoFee);
            return fail("创始人股份结算失败。");
        }

        // 创始人股份锁定期 —— 防止上市即抛售砸盘套现
        stock.setFounderLockup(requesterId, Stock.FOUNDER_SHARE_LOCKUP_DAYS);

        // 标记公司为已上市
        company.setPublic(true);

        // 财报季：上市前累积的季度流水清零，避免 IPO 后首份季报混入上市前经营数据
        company.drainQuarter();

        // 注册股票
        StockMarketManager.putStockDirect(stock);

        // 做市商 IPO 预留：发行量 × 预留比例以发行价划入做市商库存。
        // 余额不足则跳过预留，不阻断 IPO。
        reserveForMarketMaker(company, stock, issuePrice, issueQuantity);

        AccountManager.addTransactionRecord(
                new TransactionRecord(
                        requesterId,
                        companyId,
                        raisedCapital,
                        TransactionType.COMPANY_IPO,
                        requesterId,
                        company.getName() + "/" + symbol,
                        issueQuantity
                )
        );

        EconomySavedData.markDirty();

        return new IPOResult(true,
                "IPO 成功！募集资金 " + raisedCapital + "，发行 " + issueQuantity + " 股，创始人保留 " + ownerShares + " 股（"
                + String.format("%.1f", (double) ownerShares / totalShares * 100) + "%）。",
                stock);
    }

    /**
     * 生成股票代码（公司名前 4 个字符的大写首字母组合）。
     */
    private static String generateSymbol(String companyName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, companyName.length()); i++) {
            char c = companyName.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        // 如果名称没有足够的字母，补充数字
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

    /**
     * 做市商 IPO 预留 —— 发行量 × {@link FinanceConfig#ipoMarketMakerReserveRatio()}
     * 以发行价认购，资金从做市商总账户（nil UUID）真实支付给公司，
     * 股份记入做市商股票子账本库存。
     *
     * <p>这是做市商初始库存的正规来源（一级市场联动），区别于二级市场的
     * 终身接盘计数（{@code marketMakerBoughtShares}），不计入 200 股熔断。</p>
     *
     * <p>做市商总账户余额不足时跳过预留，不阻断 IPO。</p>
     */
    private static void reserveForMarketMaker(Company company, Stock stock, long issuePrice, long issueQuantity) {
        double ratio = FinanceConfig.ipoMarketMakerReserveRatio();
        if (ratio <= 0) {
            return;
        }
        long reserveQty = (long) Math.floor(issueQuantity * ratio);
        if (reserveQty <= 0) {
            return;
        }
        long reserveCost;
        try {
            reserveCost = Math.multiplyExact(issuePrice, reserveQty);
        } catch (ArithmeticException ex) {
            return;
        }
        if (reserveCost <= 0) {
            return;
        }
        if (!AccountManager.withdraw(MarketMakerLedger.MASTER_UUID, reserveCost)) {
            return; // 余额不足：跳过预留
        }
        if (!company.deposit(reserveCost)) {
            AccountManager.deposit(MarketMakerLedger.MASTER_UUID, reserveCost);
            return;
        }
        MarketMakerLedger.addInventory(MarketMakerLedger.SubMarket.STOCK, stock.getSymbol(), reserveQty);
        AccountManager.addTransactionRecord(
                new TransactionRecord(
                        MarketMakerLedger.MASTER_UUID,
                        company.getCompanyId(),
                        reserveCost,
                        TransactionType.MARKET_MAKER_BUY,
                        MarketMakerLedger.MASTER_UUID,
                        stock.getSymbol(),
                        reserveQty
                )
        );
        EconomySavedData.markDirty();
    }

    private static IPOResult fail(String message) {
        return new IPOResult(false, message, null);
    }
}
