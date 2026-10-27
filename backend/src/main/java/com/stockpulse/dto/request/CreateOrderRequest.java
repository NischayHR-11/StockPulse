package com.stockpulse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID productId,
        @Min(1) int quantity,
        @NotBlank @jakarta.validation.constraints.Size(max = 100) String idempotencyKey
) { }
