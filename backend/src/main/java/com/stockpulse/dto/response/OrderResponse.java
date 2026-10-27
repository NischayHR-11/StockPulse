package com.stockpulse.dto.response;

import com.stockpulse.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID productId,
        int quantity,
        OrderStatus status,
        String idempotencyKey,
        Instant createdAt,
        int remainingStock
) { }
