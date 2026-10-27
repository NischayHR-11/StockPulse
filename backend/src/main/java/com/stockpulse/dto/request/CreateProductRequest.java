package com.stockpulse.dto.request;

import com.stockpulse.entity.ProductCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 80) String sku,
        @NotBlank @Size(max = 160) String name,
        @NotNull ProductCategory category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal currentPrice,
        @NotNull @Min(0) Integer stockLevel,
        @NotNull @Min(0) Integer reorderThreshold,
        @NotNull @Min(0) Integer demandVelocity
) { }
