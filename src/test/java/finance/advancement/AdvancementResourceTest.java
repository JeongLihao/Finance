package finance.advancement;

import com.google.gson.JsonParser;import org.junit.jupiter.api.Test;import java.io.*;import java.nio.charset.StandardCharsets;import static org.junit.jupiter.api.Assertions.*;
class AdvancementResourceTest {
 @Test void completeProgressionResourcesAreValidAndCentralConsoleHasNoRecipe()throws Exception{String[] ids={"first_coin","portable_finance","market_access","warehouse_deposit","first_contract","first_shipment","first_village_help","village_rebuild","village_logistics","field_survey","company_member","company_production","public_company","advanced_finance"};for(String id:ids){try(InputStream in=getClass().getClassLoader().getResourceAsStream("data/finance/advancements/"+id+".json")){assertNotNull(in,id);assertTrue(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).isJsonObject());}}assertNull(getClass().getClassLoader().getResource("data/finance/recipes/central_bank_console.json"));}
}
