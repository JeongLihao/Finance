package finance.gameplay.company;

public record CompanyGameplayActionResult(boolean success, String messageKey) {
    public static CompanyGameplayActionResult ok(String key) { return new CompanyGameplayActionResult(true, key); }
    public static CompanyGameplayActionResult fail(String key) { return new CompanyGameplayActionResult(false, key); }
}
