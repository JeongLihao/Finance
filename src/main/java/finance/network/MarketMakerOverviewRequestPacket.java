package finance.network;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.chart.CandlestickService;
import finance.commodity.CommodityInventoryManager;
import finance.futures.FuturesMarketManager;
import finance.futures.MarginManager;
import finance.market.CentralBankLoanLedger;
import finance.market.MarketMakerLedger;
import finance.market.MarketMakerPauseState;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 做市商总览快照请求（Phase 4，客户端 → 服务端）。
 *
 * <p>仅管理员（2 级权限）可请求；服务端按 {@code FuturesRequestPacket} 的
 * 限流/快照模式返回 {@link MarketMakerOverviewResponsePacket}。</p>
 */
public record MarketMakerOverviewRequestPacket(long requestId) {

    public static void encode(MarketMakerOverviewRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.requestId);
    }

    public static MarketMakerOverviewRequestPacket decode(FriendlyByteBuf buffer) {
        return new MarketMakerOverviewRequestPacket(buffer.readLong());
    }

    public static void handle(MarketMakerOverviewRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> sendSnapshot(packet, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void sendSnapshot(MarketMakerOverviewRequestPacket packet, ServerPlayer player) {
        if (player == null || packet.requestId <= 0) {
            return;
        }
        if (!player.hasPermissions(2)) {
            GuiFeedbackPacket.send(player, "权限不足，需要管理员权限。");
            return;
        }
        if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "mm-overview")) {
            GuiFeedbackPacket.send(player, "请求过于频繁。");
            return;
        }
        long masterCash = AccountManager.getBalance(MarketMakerLedger.MASTER_UUID);
        long loanOutstanding = CentralBankLoanLedger.outstanding(MarketMakerLedger.SubMarket.FUTURES);
        List<MarketMakerOverviewResponsePacket.MarketRow> rows =
                List.of(commodityRow(masterCash), stockRow(masterCash), futuresRow());
        MarketMakerOverviewResponsePacket response =
                new MarketMakerOverviewResponsePacket(packet.requestId, masterCash, loanOutstanding, rows);
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
    }

    // ---- 分市场快照 ----

    private static MarketMakerOverviewResponsePacket.MarketRow commodityRow(long masterCash) {
        long inventoryQty = 0;
        long inventoryValue = 0;
        long todayVolumeQty = 0;
        int haltedCount = 0;
        for (MarketPrice mp : NpcMarketMaker.getAllMarketPrices().values()) {
            String id = mp.getCommodityId();
            long qty = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, id);
            inventoryQty = saturatedAdd(inventoryQty, qty);
            inventoryValue = saturatedAdd(inventoryValue, saturatedMul(qty, Math.max(1, mp.getMidPrice())));
            todayVolumeQty = saturatedAdd(todayVolumeQty, mp.getDayVolume());
            // Phase 2 约定：market.liquidity.QuoteEngine#isHalted("commodity", id)
            if (finance.market.liquidity.QuoteEngine.isHalted("commodity", id)) {
                haltedCount++;
            }
        }
        long[] flow = commodityFlowToday();
        return new MarketMakerOverviewResponsePacket.MarketRow(
                "commodity",
                masterCash,
                inventoryQty,
                inventoryValue,
                0,
                todayVolumeQty,
                saturatedAdd(flow[0], flow[1]),
                flow[0] - flow[1],
                MarketMakerPauseState.isPaused(MarketMakerLedger.SubMarket.COMMODITY),
                haltedCount,
                0);
    }

    private static MarketMakerOverviewResponsePacket.MarketRow stockRow(long masterCash) {
        long inventoryQty = 0;
        long inventoryValue = 0;
        long todayVolumeQty = 0;
        int haltedCount = 0;
        int cappedCount = 0;
        for (Stock stock : StockMarketManager.getListedStocks()) {
            String symbol = stock.getSymbol();
            long inv = MarketMakerLedger.getStockInventory(symbol);
            inventoryQty = saturatedAdd(inventoryQty, inv);
            inventoryValue = saturatedAdd(inventoryValue, saturatedMul(inv, Math.max(0, stock.getFairValue())));
            todayVolumeQty = saturatedAdd(todayVolumeQty, stock.getDayVolume());
            // Phase 2 约定：market.liquidity.QuoteEngine#isHalted("stock", symbol)
            if (finance.market.liquidity.QuoteEngine.isHalted("stock", symbol)) {
                haltedCount++;
            }
            long max = MarketMakerLedger.maxInventory(stock.getFloatShares());
            if (max > 0 && inv >= max) {
                cappedCount++;
            }
        }
        long[] flow = stockFlowToday();
        return new MarketMakerOverviewResponsePacket.MarketRow(
                "stock",
                masterCash,
                inventoryQty,
                inventoryValue,
                0,
                todayVolumeQty,
                saturatedAdd(flow[0], flow[1]),
                flow[0] - flow[1],
                MarketMakerPauseState.isPaused(MarketMakerLedger.SubMarket.STOCK),
                haltedCount,
                cappedCount);
    }

    private static MarketMakerOverviewResponsePacket.MarketRow futuresRow() {
        UUID mm = MarketMakerLedger.FUTURES_MARGIN_UUID;
        var marginAccount = MarginManager.accounts().get(mm);
        long funds = marginAccount == null ? 0 : Math.max(0, marginAccount.cashBalance());
        long netPosition = 0;
        long todayVolumeQty = 0;
        long day = CandlestickService.currentMcDay();
        // Phase 3 约定：futures.FuturesMarketMaker#getNetPosition(UUID)
        for (UUID contractId : FuturesMarketManager.contracts().keySet()) {
            netPosition = saturatedAdd(netPosition, finance.futures.FuturesMarketMaker.getNetPosition(contractId));
            todayVolumeQty = saturatedAdd(todayVolumeQty, FuturesMarketManager.dailyVolume(contractId, day));
        }
        long[] flow = futuresFlowToday();
        return new MarketMakerOverviewResponsePacket.MarketRow(
                "futures",
                funds,
                0,
                0,
                netPosition,
                todayVolumeQty,
                saturatedAdd(flow[0], flow[1]),
                flow[0] - flow[1],
                MarketMakerPauseState.isPaused(MarketMakerLedger.SubMarket.FUTURES),
                0,
                0);
    }

    // ---- 今日资金流（做市商视角：流入 − 流出） ----

    /** 商品做市商：流入=COMMODITY_BUY（玩家→做市商），流出=COMMODITY_SELL。返回 [inflow, outflow]。 */
    private static long[] commodityFlowToday() {
        return flowToday(TransactionType.COMMODITY_BUY, TransactionType.COMMODITY_SELL);
    }

    /** 股票做市商：流入=MARKET_MAKER_SELL（做市商卖给玩家），流出=MARKET_MAKER_BUY。 */
    private static long[] stockFlowToday() {
        return flowToday(TransactionType.MARKET_MAKER_SELL, TransactionType.MARKET_MAKER_BUY);
    }

    /** 期货做市商：FUTURES_TRADE 中 f002 为对手方的记录；Phase 3 落地前一般为空。 */
    private static long[] futuresFlowToday() {
        UUID mm = MarketMakerLedger.FUTURES_MARGIN_UUID;
        long inflow = 0;
        long outflow = 0;
        LocalDate today = LocalDate.now();
        for (TransactionRecord record : AccountManager.getTransactions()) {
            if (record.getType() != TransactionType.FUTURES_TRADE || !isToday(record, today)) {
                continue;
            }
            if (mm.equals(record.getTo())) {
                inflow = saturatedAdd(inflow, Math.max(0, record.getAmount()));
            } else if (mm.equals(record.getFrom())) {
                outflow = saturatedAdd(outflow, Math.max(0, record.getAmount()));
            }
        }
        return new long[] {inflow, outflow};
    }

    private static long[] flowToday(TransactionType inflowType, TransactionType outflowType) {
        long inflow = 0;
        long outflow = 0;
        LocalDate today = LocalDate.now();
        for (TransactionRecord record : AccountManager.getTransactions()) {
            if (!isToday(record, today)) {
                continue;
            }
            long amount = Math.max(0, record.getAmount());
            if (record.getType() == inflowType) {
                inflow = saturatedAdd(inflow, amount);
            } else if (record.getType() == outflowType) {
                outflow = saturatedAdd(outflow, amount);
            }
        }
        return new long[] {inflow, outflow};
    }

    private static boolean isToday(TransactionRecord record, LocalDate today) {
        return record.getTimestamp() != null
                && today.equals(record.getTimestamp().toLocalDate());
    }

    private static long saturatedAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        if (b < 0 && a < Long.MIN_VALUE - b) {
            return Long.MIN_VALUE;
        }
        return a + b;
    }

    private static long saturatedMul(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }
}
