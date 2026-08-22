package finance.feedback;

import finance.data.EconomySavedData;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.WorldTerminalRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/** Bounded, event-driven world feedback. No method performs a chunk or world scan. */
public final class WorldEconomyFeedbackService {
    public static final int MAX_HISTORY=256,MAX_COOLDOWNS=512,MAX_PENDING_PLAYERS=1_024,MAX_PENDING_PER_PLAYER=32;
    public static final double LOCAL_RADIUS=32.0D;
    private static final List<WorldEconomyEvent> HISTORY=new ArrayList<>();
    private static final LinkedHashMap<String,Long> LAST_BROADCAST=new LinkedHashMap<>();
    private static final LinkedHashMap<UUID,ArrayDeque<FeedbackNotification>> PENDING=new LinkedHashMap<>();
    private static long publishedCount,suppressedCount,deliveredCount;
    private WorldEconomyFeedbackService(){}

    public static synchronized boolean record(WorldEconomyEvent event){
        if(event==null)return false;long previous=LAST_BROADCAST.getOrDefault(event.cooldownKey(),Long.MIN_VALUE);
        long cooldown=event.severity()==FeedbackSeverity.INFO?2:1;
        if(previous!=Long.MIN_VALUE&&(event.worldDay()<=previous||event.worldDay()-previous<cooldown)){suppressedCount++;return false;}
        LAST_BROADCAST.put(event.cooldownKey(),event.worldDay());trimCooldowns();HISTORY.add(event);
        while(HISTORY.size()>MAX_HISTORY)HISTORY.remove(0);publishedCount++;EconomySavedData.markDirty();return true;
    }

    public static boolean publish(MinecraftServer server,WorldEconomyEvent event){
        if(server==null||!record(event))return false;Component message=message(event);
        switch(event.audience()){
            case GLOBAL->{if(finance.config.FinanceConfig.worldEconomyGlobalBroadcasts())for(ServerPlayer p:server.getPlayerList().getPlayers())send(p,message);}
            case ADMIN->{for(ServerPlayer p:server.getPlayerList().getPlayers())if(p.hasPermissions(2))send(p,message);}
            case PARTICIPANTS->{for(UUID id:event.participants()){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p==null)queue(id,new FeedbackNotification(event.worldDay(),event.severity(),event.translationKey(),event.arguments()));else send(p,message);}}
            case LOCAL->publishLocal(server,event,message);
        }
        return true;
    }

    private static void publishLocal(MinecraftServer server,WorldEconomyEvent event,Component message){
        Set<UUID> sent=new HashSet<>();
        if(event.position()!=null&&!event.dimensionId().isBlank())sendNear(server,event.dimensionId(),event.position(),message,sent);
        else for(WorldTerminalRegistry.TerminalRecord terminal:WorldTerminalRegistry.byType(terminalFor(event.type())))
            sendNear(server,terminal.dimensionId(),terminal.position(),message,sent);
    }
    private static FinanceTerminalType terminalFor(WorldFeedbackType type){return switch(type){case BANK_RESTRICTED,BANK_RESOLUTION->FinanceTerminalType.BANK_COUNTER;default->FinanceTerminalType.MARKET_TERMINAL;};}
    private static void sendNear(MinecraftServer server,String dimensionId,net.minecraft.core.BlockPos pos,Component message,Set<UUID> sent){
        ResourceLocation location=ResourceLocation.tryParse(dimensionId);if(location==null)return;
        ServerLevel level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,location));if(level==null||!level.isLoaded(pos))return;
        double radius2=LOCAL_RADIUS*LOCAL_RADIUS;for(ServerPlayer player:level.players())if(sent.add(player.getUUID())&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=radius2)send(player,message);
    }
    private static void send(ServerPlayer player,Component message){player.sendSystemMessage(message);deliveredCount++;}
    private static Component message(WorldEconomyEvent event){return Component.translatable(event.translationKey(),event.arguments().toArray());}

    public static synchronized void queue(UUID playerId,FeedbackNotification notification){
        if(playerId==null||notification==null)return;
        ArrayDeque<FeedbackNotification> queue=PENDING.get(playerId);
        if(queue==null){while(PENDING.size()>=MAX_PENDING_PLAYERS)PENDING.remove(PENDING.keySet().iterator().next());queue=new ArrayDeque<>();PENDING.put(playerId,queue);}
        while(queue.size()>=MAX_PENDING_PER_PLAYER)queue.removeFirst();queue.addLast(notification);EconomySavedData.markDirty();
    }
    public static synchronized int deliverPending(ServerPlayer player){if(player==null)return 0;ArrayDeque<FeedbackNotification> queue=PENDING.remove(player.getUUID());if(queue==null)return 0;int count=0;for(FeedbackNotification n:queue){player.sendSystemMessage(Component.translatable(n.translationKey(),n.arguments().toArray()));count++;deliveredCount++;}if(count>0)EconomySavedData.markDirty();return count;}
    public static int unreadCount(UUID playerId){ArrayDeque<FeedbackNotification> queue=PENDING.get(playerId);return queue==null?0:queue.size();}
    public static List<WorldEconomyEvent> history(){return List.copyOf(HISTORY);}
    public static Map<String,Long> cooldowns(){return Map.copyOf(LAST_BROADCAST);}
    public static Map<UUID,List<FeedbackNotification>> pending(){Map<UUID,List<FeedbackNotification>> result=new LinkedHashMap<>();PENDING.forEach((id,q)->result.put(id,List.copyOf(q)));return Map.copyOf(result);}
    public static synchronized void restoreCooldown(String key,long day){if(key!=null&&!key.isBlank()&&key.length()<=256&&day>=0){LAST_BROADCAST.put(key,day);trimCooldowns();}}
    public static void restorePending(UUID player,List<FeedbackNotification> values){if(player==null||values==null)return;for(FeedbackNotification value:values.stream().limit(MAX_PENDING_PER_PLAYER).toList())queueDirect(player,value);}
    private static void queueDirect(UUID player,FeedbackNotification value){ArrayDeque<FeedbackNotification> q=PENDING.computeIfAbsent(player,k->new ArrayDeque<>());if(q.size()<MAX_PENDING_PER_PLAYER)q.addLast(value);}
    private static void trimCooldowns(){while(LAST_BROADCAST.size()>MAX_COOLDOWNS)LAST_BROADCAST.remove(LAST_BROADCAST.keySet().iterator().next());}
    public static Metrics metrics(){return new Metrics(publishedCount,suppressedCount,deliveredCount,LAST_BROADCAST.size(),PENDING.values().stream().mapToInt(Collection::size).sum());}
    public static void clearDirect(){HISTORY.clear();LAST_BROADCAST.clear();PENDING.clear();publishedCount=suppressedCount=deliveredCount=0;}
    public record Metrics(long published,long suppressed,long delivered,int cooldownKeys,int pendingNotifications){}
}
