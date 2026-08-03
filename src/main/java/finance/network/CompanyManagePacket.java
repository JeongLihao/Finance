package finance.network;

import finance.company.Company;
import finance.company.CompanyManagementAction;
import finance.company.CompanyManagementService;
import finance.company.CompanyManager;
import finance.company.CompanyStrategy;
import finance.data.EconomySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CompanyManagePacket {

    private final CompanyManagementAction action;
    private final CompanyStrategy strategy;
    private final long amount;
    private final double ratio;

    public CompanyManagePacket(CompanyManagementAction action) {
        this(action, CompanyStrategy.STABLE, 0, 0);
    }

    public static CompanyManagePacket strategy(CompanyStrategy strategy) {
        return new CompanyManagePacket(CompanyManagementAction.SET_STRATEGY, strategy, 0, 0);
    }

    public static CompanyManagePacket sellRatio(double ratio) {
        return new CompanyManagePacket(CompanyManagementAction.SET_SELL_RATIO, CompanyStrategy.STABLE, 0, ratio);
    }

    public static CompanyManagePacket amount(CompanyManagementAction action, long amount) {
        return new CompanyManagePacket(action, CompanyStrategy.STABLE, amount, 0);
    }

    private CompanyManagePacket(CompanyManagementAction action, CompanyStrategy strategy, long amount, double ratio) {
        this.action = action;
        this.strategy = strategy;
        this.amount = amount;
        this.ratio = ratio;
    }

    public static void encode(CompanyManagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeEnum(packet.strategy);
        buffer.writeLong(packet.amount);
        buffer.writeDouble(packet.ratio);
    }

    public static CompanyManagePacket decode(FriendlyByteBuf buffer) {
        return new CompanyManagePacket(
                buffer.readEnum(CompanyManagementAction.class),
                buffer.readEnum(CompanyStrategy.class),
                buffer.readLong(),
                buffer.readDouble());
    }

    public static void handle(CompanyManagePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            Company company = CompanyManager.getCompanyByOwner(player.getUUID());
            CompanyManagementService.Result result = CompanyManagementService.apply(
                    player.getUUID(),
                    company,
                    packet.action,
                    packet.strategy,
                    packet.amount,
                    packet.ratio);

            if (result.success()) {
                EconomySavedData.markDirty();
            }

            GuiFeedbackPacket.send(player, result.message());
        });
        ctx.get().setPacketHandled(true);
    }
}
