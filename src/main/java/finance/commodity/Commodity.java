package finance.commodity;

public class Commodity {

    private final String id;

    private final String displayName;

    private final CommodityCategory category;

    private long basePrice;

    public Commodity(
            String id,
            String displayName,
            CommodityCategory category,
            long basePrice
    ) {

        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.basePrice = basePrice;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CommodityCategory getCategory() {
        return category;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(long basePrice) {
        this.basePrice = basePrice;
    }
}
