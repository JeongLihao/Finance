package finance.settlement;

import net.minecraft.core.BlockPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SettlementRecord {
    public static final int MAX_CONTRIBUTORS = 256;
    private final UUID id;
    private final String dimensionId;
    private BlockPos anchor;
    private String displayName;
    private SettlementStatus status;
    private long lastDemandDay, lastEventDay;
    private String lastEventKey;
    private long casualtyDay=-1;
    private int casualtyCount;
    private final LinkedHashMap<UUID,Integer> contribution = new LinkedHashMap<>();

    public SettlementRecord(UUID id,String dimensionId,BlockPos anchor,String displayName,SettlementStatus status,
                            long lastDemandDay,long lastEventDay,String lastEventKey){
        if(id==null||dimensionId==null||dimensionId.isBlank()||dimensionId.length()>128||anchor==null
                ||displayName==null||displayName.isBlank()||displayName.length()>64||status==null
                ||lastDemandDay < -1 || lastEventDay < -1)
            throw new IllegalArgumentException("invalid settlement");
        this.id=id;this.dimensionId=dimensionId;this.anchor=anchor.immutable();this.displayName=displayName;
        this.status=status;this.lastDemandDay=lastDemandDay;this.lastEventDay=lastEventDay;
        this.lastEventKey=limit(lastEventKey,64);
    }
    public void activate(BlockPos pos){anchor=pos.immutable();if(status==SettlementStatus.DISABLED)status=SettlementStatus.ACTIVE;}
    public void disable(){if(status!=SettlementStatus.QUARANTINED)status=SettlementStatus.DISABLED;}
    public void raidAlert(long day,String key){status=SettlementStatus.RAID_ALERT;lastEventDay=day;lastEventKey=limit(key,64);}
    public void rebuilding(long day,String key){status=SettlementStatus.REBUILDING;lastEventDay=day;lastEventKey=limit(key,64);}
    public void finishRebuild(){if(status==SettlementStatus.REBUILDING)status=SettlementStatus.ACTIVE;}
    public boolean noteCasualty(long day){if(day!=casualtyDay){casualtyDay=day;casualtyCount=0;}casualtyCount=Math.min(100,casualtyCount+1);if(casualtyCount==3&&status!=SettlementStatus.RAID_ALERT){rebuilding(day,"casualty:"+day);return true;}return false;}
    public void restoreCasualties(long day,int count){casualtyDay=day;casualtyCount=Math.max(0,Math.min(100,count));}
    public boolean markDemandDay(long day){if(day<=lastDemandDay)return false;lastDemandDay=day;return true;}
    public int addContribution(UUID player,int amount){if(player==null||amount<=0)return points(player);int now=Math.min(500,points(player)+Math.min(50,amount));contribution.put(player,now);while(contribution.size()>MAX_CONTRIBUTORS)contribution.remove(contribution.keySet().iterator().next());return now;}
    public void restoreContribution(UUID player,int points){if(player!=null&&points>0&&points<=500&&contribution.size()<MAX_CONTRIBUTORS)contribution.put(player,points);}
    public int points(UUID player){return player==null?0:contribution.getOrDefault(player,0);}
    public int level(UUID player){int p=points(player);return p>=300?5:p>=180?4:p>=100?3:p>=50?2:p>=20?1:0;}
    public Map<UUID,Integer> contributions(){return Map.copyOf(contribution);}
    public UUID id(){return id;}public String dimensionId(){return dimensionId;}public BlockPos anchor(){return anchor;}
    public String displayName(){return displayName;}public SettlementStatus status(){return status;}
    public long lastDemandDay(){return lastDemandDay;}public long lastEventDay(){return lastEventDay;}public String lastEventKey(){return lastEventKey;}
    public long casualtyDay(){return casualtyDay;}public int casualtyCount(){return casualtyCount;}
    private static String limit(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n);}
}
