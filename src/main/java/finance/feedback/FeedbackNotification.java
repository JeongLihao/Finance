package finance.feedback;

import java.util.List;

public record FeedbackNotification(long worldDay, FeedbackSeverity severity, String translationKey,
                                   List<String> arguments) {
    public FeedbackNotification {
        if (worldDay < 0 || severity == null || translationKey == null || translationKey.isBlank()
                || translationKey.length() > 128) throw new IllegalArgumentException("invalid notification");
        arguments = arguments == null ? List.of() : arguments.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.length() > 96 ? value.substring(0, 96) : value)
                .limit(WorldEconomyEvent.MAX_ARGUMENTS).toList();
    }
}
