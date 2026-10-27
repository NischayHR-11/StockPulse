package com.stockpulse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateStockRequest(@NotNull @Min(0) Integer stockLevel) { }
