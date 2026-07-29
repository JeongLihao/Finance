package finance.company;

import finance.account.AccountManager;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;

import java.util.UUID;

/**
 * 公司 IPO 服务 —— 玩家公司上市和股票发行。
 */
public class CompanyIPOService {

    /** 上市费用 */
    public static final long IPO_FEE = 5_000L;

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

        long raisedCapital;
        try {
            raisedCapital = Math.multiplyExact(issuePrice, issueQuantity);
        } catch (ArithmeticException ex) {
            return fail("募资金额过大。");
        }

        String symbol = generateUniqueSymbol(company.getName());

        // 检查上市费用
        if (!AccountManager.withdraw(requesterId, IPO_FEE)) {
            return fail("上市费用不足，需要 " + IPO_FEE + "。");
        }

        // 计算创始人持股和总股本
        // 发行数量 = 总股本 × 40%，所以总股本 = 发行数量 / 0.4
        long totalShares = Math.round(issueQuantity / DEFAULT_FLOAT_RATIO);
        long ownerShares = totalShares - issueQuantity;

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
        company.deposit(raisedCapital);
        StockPortfolioManager.addHolding(requesterId, symbol, ownerShares, issuePrice);

        // 标记公司为已上市
        company.setPublic(true);

        // 注册股票
        StockMarketManager.putStockDirect(stock);

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

    private static IPOResult fail(String message) {
        return new IPOResult(false, message, null);
    }
}
