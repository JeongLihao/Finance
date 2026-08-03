package finance.network;

import finance.company.CompanyFinancingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class CompanyFinancingPacket {

    public enum Action {
        START,
        SUBSCRIBE
    }

    private final Action action;
    private final UUID id;
    private final long issueQuantity;
    private final long issuePrice;
    private final long fundingTarget;
    private final long subscribeQuantity;

    public static CompanyFinancingPacket start(UUID companyId, long issueQuantity,
                                               long issuePrice, long fundingTarget) {
        return new CompanyFinancingPacket(Action.START, companyId, issueQuantity, issuePrice, fundingTarget, 0);
    }

    public static CompanyFinancingPacket subscribe(UUID projectId, long subscribeQuantity) {
        return new CompanyFinancingPacket(Action.SUBSCRIBE, projectId, 0, 0, 0, subscribeQuantity);
    }

    private CompanyFinancingPacket(Action action, UUID id, long issueQuantity,
                                   long issuePrice, long fundingTarget, long subscribeQuantity) {
        this.action = action;
        this.id = id;
        this.issueQuantity = issueQuantity;
        this.issuePrice = issuePrice;
        this.fundingTarget = fundingTarget;
        this.subscribeQuantity = subscribeQuantity;
    }

    public static void encode(CompanyFinancingPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.id);
        if (packet.action == Action.START) {
            buffer.writeLong(packet.issueQuantity);
            buffer.writeLong(packet.issuePrice);
            buffer.writeLong(packet.fundingTarget);
        } else {
            buffer.writeLong(packet.subscribeQuantity);
        }
    }

    public static CompanyFinancingPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID id = buffer.readUUID();
        if (action == Action.START) {
            return CompanyFinancingPacket.start(id, buffer.readLong(), buffer.readLong(), buffer.readLong());
        }
        return CompanyFinancingPacket.subscribe(id, buffer.readLong());
    }

    public static void handle(CompanyFinancingPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (packet.action == null || packet.id == null) {
                GuiFeedbackPacket.send(player, "融资请求无效。");
                return;
            }
            CompanyFinancingManager.Result result;
            if (packet.action == Action.START) {
                if (!NetworkValidation.isPositive(packet.issueQuantity)
                        || !NetworkValidation.isPositive(packet.issuePrice)
                        || !NetworkValidation.isPositive(packet.fundingTarget)) {
                    GuiFeedbackPacket.send(player, "融资参数必须为正。");
                    return;
                }
                long mcDay = player.getServer() != null ? player.getServer().getTickCount() / 24000L : 0;
                result = CompanyFinancingManager.startProject(
                        player.getUUID(),
                        packet.id,
                        packet.issueQuantity,
                        packet.issuePrice,
                        packet.fundingTarget,
                        mcDay);
            } else {
                if (!NetworkValidation.isPositive(packet.subscribeQuantity)) {
                    GuiFeedbackPacket.send(player, "认购数量必须为正。");
                    return;
                }
                result = CompanyFinancingManager.subscribe(player.getUUID(), packet.id, packet.subscribeQuantity);
            }
            GuiFeedbackPacket.send(player, result.message());
        });
        ctx.get().setPacketHandled(true);
    }
}
