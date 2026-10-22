package finance.network;

import finance.caravan.CaravanService;
import finance.cycle.EconomyCycleService;
import finance.gui.SettlementGuiOpener;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.settlement.SettlementService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** 贸易站“商路”页：从这个聚落建商队路线，暂停、恢复或关闭自己的路线。 */
public record CaravanActionPacket(
        Action action,
        UUID settlementId,
        UUID target,
        String commodityId,
        int quantity,
        int guardLevel,
        boolean insured) {

    public enum Action {
        CREATE,
        PAUSE,
        RESUME,
        CLOSE
    }

    public static void encode(CaravanActionPacket p, FriendlyByteBuf b) {
        b.writeEnum(p.action);
        b.writeUUID(p.settlementId);
        b.writeUUID(p.target);
        b.writeUtf(p.commodityId == null ? "" : p.commodityId, NetworkValidation.MAX_COMMODITY_ID_LENGTH);
        b.writeVarInt(p.quantity);
        b.writeVarInt(p.guardLevel);
        b.writeBoolean(p.insured);
    }

    public static CaravanActionPacket decode(FriendlyByteBuf b) {
        return new CaravanActionPacket(
                b.readEnum(Action.class),
                b.readUUID(),
                b.readUUID(),
                b.readUtf(NetworkValidation.MAX_COMMODITY_ID_LENGTH),
                b.readVarInt(),
                b.readVarInt(),
                b.readBoolean());
    }

    public static void handle(CaravanActionPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context c = supplier.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            SettlementRecord here = SettlementManager.get(p.settlementId);
            if (here == null || !SettlementService.isNearby(player, p.settlementId)) {
                GuiFeedbackPacket.send(player, "finance.village.not_nearby");
                return;
            }
            long day = EconomyCycleService.currentMcDay(player.getServer());
            CaravanService.Result result =
                    switch (p.action) {
                        case CREATE -> CaravanService.createRoute(
                                player.getUUID(),
                                p.settlementId,
                                p.target,
                                NetworkValidation.normalizeCommodityId(p.commodityId),
                                p.quantity,
                                p.guardLevel,
                                p.insured,
                                day);
                        case PAUSE -> CaravanService.pauseRoute(player, p.target);
                        case RESUME -> CaravanService.resumeRoute(player, p.target);
                        case CLOSE -> CaravanService.closeRoute(player, p.target);
                    };
            SettlementGuiOpener.open(player, here.anchor(), result.message());
        });
        c.setPacketHandled(true);
    }
}
