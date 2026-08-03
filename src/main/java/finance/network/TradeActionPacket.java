package finance.network;

import finance.account.AccountManager;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.util.MathUtil;
import net.minecraft.network.FriendlyByteBuf;
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
        buffer.writeUtf(packet.commodityId, NetworkValidation.MAX_COMMODITY_ID_LENGTH);
        buffer.writeLong(packet.price);
        buffer.writeVarInt(packet.quantity);
    }

    public static TradeActionPacket decode(FriendlyByteBuf buffer) {
        return new TradeActionPacket(
                buffer.readEnum(ActionType.class),
                buffer.readUtf(NetworkValidation.MAX_COMMODITY_ID_LENGTH),
                buffer.readLong(),
                buffer.readVarInt()
        );
    }

    public static void handle(TradeActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!isValidRequest(packet)) {
                GuiFeedbackPacket.send(player, "交易请求参数无效。");
                return;
            }

            TradeActionPacket safePacket = new TradeActionPacket(
                    packet.actionType,
                    NetworkValidation.normalizeCommodityId(packet.commodityId),
                    packet.price,
                    packet.quantity);

            boolean changed = switch (safePacket.actionType) {
                case P2P_BUY -> handleP2pBuy(player, safePacket);
                case P2P_SELL -> handleP2pSell(player, safePacket);
                case INTL_BUY -> handleIntlBuy(player, safePacket);
                case INTL_SELL -> handleIntlSell(player, safePacket);
            };
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean isValidRequest(TradeActionPacket packet) {
        if (packet.actionType == null || !NetworkValidation.isValidCommodityId(packet.commodityId)
                || !NetworkValidation.isPositive(packet.quantity)) {
            return false;
        }
        return switch (packet.actionType) {
            case P2P_BUY, P2P_SELL -> NetworkValidation.isPositive(packet.price);
            case INTL_BUY, INTL_SELL -> true;
        };
    }

    private static boolean handleP2pBuy(ServerPlayer player, TradeActionPacket packet) {
        if (CommodityRegistry.getCommodity(packet.commodityId) == null) {
            GuiFeedbackPacket.send(player, "未知商品。");
            return false;
        }
        if (packet.price <= 0 || packet.quantity <= 0) {
            GuiFeedbackPacket.send(player, "价格和数量必须大于 0。");
            return false;
        }

        long totalCost = MathUtil.multiplyExactOrNegative1(packet.price, packet.quantity);
        if (totalCost <= 0) {
            GuiFeedbackPacket.send(player, "订单金额过大。");
            return false;
        }
        if (AccountManager.getBalance(player.getUUID()) < totalCost) {
            GuiFeedbackPacket.send(player, "余额不足。");
            return false;
        }

        Order order = new Order(player.getUUID(), packet.commodityId, OrderType.BUY, packet.price, packet.quantity);
        if (MarketManager.placeOrder(order)) {
            GuiFeedbackPacket.send(player, "买单已挂: " + packet.quantity + "x " + packet.commodityId + " @" + packet.price);
            return true;
        } else {
            GuiFeedbackPacket.send(player, "买单提交失败。");
            return false;
        }
    }

    private static boolean handleP2pSell(ServerPlayer player, TradeActionPacket packet) {
        if (CommodityRegistry.getCommodity(packet.commodityId) == null) {
            GuiFeedbackPacket.send(player, "未知商品。");
            return false;
        }
        if (packet.price <= 0 || packet.quantity <= 0) {
            GuiFeedbackPacket.send(player, "价格和数量必须大于 0。");
            return false;
        }

        int owned = CommodityInventoryManager.getCommodityAmount(player.getUUID(), packet.commodityId);
        if (owned < packet.quantity) {
            GuiFeedbackPacket.send(player, "库存不足。拥有: " + owned + " 需要: " + packet.quantity);
            return false;
        }

        Order order = new Order(player.getUUID(), packet.commodityId, OrderType.SELL, packet.price, packet.quantity);
        if (MarketManager.placeOrder(order)) {
            GuiFeedbackPacket.send(player, "卖单已挂: " + packet.quantity + "x " + packet.commodityId + " @" + packet.price);
            return true;
        } else {
            GuiFeedbackPacket.send(player, "卖单提交失败。");
            return false;
        }
    }

    private static boolean handleIntlBuy(ServerPlayer player, TradeActionPacket packet) {
        if (packet.quantity <= 0) {
            GuiFeedbackPacket.send(player, "数量必须大于 0。");
            return false;
        }
        MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(packet.commodityId);
        if (mp == null) {
            GuiFeedbackPacket.send(player, "该商品未接入国际市场，只能通过玩家挂单交易。");
            return false;
        }

        int marketStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, packet.commodityId);
        if (marketStock < packet.quantity) {
            GuiFeedbackPacket.send(player, "国际市场库存不足。可用: " + marketStock);
            return false;
        }

        long askPrice = mp.getAskPrice();
        long totalCost = MathUtil.multiplyExactOrNegative1(askPrice, packet.quantity);
        if (totalCost <= 0) {
            GuiFeedbackPacket.send(player, "交易金额过大。");
            return false;
        }
        if (AccountManager.getBalance(player.getUUID()) < totalCost) {
            GuiFeedbackPacket.send(player, "余额不足。需要: " + totalCost);
            return false;
        }

        if (NpcMarketMaker.npcSell(player.getUUID(), packet.commodityId, packet.quantity)) {
            GuiFeedbackPacket.send(player, "已从国际市场买入 " + packet.quantity + "x " + packet.commodityId + "，单价: " + askPrice + "  支付: " + totalCost);
            return true;
        } else {
            GuiFeedbackPacket.send(player, "国际市场交易失败。");
            return false;
        }
    }

    private static boolean handleIntlSell(ServerPlayer player, TradeActionPacket packet) {
        if (packet.quantity <= 0) {
            GuiFeedbackPacket.send(player, "数量必须大于 0。");
            return false;
        }
        MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(packet.commodityId);
        if (mp == null) {
            GuiFeedbackPacket.send(player, "该商品未接入国际市场，只能通过玩家挂单交易。");
            return false;
        }

        int owned = CommodityInventoryManager.getCommodityAmount(player.getUUID(), packet.commodityId);
        if (owned < packet.quantity) {
            GuiFeedbackPacket.send(player, "库存不足。拥有: " + owned + " 需要: " + packet.quantity);
            return false;
        }

        long bidPrice = mp.getBidPrice();
        if (NpcMarketMaker.npcBuy(player.getUUID(), packet.commodityId, packet.quantity)) {
            long received = MathUtil.multiplyExactOrNegative1(bidPrice, packet.quantity);
            GuiFeedbackPacket.send(player, "已向国际市场卖出 " + packet.quantity + "x " + packet.commodityId + "，单价: " + bidPrice + "  收入: " + received);
            return true;
        } else {
            GuiFeedbackPacket.send(player, "国际市场交易失败。");
            return false;
        }
    }
}
