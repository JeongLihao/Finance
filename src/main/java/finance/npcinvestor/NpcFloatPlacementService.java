package finance.npcinvestor;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.governance.ShareholderRegistryService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.UUID;

/**
 * 残差配售：NPC 投资者从"已发行但无人持有"的系统流动性残差里买入股份。
 *
 * <p>IPO 时公司已按全部发行量收过一次铸造募资，所以配售款不再付给公司，
 * 而是作为有类型记录的系统水槽销毁（先例：{@code MarketMakerLedger.seedStockInventory}）。
 * 股份守恒：残差减少多少，持仓就增加多少；残差不足时拒绝，绝不超发。</p>
 *
 * <p>只接受 NPC 投资者账户调用，玩家不能走这个入口。</p>
 */
public final class NpcFloatPlacementService {
    /** 配售款去向的记账标识。不是真实账户，资金在这里销毁，任何代码都不能往这个 UUID 转钱。 */
    public static final UUID PLACEMENT_SINK = UUID.nameUUIDFromBytes(
            "finance-npc-float-placement-sink".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private NpcFloatPlacementService() {}

    public record Result(boolean success, long quantity, long price, long cost, String message) {
        static Result fail(String message) {
            return new Result(false, 0, 0, 0, message);
        }
    }

    /** 配售价 = max(现价, 公允价)，避免 NPC 低价吸筹。 */
    public static long placementPrice(Stock stock) {
        if (stock == null) return 0;
        return Math.max(stock.getLastPrice(), stock.getFairValue());
    }

    /** 当前可配售的残差股数。股本核对不一致时返回 0。 */
    public static long availableResidual(Stock stock, long day) {
        if (stock == null) return 0;
        ShareholderRegistryService.Snapshot snapshot = ShareholderRegistryService.snapshot(stock.getCompanyId(), day);
        if (!snapshot.consistent()) return 0;
        return Math.max(0, snapshot.systemLiquidity());
    }

    public static Result place(UUID investor, String symbol, long quantity, long day) {
        NpcInvestorArchetype archetype = NpcInvestorArchetype.fromAccount(investor);
        // 竞争公司的投资账户也可以配售（敌对收购建仓，单人经济 P4），玩家账户不行
        if (archetype == null && !finance.rival.RivalManager.isInvestmentAccount(investor)) {
            return Result.fail("只有 NPC 投资者可以参与残差配售。");
        }
        if (quantity <= 0) return Result.fail("数量必须为正。");
        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) return Result.fail("未知股票。");
        if (stock.isTradingHalted()) return Result.fail("股票停牌中。");
        long residual = availableResidual(stock, day);
        if (residual < quantity) return Result.fail("可配售残差不足。");
        long price = placementPrice(stock);
        if (price <= 0) return Result.fail("价格异常。");
        long cost;
        try {
            cost = Math.multiplyExact(price, quantity);
        } catch (ArithmeticException e) {
            return Result.fail("金额过大。");
        }
        NpcInvestorManager.ensureAccounts();
        if (!StockPortfolioManager.canAddHolding(investor, stock.getSymbol(), quantity)) {
            return Result.fail("持仓无法容纳。");
        }
        if (!AccountManager.withdraw(investor, cost)) return Result.fail("资金不足。");
        if (!StockPortfolioManager.addHolding(investor, stock.getSymbol(), quantity, price)) {
            AccountManager.deposit(investor, cost);
            return Result.fail("持仓结算失败。");
        }
        AccountManager.addTransactionRecord(new TransactionRecord(
                investor,
                PLACEMENT_SINK,
                cost,
                TransactionType.NPC_FLOAT_PLACEMENT,
                investor,
                stock.getSymbol(),
                quantity));
        EconomySavedData.markDirty();
        return new Result(true, quantity, price, cost, "配售成功");
    }
}
