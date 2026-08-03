package finance.network;

import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.CommodityInventorySavedData;
import finance.util.InventoryUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 商品存取数据包 —— MC 物品栏 ↔ 虚拟商品库存。
 */
public class InventoryActionPacket {

    public enum ActionType {
        DEPOSIT,    // 从 MC 物品栏存入虚拟商品库存
        WITHDRAW    // 从虚拟商品库存取到 MC 物品栏
    }

    private final ActionType actionType;
    private final String commodityId;
    private final int amount;

    public InventoryActionPacket(ActionType actionType, String commodityId, int amount) {
        this.actionType = actionType;
        this.commodityId = commodityId;
        this.amount = amount;
    }

    public static void encode(InventoryActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.actionType);
        buffer.writeUtf(packet.commodityId, NetworkValidation.MAX_COMMODITY_ID_LENGTH);
        buffer.writeVarInt(packet.amount);
    }

    public static InventoryActionPacket decode(FriendlyByteBuf buffer) {
        return new InventoryActionPacket(
                buffer.readEnum(ActionType.class),
                buffer.readUtf(NetworkValidation.MAX_COMMODITY_ID_LENGTH),
                buffer.readVarInt()
        );
    }

    public static void handle(InventoryActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (packet.actionType == null
                    || !NetworkValidation.isValidCommodityId(packet.commodityId)
                    || !NetworkValidation.isPositive(packet.amount)) {
                GuiFeedbackPacket.send(player, "库存请求参数无效。");
                return;
            }
            String commodityId = NetworkValidation.normalizeCommodityId(packet.commodityId);

            switch (packet.actionType) {
                case DEPOSIT -> handleDeposit(player, commodityId, packet.amount);
                case WITHDRAW -> handleWithdraw(player, commodityId, packet.amount);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** 从 MC 物品栏存入虚拟商品库存 */
    private static void handleDeposit(ServerPlayer player, String commodityId, int amount) {
        if (amount <= 0) {
            GuiFeedbackPacket.send(player, "数量必须大于 0。");
            return;
        }

        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) {
            GuiFeedbackPacket.send(player, "未知商品: " + commodityId);
            return;
        }

        String itemId = commodity.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            GuiFeedbackPacket.send(player, "该商品没有关联的物品 ID，无法存入。");
            return;
        }

        int available = InventoryUtil.countItemInInventory(player, itemId);
        if (available < amount) {
            GuiFeedbackPacket.send(player, "物品栏不足。拥有: " + available + " 需要: " + amount);
            return;
        }

        int removed = InventoryUtil.removeFromInventory(player, itemId, amount);
        if (removed > 0) {
            CommodityInventoryManager.addCommodity(player.getUUID(), commodityId, removed);
            GuiFeedbackPacket.send(player, "§a已存入 " + removed + " 个「" + commodity.getDisplayName() + "」到商品库存。");
        }

    }

    /** 从虚拟商品库存取到 MC 物品栏 */
    private static void handleWithdraw(ServerPlayer player, String commodityId, int amount) {
        if (amount <= 0) {
            GuiFeedbackPacket.send(player, "数量必须大于 0。");
            return;
        }

        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) {
            GuiFeedbackPacket.send(player, "未知商品: " + commodityId);
            return;
        }

        String itemId = commodity.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            GuiFeedbackPacket.send(player, "该商品没有关联的物品 ID，无法取出。");
            return;
        }

        int owned = CommodityInventoryManager.getCommodityAmount(player.getUUID(), commodityId);
        if (owned < amount) {
            GuiFeedbackPacket.send(player, "商品库存不足。拥有: " + owned + " 需要: " + amount);
            return;
        }

        CommodityInventoryManager.removeCommodity(player.getUUID(), commodityId, amount);
        InventoryUtil.addToInventory(player, itemId, amount);
        GuiFeedbackPacket.send(player, "§a已从商品库存取出 " + amount + " 个「" + commodity.getDisplayName() + "」到物品栏。");

    }
}
