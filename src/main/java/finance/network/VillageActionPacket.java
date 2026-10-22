package finance.network;

import finance.diagnostic.ModuleHealthRegistry;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementService;
import finance.village.VillageManager;
import finance.village.VillageService;
import finance.village.VillageStall;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record VillageActionPacket(
        Action action, String settlementId, String stallId, String commodityId, long price, int quantity) {
    public enum Action {
        REGISTER_STALL,
        STOCK_STALL,
        SET_PRICE,
        WITHDRAW_STALL,
        CLOSE_STALL
    }

    private static final int MAX_ID_LENGTH = 64;

    public static void encode(VillageActionPacket p, FriendlyByteBuf b) {
        b.writeEnum(p.action);
        b.writeUtf(p.settlementId == null ? "" : p.settlementId, MAX_ID_LENGTH);
        b.writeUtf(p.stallId == null ? "" : p.stallId, MAX_ID_LENGTH);
        b.writeUtf(p.commodityId == null ? "" : p.commodityId, NetworkValidation.MAX_COMMODITY_ID_LENGTH);
        b.writeLong(p.price);
        b.writeInt(p.quantity);
    }

    public static VillageActionPacket decode(FriendlyByteBuf b) {
        Action a = b.readEnum(Action.class);
        String s = b.readUtf(MAX_ID_LENGTH);
        String stall = b.readUtf(MAX_ID_LENGTH);
        String commodity = b.readUtf(NetworkValidation.MAX_COMMODITY_ID_LENGTH);
        long price = b.readLong();
        int qty = b.readInt();
        return new VillageActionPacket(a, s, stall, commodity, price, qty);
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    private static boolean validQuantity(int quantity) {
        return quantity >= 1 && quantity <= 4096;
    }

    public static void handle(VillageActionPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context c = supplier.get();
        c.enqueueWork(() -> {
            var player = c.getSender();
            if (player == null) return;
            if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
                GuiFeedbackPacket.send(player, "finance.village.paused");
                return;
            }
            String message = apply(player, p);
            UUID settlementUuid = parseUuid(p.settlementId);
            var settlement = settlementUuid == null ? null : SettlementManager.get(settlementUuid);
            if (settlement != null && SettlementService.isNearby(player, settlementUuid))
                finance.gui.SettlementGuiOpener.open(player, settlement.anchor(), message);
            else GuiFeedbackPacket.send(player, message);
        });
        c.setPacketHandled(true);
    }

    /** 执行摊位操作，返回要显示给玩家的提示（翻译键）。 */
    private static String apply(ServerPlayer player, VillageActionPacket p) {
        UUID settlementUuid = parseUuid(p.settlementId);
        UUID stallUuid = parseUuid(p.stallId);
        if (p.action == Action.REGISTER_STALL) {
            // 开店：在目标聚落附近才能登记；价格填 0 表示按村民的公道价
            if (settlementUuid == null
                    || SettlementManager.get(settlementUuid) == null
                    || !SettlementService.isNearby(player, settlementUuid)) return "finance.village.not_nearby";
            String commodityId = NetworkValidation.normalizeCommodityId(p.commodityId);
            if (!NetworkValidation.isValidCommodityId(commodityId)) return "finance.village.invalid_commodity";
            long price = p.price > 0 ? p.price : VillageService.fairPrice(settlementUuid, commodityId, 1);
            if (price < 1) return "finance.village.invalid_price";
            return VillageService.registerStall(player, settlementUuid, commodityId, price) == null
                    ? "finance.village.register_failed"
                    : "finance.village.registered";
        }
        // 摊位管理：settlementId 必须与摊位归属一致，且玩家须在该摊位所在聚落附近，
        // 否则 A 聚落附近可远程管理 B 聚落摊位（owner 校验仍在，无越权，但属非预期行为）。
        VillageStall stall = stallUuid == null ? null : VillageManager.stall(stallUuid);
        if (stall == null || settlementUuid == null || !settlementUuid.equals(stall.settlementId()))
            return "finance.village.invalid_stall";
        if (!SettlementService.isNearby(player, stall.settlementId())) return "finance.village.not_nearby";
        return switch (p.action) {
            case STOCK_STALL -> {
                // 数量填 0 表示把背包里这种东西全摆上
                int quantity = p.quantity == 0 ? Math.min(4096, VillageService.plainCount(player, stall)) : p.quantity;
                if (!validQuantity(quantity)) yield "finance.village.invalid_quantity";
                yield VillageService.stockStall(player, stallUuid, quantity)
                        ? "finance.village.stocked"
                        : "finance.village.stock_failed";
            }
            case SET_PRICE -> {
                if (p.price < 1) yield "finance.village.invalid_price";
                yield VillageService.setStallPrice(player, stallUuid, p.price)
                        ? "finance.village.price_set"
                        : "finance.village.set_price_failed";
            }
            case WITHDRAW_STALL -> {
                // 数量填 0 表示全部取回
                int quantity = p.quantity == 0 ? Math.min(4096, stall.stock()) : p.quantity;
                if (!validQuantity(quantity)) yield "finance.village.invalid_quantity";
                yield VillageService.withdrawStall(player, stallUuid, quantity)
                        ? "finance.village.withdrawn"
                        : "finance.village.withdraw_failed";
            }
            case CLOSE_STALL -> VillageService.closeStall(player, stallUuid)
                    ? "finance.village.closed"
                    : "finance.village.close_failed";
            case REGISTER_STALL -> "finance.village.register_failed";
        };
    }
}
