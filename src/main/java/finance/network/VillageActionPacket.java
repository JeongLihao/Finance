package finance.network;

import finance.diagnostic.ModuleHealthRegistry;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementService;
import finance.village.VillageManager;
import finance.village.VillageService;
import finance.village.VillageStall;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record VillageActionPacket(Action action, String settlementId, String stallId, String commodityId, long price, int quantity) {
    public enum Action { REGISTER_STALL, STOCK_STALL, SET_PRICE, WITHDRAW_STALL, CLOSE_STALL }

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
            UUID settlementUuid = parseUuid(p.settlementId);
            UUID stallUuid = parseUuid(p.stallId);
            switch (p.action) {
                case REGISTER_STALL -> {
                    // 开店：在目标聚落附近才能登记
                    if (settlementUuid == null || SettlementManager.get(settlementUuid) == null
                            || !SettlementService.isNearby(player, settlementUuid)) {
                        GuiFeedbackPacket.send(player, "finance.village.not_nearby");
                        return;
                    }
                    String commodityId = NetworkValidation.normalizeCommodityId(p.commodityId);
                    if (!NetworkValidation.isValidCommodityId(commodityId)) {
                        GuiFeedbackPacket.send(player, "finance.village.invalid_commodity");
                        return;
                    }
                    if (p.price < 1) {
                        GuiFeedbackPacket.send(player, "finance.village.invalid_price");
                        return;
                    }
                    VillageStall stall = VillageService.registerStall(player, settlementUuid, commodityId, p.price);
                    if (stall == null) GuiFeedbackPacket.send(player, "finance.village.register_failed");
                }
                case STOCK_STALL, SET_PRICE, WITHDRAW_STALL, CLOSE_STALL -> {
                    // 摊位管理：settlementId 必须与摊位归属一致，且玩家须在该摊位所在聚落附近。
                    // 否则 A 聚落附近可远程管理 B 聚落摊位（owner 校验仍在，无越权，但属非预期行为）。
                    VillageStall stall = stallUuid == null ? null : VillageManager.stall(stallUuid);
                    if (stall == null
                            || settlementUuid == null
                            || !settlementUuid.equals(stall.settlementId())) {
                        GuiFeedbackPacket.send(player, "finance.village.invalid_stall");
                        return;
                    }
                    if (!SettlementService.isNearby(player, stall.settlementId())) {
                        GuiFeedbackPacket.send(player, "finance.village.not_nearby");
                        return;
                    }
                    switch (p.action) {
                        case STOCK_STALL -> {
                            if (!validQuantity(p.quantity)) {
                                GuiFeedbackPacket.send(player, "finance.village.invalid_quantity");
                                return;
                            }
                            if (!VillageService.stockStall(player, stallUuid, p.quantity))
                                GuiFeedbackPacket.send(player, "finance.village.stock_failed");
                        }
                        case SET_PRICE -> {
                            if (p.price < 1) {
                                GuiFeedbackPacket.send(player, "finance.village.invalid_price");
                                return;
                            }
                            if (!VillageService.setStallPrice(player, stallUuid, p.price))
                                GuiFeedbackPacket.send(player, "finance.village.set_price_failed");
                        }
                        case WITHDRAW_STALL -> {
                            if (!validQuantity(p.quantity)) {
                                GuiFeedbackPacket.send(player, "finance.village.invalid_quantity");
                                return;
                            }
                            if (!VillageService.withdrawStall(player, stallUuid, p.quantity))
                                GuiFeedbackPacket.send(player, "finance.village.withdraw_failed");
                        }
                        case CLOSE_STALL -> {
                            if (!VillageService.closeStall(player, stallUuid))
                                GuiFeedbackPacket.send(player, "finance.village.close_failed");
                        }
                        default -> { /* 不可达 */ }
                    }
                }
            }
        });
        c.setPacketHandled(true);
    }
}
