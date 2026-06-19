package finance.network;

import finance.account.AccountManager;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.gui.FinanceGuiOpener;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.util.MathUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 交易操作数据包 —— 支持 P2P 买卖和国际市场买卖。
 */
public class TradeActionPacket {

    public enum ActionType {
        P2P_BUY, P2P_SELL, INTL_BUY, INTL_SELL
    }

    private final ActionType actionType;
    private final String commodityId;
    private final long price;
    private final int quantity;

    public TradeActionPacket(ActionType actionType, String commodityId, long price, int quantity) {
        this.actionType = actionType;
        this.commodityId = commodityId;
        this.price = price;
        this.quantity = quantity;
    }

    public static void encode(TradeActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.actionType);
        buffer.writeUtf(packet.commodityId);
        buffer.writeLong(packet.price);
        buffer.writeVarInt(packet.quantity);
    }

    public static TradeActionPacket decode(FriendlyByteBuf buffer) {
        return new TradeActionPacket(
                buffer.readEnum(ActionType.class),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readVarInt()
        );
    }

    public static void handle(TradeActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            boolean changed = switch (packet.actionType) {
                case P2P_BUY -> handleP2pBuy(player, packet);
                case P2P_SELL -> handleP2pSell(player, packet);
                case INTL_BUY -> handleIntlBuy(player, packet);
                case INTL_SELL -> handleIntlSell(player, packet);
            };
            if (changed) {
                FinanceGuiOpener.open(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean handleP2pBuy(ServerPlayer player, TradeActionPacket packet) {
        if (CommodityRegistry.getCommodity(packet.commodityId) == null) {
            player.sendSystemMessage(Component.literal("未知商品。"));
            return false;
        }
        if (packet.price <= 0 || packet.quantity <= 0) {
            player.sendSystemMessage(Component.literal("价格和数量必须大于 0。"));
            return false;
        }

        long totalCost = MathUtil.multiplyExactOrNegative1(packet.price, packet.quantity);
        if (totalCost <= 0) {
            player.sendSystemMessage(Component.literal("订单金额过大。"));
            return false;
        }
        if (AccountManager.getBalance(player.getUUID()) < totalCost) {
            player.sendSystemMessage(Component.literal("余额不足。"));
            return false;
        }

        Order order = new Order(player.getUUID(), packet.commodityId, OrderType.BUY, packet.price, packet.quantity);
        if (MarketManager.placeOrder(order)) {
            player.sendSystemMessage(Component.literal("买单已挂: " + packet.quantity + "x " + packet.commodityId + " @" + packet.price));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("买单提交失败。"));
            return false;
        }
    }

    private static boolean handleP2pSell(ServerPlayer player, TradeActionPacket packet) {
        if (CommodityRegistry.getCommodity(packet.commodityId) == null) {
            player.sendSystemMessage(Component.literal("未知商品。"));
            return false;
        }
        if (packet.price <= 0 || packet.quantity <= 0) {
            player.sendSystemMessage(Component.literal("价格和数量必须大于 0。"));
            return false;
        }

        int owned = CommodityInventoryManager.getCommodityAmount(player.getUUID(), packet.commodityId);
        if (owned < packet.quantity) {
            player.sendSystemMessage(Component.literal("库存不足。拥有: " + owned + " 需要: " + packet.quantity));
            return false;
        }

        Order order = new Order(player.getUUID(), packet.commodityId, OrderType.SELL, packet.price, packet.quantity);
        if (MarketManager.placeOrder(order)) {
            player.sendSystemMessage(Component.literal("卖单已挂: " + packet.quantity + "x " + packet.commodityId + " @" + packet.price));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("卖单提交失败。"));
            return false;
        }
    }

    private static boolean handleIntlBuy(ServerPlayer player, TradeActionPacket packet) {
        if (packet.quantity <= 0) {
            player.sendSystemMessage(Component.literal("数量必须大于 0。"));
            return false;
        }
        MarketPrice mp = NpcMarketMaker.getMarketPrice(packet.commodityId);
        if (mp == null) {
            player.sendSystemMessage(Component.literal("未知商品。"));
            return false;
        }

        int marketStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, packet.commodityId);
        if (marketStock < packet.quantity) {
            player.sendSystemMessage(Component.literal("国际市场库存不足。可用: " + marketStock));
            return false;
        }

        long askPrice = mp.getAskPrice();
        long totalCost = MathUtil.multiplyExactOrNegative1(askPrice, packet.quantity);
        if (totalCost <= 0) {
            player.sendSystemMessage(Component.literal("交易金额过大。"));
            return false;
        }
        if (AccountManager.getBalance(player.getUUID()) < totalCost) {
            player.sendSystemMessage(Component.literal("余额不足。需要: " + totalCost));
            return false;
        }

        if (NpcMarketMaker.npcSell(player.getUUID(), packet.commodityId, packet.quantity)) {
            player.sendSystemMessage(Component.literal("已从国际市场买入 " + packet.quantity + "x " + packet.commodityId + "，单价: " + askPrice + "  支付: " + totalCost));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("国际市场交易失败。"));
            return false;
        }
    }

    private static boolean handleIntlSell(ServerPlayer player, TradeActionPacket packet) {
        if (packet.quantity <= 0) {
            player.sendSystemMessage(Component.literal("数量必须大于 0。"));
            return false;
        }
        MarketPrice mp = NpcMarketMaker.getMarketPrice(packet.commodityId);
        if (mp == null) {
            player.sendSystemMessage(Component.literal("未知商品。"));
            return false;
        }

        int owned = CommodityInventoryManager.getCommodityAmount(player.getUUID(), packet.commodityId);
        if (owned < packet.quantity) {
            player.sendSystemMessage(Component.literal("库存不足。拥有: " + owned + " 需要: " + packet.quantity));
            return false;
        }

        long bidPrice = mp.getBidPrice();
        if (NpcMarketMaker.npcBuy(player.getUUID(), packet.commodityId, packet.quantity)) {
            long received = MathUtil.multiplyExactOrNegative1(bidPrice, packet.quantity);
            player.sendSystemMessage(Component.literal("已向国际市场卖出 " + packet.quantity + "x " + packet.commodityId + "，单价: " + bidPrice + "  收入: " + received));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("国际市场交易失败。"));
            return false;
        }
    }
}
