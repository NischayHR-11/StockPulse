package com.stockpulse.mapper;

import com.stockpulse.dto.response.ProductResponse;
import com.stockpulse.entity.Product;

public final class ProductMapper {
    private ProductMapper() { }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getCategory(),
                product.getCurrentPrice(), product.getStockLevel(), product.getReorderThreshold(),
                product.getDemandVelocity(), product.getStatus(), product.getCreatedAt(), product.getUpdatedAt());
    }
}
