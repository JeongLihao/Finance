package finance.network;

import finance.cycle.EconomyCycleService;
import finance.fund.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record FundRequestPacket(long requestId){
    public static void encode(FundRequestPacket p,FriendlyByteBuf b){b.writeLong(p.requestId);}
    public static FundRequestPacket decode(FriendlyByteBuf b){return new FundRequestPacket(b.readLong());}
    public static void handle(FundRequestPacket p,Supplier<NetworkEvent.Context> supplier){NetworkEvent.Context c=supplier.get();c.enqueueWork(()->{ServerPlayer player=c.getSender();if(player==null||p.requestId<=0||!MarketDataRequestLimiter.allow(player.getUUID(),player.server.getTickCount(),"fund-data"))return;FundManager.seedDefaultsIfNeeded();long day=EconomyCycleService.currentMcDay(player.server);List<FundResponsePacket.FundRow> funds=new ArrayList<>();var states=FundManager.states();FundManager.definitions().forEach((id,d)->{FundState s=states.get(id);var pos=FundManager.position(player.getUUID(),id);var risk=FundRiskService.calculate(d,s,day);funds.add(new FundResponsePacket.FundRow(id,d.displayName(),d.type(),s.status(),s.currentNav(),s.previousNav(),pos==null?0:pos.shareUnits(),pos==null?0:pos.frozenShareUnits(),pos==null?0:pos.totalCost(),d.managementFeeBps(),d.subscriptionFeeBps(),d.redemptionFeeBps(),risk.totalReturnPercent(),risk.recentVolatilityPercent(),risk.maximumDrawdownPercent(),risk.liquidityGrade(),risk.sufficientHistory(),s.suspensionReason()));});List<FundResponsePacket.PlanRow> plans=FundManager.plansFor(player.getUUID(),FundResponsePacket.MAX_ROWS).stream().map(x->new FundResponsePacket.PlanRow(x.id(),x.fundId(),x.amount(),x.intervalDays(),x.nextExecutionDay(),x.failureCount(),x.status())).toList();List<FundResponsePacket.RedemptionRow> redemptions=FundManager.requestsFor(player.getUUID(),FundResponsePacket.MAX_ROWS).stream().map(x->new FundResponsePacket.RedemptionRow(x.id(),x.fundId(),x.shareUnits(),x.createdDay(),x.status(),x.failureReason())).toList();FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new FundResponsePacket(p.requestId,funds,plans,redemptions));});c.setPacketHandled(true);}
}
