package finance.bondmarket;

import java.time.LocalDateTime;
import java.util.UUID;

public record BondTrade(UUID buyerId, UUID sellerId, UUID bondId, long pricePerUnit,
                        long quantity, long mcDay, LocalDateTime timestamp) { }
