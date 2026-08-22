package finance.feedback;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record WorldEconomyEvent(String eventKey, WorldFeedbackType type, FeedbackSeverity severity,
                                String subjectId, long worldDay, String dimensionId, BlockPos position,
                                String translationKey, List<String> arguments,
                                FeedbackAudience audience, Set<UUID> participants) {
    public static final int MAX_ARGUMENTS = 6, MAX_PARTICIPANTS = 128;
    public WorldEconomyEvent {
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > 96 || type == null || severity == null
                || worldDay < 0 || translationKey == null || translationKey.isBlank()
                || translationKey.length() > 128 || audience == null) throw new IllegalArgumentException("invalid world economy event");
        subjectId = limit(subjectId, 96); dimensionId = limit(dimensionId, 128);
        arguments = arguments == null ? List.of() : arguments.stream().filter(java.util.Objects::nonNull)
                .map(value -> limit(value, 96)).limit(MAX_ARGUMENTS).toList();
        participants = participants == null ? Set.of() : participants.stream().filter(java.util.Objects::nonNull)
                .limit(MAX_PARTICIPANTS).collect(java.util.stream.Collectors.toUnmodifiableSet());
        position = position == null ? null : position.immutable();
    }
    public String cooldownKey() { return type + ":" + eventKey + ":" + subjectId; }
    private static String limit(String value, int max) { String safe=value==null?"":value; return safe.length()>max?safe.substring(0,max):safe; }
}
