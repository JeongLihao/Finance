package finance.network;

import finance.tutorial.TutorialOptionalGoal;
import finance.tutorial.TutorialStage;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TutorialProgressPacketTest {
    @Test
    void mainAndOptionalProgressRoundTrip() {
        int mask = TutorialOptionalGoal.LOGISTICS.bit() | TutorialOptionalGoal.EXPLORATION.bit();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        TutorialProgressPacket.encode(new TutorialProgressPacket(TutorialStage.COMPLETE, mask), buffer);
        TutorialProgressPacket decoded = TutorialProgressPacket.decode(buffer);
        assertEquals(TutorialStage.COMPLETE, decoded.stage());
        assertEquals(mask, decoded.optionalMask());
    }

    @Test
    void hostileValuesAreClampedToTheBoundedTutorialDomain() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(Integer.MAX_VALUE);
        buffer.writeVarInt(Integer.MAX_VALUE);
        TutorialProgressPacket decoded = TutorialProgressPacket.decode(buffer);
        assertEquals(TutorialStage.COMPLETE, decoded.stage());
        assertEquals(TutorialOptionalGoal.validMask(), decoded.optionalMask());
    }
}
