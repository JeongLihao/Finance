package finance.network;

import finance.gui.SettlementGuiOpener;
import finance.settlement.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;import java.util.function.Supplier;

public record SettlementActionPacket(Action action,UUID settlementId,UUID demandId,String operationKey){public enum Action{ACCEPT,DELIVER}
    public static void encode(SettlementActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeUUID(p.settlementId);b.writeUUID(p.demandId);b.writeUtf(p.operationKey,64);}
    public static SettlementActionPacket decode(FriendlyByteBuf b){Action a=b.readEnum(Action.class);UUID s=b.readUUID(),d=b.readUUID();String k=b.readUtf(64);if(k.isBlank())throw new IllegalArgumentException("blank operation");return new SettlementActionPacket(a,s,d,k);}
    public static void handle(SettlementActionPacket p,Supplier<NetworkEvent.Context> supplier){NetworkEvent.Context c=supplier.get();c.enqueueWork(()->{var player=c.getSender();if(player==null)return;SettlementActionResult r=p.action==Action.ACCEPT?SettlementService.accept(player,p.settlementId,p.demandId,p.operationKey):SettlementService.deliver(player,p.settlementId,p.demandId,p.operationKey);var s=SettlementManager.get(p.settlementId);if(s!=null&&s.dimensionId().equals(player.serverLevel().dimension().location().toString()))SettlementGuiOpener.open(player,s.anchor(),r.messageKey());});c.setPacketHandled(true);}
}
