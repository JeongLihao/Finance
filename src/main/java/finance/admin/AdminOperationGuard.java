package finance.admin;

import java.util.*;

/** Bounded two-step confirmation and audit trail for destructive console actions. */
public final class AdminOperationGuard {
    public static final long CONFIRM_WINDOW_TICKS=200;
    private static final int MAX_PENDING=128,MAX_AUDIT=256;
    private static final LinkedHashMap<String,Long>PENDING=new LinkedHashMap<>();
    private static final ArrayDeque<AuditEntry>AUDIT=new ArrayDeque<>();
    private AdminOperationGuard(){}
    public static synchronized boolean confirm(UUID player,String action,String subject,long tick){
        if(player==null||action==null||action.isBlank()||subject==null||subject.length()>128||tick<0)return false;
        String key=player+":"+action+":"+subject;Long previous=PENDING.remove(key);
        if(previous!=null&&tick>=previous&&tick-previous<=CONFIRM_WINDOW_TICKS)return true;
        while(PENDING.size()>=MAX_PENDING)PENDING.remove(PENDING.keySet().iterator().next());PENDING.put(key,tick);return false;
    }
    public static synchronized void audit(UUID player,String action,String subject,long tick,boolean success){AUDIT.addLast(new AuditEntry(player,action,subject,tick,success));while(AUDIT.size()>MAX_AUDIT)AUDIT.removeFirst();}
    public static synchronized List<AuditEntry> auditLog(){return List.copyOf(AUDIT);}
    public static synchronized void clear(){PENDING.clear();AUDIT.clear();}
    public record AuditEntry(UUID player,String action,String subject,long serverTick,boolean success){}
}
