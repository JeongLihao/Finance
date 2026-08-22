package finance.network;

import finance.contract.ContractService;
import finance.contract.ContractSettlementResult;
import finance.gui.WarehouseGuiOpener;
import finance.gui.WarehouseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;
import io.netty.handler.codec.DecoderException;

public record ContractActionPacket(Action action, UUID contractId, UUID warehouseId, String operationKey) {
    public enum Action { ACCEPT, COMPLETE }

    public static void encode(ContractActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action); buffer.writeUUID(packet.contractId); buffer.writeUUID(packet.warehouseId);
        buffer.writeUtf(packet.operationKey, 64);
    }
    public static ContractActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID contractId = buffer.readUUID();
        UUID warehouseId = buffer.readUUID();
        String operationKey = buffer.readUtf(64);
        if (operationKey.isBlank()) throw new DecoderException("Invalid contract operation key");
        return new ContractActionPacket(action, contractId, warehouseId, operationKey);
    }
    public static void handle(ContractActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || packet.action == null || packet.operationKey.isBlank()
                    || !(player.containerMenu instanceof WarehouseMenu menu)
                    || !menu.warehouseId().equals(packet.warehouseId) || !menu.stillValid(player)) return;
            long day = player.serverLevel().getGameTime() / 24_000L;
            ContractSettlementResult result = packet.action == Action.ACCEPT
                    ? ContractService.accept(player, packet.contractId, packet.warehouseId, day, packet.operationKey)
                    : ContractService.complete(player, packet.contractId, packet.warehouseId, day, packet.operationKey);
            if (result.success()) {
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.25F);
                player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + 1.0D, player.getZ(), 7, 0.3D, 0.35D, 0.3D, 0.0D);
            }
            WarehouseGuiOpener.open(player, menu.blockPos(), result.messageKey(),
                    (int) Math.min(Integer.MAX_VALUE, result.reward()));
        });
        context.setPacketHandled(true);
    }
}
