package finance.network;

import finance.contract.FinanceContract;
import finance.gameplay.FinanceScreenMode;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.company.*;
import finance.gui.CompanyGameplayGuiOpener;
import finance.gui.CompanyGameplayMenu;
import finance.gui.FinanceGuiOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;
import io.netty.handler.codec.DecoderException;

public record CompanyGameplayActionPacket(Action action,UUID companyId,UUID targetId,String text,
                                          int quantity,long amount,String operationKey) {
    private static final int MAX_CONTRACT_QUANTITY=1_000_000;
    private static final long MAX_CONTRACT_REWARD=1_000_000L;
    public enum Action { MODE_NEXT, AUTO_SELL_NEXT, INVITE, ACCEPT_INVITE, REJECT_INVITE, LEAVE, ROLE_NEXT, REMOVE_MEMBER, UPGRADE_FACILITY, PUBLISH_CONTRACT, OPEN_ADVANCED }
    private static final UUID NIL=new UUID(0,0);
    public static void encode(CompanyGameplayActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeUUID(p.companyId);b.writeUUID(p.targetId==null?NIL:p.targetId);b.writeUtf(p.text==null?"":p.text,64);b.writeVarInt(p.quantity);b.writeLong(p.amount);b.writeUtf(p.operationKey,64);}
    public static CompanyGameplayActionPacket decode(FriendlyByteBuf b){
        Action a=b.readEnum(Action.class);UUID c=b.readUUID(),t=b.readUUID();String text=b.readUtf(64);
        int quantity=b.readVarInt();long amount=b.readLong();String operationKey=b.readUtf(64);
        UUID target=t.equals(NIL)?null:t;
        if(c.equals(NIL)||operationKey.isBlank()||quantity<0||amount<0
                ||a==Action.PUBLISH_CONTRACT&&(text.isBlank()||quantity<=0||quantity>MAX_CONTRACT_QUANTITY
                ||amount<=0||amount>MAX_CONTRACT_REWARD)
                ||(a==Action.INVITE||a==Action.ROLE_NEXT||a==Action.REMOVE_MEMBER||a==Action.UPGRADE_FACILITY)&&target==null)
            throw new DecoderException("Invalid company gameplay action intent");
        return new CompanyGameplayActionPacket(a,c,target,text,quantity,amount,operationKey);
    }
    public static void handle(CompanyGameplayActionPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || p.action == null || p.companyId == null || p.operationKey == null
                    || p.operationKey.isBlank() || !(player.containerMenu instanceof CompanyGameplayMenu menu)
                    || !menu.companyId().equals(p.companyId) || !menu.stillValid(player)) return;
            CompanyGameplayActionResult result;
            long day = player.serverLevel().getGameTime() / 24_000L;
            switch (p.action) {
                case MODE_NEXT -> {
                    CompanyGameplayProfile profile = CompanyGameplayManager.get(p.companyId);
                    CompanyOperatingMode[] modes = CompanyOperatingMode.values();
                    CompanyOperatingMode next = profile == null ? null
                            : modes[(profile.operatingMode().ordinal() + 1) % modes.length];
                    result = CompanyOperatingModeService.setMode(player.getUUID(), p.companyId, next, p.operationKey);
                }
                case AUTO_SELL_NEXT -> result = CompanyOperatingModeService.cycleAutoSell(
                        player.getUUID(), p.companyId, p.operationKey);
                case INVITE -> result = CompanyMembershipService.invite(player.getUUID(), p.companyId,
                        p.targetId, CompanyMemberRole.MEMBER, day, p.operationKey);
                case ACCEPT_INVITE -> result = CompanyMembershipService.acceptInvite(
                        player.getUUID(), p.companyId, day, p.operationKey);
                case REJECT_INVITE -> result = CompanyMembershipService.rejectInvite(
                        player.getUUID(), p.companyId, p.operationKey);
                case LEAVE -> result = CompanyMembershipService.leaveCompany(
                        player.getUUID(), p.companyId, p.operationKey);
                case ROLE_NEXT -> {
                    CompanyGameplayProfile profile = CompanyGameplayManager.get(p.companyId);
                    CompanyMemberRecord member = profile == null ? null : profile.members().get(p.targetId);
                    CompanyMemberRole next = member == null ? null : switch (member.role()) {
                        case MEMBER -> CompanyMemberRole.WAREHOUSE_WORKER;
                        case WAREHOUSE_WORKER -> CompanyMemberRole.TREASURER;
                        case TREASURER -> CompanyMemberRole.MANAGER;
                        case MANAGER, OWNER -> CompanyMemberRole.MEMBER;
                    };
                    result = CompanyMembershipService.changeRole(player.getUUID(), p.companyId,
                            p.targetId, next, p.operationKey);
                }
                case REMOVE_MEMBER -> result = CompanyMembershipService.removeMember(
                        player.getUUID(), p.companyId, p.targetId, p.operationKey);
                case UPGRADE_FACILITY -> result = CompanyUpgradeService.upgrade(
                        player, p.targetId, p.operationKey);
                case PUBLISH_CONTRACT -> {
                    FinanceContract contract = CompanyContractService.publishProcurement(player.getUUID(),
                            p.companyId, p.text, p.quantity, p.amount, day, 3, p.operationKey);
                    result = contract == null
                            ? CompanyGameplayActionResult.fail("finance.company_gameplay.contract_failed")
                            : CompanyGameplayActionResult.ok("finance.company_gameplay.contract_published");
                }
                case OPEN_ADVANCED -> {
                    FinanceGuiOpener.open(player, FinanceScreenMode.COMPANY,
                            FinanceTerminalType.COMPANY_DESK, menu.pos()); return;
                }
                default -> result = CompanyGameplayActionResult.fail("finance.company_gameplay.invalid_request");
            }
            CompanyGameplayGuiOpener.open(player, menu.pos(), result.messageKey());
        });
        context.setPacketHandled(true);
    }
}
