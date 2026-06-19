package finance.network;

import finance.company.CompanyCreationService;
import finance.company.CompanyType;
import finance.gui.FinanceGuiOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 创建公司数据包。
 */
public class CreateCompanyPacket {

    private static final int MAX_COMPANY_NAME_LENGTH = 32;

    private final CompanyType companyType;
    private final String companyName;

    public CreateCompanyPacket(CompanyType companyType, String companyName) {
        this.companyType = companyType;
        this.companyName = companyName;
    }

    public static void encode(CreateCompanyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.companyType);
        buffer.writeUtf(packet.companyName, MAX_COMPANY_NAME_LENGTH);
    }

    public static CreateCompanyPacket decode(FriendlyByteBuf buffer) {
        return new CreateCompanyPacket(
                buffer.readEnum(CompanyType.class),
                buffer.readUtf(MAX_COMPANY_NAME_LENGTH)
        );
    }

    public static void handle(CreateCompanyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            CompanyCreationService.Result result =
                    CompanyCreationService.createPlayerCompany(
                            player.getUUID(), packet.companyType, packet.companyName);
            player.sendSystemMessage(Component.literal(result.message()));
            if (result.success()) {
                FinanceGuiOpener.open(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
