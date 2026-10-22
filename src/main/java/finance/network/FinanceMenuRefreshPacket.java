package finance.network;

import finance.gui.FinanceMenu;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：金融中心开着时推送最新数据快照，替换界面上的余额、库存、订单等数字。
 * 载荷格式与打开界面时的 {@link FinanceMenu#writeAll} 相同。
 */
public class FinanceMenuRefreshPacket {

    private final int containerId;
    /** 服务端发送时使用。 */
    private final Consumer<FriendlyByteBuf> writer;
    /** 客户端解码后得到。 */
    private final FinanceMenu snapshot;

    public FinanceMenuRefreshPacket(int containerId, Consumer<FriendlyByteBuf> writer) {
        this.containerId = containerId;
        this.writer = writer;
        this.snapshot = null;
    }

    private FinanceMenuRefreshPacket(int containerId, FinanceMenu snapshot) {
        this.containerId = containerId;
        this.writer = null;
        this.snapshot = snapshot;
    }

    public int containerId() {
        return containerId;
    }

    public FinanceMenu snapshot() {
        return snapshot;
    }

    public static void encode(FinanceMenuRefreshPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        packet.writer.accept(buffer);
    }

    public static FinanceMenuRefreshPacket decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        return new FinanceMenuRefreshPacket(containerId, new FinanceMenu(containerId, null, buffer));
    }

    public static void handle(FinanceMenuRefreshPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () -> () -> finance.client.FinanceRefreshClientHandler.handle(
                                packet.containerId, packet.snapshot)));
        ctx.get().setPacketHandled(true);
    }
}
