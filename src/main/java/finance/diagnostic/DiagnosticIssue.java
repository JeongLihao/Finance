package finance.diagnostic;

public record DiagnosticIssue(DiagnosticSeverity severity, String module, String code,
                              String subject, String message) {
    public DiagnosticIssue {
        if (severity == null || module == null || module.isBlank() || code == null || code.isBlank()
                || subject == null || message == null) throw new IllegalArgumentException();
        module = trim(module, 32); code = trim(code, 48); subject = trim(subject, 96); message = trim(message, 256);
    }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
