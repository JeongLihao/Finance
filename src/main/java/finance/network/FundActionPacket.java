package finance.network;

import finance.cycle.EconomyCycleService;
import finance.fund.FundInvestmentPlan;
import finance.fund.FundManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record FundActionPacket(Action action,String fundId,UUID targetId,long amount,long shareUnits,int intervalDays,String operationKey){
    public enum Action{ACKNOWLEDGE_RISK,SUBSCRIBE,REDEEM,CANCEL_REDEMPTION,CREATE_PLAN,PAUSE_PLAN,RESUME_PLAN,CANCEL_PLAN}
    private static final int MAX_ID=48,MAX_KEY=96;
    public static void encode(FundActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeUtf(p.fundId==null?"":p.fundId,MAX_ID);b.writeBoolean(p.targetId!=null);if(p.targetId!=null)b.writeUUID(p.targetId);b.writeLong(p.amount);b.writeLong(p.shareUnits);b.writeVarInt(p.intervalDays);b.writeUtf(p.operationKey==null?"":p.operationKey,MAX_KEY);}
    public static FundActionPacket decode(FriendlyByteBuf b){Action action=b.readEnum(Action.class);String id=b.readUtf(MAX_ID);UUID target=b.readBoolean()?b.readUUID():null;long amount=b.readLong(),shares=b.readLong();int interval=b.readVarInt();String key=b.readUtf(MAX_KEY);if(interval<0||interval>3650)throw new IllegalArgumentException("fund interval");return new FundActionPacket(action,id,target,amount,shares,interval,key);}
    public static void handle(FundActionPacket p,Supplier<NetworkEvent.Context> supplier){NetworkEvent.Context c=supplier.get();c.enqueueWork(()->{ServerPlayer player=c.getSender();if(player==null||p.action==null)return;if(!MarketDataRequestLimiter.allow(player.getUUID(),player.server.getTickCount(),"fund:"+p.action)){GuiFeedbackPacket.send(player,"操作过于频繁");return;}long day=EconomyCycleService.currentMcDay(player.server);String message=switch(p.action){case ACKNOWLEDGE_RISK->{var d=FundManager.definitions().get(finance.fund.FundDefinition.normalize(p.fundId));yield d!=null&&FundManager.acknowledgeRisk(player.getUUID(),d.type())?"风险说明已确认，可再次点击申购":"基金不存在";}case SUBSCRIBE->p.amount<=0?"申购金额无效":FundManager.subscribe(player.getUUID(),p.fundId,p.amount,day,p.operationKey).message();case REDEEM->p.shareUnits<=0?"赎回份额无效":FundManager.requestRedemption(player.getUUID(),p.fundId,p.shareUnits,day,p.operationKey).message();case CANCEL_REDEMPTION->p.targetId!=null&&FundManager.cancelRedemption(player.getUUID(),p.targetId)?"赎回已撤销":"无法撤销赎回";case CREATE_PLAN->FundManager.createPlan(player.getUUID(),p.fundId,p.amount,p.intervalDays,day).message();case PAUSE_PLAN->setPlan(player,p.targetId,FundInvestmentPlan.Status.PAUSED);case RESUME_PLAN->setPlan(player,p.targetId,FundInvestmentPlan.Status.ACTIVE);case CANCEL_PLAN->setPlan(player,p.targetId,FundInvestmentPlan.Status.CANCELLED);};GuiFeedbackPacket.send(player,message);});c.setPacketHandled(true);}
    private static String setPlan(ServerPlayer player,UUID id,FundInvestmentPlan.Status status){return id!=null&&FundManager.setPlanStatus(player.getUUID(),id,status)?"定投状态已更新":"无法更新定投";}
}
