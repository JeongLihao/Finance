package finance.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FinanceTextureResourceTest {
    private static final List<String> BLOCK_TEXTURES = List.of(
            "indicator_full",
            "indicator_normal",
            "indicator_offline",
            "indicator_risk",
            "indicator_warning",
            "market_screen",
            "securities_screen",
            "keyboard",
            "gauge_panel",
            "factory_panel",
            "teller_grille",
            "desk_drawers",
            "ledger_cover",
            "ledger_paper",
            "awning_green",
            "awning_yellow",
            "awning_red",
            "notice_board",
            "warehouse_front",
            "factory_front",
            "factory_side",
            "factory_top");

    @Test
    void financeBlocksAndItemsHaveReadableSquareTextures() throws Exception {
        for (String texture : BLOCK_TEXTURES) {
            assertTexture("assets/finance/textures/block/" + texture + ".png");
        }
        assertTexture("assets/finance/textures/item/portable_ledger.png");
        assertTexture("assets/finance/textures/item/finance_guide.png");
        assertTexture("assets/finance/textures/item/sealed_cargo_crate.png");
    }

    @Test
    void everyFinanceModelReferenceResolves() throws Exception {
        for (String blockState : List.of(
                "bank_counter",
                "boardroom_table",
                "central_bank_console",
                "company_desk",
                "market_terminal",
                "securities_terminal",
                "warehouse_controller",
                "factory",
                "settlement_trade_station",
                "survey_board")) {
            JsonObject root = readJson("assets/finance/blockstates/" + blockState + ".json");
            for (var entry : root.getAsJsonObject("variants").entrySet()) {
                assertModel(entry.getValue().getAsJsonObject().get("model").getAsString());
            }
        }
    }

    @Test
    void portableItemsUseFinanceTexturesInsteadOfVanillaBooks() throws Exception {
        for (String item : List.of("portable_ledger", "finance_guide", "sealed_cargo_crate")) {
            JsonObject model = readJson("assets/finance/models/item/" + item + ".json");
            assertEquals(
                    "finance:item/" + item,
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
        assertFaceTexturesDefined(name);
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

    /** Every "#variable" an element face uses must be defined by the model or one of its finance parents. */
    private void assertFaceTexturesDefined(String name) throws Exception {
        java.util.Set<String> defined = new java.util.HashSet<>();
        com.google.gson.JsonArray elements = null;
        for (String current = name; current != null; ) {
            JsonObject model = readJson("assets/finance/models/block/" + current + ".json");
            if (model.has("textures")) {
                defined.addAll(model.getAsJsonObject("textures").keySet());
            }
            if (elements == null && model.has("elements")) {
                elements = model.getAsJsonArray("elements");
            }
            String parent = model.has("parent") ? model.get("parent").getAsString() : "";
            current = parent.startsWith("finance:block/") ? parent.substring("finance:block/".length()) : null;
        }
        assertNotNull(elements, name);
        for (var element : elements) {
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                String texture =
                        face.getValue().getAsJsonObject().get("texture").getAsString();
                assertTrue(defined.contains(texture.substring(1)), name + " " + face.getKey() + " " + texture);
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
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
