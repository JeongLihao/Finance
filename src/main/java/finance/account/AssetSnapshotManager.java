package finance.account;

import finance.data.EconomySavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AssetSnapshotManager {

    private static final Map<UUID, AssetSnapshot> SNAPSHOTS = new HashMap<>();

    private AssetSnapshotManager() {
    }

    public static long getTodayProfit(UUID playerId, long currentTotalAsset, long currentMcDay) {
        AssetSnapshot snapshot = SNAPSHOTS.get(playerId);
        if (snapshot == null || snapshot.mcDay() != currentMcDay) {
            SNAPSHOTS.put(playerId, new AssetSnapshot(currentMcDay, currentTotalAsset));
            EconomySavedData.markDirty();
            return 0;
        }
        return currentTotalAsset - snapshot.totalAsset();
    }

    public static Map<UUID, AssetSnapshot> getSnapshots() {
        return SNAPSHOTS;
    }

    public static void putSnapshotDirect(UUID playerId, AssetSnapshot snapshot) {
        if (playerId != null && snapshot != null) {
            SNAPSHOTS.put(playerId, snapshot);
        }
    }

    public static void clearSnapshotsDirect() {
        SNAPSHOTS.clear();
    }

    public record AssetSnapshot(long mcDay, long totalAsset) {
    }
}
