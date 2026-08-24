package finance.exploration;

import java.util.*;

public final class ExplorationManager {
    public static final int MAX_ASSIGNMENTS=1024,MAX_COOLDOWNS=2048,MAX_TERMINAL_HISTORY=256;
    private static final Map<UUID,ExplorationAssignment> ASSIGNMENTS=new LinkedHashMap<>();
    private static final Map<UUID,Long> LAST_REQUEST_DAY=new LinkedHashMap<>();
    private ExplorationManager(){}
    public static Map<UUID,ExplorationAssignment> assignments(){return Map.copyOf(ASSIGNMENTS);}
    public static Map<UUID,Long> cooldowns(){return Map.copyOf(LAST_REQUEST_DAY);}
    public static ExplorationAssignment get(UUID id){return id==null?null:ASSIGNMENTS.get(id);}
    public static ExplorationAssignment activeFor(UUID player){return ASSIGNMENTS.values().stream().filter(a->a.status()==ExplorationStatus.ACTIVE&&a.playerId().equals(player)).findFirst().orElse(null);}
    public static long lastRequestDay(UUID player){return LAST_REQUEST_DAY.getOrDefault(player,-1L);}
    public static synchronized boolean add(ExplorationAssignment assignment){prune();if(assignment==null||ASSIGNMENTS.size()>=MAX_ASSIGNMENTS||ASSIGNMENTS.containsKey(assignment.id())||activeFor(assignment.playerId())!=null)return false;ASSIGNMENTS.put(assignment.id(),assignment);return true;}
    public static synchronized boolean restore(ExplorationAssignment assignment){if(assignment==null||ASSIGNMENTS.size()>=MAX_ASSIGNMENTS||ASSIGNMENTS.containsKey(assignment.id()))return false;ASSIGNMENTS.put(assignment.id(),assignment);return true;}
    public static synchronized void recordRequest(UUID player,long day){if(player==null||day<0)return;LAST_REQUEST_DAY.put(player,day);while(LAST_REQUEST_DAY.size()>MAX_COOLDOWNS)LAST_REQUEST_DAY.remove(LAST_REQUEST_DAY.keySet().iterator().next());}
    public static synchronized void restoreCooldown(UUID player,long day){recordRequest(player,day);}
    public static synchronized void prune(){List<ExplorationAssignment> terminal=ASSIGNMENTS.values().stream().filter(a->a.status().terminal()).toList();for(int i=0;i<Math.max(0,terminal.size()-MAX_TERMINAL_HISTORY);i++){ExplorationAssignment assignment=terminal.get(i);var account=finance.account.AccountManager.getAccounts().get(assignment.escrowId());if(account==null||(account.getBalance()==0&&account.getFrozenBalance()==0)){ASSIGNMENTS.remove(assignment.id());finance.account.AccountManager.getAccounts().remove(assignment.escrowId());}}}
    public static void clearDirect(){ASSIGNMENTS.clear();LAST_REQUEST_DAY.clear();}
}
