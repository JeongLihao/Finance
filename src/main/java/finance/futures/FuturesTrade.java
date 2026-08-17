package finance.futures;

import java.time.LocalDateTime;
import java.util.UUID;

public record FuturesTrade(UUID buyerId, UUID sellerId, UUID contractId, long price, long quantity,
                           long mcDay, LocalDateTime timestamp) {
    public FuturesTrade { if(buyerId==null||sellerId==null||contractId==null||price<=0||quantity<=0||mcDay<0||timestamp==null)throw new IllegalArgumentException(); }
}
