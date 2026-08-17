package finance.diagnostic;

import finance.data.EconomySavedData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DiagnosticManager {
    public static final int MAX_REPORTS = 20;
    private static final List<DiagnosticReport> REPORTS = new ArrayList<>();
    private DiagnosticManager() { }
    public static synchronized DiagnosticReport runFull(long day) { DiagnosticReport report=EconomyConsistencyService.run(day);add(report);return report; }
    public static synchronized void add(DiagnosticReport report){if(report==null)return;REPORTS.add(report);if(REPORTS.size()>MAX_REPORTS)REPORTS.subList(0,REPORTS.size()-MAX_REPORTS).clear();EconomySavedData.markDirty();}
    public static synchronized List<DiagnosticReport> reports(){return List.copyOf(REPORTS);}
    public static synchronized DiagnosticReport latest(){return REPORTS.isEmpty()?null:REPORTS.get(REPORTS.size()-1);}
    public static synchronized void restore(List<DiagnosticReport> reports){REPORTS.clear();if(reports!=null)for(DiagnosticReport report:reports)if(report!=null)addDirect(report);}
    public static synchronized void addDirect(DiagnosticReport report){if(report==null)return;REPORTS.add(report);if(REPORTS.size()>MAX_REPORTS)REPORTS.subList(0,REPORTS.size()-MAX_REPORTS).clear();}
    public static synchronized void clear(){REPORTS.clear();}
    public static DiagnosticReport combine(long day,List<DiagnosticReport> parts,long duration){List<DiagnosticIssue>issues=new ArrayList<>();if(parts!=null)for(DiagnosticReport p:parts)issues.addAll(p.issues());return new DiagnosticReport(UUID.randomUUID(),Instant.now(),Math.max(-1,day),Math.max(0,duration),issues);}
}
