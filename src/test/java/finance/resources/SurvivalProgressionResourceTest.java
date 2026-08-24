package finance.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SurvivalProgressionResourceTest {
    private static final List<String> RECIPES = List.of("portable_ledger", "finance_guide",
            "market_terminal", "warehouse_controller", "company_desk", "company_factory_controller",
            "bank_counter", "securities_terminal", "boardroom_table", "sealed_cargo_crate",
            "settlement_trade_station", "survey_board");

    @Test
    void survivalRecipesParseUseBoundedVanillaMaterialsAndExcludeCentralConsole() throws Exception {
        for (String id : RECIPES) {
            JsonObject recipe = json("data/finance/recipes/" + id + ".json");
            assertTrue(recipe.get("type").getAsString().startsWith("minecraft:crafting_"), id);
            String serialized = recipe.toString();
            assertFalse(serialized.contains("diamond"), id + " uses diamond as a time wall");
            assertFalse(serialized.contains("netherite"), id + " uses netherite as a time wall");
        }
        assertNull(getClass().getClassLoader().getResource("data/finance/recipes/central_bank_console.json"));
        assertTrue(json("data/finance/recipes/market_terminal.json").toString().contains("minecraft:planks"));
        assertTrue(json("data/finance/recipes/warehouse_controller.json").toString().contains("minecraft:chest"));
        assertTrue(json("data/finance/recipes/securities_terminal.json").toString().contains("minecraft:obsidian"));
    }

    @Test
    void minecraftFirstAdvancementRouteAndRecipeRewardsResolve() throws Exception {
        List<String> route = List.of("first_coin", "portable_finance", "market_access", "warehouse_deposit",
                "public_company", "first_contract", "company_member", "company_production", "advanced_finance");
        for (int i = 0; i < route.size(); i++) {
            JsonObject advancement = json("data/finance/advancements/" + route.get(i) + ".json");
            if (i > 0) assertEquals("finance:" + route.get(i - 1), advancement.get("parent").getAsString());
            if (advancement.has("rewards") && advancement.getAsJsonObject("rewards").has("recipes")) {
                JsonArray rewards = advancement.getAsJsonObject("rewards").getAsJsonArray("recipes");
                rewards.forEach(value -> {
                    String recipe = value.getAsString();
                    assertTrue(recipe.startsWith("finance:"));
                    assertNotNull(getClass().getClassLoader().getResource("data/finance/recipes/"
                            + recipe.substring("finance:".length()) + ".json"), recipe);
                });
            }
        }
        assertTrue(json("data/finance/advancements/first_coin.json").toString()
                .contains("minecraft:inventory_changed"));
        assertTrue(json("data/finance/advancements/public_company.json").toString().contains("first_trade"));
        assertEquals("finance:first_contract",
                json("data/finance/advancements/first_shipment.json").get("parent").getAsString());
        assertEquals("finance:first_contract",
                json("data/finance/advancements/first_village_help.json").get("parent").getAsString());
        assertEquals("finance:first_village_help",
                json("data/finance/advancements/village_rebuild.json").get("parent").getAsString());
    }

    private JsonObject json(String path) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
