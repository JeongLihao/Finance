package finance.network;

import finance.company.CompanyIPOService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 公司 IPO 数据包（P4）—— 玩家公司上市。
 */
public class CompanyIPOPacket {

    private final UUID companyId;
    private final long issuePrice;
    private final long issueQuantity;

    public CompanyIPOPacket(UUID companyId, long issuePrice, long issueQuantity) {
        this.companyId = companyId;
        this.issuePrice = issuePrice;
        this.issueQuantity = issueQuantity;
    }

    public static void encode(CompanyIPOPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.companyId);
        buffer.writeLong(packet.issuePrice);
        buffer.writeLong(packet.issueQuantity);
    }

    public static CompanyIPOPacket decode(FriendlyByteBuf buffer) {
        return new CompanyIPOPacket(buffer.readUUID(), buffer.readLong(), buffer.readLong());
    }

    public static void handle(CompanyIPOPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            CompanyIPOService.IPOResult result = CompanyIPOService.ipo(
                    player.getUUID(), packet.companyId, packet.issuePrice, packet.issueQuantity);


            GuiFeedbackPacket.send(player, result.message());
        });
        ctx.get().setPacketHandled(true);
    }
}
