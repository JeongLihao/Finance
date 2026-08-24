package finance.logistics;

import java.util.UUID;

public record TransportCargo(UUID shipmentId, String commodityId, int quantity) {
    public TransportCargo {
        if (shipmentId == null || commodityId == null || commodityId.isBlank()
                || commodityId.length() > 64 || quantity <= 0) {
            throw new IllegalArgumentException("Invalid transport cargo");
        }
    }
}
