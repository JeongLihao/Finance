package finance.diagnostic;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DiagnosticReport(UUID reportId, Instant createdAt, long mcDay,
                               long durationNanos, List<DiagnosticIssue> issues) {
    public DiagnosticReport {
        if (reportId == null || createdAt == null || mcDay < -1 || durationNanos < 0) throw new IllegalArgumentException();
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
    public long count(DiagnosticSeverity severity) { return issues.stream().filter(i -> i.severity() == severity).count(); }
    public boolean healthy() { return count(DiagnosticSeverity.ERROR) == 0 && count(DiagnosticSeverity.FATAL) == 0; }
    public Map<DiagnosticSeverity, Long> counts() {
        Map<DiagnosticSeverity, Long> result = new EnumMap<>(DiagnosticSeverity.class);
        for (DiagnosticSeverity severity : DiagnosticSeverity.values()) result.put(severity, count(severity));
        return result;
    }
    public String summary() { return "报告 " + reportId + "：提示 " + count(DiagnosticSeverity.INFO)
            + "，警告 " + count(DiagnosticSeverity.WARN) + "，错误 " + count(DiagnosticSeverity.ERROR)
            + "，致命 " + count(DiagnosticSeverity.FATAL) + "，耗时 " + durationNanos / 1_000_000.0 + " 毫秒"; }
}
