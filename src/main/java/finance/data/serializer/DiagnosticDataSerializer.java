package finance.data.serializer;

import finance.diagnostic.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DiagnosticDataSerializer {
    private DiagnosticDataSerializer() { }
    public static void save(CompoundTag root){CompoundTag state=new CompoundTag();ListTag reports=new ListTag();for(DiagnosticReport report:DiagnosticManager.reports()){CompoundTag r=new CompoundTag();r.putUUID("Id",report.reportId());r.putLong("At",report.createdAt().toEpochMilli());r.putLong("Day",report.mcDay());r.putLong("Duration",report.durationNanos());ListTag issues=new ListTag();for(DiagnosticIssue issue:report.issues()){CompoundTag i=new CompoundTag();i.putString("Severity",issue.severity().name());i.putString("Module",issue.module());i.putString("Code",issue.code());i.putString("Subject",issue.subject());i.putString("Message",issue.message());issues.add(i);}r.put("Issues",issues);reports.add(r);}state.put("Reports",reports);ListTag modules=new ListTag();ModuleHealthRegistry.statuses().forEach((module,status)->{CompoundTag m=new CompoundTag();m.putString("Module",module.name());m.putString("State",status.state().name());m.putString("Reason",status.reason());m.putLong("Since",status.sinceDay());modules.add(m);});state.put("Modules",modules);root.put("Diagnostics",state);}
    public static void load(CompoundTag root){DiagnosticManager.clear();ModuleHealthRegistry.clear();if(!root.contains("Diagnostics",Tag.TAG_COMPOUND))return;CompoundTag state=root.getCompound("Diagnostics");List<DiagnosticReport>reports=new ArrayList<>();for(Tag raw:state.getList("Reports",Tag.TAG_COMPOUND)){if(reports.size()>=DiagnosticManager.MAX_REPORTS)break;CompoundTag r=(CompoundTag)raw;try{UUID id=r.hasUUID("Id")?r.getUUID("Id"):null;long at=r.getLong("At"),day=r.getLong("Day"),duration=r.getLong("Duration");List<DiagnosticIssue>issues=new ArrayList<>();for(Tag ir:r.getList("Issues",Tag.TAG_COMPOUND)){if(issues.size()>=EconomyConsistencyService.MAX_ISSUES)break;CompoundTag i=(CompoundTag)ir;try{issues.add(new DiagnosticIssue(DiagnosticSeverity.valueOf(i.getString("Severity")),i.getString("Module"),i.getString("Code"),i.getString("Subject"),i.getString("Message")));}catch(IllegalArgumentException ignored){}}reports.add(new DiagnosticReport(id,Instant.ofEpochMilli(at),day,duration,issues));}catch(RuntimeException ignored){}}DiagnosticManager.restore(reports);for(Tag raw:state.getList("Modules",Tag.TAG_COMPOUND)){CompoundTag m=(CompoundTag)raw;try{ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.valueOf(m.getString("Module")),ModuleRunState.valueOf(m.getString("State")),m.getString("Reason"),m.getLong("Since"));}catch(IllegalArgumentException ignored){}}}
}
