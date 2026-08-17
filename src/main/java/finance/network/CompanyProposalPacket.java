package finance.network;

import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalType;
import finance.cycle.EconomyCycleService;
import finance.diagnostic.ModuleHealthRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class CompanyProposalPacket {

    public enum Action { CREATE, VOTE }

    private final Action action;
    private final UUID id;
    private final CompanyProposalType type;
    private final String text;
    private final long value1;
    private final long value2;
    private final long value3;
    private final int durationDays;
    private final int passPercent;
    private final boolean support;

    public static CompanyProposalPacket create(UUID companyId, CompanyProposalType type, String text,
                                               long value1, long value2, long value3,
                                               int durationDays, int passPercent) {
        return new CompanyProposalPacket(Action.CREATE, companyId, type, text, value1, value2, value3,
                durationDays, passPercent, false);
    }

    public static CompanyProposalPacket vote(UUID proposalId, boolean support) {
        return new CompanyProposalPacket(Action.VOTE, proposalId, CompanyProposalType.FUND_USAGE, "",
                0, 0, 0, 0, 0, support);
    }

    private CompanyProposalPacket(Action action, UUID id, CompanyProposalType type, String text,
                                  long value1, long value2, long value3,
                                  int durationDays, int passPercent, boolean support) {
        this.action = action;
        this.id = id;
        this.type = type;
        this.text = text == null ? "" : text;
        this.value1 = value1;
        this.value2 = value2;
        this.value3 = value3;
        this.durationDays = durationDays;
        this.passPercent = passPercent;
        this.support = support;
    }

    public static void encode(CompanyProposalPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.id);
        if (packet.action == Action.CREATE) {
            buffer.writeEnum(packet.type);
            buffer.writeUtf(packet.text, NetworkValidation.MAX_DISPLAY_NAME_LENGTH);
            buffer.writeLong(packet.value1);
            buffer.writeLong(packet.value2);
            buffer.writeLong(packet.value3);
            buffer.writeVarInt(packet.durationDays);
            buffer.writeVarInt(packet.passPercent);
        } else {
            buffer.writeBoolean(packet.support);
        }
    }

    public static CompanyProposalPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID id = buffer.readUUID();
        if (action == Action.CREATE) {
            CompanyProposalPacket packet=create(id, buffer.readEnum(CompanyProposalType.class),
                    buffer.readUtf(NetworkValidation.MAX_DISPLAY_NAME_LENGTH),
                    buffer.readLong(), buffer.readLong(), buffer.readLong(),
                    buffer.readVarInt(), buffer.readVarInt());
            if(packet.durationDays<=0||packet.durationDays>CompanyProposalManager.MAX_PROPOSAL_DURATION_DAYS)
                throw new IllegalArgumentException("proposal duration");
            return packet;
        }
        return vote(id, buffer.readBoolean());
    }

    public static void handle(CompanyProposalPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || packet.action == null || packet.id == null) {
                return;
            }
            if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.STOCK)) {
                GuiFeedbackPacket.send(player, "股票与治理模块已暂停写入。");
                return;
            }
            if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(),
                    "proposal:" + packet.action)) {
                GuiFeedbackPacket.send(player, "操作过于频繁。");
                return;
            }
            long mcDay = EconomyCycleService.currentMcDay(player.getServer());
            CompanyProposalManager.Result result;
            if (packet.action == Action.CREATE) {
                if (packet.type == null || packet.durationDays <= 0
                        || packet.durationDays > CompanyProposalManager.MAX_PROPOSAL_DURATION_DAYS
                        || packet.passPercent <= 0 || packet.passPercent > 100) {
                    GuiFeedbackPacket.send(player, "提案参数无效。");
                    return;
                }
                long endDay;
                try { endDay=Math.addExact(mcDay,packet.durationDays); }
                catch(ArithmeticException overflow){GuiFeedbackPacket.send(player,"提案期限无效。");return;}
                result = CompanyProposalManager.createProposal(
                        player.getUUID(),
                        packet.id,
                        packet.type,
                        packet.text,
                        packet.value1,
                        packet.value2,
                        packet.value3,
                        mcDay,
                        endDay,
                        packet.passPercent / 100.0);
            } else {
                result = CompanyProposalManager.vote(player.getUUID(), packet.id, packet.support, mcDay);
            }
            GuiFeedbackPacket.send(player, result.message());
        });
        ctx.get().setPacketHandled(true);
    }
}
