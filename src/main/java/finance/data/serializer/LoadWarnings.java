package finance.data.serializer;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;

/**
 * 读档时跳过的坏记录：每类前 {@value #DETAILED_PER_KIND} 条写一行详细警告，读完后由
 * {@code EconomySavedData.load} 汇总一行“各类跳过了几条”。坏记录照旧跳过，不影响其余数据，
 * 只是让管理员能在日志里看到少了什么。
 */
public final class LoadWarnings {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int DETAILED_PER_KIND = 5;
    private static final Map<String, Integer> SKIPPED = new LinkedHashMap<>();

    private LoadWarnings() {}

    /** 一条 kind 类记录读不出来、已跳过。 */
    public static synchronized void skipped(String kind, Exception cause) {
        int count = SKIPPED.merge(kind, 1, Integer::sum);
        if (count <= DETAILED_PER_KIND)
            LOGGER.warn("Skipped a damaged {} record while loading the economy save: {}", kind, String.valueOf(cause));
    }

    /** 取出并清空本次读档的跳过统计（类别 → 条数）。 */
    public static synchronized Map<String, Integer> takeSummary() {
        Map<String, Integer> out = new LinkedHashMap<>(SKIPPED);
        SKIPPED.clear();
        return out;
    }
}
