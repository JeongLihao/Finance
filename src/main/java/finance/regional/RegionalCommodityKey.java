package finance.regional;

import java.util.UUID;

public record RegionalCommodityKey(String dimensionId, UUID regionId, String commodityId) {
    public RegionalCommodityKey {
        if (dimensionId == null || dimensionId.isBlank() || dimensionId.length() > 128
                || regionId == null || commodityId == null || commodityId.isBlank()
                || commodityId.length() > 64) throw new IllegalArgumentException("invalid regional commodity key");
    }
}
