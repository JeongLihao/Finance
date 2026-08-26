package finance.network;

import finance.FinanceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkDirection;

import java.util.Optional;

/**
 * 网络通道注册 —— GUI 操作数据包的入口。
 */
public class FinancePacketHandler {

    // OpenFinanceGuiPacket 从空载荷升级为受校验的入口类型 + 界面模式。
    private static final String PROTOCOL_VERSION = "10";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(FinanceMod.MOD_ID, "gui"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(packetId++, CompanyGameplayActionPacket.class,
                CompanyGameplayActionPacket::encode, CompanyGameplayActionPacket::decode,
                CompanyGameplayActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, WarehouseActionPacket.class,
                WarehouseActionPacket::encode, WarehouseActionPacket::decode,
                WarehouseActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, ContractActionPacket.class,
                ContractActionPacket::encode, ContractActionPacket::decode,
                ContractActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,
                TradeActionPacket.class,
                 TradeActionPacket::encode,
                 TradeActionPacket::decode,
                 TradeActionPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CancelOrderPacket.class,
                 CancelOrderPacket::encode,
                 CancelOrderPacket::decode,
                 CancelOrderPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CreateCompanyPacket.class,
                 CreateCompanyPacket::encode,
                 CreateCompanyPacket::decode,
                 CreateCompanyPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                StockTradePacket.class,
                 StockTradePacket::encode,
                 StockTradePacket::decode,
                 StockTradePacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                OpenFinanceGuiPacket.class,
                OpenFinanceGuiPacket::encode,
                OpenFinanceGuiPacket::decode,
                OpenFinanceGuiPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                TakeOrderPacket.class,
                 TakeOrderPacket::encode,
                 TakeOrderPacket::decode,
                 TakeOrderPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                AdminActionPacket.class,
                 AdminActionPacket::encode,
                 AdminActionPacket::decode,
                 AdminActionPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                InventoryActionPacket.class,
                 InventoryActionPacket::encode,
                 InventoryActionPacket::decode,
                 InventoryActionPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                StockOrderPacket.class,
                 StockOrderPacket::encode,
                 StockOrderPacket::decode,
                 StockOrderPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CompanyIPOPacket.class,
                 CompanyIPOPacket::encode,
                 CompanyIPOPacket::decode,
                 CompanyIPOPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CompanyManagePacket.class,
                 CompanyManagePacket::encode,
                 CompanyManagePacket::decode,
                 CompanyManagePacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                GuiFeedbackPacket.class,
                 GuiFeedbackPacket::encode,
                 GuiFeedbackPacket::decode,
                 GuiFeedbackPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++,
                PriceAlertPacket.class,
                 PriceAlertPacket::encode,
                 PriceAlertPacket::decode,
                 PriceAlertPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                ConditionalStockOrderPacket.class,
                 ConditionalStockOrderPacket::encode,
                 ConditionalStockOrderPacket::decode,
                 ConditionalStockOrderPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CompanyFinancingPacket.class,
                 CompanyFinancingPacket::encode,
                 CompanyFinancingPacket::decode,
                 CompanyFinancingPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CompanyProposalPacket.class,
                 CompanyProposalPacket::encode,
                 CompanyProposalPacket::decode,
                 CompanyProposalPacket::handle,
                 Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CandlestickRequestPacket.class,
                CandlestickRequestPacket::encode,
                CandlestickRequestPacket::decode,
                CandlestickRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(packetId++,
                CandlestickResponsePacket.class,
                CandlestickResponsePacket::encode,
                CandlestickResponsePacket::decode,
                CandlestickResponsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, FinancialProductRequestPacket.class,
                FinancialProductRequestPacket::encode, FinancialProductRequestPacket::decode,
                FinancialProductRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, FinancialProductResponsePacket.class,
                FinancialProductResponsePacket::encode, FinancialProductResponsePacket::decode,
                FinancialProductResponsePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, FinancialProductActionPacket.class,
                FinancialProductActionPacket::encode, FinancialProductActionPacket::decode,
                FinancialProductActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, FuturesRequestPacket.class,
                FuturesRequestPacket::encode, FuturesRequestPacket::decode,
                FuturesRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, FuturesResponsePacket.class,
                FuturesResponsePacket::encode, FuturesResponsePacket::decode,
                FuturesResponsePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, FuturesActionPacket.class,
                FuturesActionPacket::encode, FuturesActionPacket::decode,
                FuturesActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,BankRequestPacket.class,BankRequestPacket::encode,BankRequestPacket::decode,BankRequestPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,BankResponsePacket.class,BankResponsePacket::encode,BankResponsePacket::decode,BankResponsePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++,BankActionPacket.class,BankActionPacket::encode,BankActionPacket::decode,BankActionPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,FundActionPacket.class,FundActionPacket::encode,FundActionPacket::decode,FundActionPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,FundRequestPacket.class,FundRequestPacket::encode,FundRequestPacket::decode,FundRequestPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,FundResponsePacket.class,FundResponsePacket::encode,FundResponsePacket::decode,FundResponsePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++,InsuranceActionPacket.class,InsuranceActionPacket::encode,InsuranceActionPacket::decode,InsuranceActionPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,InsuranceRequestPacket.class,InsuranceRequestPacket::encode,InsuranceRequestPacket::decode,InsuranceRequestPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,InsuranceResponsePacket.class,InsuranceResponsePacket::encode,InsuranceResponsePacket::decode,InsuranceResponsePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++,GovernanceActionPacket.class,GovernanceActionPacket::encode,GovernanceActionPacket::decode,GovernanceActionPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,GovernanceActionResultPacket.class,GovernanceActionResultPacket::encode,GovernanceActionResultPacket::decode,GovernanceActionResultPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++,GovernanceRequestPacket.class,GovernanceRequestPacket::encode,GovernanceRequestPacket::decode,GovernanceRequestPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++,GovernanceResponsePacket.class,GovernanceResponsePacket::encode,GovernanceResponsePacket::decode,GovernanceResponsePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, SettlementActionPacket.class,
                SettlementActionPacket::encode, SettlementActionPacket::decode, SettlementActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, TutorialProgressPacket.class,
                TutorialProgressPacket::encode, TutorialProgressPacket::decode, TutorialProgressPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
