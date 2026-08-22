package finance.network;

import finance.gameplay.FinanceGameplayService;
import finance.gameplay.FinanceScreenMode;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端快捷键请求打开金融中心 GUI。
 */
public class OpenFinanceGuiPacket {

    private final FinanceTerminalType terminalType;
    private final FinanceScreenMode requestedMode;

    /** 旧快捷键兼容构造；服务端仍会按照当前 Minecraft-first 配置判断是否允许。 */
    public OpenFinanceGuiPacket() {
        this(FinanceTerminalType.LEGACY_FULL_SCREEN, FinanceScreenMode.ADVANCED);
    }

    public OpenFinanceGuiPacket(FinanceTerminalType terminalType, FinanceScreenMode requestedMode) {
        this.terminalType = terminalType;
        this.requestedMode = requestedMode;
    }

    public FinanceTerminalType terminalType() {
        return terminalType;
    }

    public FinanceScreenMode requestedMode() {
        return requestedMode;
    }

    public static void encode(OpenFinanceGuiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.terminalType);
        buffer.writeEnum(packet.requestedMode);
    }

    public static OpenFinanceGuiPacket decode(FriendlyByteBuf buffer) {
        return new OpenFinanceGuiPacket(
                buffer.readEnum(FinanceTerminalType.class),
                buffer.readEnum(FinanceScreenMode.class));
    }

    public static void handle(OpenFinanceGuiPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FinanceGameplayService.openRemoteRequest(player, packet.terminalType, packet.requestedMode);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
