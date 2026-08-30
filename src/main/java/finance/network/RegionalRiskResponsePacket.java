package finance.network;

import finance.collateral.InventoryCollateralStatus;
import finance.futures.MarginRiskStatus;
import finance.hedge.*;
import finance.regional.RegionalSupplyPressure;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public record RegionalRiskResponsePacket(long requestId,List<RegionRow> regions,List<CollateralRow> collateral,
                                         List<HedgeRow> hedges,List<ContractRow> contracts,List<BankRow> banks){
    public static final int MAX_ROWS=128,MAX_TREND=7;
    public record RegionRow(String region,String dimension,String commodity,long day,int premiumBps,int shortageBps,int deliveryBps,RegionalSupplyPressure pressure,boolean reliable,List<Integer> trend){}
    public record CollateralRow(UUID id,UUID bankId,UUID loanId,String commodity,int quantity,long value,int ltvBps,InventoryCollateralStatus status,long marginCallDay,long recovered){}
    public record HedgeRow(UUID id,UUID contractId,String contractCode,String commodity,HedgeObjectiveType type,long target,long deadline,HedgeCoverageStatus status,int coverageBps,MarginRiskStatus marginRisk,long realized,boolean personal){}
    public record ContractRow(UUID id,String code,String commodity,long maturityDay){}
    public record BankRow(UUID id,String code){}
    public RegionalRiskResponsePacket{if(requestId<=0)throw new IllegalArgumentException("request");regions=bound(regions);collateral=bound(collateral);hedges=bound(hedges);contracts=bound(contracts);banks=bound(banks);}
    private static<T>List<T>bound(List<T>x){return x==null?List.of():List.copyOf(x.subList(0,Math.min(MAX_ROWS,x.size())));}
    public static void encode(RegionalRiskResponsePacket p,FriendlyByteBuf b){b.writeLong(p.requestId);b.writeVarInt(p.regions.size());for(var r:p.regions){b.writeUtf(r.region,16);b.writeUtf(r.dimension,128);b.writeUtf(r.commodity,64);b.writeLong(r.day);b.writeVarInt(r.premiumBps);b.writeVarInt(r.shortageBps);b.writeVarInt(r.deliveryBps);b.writeEnum(r.pressure);b.writeBoolean(r.reliable);List<Integer> trend=r.trend==null?List.of():r.trend.subList(0,Math.min(MAX_TREND,r.trend.size()));b.writeVarInt(trend.size());for(int x:trend)b.writeVarInt(x);}b.writeVarInt(p.collateral.size());for(var r:p.collateral){b.writeUUID(r.id);b.writeUUID(r.bankId);b.writeUUID(r.loanId);b.writeUtf(r.commodity,64);b.writeVarInt(r.quantity);b.writeLong(r.value);b.writeVarInt(r.ltvBps);b.writeEnum(r.status);b.writeLong(r.marginCallDay);b.writeLong(r.recovered);}b.writeVarInt(p.hedges.size());for(var r:p.hedges){b.writeUUID(r.id);b.writeUUID(r.contractId);b.writeUtf(r.contractCode,16);b.writeUtf(r.commodity,64);b.writeEnum(r.type);b.writeLong(r.target);b.writeLong(r.deadline);b.writeEnum(r.status);b.writeVarInt(r.coverageBps);b.writeEnum(r.marginRisk);b.writeLong(r.realized);b.writeBoolean(r.personal);}b.writeVarInt(p.contracts.size());for(var r:p.contracts){b.writeUUID(r.id);b.writeUtf(r.code,16);b.writeUtf(r.commodity,64);b.writeLong(r.maturityDay);}b.writeVarInt(p.banks.size());for(var r:p.banks){b.writeUUID(r.id);b.writeUtf(r.code,16);}}
    public static RegionalRiskResponsePacket decode(FriendlyByteBuf b){long id=b.readLong();int n=count(b);List<RegionRow>regions=new ArrayList<>(n);for(int i=0;i<n;i++){String region=b.readUtf(16),dimension=b.readUtf(128),commodity=b.readUtf(64);long day=b.readLong();int premium=b.readVarInt(),shortage=b.readVarInt(),delivery=b.readVarInt();var pressure=b.readEnum(RegionalSupplyPressure.class);boolean reliable=b.readBoolean();int tc=b.readVarInt();if(tc<0||tc>MAX_TREND)throw new IllegalArgumentException("trend");List<Integer>trend=new ArrayList<>(tc);for(int j=0;j<tc;j++)trend.add(b.readVarInt());regions.add(new RegionRow(region,dimension,commodity,day,premium,shortage,delivery,pressure,reliable,trend));}n=count(b);List<CollateralRow>collateral=new ArrayList<>(n);for(int i=0;i<n;i++)collateral.add(new CollateralRow(b.readUUID(),b.readUUID(),b.readUUID(),b.readUtf(64),b.readVarInt(),b.readLong(),b.readVarInt(),b.readEnum(InventoryCollateralStatus.class),b.readLong(),b.readLong()));n=count(b);List<HedgeRow>hedges=new ArrayList<>(n);for(int i=0;i<n;i++)hedges.add(new HedgeRow(b.readUUID(),b.readUUID(),b.readUtf(16),b.readUtf(64),b.readEnum(HedgeObjectiveType.class),b.readLong(),b.readLong(),b.readEnum(HedgeCoverageStatus.class),b.readVarInt(),b.readEnum(MarginRiskStatus.class),b.readLong(),b.readBoolean()));n=count(b);List<ContractRow>contracts=new ArrayList<>(n);for(int i=0;i<n;i++)contracts.add(new ContractRow(b.readUUID(),b.readUtf(16),b.readUtf(64),b.readLong()));n=count(b);List<BankRow>banks=new ArrayList<>(n);for(int i=0;i<n;i++)banks.add(new BankRow(b.readUUID(),b.readUtf(16)));return new RegionalRiskResponsePacket(id,regions,collateral,hedges,contracts,banks);}
    private static int count(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>MAX_ROWS)throw new IllegalArgumentException("risk rows");return n;}
    public static void handle(RegionalRiskResponsePacket p,Supplier<NetworkEvent.Context>s){var c=s.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->finance.client.RegionalRiskClientCache.accept(p)));c.setPacketHandled(true);}
}
