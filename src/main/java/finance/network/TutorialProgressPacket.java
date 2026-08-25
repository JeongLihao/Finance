package finance.network;

import finance.tutorial.TutorialStage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Bounded server-to-client snapshot containing only the current tutorial stage. */
public record TutorialProgressPacket(TutorialStage stage) {
    public static void encode(TutorialProgressPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.stage.ordinal());
    }

    public static TutorialProgressPacket decode(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        TutorialStage[] stages = TutorialStage.values();
        return new TutorialProgressPacket(stages[Math.max(0, Math.min(ordinal, stages.length - 1))]);
    }

    public static void handle(TutorialProgressPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> finance.client.TutorialClientState.update(packet.stage)));
        context.get().setPacketHandled(true);
    }
}
