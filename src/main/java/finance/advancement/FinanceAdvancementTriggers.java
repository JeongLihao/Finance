package finance.advancement;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;

public final class FinanceAdvancementTriggers {
    public static final FinanceProgressTrigger PROGRESS=new FinanceProgressTrigger();
    private static boolean registered;
    private FinanceAdvancementTriggers(){}
    public static synchronized void register(){if(!registered){CriteriaTriggers.register(PROGRESS);registered=true;}}
    public static void trigger(ServerPlayer player,String event){PROGRESS.trigger(player,event);}
}
