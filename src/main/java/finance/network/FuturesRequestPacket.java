package finance.network;

import finance.chart.CandlestickService;
import finance.futures.*;
import finance.market.NpcMarketMaker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record FuturesRequestPacket(long requestId) {
    private static final UUID PRIVATE_ORDER = new UUID(0, 0);

    public static void encode(FuturesRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.requestId);
    }

    public static FuturesRequestPacket decode(FriendlyByteBuf buffer) {
        return new FuturesRequestPacket(buffer.readLong());
    }

    public static void handle(FuturesRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> sendSnapshot(packet, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void sendSnapshot(FuturesRequestPacket packet, ServerPlayer player) {
        if (player == null || packet.requestId <= 0
                || !MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "futures-request")) return;
        UUID owner = player.getUUID();
        MarginAccount account = MarginManager.accounts().get(owner);
        if (account == null) account = new MarginAccount(owner, 0, 0, MarginRiskStatus.NORMAL, -1);
        long currentDay = CandlestickService.currentMcDay();
        List<FuturesResponsePacket.ContractRow> contracts = FuturesMarketManager.contracts().values().stream()
                .limit(FuturesResponsePacket.MAX_ROWS).map(contract -> {
                    var spot = NpcMarketMaker.getMarketPrice(contract.commodityId());
                    return new FuturesResponsePacket.ContractRow(contract.id(), contract.code(), contract.commodityId(),
                            contract.status(), contract.contractSize(), contract.lastTradingDay(), contract.maturityDay(),
                            FuturesMarketManager.riskPrice(contract.id()), FuturesClearingService.lastSettlementPrice(contract.id()),
                            spot == null ? 0 : spot.getMidPrice(), FuturesMarketManager.openInterest(contract.id()),
                            FuturesMarketManager.dailyVolume(contract.id(), currentDay));
                }).toList();
        List<FuturesResponsePacket.PositionRow> positions = MarginManager.positions().values().stream()
                .filter(position -> position.ownerId().equals(owner)).limit(FuturesResponsePacket.MAX_ROWS).map(position -> {
                    FuturesContract contract = FuturesMarketManager.contract(position.contractId());
                    long price = FuturesMarketManager.riskPrice(position.contractId()), unrealized = 0;
                    try { if (contract != null) unrealized = FuturesMath.signedPnl(position.settlementReferencePrice(), price,
                            contract.contractSize(), position.signedQuantity()); } catch (ArithmeticException ignored) { }
                    return new FuturesResponsePacket.PositionRow(position.contractId(), position.signedQuantity(),
                            position.averageEntryPrice(), position.settlementReferencePrice(), unrealized, position.realizedPnl());
                }).toList();
        List<FuturesResponsePacket.OrderRow> orders = FuturesMarketManager.orders().stream()
                .limit(FuturesResponsePacket.MAX_ROWS).map(order -> {
                    boolean owned = order.playerId().equals(owner);
                    return new FuturesResponsePacket.OrderRow(owned ? order.orderId() : PRIVATE_ORDER, order.contractId(),
                            order.side(), order.limitPrice(), order.remainingQuantity(), owned);
                }).toList();
        List<FuturesResponsePacket.SettlementRow> settlements = FuturesClearingService.recentHistory(FuturesResponsePacket.MAX_ROWS).stream()
                .map(row -> new FuturesResponsePacket.SettlementRow(row.contractId(), row.day(), row.settlementPrice(),
                        row.guaranteeFundUsed(), row.profitHaircut(), row.finalSettlement())).toList();
        long initial = MarginManager.initialRequirement(owner);
        long available = Math.max(0, account.cashBalance() - account.frozenForOrders() - Math.max(0, initial));
        FuturesResponsePacket response = new FuturesResponsePacket(packet.requestId, account.cashBalance(),
                account.frozenForOrders(), FuturesRiskService.equity(owner), initial,
                MarginManager.maintenanceRequirement(owner), available, account.riskStatus(),
                FuturesClearingService.guaranteeFund(), FuturesClearingService.todayFundUse(),
                contracts, positions, orders, settlements);
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
    }
}
