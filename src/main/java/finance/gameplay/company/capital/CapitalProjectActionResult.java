package finance.gameplay.company.capital;

import java.util.UUID;

/** Result of a capital project action; the message is a translation key. */
public record CapitalProjectActionResult(boolean success, UUID projectId, String messageKey) {

    public static CapitalProjectActionResult ok(UUID projectId, String messageKey) {
        return new CapitalProjectActionResult(true, projectId, messageKey);
    }

    public static CapitalProjectActionResult fail(String messageKey) {
        return new CapitalProjectActionResult(false, null, messageKey);
    }
}
