package com.stockpulse.dto;

import com.stockpulse.dto.request.CreateProductRequest;
import com.stockpulse.dto.request.UpdateStockRequest;
import com.stockpulse.entity.ProductCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {
    private static Validator validator;
    private static jakarta.validation.ValidatorFactory factory;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void rejectsMissingNumericProductFields() {
        CreateProductRequest request = new CreateProductRequest(
                "SKU-1", "Product", ProductCategory.HOME, new BigDecimal("1.00"),
                null, null, null);

        assertThat(validator.validate(request)).hasSize(3);
    }

    @Test
    void acceptsZeroStockButRejectsNegativeStock() {
        assertThat(validator.validate(new UpdateStockRequest(0))).isEmpty();
        assertThat(validator.validate(new UpdateStockRequest(-1))).isNotEmpty();
        assertThat(validator.validate(new UpdateStockRequest(null))).isNotEmpty();
    }
}
