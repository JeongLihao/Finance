package finance.gui;

import finance.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class SettlementMenu extends AbstractContainerMenu {
    public static final int MAX_ROWS=8;
    public record DemandRow(UUID id,String commodity,String theme,int quantity,long reward,long deadline,String status,boolean mine){}
    private final UUID settlementId;private final String name,status,message;private final int contributionLevel,contributionPoints;private final List<DemandRow> rows;
    public SettlementMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,b.readUUID(),b.readUtf(64),b.readUtf(24),b.readVarInt(),b.readVarInt(),readRows(b),b.readUtf(96));}
    public SettlementMenu(int id,UUID settlementId,String name,String status,int level,int points,List<DemandRow> rows,String message){super(ModMenus.SETTLEMENT.get(),id);this.settlementId=settlementId;this.name=limit(name,64);this.status=limit(status,24);this.contributionLevel=Math.max(0,Math.min(5,level));this.contributionPoints=Math.max(0,points);this.rows=List.copyOf(rows.subList(0,Math.min(MAX_ROWS,rows.size())));this.message=limit(message,96);}
    public static void write(FriendlyByteBuf b,UUID id,String name,String status,int level,int points,List<DemandRow> rows,String message){b.writeUUID(id);b.writeUtf(limit(name,64),64);b.writeUtf(limit(status,24),24);b.writeVarInt(Math.max(0,Math.min(5,level)));b.writeVarInt(Math.max(0,points));int n=Math.min(MAX_ROWS,rows.size());b.writeVarInt(n);for(int i=0;i<n;i++){DemandRow r=rows.get(i);b.writeUUID(r.id());b.writeUtf(limit(r.commodity(),64),64);b.writeUtf(limit(r.theme(),32),32);b.writeVarInt(r.quantity());b.writeLong(r.reward());b.writeLong(r.deadline());b.writeUtf(limit(r.status(),16),16);b.writeBoolean(r.mine());}b.writeUtf(limit(message,96),96);}
    static List<DemandRow> readRows(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>MAX_ROWS)throw new IllegalArgumentException("settlement rows");List<DemandRow> out=new ArrayList<>(n);for(int i=0;i<n;i++){DemandRow r=new DemandRow(b.readUUID(),b.readUtf(64),b.readUtf(32),b.readVarInt(),b.readLong(),b.readLong(),b.readUtf(16),b.readBoolean());if(r.commodity().isBlank()||r.quantity()<=0||r.reward()<=0||r.deadline()<0)throw new IllegalArgumentException("invalid settlement row");out.add(r);}return out;}
    private static String limit(String s,int n){s=s==null?"":s;return s.length()<=n?s:s.substring(0,n);}public UUID settlementId(){return settlementId;}public String name(){return name;}public String status(){return status;}public int contributionLevel(){return contributionLevel;}public int contributionPoints(){return contributionPoints;}public List<DemandRow> rows(){return rows;}public String message(){return message;}
    @Override public boolean stillValid(Player p){return !(p instanceof ServerPlayer sp)||finance.settlement.SettlementService.isNearby(sp,settlementId);}@Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
}
