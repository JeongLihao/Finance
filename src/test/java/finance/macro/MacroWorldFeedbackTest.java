package finance.macro;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import finance.data.EconomySavedData;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 世界反馈：心情与抱怨的判定、状态翻转时的公告队列。粒子和音效需要真实世界，这里不测。 */
class MacroWorldFeedbackTest {
    private SettlementEconomy econ;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        SettlementRecord s = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        econ = MacroEconomyManager.getOrCreate(s.id());
        for (NeedCategory c : NeedCategory.values()) econ.lastFulfilment.put(c, 1.0);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    private static List<String> headlines() {
        return NewsService.pool().stream().map(NewsService.Item::headline).toList();
    }

    @Test void moodAndComplaintFollowTheWorstNeed() {
        econ.solBps = 5_000;
        assertEquals(MacroWorldFeedback.Mood.CALM, MacroWorldFeedback.mood(econ));
        assertNull(MacroWorldFeedback.complaintKey(econ), "过得一般时村民不说话");
        econ.lastFulfilment.put(NeedCategory.HEATING, 0.2);
        econ.lastFulfilment.put(NeedCategory.CLOTHING, 0.4);
        assertEquals("finance.macro.complaint.heating", MacroWorldFeedback.complaintKey(econ), "抱怨最缺的那一类");
        econ.lastFulfilment.put(NeedCategory.FOOD, 0.3);
        assertEquals(MacroWorldFeedback.Mood.ANGRY, MacroWorldFeedback.mood(econ), "挨饿就生气");
        econ.unrest = true;
        assertEquals("finance.macro.complaint.unrest", MacroWorldFeedback.complaintKey(econ));
        econ.unrest = false;
        for (NeedCategory c : NeedCategory.values()) econ.lastFulfilment.put(c, 1.0);
        econ.solBps = 8_000;
        assertEquals(MacroWorldFeedback.Mood.HAPPY, MacroWorldFeedback.mood(econ));
        assertEquals("finance.macro.complaint.happy", MacroWorldFeedback.complaintKey(econ));
    }

    @Test void firstDayAfterStartupOnlyRecordsTheBaseline() {
        econ.lastFulfilment.put(NeedCategory.FOOD, 0.2);
        MacroEconomyManager.setUnrestFlags(true, true);
        MacroWorldFeedback.onDayClosed();
        assertTrue(NewsService.pool().isEmpty(), "重启前就在的罢工和饥荒不再上报");
    }

    @Test void stateFlipsGoToTheNewsDeskOnce() {
        MacroWorldFeedback.onDayClosed();
        econ.lastFulfilment.put(NeedCategory.FOOD, 0.2);
        MacroEconomyManager.setUnrestFlags(true, false);
        MacroWorldFeedback.onDayClosed();
        assertEquals(List.of("alpha闹饥荒", "工人大罢工！"), headlines(), "按新闻价值排序");
        assertTrue(MacroWorldFeedback.queued().isEmpty(), "大事交给报社，不再直接刷聊天栏");
        MacroWorldFeedback.onDayClosed();
        assertEquals(2, NewsService.pool().size(), "状态没变就不重复上报");
        econ.lastFulfilment.put(NeedCategory.FOOD, 1.0);
        MacroEconomyManager.setUnrestFlags(false, false);
        MacroWorldFeedback.onDayClosed();
        assertTrue(headlines().containsAll(List.of("alpha饥荒结束", "罢工落幕，工人复工")));
    }

    @Test void lawResultsAreReported() {
        MacroWorldFeedback.onLawResolved(LawLevel.HIGH_TAX, true, 9_000, 4_000);
        NewsService.Item item = NewsService.pool().get(0);
        assertEquals("议会通过：税制改为「高税」", item.headline());
        assertTrue(item.body().contains("9000"));
    }

    @Test void queueIsBounded() {
        for (int i = 0; i < 200; i++) MacroWorldFeedback.announce("finance.macro.announce.strike_end", MacroWorldFeedback.Tone.GOOD);
        assertEquals(MacroWorldFeedback.MAX_QUEUE, MacroWorldFeedback.queued().size());
    }

    @Test void everyMessageKeyIsTranslated() throws Exception {
        JsonObject zh = json("assets/finance/lang/zh_cn.json");
        JsonObject en = json("assets/finance/lang/en_us.json");
        List<String> keys = new java.util.ArrayList<>(List.of("finance.macro.announce.brief", "finance.macro.announce.extra",
                "finance.macro.announce.weekly", "finance.macro.news.claimed", "finance.macro.news.waiting",
                "finance.macro.caravan_arrived", "finance.macro.factory_strike",
                "finance.macro.complaint.unrest", "finance.macro.complaint.happy"));
        for (NeedCategory c : NeedCategory.values()) keys.add("finance.macro.complaint." + c.name().toLowerCase(java.util.Locale.ROOT));
        for (String k : keys) {
            assertTrue(zh.has(k), k);
            assertTrue(en.has(k), k);
        }
    }

    private JsonObject json(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
