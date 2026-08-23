package finance.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceTextureResourceTest {
    private static final List<String> BLOCK_TEXTURES = List.of(
            "bank_counter", "boardroom_table", "central_bank_console", "company_desk",
            "company_factory_controller", "indicator_full", "indicator_normal", "indicator_offline",
            "indicator_risk", "indicator_warning", "machine_casing", "machine_top",
            "market_terminal", "securities_terminal", "teal_casing", "warehouse_controller"
    );

    @Test
    void financeBlocksAndItemsHaveReadableSquareTextures() throws Exception {
        for (String texture : BLOCK_TEXTURES) {
            assertTexture("assets/finance/textures/block/" + texture + ".png");
        }
        assertTexture("assets/finance/textures/item/portable_ledger.png");
        assertTexture("assets/finance/textures/item/finance_guide.png");
    }

    @Test
    void everyFinanceModelReferenceResolves() throws Exception {
        for (String blockState : List.of(
                "bank_counter", "boardroom_table", "central_bank_console", "company_desk",
                "company_factory_controller", "market_terminal", "securities_terminal", "warehouse_controller")) {
            JsonObject root = readJson("assets/finance/blockstates/" + blockState + ".json");
            for (var entry : root.getAsJsonObject("variants").entrySet()) {
                assertModel(entry.getValue().getAsJsonObject().get("model").getAsString());
            }
        }
    }

    @Test
    void portableItemsUseFinanceTexturesInsteadOfVanillaBooks() throws Exception {
        for (String item : List.of("portable_ledger", "finance_guide")) {
            JsonObject model = readJson("assets/finance/models/item/" + item + ".json");
            assertEquals("finance:item/" + item,
                    model.getAsJsonObject("textures").get("layer0").getAsString());
        }
    }

    private void assertModel(String id) throws Exception {
        assertTrue(id.startsWith("finance:block/"), id);
        String name = id.substring("finance:block/".length());
        JsonObject model = readJson("assets/finance/models/block/" + name + ".json");
        if (model.has("parent") && model.get("parent").getAsString().startsWith("finance:block/")) {
            assertModel(model.get("parent").getAsString());
        }
        if (model.has("textures")) {
            for (var entry : model.getAsJsonObject("textures").entrySet()) {
                String textureId = entry.getValue().getAsString();
                if (textureId.startsWith("finance:block/")) {
                    String textureName = textureId.substring("finance:block/".length());
                    assertTexture("assets/finance/textures/block/" + textureName + ".png");
                }
            }
        }
    }

    private void assertTexture(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path);
            assertEquals(image.getWidth(), image.getHeight(), path);
            assertTrue(image.getWidth() >= 16, path);
            assertTrue(image.getWidth() <= 64, path);
        }
    }

    private JsonObject readJson(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
