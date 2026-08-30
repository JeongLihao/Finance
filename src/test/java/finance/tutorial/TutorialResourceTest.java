package finance.tutorial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TutorialResourceTest {
    private static final String[] SCENES = {"getting_started", "warehouse_basics", "market_trading",
            "contract_delivery", "company_production", "logistics_delivery", "settlement_help",
            "field_survey", "advanced_finance", "regional_trade_flow", "inventory_collateral",
            "company_hedge", "insurance_evidence"};

    @Test
    void allPonderScenesHaveEnglishAndChineseHeaders() throws Exception {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");
        for (String scene : SCENES) {
            String key = "finance.ponder." + scene + ".header";
            assertTrue(english.has(key), key);
            assertTrue(chinese.has(key), key);
        }
    }

    @Test
    void ponderIsOptionalClientOnlyAndIntegrationIsReflectivelyIsolated() throws Exception {
        String metadata = resource("META-INF/mods.toml");
        assertTrue(metadata.contains("modId=\"ponder\""));
        assertTrue(metadata.contains("mandatory=false"));
        assertTrue(metadata.contains("side=\"CLIENT\""));
        String setup = Files.readString(Path.of("src/main/java/finance/client/ClientSetup.java"));
        assertTrue(setup.contains("Class.forName(\"finance.compat.ponder.FinancePonderBootstrap\")"));
        assertFalse(setup.contains("import net.createmod"));
    }

    @Test
    void pluginRegistersExactlyThirteenStoryboards() throws Exception {
        String source = Files.readString(Path.of("src/main/java/finance/compat/ponder/FinancePonderPlugin.java"));
        assertEquals(13, source.split("helper\\.addStoryBoard", -1).length - 1);
    }

    @Test
    void storyboardsUseMotionAndItemFlowInsteadOfNarratedSlides() throws Exception {
        String source = Files.readString(Path.of("src/main/java/finance/compat/ponder/FinancePonderScenes.java"));
        assertTrue(source.contains("createItemEntity"));
        assertTrue(source.contains("moveSection"));
        assertTrue(source.contains("rotateCameraY"));
        assertTrue(source.split("flow\\(scene", -1).length - 1 >= 20);
        assertFalse(source.contains("Finance starts with places in the world"));
        assertFalse(source.contains("The bank reserves real company custody"));
    }

    private JsonObject language(String code) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("assets/finance/lang/" + code + ".json")) {
            assertNotNull(input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private String resource(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
