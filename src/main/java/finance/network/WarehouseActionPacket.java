package finance.network;

import finance.gui.WarehouseGuiOpener;
import finance.gui.WarehouseMenu;
import finance.warehouse.WarehouseActionResult;
import finance.warehouse.WarehouseService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;
import io.netty.handler.codec.DecoderException;

public record WarehouseActionPacket(Action action, UUID warehouseId, String commodityId,
                                    int amount, String operationKey) {
    private static final int MAX_TRANSFER_AMOUNT = 1_000_000;
    public enum Action { DEPOSIT, WITHDRAW, BIND_COMPANY, UNBIND_COMPANY, UPGRADE }

    public static void encode(WarehouseActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.warehouseId);
        buffer.writeUtf(packet.commodityId, 64);
        buffer.writeVarInt(packet.amount);
        buffer.writeUtf(packet.operationKey, 64);
    }

    public static WarehouseActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID warehouseId = buffer.readUUID();
        String commodityId = buffer.readUtf(64);
        int amount = buffer.readVarInt();
        String operationKey = buffer.readUtf(64);
        boolean inventoryAction = action == Action.DEPOSIT || action == Action.WITHDRAW;
        boolean invalidUpgrade = action == Action.UPGRADE
                && (amount != 0 || !"upgrade".equals(commodityId));
        if (operationKey.isBlank() || invalidUpgrade || inventoryAction
                && (commodityId.isBlank() || amount <= 0 || amount > MAX_TRANSFER_AMOUNT)) {
            throw new DecoderException("Invalid warehouse action intent");
        }
        return new WarehouseActionPacket(action, warehouseId, commodityId, amount, operationKey);
    }

    public static void handle(WarehouseActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            boolean inventoryAction = packet.action == Action.DEPOSIT || packet.action == Action.WITHDRAW;
            boolean invalidUpgrade = packet.action == Action.UPGRADE
                    && (packet.amount != 0 || !"upgrade".equals(packet.commodityId));
            if (player == null || packet.action == null || invalidUpgrade
                    || inventoryAction && (packet.amount <= 0 || packet.commodityId.isBlank())
                    || packet.operationKey.isBlank() || !(player.containerMenu instanceof WarehouseMenu menu)
                    || !menu.warehouseId().equals(packet.warehouseId) || !menu.stillValid(player)) return;
            WarehouseActionResult result;
            if (packet.action == Action.DEPOSIT) result = WarehouseService.deposit(player, packet.warehouseId,
                    packet.commodityId, packet.amount, packet.operationKey);
            else if (packet.action == Action.WITHDRAW) result = WarehouseService.withdraw(player, packet.warehouseId,
                    packet.commodityId, packet.amount, packet.operationKey);
            else if (packet.action == Action.UPGRADE) {
                result = finance.warehouse.WarehouseUpgradeService.upgrade(player, packet.warehouseId,
                        packet.operationKey);
            } else {
                finance.company.Company company = finance.company.CompanyManager.getCompanyByOwner(player.getUUID());
                finance.gameplay.company.CompanyGameplayActionResult binding = company == null
                        ? finance.gameplay.company.CompanyGameplayActionResult.fail("finance.company_gameplay.no_company")
                        : packet.action == Action.BIND_COMPANY
                        ? finance.gameplay.company.CompanyWarehouseBindingService.bind(player.getUUID(), company.getCompanyId(),
                        packet.warehouseId, packet.operationKey)
                        : finance.gameplay.company.CompanyWarehouseBindingService.unbind(player.getUUID(), company.getCompanyId(),
                        packet.warehouseId, packet.operationKey);
                result = new WarehouseActionResult(binding.success(), binding.messageKey(), 0);
            }
            if (result.success()) {
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55F, 1.15F);
                player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + 1.0D, player.getZ(), 5, 0.25D, 0.3D, 0.25D, 0.0D);
            }
            WarehouseGuiOpener.open(player, menu.blockPos(), result.messageKey(), result.amount());
        });
        context.setPacketHandled(true);
    }
}
