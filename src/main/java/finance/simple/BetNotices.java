package finance.simple;

import finance.feedback.FeedbackSeverity;
import finance.feedback.PlayerNotices;
import finance.futures.FuturesContract;
import finance.futures.FuturesMath;
import finance.futures.FuturesPosition;
import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import finance.margin.MarginService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.util.MoneyFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 押注的主动提醒：被强制结束、到期结算、快被强制结束。
 * 在线玩家马上在聊天栏看到并听到提示音；离线的存进反馈队列，上线时补发，战绩也在上线后补记。
 */
public final class BetNotices {

    private BetNotices() {}

    /** 每个玩家每个 MC 日最多预警一次。 */
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    /** 一笔股票押注被强制结束（每分钟检查和日终检查都会调用）。 */
    public static void liquidated(MarginService.Liquidation l) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || l == null) return;
        Stock stock = StockMarketManager.getStock(l.symbol());
        String name = stock == null ? l.symbol() : stock.getName();
        String key = "s:" + l.symbol() + (l.shortSide() ? ":down" : ":up");
        String side = l.shortSide() ? "看跌" : "看涨";
        ServerPlayer player = server.getPlayerList().getPlayer(l.player());
        String pnlText = "";
        if (player != null) {
            Long pnl = SimpleBetService.settleReturned(player, key, l.returned());
            if (pnl != null)
                pnlText = pnl >= 0 ? "，这笔赚了 " + MoneyFormat.pounds(pnl) : "，这笔亏了 " + MoneyFormat.pounds(-pnl);
        } else {
            SimpleBetService.defer(l.player(), key, l.returned(), false);
        }
        PlayerNotices.tell(
                server,
                l.player(),
                FeedbackSeverity.CRITICAL,
                SoundEvents.ANVIL_LAND,
                "finance.bet.liquidated",
                name,
                side,
                Long.toString(l.quantity()),
                MoneyFormat.pounds(l.price()),
                MoneyFormat.pounds(l.returned()),
                pnlText);
    }

    /** 商品押注到期结算：按结算价算赚亏、把押金退回钱包。 */
    public static void futuresSettled(FuturesContract contract, long price, List<FuturesPosition> positions) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || contract == null) return;
        String name = SimpleBetService.commodityName(contract.commodityId());
        String key = "f:" + contract.id();
        for (FuturesPosition p : positions) {
            long pnl = FuturesMath.signedPnl(p.averageEntryPrice(), price, contract.contractSize(), p.signedQuantity());
            ServerPlayer player = server.getPlayerList().getPlayer(p.ownerId());
            if (player != null) SimpleBetService.settlePnl(player, key, pnl);
            else SimpleBetService.defer(p.ownerId(), key, pnl, true);
            // 先退这一笔的押金（开仓时那份保证金）和赚亏，身上还有别的商品押注也照退；没押注了再把剩下的全退
            long stake = FuturesMath.margin(
                    p.averageEntryPrice(),
                    contract.contractSize(),
                    p.quantity(),
                    finance.config.FinanceConfig.futuresInitialMarginBps());
            long back = SimpleBetService.releaseFutures(p.ownerId(), Math.max(0, stake) + pnl)
                    + SimpleBetService.sweepIdle(p.ownerId());
            String result = pnl >= 0 ? "，赚了 " + MoneyFormat.pounds(pnl) : "，亏了 " + MoneyFormat.pounds(-pnl);
            PlayerNotices.tell(
                    server,
                    p.ownerId(),
                    FeedbackSeverity.NOTICE,
                    pnl >= 0 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_LAND,
                    "finance.bet.settled",
                    name,
                    p.signedQuantity() > 0 ? "看涨" : "看跌",
                    result,
                    MoneyFormat.pounds(back));
        }
    }

    /** 安全度跌到强制结束线上方 20 个百分点以内时，提醒在线玩家（每天一次）。 */
    public static void warnDanger(MinecraftServer server, long day) {
        if (server == null) return;
        double m = MarginService.maintenanceRatio();
        for (Map.Entry<UUID, MarginAccount> e : MarginPoolManager.accounts().entrySet()) {
            if (!e.getValue().hasOpenPositions() || WARNED.getOrDefault(e.getKey(), -1L) == day) continue;
            double ratio = MarginService.marginRatio(e.getKey());
            if (!Double.isFinite(ratio) || ratio < m || ratio >= m + 20) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) continue;
            WARNED.put(e.getKey(), day);
            PlayerNotices.tell(
                    server,
                    e.getKey(),
                    FeedbackSeverity.WARNING,
                    SoundEvents.NOTE_BLOCK_BELL.value(),
                    "finance.bet.warning",
                    Long.toString(Math.round(ratio)),
                    Long.toString(Math.round(m)));
        }
        if (WARNED.size() > 4_096) WARNED.clear();
    }
}
