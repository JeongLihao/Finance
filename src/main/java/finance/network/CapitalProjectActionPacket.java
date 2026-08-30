package finance.network;

import finance.gameplay.company.capital.CapitalFundingSource;
import finance.gameplay.company.capital.CapitalProjectActionResult;
import finance.gameplay.company.capital.CapitalProjectManager;
import finance.gameplay.company.capital.CapitalProjectService;
import finance.gameplay.company.capital.WorldCapitalProject;
import finance.gameplay.company.capital.WorldCapitalProjectType;
import finance.gui.CompanyGameplayGuiOpener;
import finance.gui.CompanyGameplayMenu;
import finance.gui.WarehouseGuiOpener;
import finance.gui.WarehouseMenu;
import finance.warehouse.WarehouseManager;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server-authoritative intent packet; it never accepts budgets or material quantities. */
public record CapitalProjectActionPacket(Action action, UUID projectId, UUID targetId, UUID proposalId,
                                         UUID bankId, WorldCapitalProjectType projectType,
                                         CapitalFundingSource fundingSource, String operationKey) {
    public enum Action { CREATE, PROPOSE, AUTHORIZE, START_FUNDING, EXECUTE, RECOVER, CANCEL }

    public static void encode(CapitalProjectActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        writeId(buffer, packet.projectId);
        writeId(buffer, packet.targetId);
        writeId(buffer, packet.proposalId);
        writeId(buffer, packet.bankId);
        buffer.writeBoolean(packet.projectType != null);
        if (packet.projectType != null) buffer.writeEnum(packet.projectType);
        buffer.writeBoolean(packet.fundingSource != null);
        if (packet.fundingSource != null) buffer.writeEnum(packet.fundingSource);
        buffer.writeUtf(packet.operationKey == null ? "" : packet.operationKey, 48);
    }

    public static CapitalProjectActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID project = readId(buffer), target = readId(buffer), proposal = readId(buffer), bank = readId(buffer);
        WorldCapitalProjectType type = buffer.readBoolean() ? buffer.readEnum(WorldCapitalProjectType.class) : null;
        CapitalFundingSource source = buffer.readBoolean() ? buffer.readEnum(CapitalFundingSource.class) : null;
        String key = buffer.readUtf(48);
        if (key.isBlank() || action == Action.CREATE && (target == null || type == null || source == null)
                || action != Action.CREATE && project == null || action == Action.AUTHORIZE && proposal == null)
            throw new DecoderException("Invalid capital project intent");
        return new CapitalProjectActionPacket(action, project, target, proposal, bank, type, source, key);
    }

    public static void handle(CapitalProjectActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> run(packet, context.getSender()));
        context.setPacketHandled(true);
    }

    private static void run(CapitalProjectActionPacket packet, ServerPlayer player) {
        if (player == null || packet == null || packet.action == null
                || !MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(),
                "capital:" + packet.action)) return;
        Session session = session(player);
        if (session == null) return;
        WorldCapitalProject existing = CapitalProjectManager.get(packet.projectId);
        if (packet.action != Action.CREATE && (existing == null || !session.companyId.equals(existing.companyId()))) return;
        if (packet.action == Action.CREATE && !session.acceptsTarget(packet.targetId)) return;
        long day = finance.cycle.EconomyCycleService.currentMcDay(player.server);
        CapitalProjectActionResult result = switch (packet.action) {
            case CREATE -> CapitalProjectService.create(player, session.companyId, packet.projectType,
                    packet.targetId, packet.fundingSource, day, packet.operationKey);
            case PROPOSE -> CapitalProjectService.propose(player.getUUID(), packet.projectId,
                    day, packet.operationKey);
            case AUTHORIZE -> CapitalProjectService.authorize(player.getUUID(), packet.projectId,
                    packet.proposalId, day, packet.operationKey);
            case START_FUNDING -> CapitalProjectService.startFunding(player.getUUID(), packet.projectId,
                    packet.bankId, day, packet.operationKey);
            case EXECUTE -> CapitalProjectService.execute(player, packet.projectId, day, packet.operationKey);
            case RECOVER -> CapitalProjectService.recover(player.getUUID(), packet.projectId,
                    day, packet.operationKey);
            case CANCEL -> CapitalProjectService.cancel(player, packet.projectId, day, packet.operationKey);
        };
        if (session.warehouse) WarehouseGuiOpener.open(player, session.pos, result.messageKey(), 0);
        else CompanyGameplayGuiOpener.open(player, session.pos, result.messageKey());
    }

    private static Session session(ServerPlayer player) {
        if (player.containerMenu instanceof CompanyGameplayMenu menu && menu.stillValid(player)) {
            return new Session(menu.companyId(), menu.pos(), false, null);
        }
        if (player.containerMenu instanceof WarehouseMenu menu && menu.stillValid(player)) {
            var record = WarehouseManager.get(menu.warehouseId());
            if (record != null && record.companyId() != null)
                return new Session(record.companyId(), menu.blockPos(), true, record.warehouseId());
        }
        return null;
    }

    private static void writeId(FriendlyByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) buffer.writeUUID(id);
    }

    private static UUID readId(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    private record Session(UUID companyId, BlockPos pos, boolean warehouse, UUID warehouseId) {
        boolean acceptsTarget(UUID targetId) {
            if (targetId == null) return false;
            if (warehouse) return targetId.equals(warehouseId);
            var facility = finance.gameplay.company.CompanyFacilityManager.get(targetId);
            var warehouseRecord = finance.warehouse.WarehouseManager.get(targetId);
            return facility != null && companyId.equals(facility.companyId())
                    || warehouseRecord != null && companyId.equals(warehouseRecord.companyId());
        }
    }
}
