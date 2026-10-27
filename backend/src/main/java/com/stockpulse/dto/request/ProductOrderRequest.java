package com.stockpulse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductOrderRequest(
        @Min(1) int quantity,
        @NotBlank @Size(max = 100) String idempotencyKey
) { }
