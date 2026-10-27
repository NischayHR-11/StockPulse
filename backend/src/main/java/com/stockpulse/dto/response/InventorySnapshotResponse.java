package com.stockpulse.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InventorySnapshotResponse(
        UUID id,
        UUID productId,
        int stockLevel,
        String reason,
        String referenceId,
        Instant capturedAt
) { }
