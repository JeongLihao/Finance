package finance.network;

import finance.FinanceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道注册 —— GUI 操作数据包的入口。
 */
public class FinancePacketHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(FinanceMod.MOD_ID, "gui"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(packetId++,
                TradeActionPacket.class,
                TradeActionPacket::encode,
                TradeActionPacket::decode,
                TradeActionPacket::handle);

        CHANNEL.registerMessage(packetId++,
                CancelOrderPacket.class,
                CancelOrderPacket::encode,
                CancelOrderPacket::decode,
                CancelOrderPacket::handle);

        CHANNEL.registerMessage(packetId++,
                CreateCompanyPacket.class,
                CreateCompanyPacket::encode,
                CreateCompanyPacket::decode,
                CreateCompanyPacket::handle);

        CHANNEL.registerMessage(packetId++,
                StockTradePacket.class,
                StockTradePacket::encode,
                StockTradePacket::decode,
                StockTradePacket::handle);

        CHANNEL.registerMessage(packetId++,
                OpenFinanceGuiPacket.class,
                OpenFinanceGuiPacket::encode,
                OpenFinanceGuiPacket::decode,
                OpenFinanceGuiPacket::handle);

        CHANNEL.registerMessage(packetId++,
                TakeOrderPacket.class,
                TakeOrderPacket::encode,
                TakeOrderPacket::decode,
                TakeOrderPacket::handle);

        CHANNEL.registerMessage(packetId++,
                AdminActionPacket.class,
                AdminActionPacket::encode,
                AdminActionPacket::decode,
                AdminActionPacket::handle);

        CHANNEL.registerMessage(packetId++,
                InventoryActionPacket.class,
                InventoryActionPacket::encode,
                InventoryActionPacket::decode,
                InventoryActionPacket::handle);

        CHANNEL.registerMessage(packetId++,
                StockOrderPacket.class,
                StockOrderPacket::encode,
                StockOrderPacket::decode,
                StockOrderPacket::handle);

        CHANNEL.registerMessage(packetId++,
                CompanyIPOPacket.class,
                CompanyIPOPacket::encode,
                CompanyIPOPacket::decode,
                CompanyIPOPacket::handle);

        CHANNEL.registerMessage(packetId++,
                CompanyManagePacket.class,
                CompanyManagePacket::encode,
                CompanyManagePacket::decode,
                CompanyManagePacket::handle);
    }
}
