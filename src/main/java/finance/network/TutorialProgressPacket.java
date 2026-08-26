package finance.network;

import finance.tutorial.TutorialStage;
import finance.tutorial.TutorialOptionalGoal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Bounded server-to-client snapshot of main-route and optional-route progress. */
public record TutorialProgressPacket(TutorialStage stage, int optionalMask) {
    public static void encode(TutorialProgressPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.stage.ordinal());
        buffer.writeVarInt(packet.optionalMask & TutorialOptionalGoal.validMask());
    }

    public static TutorialProgressPacket decode(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        TutorialStage[] stages = TutorialStage.values();
        int optionalMask = buffer.readVarInt() & TutorialOptionalGoal.validMask();
        return new TutorialProgressPacket(stages[Math.max(0, Math.min(ordinal, stages.length - 1))], optionalMask);
    }

    public static void handle(TutorialProgressPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> finance.client.TutorialClientState.update(packet.stage, packet.optionalMask)));
        context.get().setPacketHandled(true);
    }
}
