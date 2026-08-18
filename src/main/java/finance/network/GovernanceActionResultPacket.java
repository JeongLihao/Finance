package finance.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Correlates a governance write response with the proposal shown in the task list. */
public record GovernanceActionResultPacket(UUID targetId, boolean success) {
    public static void encode(GovernanceActionResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.targetId);
        buffer.writeBoolean(packet.success);
    }

    public static GovernanceActionResultPacket decode(FriendlyByteBuf buffer) {
        return new GovernanceActionResultPacket(buffer.readUUID(), buffer.readBoolean());
    }

    public static void handle(GovernanceActionResultPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> finance.client.GovernanceTaskClientState.complete(packet.targetId, packet.success)));
        context.setPacketHandled(true);
    }
}
