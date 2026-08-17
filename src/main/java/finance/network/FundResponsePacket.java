package finance.network;

import finance.fund.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record FundResponsePacket(long requestId,List<FundRow> funds,List<PlanRow> plans,List<RedemptionRow> redemptions){
    public static final int MAX_ROWS=128;
    public record FundRow(String id,String name,FundType type,FundStatus status,long nav,long previousNav,long shares,long frozenShares,long cost,int managementFeeBps,int subscriptionFeeBps,int redemptionFeeBps,double totalReturn,double volatility,double maxDrawdown,String liquidity,boolean sufficientHistory,String reason){}
    public record PlanRow(UUID id,String fundId,long amount,int interval,long nextDay,int failures,FundInvestmentPlan.Status status){}
    public record RedemptionRow(UUID id,String fundId,long shares,long day,FundRedemptionRequest.Status status,String reason){}
    public FundResponsePacket{if(requestId<=0)throw new IllegalArgumentException();funds=bounded(funds);plans=bounded(plans);redemptions=bounded(redemptions);}private static<T>List<T>bounded(List<T>v){return v==null?List.of():List.copyOf(v.subList(0,Math.min(MAX_ROWS,v.size())));}
    public static void encode(FundResponsePacket p,FriendlyByteBuf b){b.writeLong(p.requestId);b.writeVarInt(p.funds.size());for(var r:p.funds){b.writeUtf(r.id,48);b.writeUtf(r.name,64);b.writeEnum(r.type);b.writeEnum(r.status);b.writeLong(r.nav);b.writeLong(r.previousNav);b.writeLong(r.shares);b.writeLong(r.frozenShares);b.writeLong(r.cost);b.writeInt(r.managementFeeBps);b.writeInt(r.subscriptionFeeBps);b.writeInt(r.redemptionFeeBps);b.writeDouble(r.totalReturn);b.writeDouble(r.volatility);b.writeDouble(r.maxDrawdown);b.writeUtf(r.liquidity,8);b.writeBoolean(r.sufficientHistory);b.writeUtf(r.reason,256);}b.writeVarInt(p.plans.size());for(var r:p.plans){b.writeUUID(r.id);b.writeUtf(r.fundId,48);b.writeLong(r.amount);b.writeInt(r.interval);b.writeLong(r.nextDay);b.writeInt(r.failures);b.writeEnum(r.status);}b.writeVarInt(p.redemptions.size());for(var r:p.redemptions){b.writeUUID(r.id);b.writeUtf(r.fundId,48);b.writeLong(r.shares);b.writeLong(r.day);b.writeEnum(r.status);b.writeUtf(r.reason,256);}}
    public static FundResponsePacket decode(FriendlyByteBuf b){long id=b.readLong();int n=count(b);List<FundRow>funds=new ArrayList<>(n);for(int i=0;i<n;i++)funds.add(new FundRow(b.readUtf(48),b.readUtf(64),b.readEnum(FundType.class),b.readEnum(FundStatus.class),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readInt(),b.readInt(),b.readInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readUtf(8),b.readBoolean(),b.readUtf(256)));n=count(b);List<PlanRow>plans=new ArrayList<>(n);for(int i=0;i<n;i++)plans.add(new PlanRow(b.readUUID(),b.readUtf(48),b.readLong(),b.readInt(),b.readLong(),b.readInt(),b.readEnum(FundInvestmentPlan.Status.class)));n=count(b);List<RedemptionRow>requests=new ArrayList<>(n);for(int i=0;i<n;i++)requests.add(new RedemptionRow(b.readUUID(),b.readUtf(48),b.readLong(),b.readLong(),b.readEnum(FundRedemptionRequest.Status.class),b.readUtf(256)));return new FundResponsePacket(id,funds,plans,requests);}private static int count(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>MAX_ROWS)throw new IllegalArgumentException("fund row limit");return n;}
    public static void handle(FundResponsePacket p,Supplier<NetworkEvent.Context>s){var c=s.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->finance.client.FundClientCache.accept(p)));c.setPacketHandled(true);}
}
