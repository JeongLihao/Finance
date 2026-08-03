package finance.network;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.market.MarketManager;
import finance.market.Order;
import finance.market.OrderType;
import finance.stock.StockMarketManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkValidationTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000002001");

    @BeforeEach
    void setup() {
        MarketManager.clearOrders();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("iron", "Iron", CommodityCategory.RAW_MATERIALS, 10));
    }

    @AfterEach
    void cleanup() {
        MarketManager.clearOrders();
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void validationRejectsBlankOrOverlongNetworkStrings() {
        assertFalse(NetworkValidation.isValidCommodityId("   "));
        assertFalse(NetworkValidation.isValidCommodityId("x".repeat(NetworkValidation.MAX_COMMODITY_ID_LENGTH + 1)));
        assertFalse(NetworkValidation.isValidSymbol("   "));
        assertFalse(NetworkValidation.isValidSymbol("S".repeat(NetworkValidation.MAX_SYMBOL_LENGTH + 1)));
        assertFalse(NetworkValidation.isValidDisplayName(""));
        assertFalse(NetworkValidation.isValidDisplayName("x".repeat(NetworkValidation.MAX_DISPLAY_NAME_LENGTH + 1)));
    }

    @Test
    void validationRejectsInvalidResourceLocationItemIds() {
        assertFalse(NetworkValidation.isValidItemId(""));
        assertFalse(NetworkValidation.isValidItemId("not valid:item"));
        assertFalse(NetworkValidation.isValidItemId("minecraft:bad item"));
        assertFalse(NetworkValidation.isValidItemId("minecraft:" + "x".repeat(NetworkValidation.MAX_ITEM_ID_LENGTH)));
    }

    @Test
    void tradePacketDecodeRejectsOverlongCommodityId() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(TradeActionPacket.ActionType.P2P_BUY);
        buffer.writeUtf("x".repeat(NetworkValidation.MAX_COMMODITY_ID_LENGTH + 1), Short.MAX_VALUE);
        buffer.writeLong(1);
        buffer.writeVarInt(1);

        assertThrows(RuntimeException.class, () -> TradeActionPacket.decode(buffer));
    }

    @Test
    void inventoryPacketDecodeRejectsOverlongCommodityId() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(InventoryActionPacket.ActionType.DEPOSIT);
        buffer.writeUtf("x".repeat(NetworkValidation.MAX_COMMODITY_ID_LENGTH + 1), Short.MAX_VALUE);
        buffer.writeVarInt(1);

        assertThrows(RuntimeException.class, () -> InventoryActionPacket.decode(buffer));
    }

    @Test
    void adminPacketDecodeRejectsOverlongDisplayName() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeEnum(AdminActionPacket.ActionType.ADD_COMMODITY);
        buffer.writeUtf("custom", NetworkValidation.MAX_COMMODITY_ID_LENGTH);
        buffer.writeBoolean(false);
        buffer.writeUtf("x".repeat(NetworkValidation.MAX_DISPLAY_NAME_LENGTH + 1), Short.MAX_VALUE);
        buffer.writeLong(1);
        buffer.writeEnum(CommodityCategory.RAW_MATERIALS);

        assertThrows(RuntimeException.class, () -> AdminActionPacket.decode(buffer));
    }

    @Test
    void marketManagerRejectsInvalidOrderBeforeMovingAssets() {
        assertFalse(MarketManager.placeOrder(new Order(PLAYER_ID, "iron", OrderType.BUY, -1, 1)));
        assertFalse(MarketManager.placeOrder(new Order(PLAYER_ID, "iron", OrderType.BUY, 1, 0)));
        assertFalse(MarketManager.placeOrder(new Order(PLAYER_ID, "   ", OrderType.BUY, 1, 1)));
        assertEquals(0, MarketManager.getOrders().size());
    }

    @Test
    void stockManagerRejectsBlankSymbolBeforeMovingFunds() {
        long balanceBefore = finance.account.AccountManager.getBalance(PLAYER_ID);

        StockMarketManager.TradeResult result = StockMarketManager.placeLimitBuy(PLAYER_ID, "   ", 1, 1);

        assertFalse(result.success());
        assertEquals(balanceBefore, finance.account.AccountManager.getBalance(PLAYER_ID));
        assertEquals(0, StockMarketManager.getOrders().size());
    }
}
