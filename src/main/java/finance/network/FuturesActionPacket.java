package finance.network;

import finance.cycle.EconomyCycleService;
import finance.futures.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record FuturesActionPacket(Action action,UUID targetId,String commodityId,long amount,long price,long quantity,int termDays){
    public enum Action{DEPOSIT_MARGIN,WITHDRAW_MARGIN,PLACE_BUY,PLACE_SELL,CANCEL_ORDER,CREATE_CONTRACT}
    public FuturesActionPacket{commodityId=commodityId==null?"":commodityId.trim();}
    public static void encode(FuturesActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeBoolean(p.targetId!=null);if(p.targetId!=null)b.writeUUID(p.targetId);b.writeUtf(p.commodityId,64);b.writeLong(p.amount);b.writeLong(p.price);b.writeLong(p.quantity);b.writeVarInt(p.termDays);}
    public static FuturesActionPacket decode(FriendlyByteBuf b){var a=b.readEnum(Action.class);UUID id=b.readBoolean()?b.readUUID():null;var p=new FuturesActionPacket(a,id,b.readUtf(64),b.readLong(),b.readLong(),b.readLong(),b.readVarInt());if(p.termDays<0||p.termDays>3_650)throw new IllegalArgumentException("futures term range");return p;}
    public static void handle(FuturesActionPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->{ServerPlayer player=ctx.getSender();if(player==null||p.action==null)return;if(!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.FUTURES)){GuiFeedbackPacket.send(player,"期货模块已因一致性问题暂停");return;}if(!MarketDataRequestLimiter.allow(player.getUUID(),player.server.getTickCount(),"futures-action:"+p.action)){GuiFeedbackPacket.send(player,"操作过于频繁");return;}String message;switch(p.action){
        case DEPOSIT_MARGIN->message=p.amount>0&&MarginManager.deposit(player.getUUID(),p.amount)?"保证金已转入":"保证金转入失败";
        case WITHDRAW_MARGIN->message=p.amount>0&&MarginManager.withdraw(player.getUUID(),p.amount)?"保证金已转出":"可提保证金不足";
        case PLACE_BUY->message=p.targetId==null?"合约无效":FuturesMarketManager.place(player.getUUID(),p.targetId,FuturesOrderSide.BUY,p.price,p.quantity).message();
        case PLACE_SELL->message=p.targetId==null?"合约无效":FuturesMarketManager.place(player.getUUID(),p.targetId,FuturesOrderSide.SELL,p.price,p.quantity).message();
        case CANCEL_ORDER->message=p.targetId!=null&&FuturesMarketManager.cancel(player.getUUID(),p.targetId)?"期货订单已撤销":"订单不存在或无权撤销";
        case CREATE_CONTRACT->{if(!player.hasPermissions(2)||p.commodityId.isBlank()||p.termDays<2)message="权限不足或合约参数无效";else{long day=EconomyCycleService.currentMcDay(player.server),maturity;try{maturity=Math.addExact(day,p.termDays);}catch(ArithmeticException ex){message="到期日溢出";break;}message=FuturesMarketManager.createStandard(p.commodityId,day,maturity-1,maturity).message();}}
        default->message="未知期货操作";}GuiFeedbackPacket.send(player,message);});ctx.setPacketHandled(true);}
}
