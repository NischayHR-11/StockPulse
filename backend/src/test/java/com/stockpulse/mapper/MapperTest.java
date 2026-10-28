package com.stockpulse.mapper;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {
    @Test
    void mapsProductFieldsWithoutExposingTheEntity() {
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("12.34"), 4, 5, 2);

        var response = ProductMapper.toResponse(product);

        assertThat(response.sku()).isEqualTo("SKU-1");
        assertThat(response.currentPrice()).isEqualByComparingTo("12.34");
        assertThat(response.stockLevel()).isEqualTo(4);
        assertThat(response.unitsSoldLast24Hours()).isZero();
        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.category()).isEqualTo(ProductCategory.HOME);
    }
}
