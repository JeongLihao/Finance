package finance.network;

import finance.cycle.EconomyCycleService;
import finance.insurance.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record InsuranceActionPacket(Action action,InsuranceProduct product,UUID objectId,long coverage,int termDays,String operationKey){
 public enum Action{PURCHASE,CANCEL,PROCESS_PAYMENTS,WAREHOUSE_TEST,SHUTDOWN_TEST}
 public static void encode(InsuranceActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeEnum(p.product);b.writeBoolean(p.objectId!=null);if(p.objectId!=null)b.writeUUID(p.objectId);b.writeLong(p.coverage);b.writeVarInt(p.termDays);b.writeUtf(p.operationKey==null?"":p.operationKey,96);}
 public static InsuranceActionPacket decode(FriendlyByteBuf b){Action a=b.readEnum(Action.class);InsuranceProduct p=b.readEnum(InsuranceProduct.class);UUID id=b.readBoolean()?b.readUUID():null;long coverage=b.readLong();int term=b.readVarInt();String key=b.readUtf(96);if(term<0||term>3650)throw new IllegalArgumentException("insurance term");return new InsuranceActionPacket(a,p,id,coverage,term,key);}
 public static void handle(InsuranceActionPacket p,Supplier<NetworkEvent.Context>s){var c=s.get();c.enqueueWork(()->{ServerPlayer player=c.getSender();if(player==null||p.action==null||p.product==null)return;if(!MarketDataRequestLimiter.allow(player.getUUID(),player.server.getTickCount(),"insurance:"+p.action)){GuiFeedbackPacket.send(player,"操作过于频繁");return;}long day=EconomyCycleService.currentMcDay(player.server);String msg=switch(p.action){case PURCHASE->InsuranceManager.purchase(player.getUUID(),p.product,p.objectId,p.coverage,p.termDays,day,p.operationKey).message();case CANCEL->InsuranceManager.cancel(player.getUUID(),p.objectId,day).message();case PROCESS_PAYMENTS->player.hasPermissions(2)?"处理赔付 "+InsuranceManager.processPayments(day):"权限不足";case WAREHOUSE_TEST,SHUTDOWN_TEST->"风险事件只能由服务端事件调度器或管理员命令携带权威参数创建";};GuiFeedbackPacket.send(player,msg);});c.setPacketHandled(true);}
}
