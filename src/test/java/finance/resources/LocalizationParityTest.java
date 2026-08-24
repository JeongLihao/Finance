package finance.resources;
import com.google.gson.*;import org.junit.jupiter.api.Test;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class LocalizationParityTest {
 @Test void englishAndChineseKeysMatchExactlyAndContainNoBlankValues()throws Exception{JsonObject en=json("assets/finance/lang/en_us.json"),zh=json("assets/finance/lang/zh_cn.json");assertEquals(en.keySet(),zh.keySet(),()->"Only EN="+difference(en.keySet(),zh.keySet())+" only ZH="+difference(zh.keySet(),en.keySet()));en.entrySet().forEach(e->assertFalse(e.getValue().getAsString().isBlank(),e.getKey()));zh.entrySet().forEach(e->assertFalse(e.getValue().getAsString().isBlank(),e.getKey()));}
 private static Set<String> difference(Set<String>a,Set<String>b){Set<String>copy=new TreeSet<>(a);copy.removeAll(b);return copy;}
 private JsonObject json(String path)throws Exception{try(InputStream in=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}}
}
