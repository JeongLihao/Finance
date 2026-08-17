package finance.policy;

public record PolicyRateRecord(long mcDay, int basisPoints, String reason) {
    public PolicyRateRecord {
        reason = reason == null ? "" : reason.substring(0, Math.min(128, reason.length()));
    }
}
