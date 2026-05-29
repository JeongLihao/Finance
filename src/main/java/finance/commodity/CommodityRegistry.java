package finance.commodity;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CommodityRegistry {

    private static final Map<String, Commodity> COMMODITIES =
            new HashMap<>();

    public static void register(Commodity commodity) {

        COMMODITIES.put(
                commodity.getId(),
                commodity
        );
    }

    public static Commodity getCommodity(String id) {

        return COMMODITIES.get(id);
    }

    public static Collection<Commodity> getAllCommodities() {

        return COMMODITIES.values();
    }
}
