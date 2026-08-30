package finance.network;

import finance.gameplay.company.capital.CapitalFundingSource;
import finance.gameplay.company.capital.WorldCapitalProjectType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectPacketTest {
    @Test void createIntentRoundTripsWithoutClientMoneyOrMaterialFields() {
        var packet = new CapitalProjectActionPacket(CapitalProjectActionPacket.Action.CREATE, null,
                UUID.randomUUID(), null, null, WorldCapitalProjectType.WAREHOUSE_UPGRADE,
                CapitalFundingSource.CORPORATE_BOND, "once");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        CapitalProjectActionPacket.encode(packet, buffer);
        assertEquals(packet, CapitalProjectActionPacket.decode(buffer));
    }

    @Test void blankOperationAndMissingAuthorizationProposalAreRejectedAtDecode() {
        FriendlyByteBuf blank = new FriendlyByteBuf(Unpooled.buffer());
        var create = new CapitalProjectActionPacket(CapitalProjectActionPacket.Action.CREATE, null,
                UUID.randomUUID(), null, null, WorldCapitalProjectType.FACTORY_UPGRADE,
                CapitalFundingSource.RETAINED_EARNINGS, "");
        CapitalProjectActionPacket.encode(create, blank);
        assertThrows(RuntimeException.class, () -> CapitalProjectActionPacket.decode(blank));
        FriendlyByteBuf auth = new FriendlyByteBuf(Unpooled.buffer());
        var authorize = new CapitalProjectActionPacket(CapitalProjectActionPacket.Action.AUTHORIZE,
                UUID.randomUUID(), null, null, null, null, null, "auth");
        CapitalProjectActionPacket.encode(authorize, auth);
        assertThrows(RuntimeException.class, () -> CapitalProjectActionPacket.decode(auth));
    }
}
