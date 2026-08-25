package finance.advancement;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;

public final class FinanceAdvancementTriggers {
    public static final FinanceProgressTrigger PROGRESS=new FinanceProgressTrigger();
    private static boolean registered;
    private FinanceAdvancementTriggers(){}
    public static synchronized void register(){if(!registered){CriteriaTriggers.register(PROGRESS);registered=true;}}
    public static void trigger(ServerPlayer player,String event){
        finance.tutorial.TutorialProgressService.record(player,event);
        // GameTest creates legitimate server-side players without a packet listener.
        // Advancement dispatch ultimately sends a packet, so only connected players
        // may enter the vanilla criterion pipeline.
        if(player!=null&&player.connection!=null)PROGRESS.trigger(player,event);
    }
}
