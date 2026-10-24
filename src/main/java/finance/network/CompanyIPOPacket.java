package finance.network;

import finance.company.CompanyIPOService;
import finance.cycle.EconomyCycleService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 公司上市数据包：发起上市认购（START，id 是公司）或认购别家的新股（SUBSCRIBE，id 是认购）。
 */
public class CompanyIPOPacket {

    public enum Action {
        START,
        SUBSCRIBE
    }

    private final Action action;
    private final UUID id;
    private final long issuePrice;
    private final long quantity;

    public static CompanyIPOPacket start(UUID companyId, long issuePrice, long issueQuantity) {
        return new CompanyIPOPacket(Action.START, companyId, issuePrice, issueQuantity);
    }

    public static CompanyIPOPacket subscribe(UUID subscriptionId, long shares) {
        return new CompanyIPOPacket(Action.SUBSCRIBE, subscriptionId, 0, shares);
    }

    private CompanyIPOPacket(Action action, UUID id, long issuePrice, long quantity) {
        this.action = action;
        this.id = id;
        this.issuePrice = issuePrice;
        this.quantity = quantity;
    }

    public static void encode(CompanyIPOPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.id);
        if (packet.action == Action.START) buffer.writeLong(packet.issuePrice);
        buffer.writeLong(packet.quantity);
    }

    public static CompanyIPOPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID id = buffer.readUUID();
        if (action == Action.START) return start(id, buffer.readLong(), buffer.readLong());
        return subscribe(id, buffer.readLong());
    }

    public static void handle(CompanyIPOPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (packet.action == null || packet.id == null || !NetworkValidation.isPositive(packet.quantity)) {
                GuiFeedbackPacket.send(player, "上市请求无效。");
                return;
            }
            long day = EconomyCycleService.currentMcDay(player.getServer());
            CompanyIPOService.Result result = packet.action == Action.START
                    ? CompanyIPOService.start(player.getUUID(), packet.id, packet.issuePrice, packet.quantity, day)
                    : CompanyIPOService.subscribe(player.getUUID(), packet.id, packet.quantity, day);
            GuiFeedbackPacket.send(player, result.message());
        });
        ctx.get().setPacketHandled(true);
    }
}
