package finance.network;

import finance.cycle.EconomyCycleService;
import finance.governance.CorporateActionManager;
import finance.governance.CorporateRestructuringService;
import finance.diagnostic.ModuleHealthRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public record GovernanceActionPacket(Action action, UUID targetId, long price, long quantity,
                                     long minimum, int duration, String operationKey) {
    public enum Action {
        START_BUYBACK, ACCEPT_BUYBACK, START_TENDER, ACCEPT_TENDER,
        CANCEL_BUYBACK, CANCEL_TENDER, RETIRE_TREASURY,
        EXECUTE_RECAPITALIZATION, EXECUTE_ASSET_PURCHASE
    }

    public static void encode(GovernanceActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeBoolean(packet.targetId != null);
        if (packet.targetId != null) buffer.writeUUID(packet.targetId);
        buffer.writeLong(packet.price);
        buffer.writeLong(packet.quantity);
        buffer.writeLong(packet.minimum);
        buffer.writeVarInt(packet.duration);
        buffer.writeUtf(packet.operationKey == null ? "" : packet.operationKey, 96);
    }

    public static GovernanceActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID id = buffer.readBoolean() ? buffer.readUUID() : null;
        long price = buffer.readLong();
        long quantity = buffer.readLong();
        long minimum = buffer.readLong();
        int duration = buffer.readVarInt();
        String key = buffer.readUtf(96);
        if (duration < 0 || duration > 90) throw new IllegalArgumentException("governance duration");
        return new GovernanceActionPacket(action, id, price, quantity, minimum, duration, key);
    }

    public static void handle(GovernanceActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || packet.action == null || packet.targetId == null
                    || packet.operationKey == null || packet.operationKey.isBlank()
                    || packet.operationKey.length() > 96) return;
            if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.STOCK)) {
                sendResult(player,packet.targetId,false);
                GuiFeedbackPacket.send(player, "股票与治理模块已因一致性问题暂停写入");
                return;
            }
            if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(),
                    "governance:" + packet.action)) {
                sendResult(player,packet.targetId,false);
                GuiFeedbackPacket.send(player, "操作过于频繁");
                return;
            }
            long day = EconomyCycleService.currentMcDay(player.server);
            CorporateActionManager.Result result = switch (packet.action) {
                case START_BUYBACK -> CorporateActionManager.startBuyback(player.getUUID(), packet.targetId,
                        packet.price, packet.quantity, packet.duration, day, packet.operationKey);
                case ACCEPT_BUYBACK -> CorporateActionManager.acceptBuyback(player.getUUID(), packet.targetId,
                        packet.quantity, day, packet.operationKey);
                case START_TENDER -> CorporateActionManager.startTender(player.getUUID(), packet.targetId,
                        packet.price, packet.quantity, packet.minimum, packet.duration, day, packet.operationKey);
                case ACCEPT_TENDER -> CorporateActionManager.acceptTender(player.getUUID(), packet.targetId,
                        packet.quantity, day, packet.operationKey);
                case CANCEL_BUYBACK -> CorporateActionManager.cancelBuyback(player.getUUID(), packet.targetId,
                        day, packet.operationKey);
                case CANCEL_TENDER -> CorporateActionManager.cancelTender(player.getUUID(), packet.targetId,
                        day, packet.operationKey);
                case RETIRE_TREASURY -> CorporateActionManager.retireTreasury(player.getUUID(), packet.targetId,
                        packet.quantity, day, packet.operationKey);
                case EXECUTE_RECAPITALIZATION -> CorporateRestructuringService.emergencyContribution(
                        player.getUUID(), packet.targetId, day, packet.operationKey);
                case EXECUTE_ASSET_PURCHASE -> CorporateRestructuringService.purchaseInventoryAsset(
                        player.getUUID(), packet.targetId, day, packet.operationKey);
            };
            sendResult(player,packet.targetId,result.success());
            GuiFeedbackPacket.send(player, result.message());
        });
        context.setPacketHandled(true);
    }

    private static void sendResult(ServerPlayer player,UUID targetId,boolean success){
        if(player!=null&&targetId!=null)FinancePacketHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(()->player),new GovernanceActionResultPacket(targetId,success));
    }
}
