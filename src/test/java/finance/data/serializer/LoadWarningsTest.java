package finance.data.serializer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 读档跳过坏记录的统计：按类别计数，取出后清空。 */
class LoadWarningsTest {

    @Test
    void countsPerKindAndClearsOnTake() {
        LoadWarnings.takeSummary();
        for (int i = 0; i < LoadWarnings.DETAILED_PER_KIND + 3; i++)
            LoadWarnings.skipped("warehouse", new IllegalArgumentException("bad " + i));
        LoadWarnings.skipped("contract", new RuntimeException("bad"));
        assertEquals(
                Map.of("warehouse", LoadWarnings.DETAILED_PER_KIND + 3, "contract", 1), LoadWarnings.takeSummary());
        assertTrue(LoadWarnings.takeSummary().isEmpty(), "取出后清空，下次读档重新计数");
    }
}
