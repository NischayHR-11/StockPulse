package com.stockpulse.dto.response;

import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        ProductCategory category,
        BigDecimal currentPrice,
        int stockLevel,
        int reorderThreshold,
        int demandVelocity,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) { }
