package finance.diagnostic;

import java.util.ArrayList;
import java.util.List;

/** Runs one module per server tick so world loading never performs the entire audit in one tick. */
public final class StartupSelfCheckService {
    private static final ModuleHealthRegistry.Module[] MODULES=ModuleHealthRegistry.Module.values();
    private static final List<DiagnosticReport> PARTS=new ArrayList<>();
    private static int next=-1;private static long day=-1,started;
    private StartupSelfCheckService(){}
    public static synchronized void schedule(long currentDay){PARTS.clear();next=0;day=Math.max(0,currentDay);started=System.nanoTime();}
    public static synchronized boolean tick(){if(next<0)return false;if(next<MODULES.length){PARTS.add(EconomyConsistencyService.runModule(MODULES[next++],day));return true;}DiagnosticReport report=DiagnosticManager.combine(day,PARTS,System.nanoTime()-started);DiagnosticManager.add(report);for(DiagnosticIssue issue:report.issues())if(issue.severity()==DiagnosticSeverity.FATAL){try{ModuleHealthRegistry.Module module=ModuleHealthRegistry.Module.valueOf(issue.module());ModuleHealthRegistry.restrict(module,ModuleRunState.PAUSED,issue.code()+": "+issue.message(),day);}catch(IllegalArgumentException ignored){}}next=-1;return true;}
    public static synchronized boolean pending(){return next>=0;}
    public static synchronized void clear(){PARTS.clear();next=-1;day=-1;}
}
