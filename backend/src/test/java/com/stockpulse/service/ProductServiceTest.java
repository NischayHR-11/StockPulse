package com.stockpulse.service;

import com.stockpulse.exception.ConflictException;
import com.stockpulse.dto.request.CreateProductRequest;
import com.stockpulse.dto.response.ProductResponse;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductService productService;

    @Test
    void createRejectsDuplicateSkuIgnoringCaseAndWhitespace() {
        CreateProductRequest request = request(" SKU-001 ");
        when(productRepository.existsBySkuIgnoreCase("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A product with this SKU already exists.");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateStockTransitionsOutOfStockProductBackToActive() {
        UUID id = UUID.randomUUID();
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 0, 5, 1);
        when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(product));

        ProductResponse response = productService.updateStock(id, 4);

        assertThat(response.stockLevel()).isEqualTo(4);
        assertThat(response.status()).isEqualTo(com.stockpulse.entity.ProductStatus.ACTIVE);
    }

    @Test
    void updateStockSetsOutOfStockWhenStockReachesZero() {
        UUID id = UUID.randomUUID();
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 4, 5, 1);
        when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(product));

        ProductResponse response = productService.updateStock(id, 0);

        assertThat(response.stockLevel()).isZero();
        assertThat(response.status()).isEqualTo(com.stockpulse.entity.ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void findAllIncludesAggregatedRecentSalesWithoutChangingDemandVelocity() {
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 4, 5, 12);
        var aggregate = mock(OrderRepository.SalesAggregate.class);
        when(aggregate.getProductId()).thenReturn(product.getId());
        when(aggregate.getUnitsSold()).thenReturn(7L);
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(orderRepository.sumProcessedQuantitiesByProductIdsSince(any(), any()))
                .thenReturn(List.of(aggregate));

        ProductResponse response = productService.findAll(null, null).get(0);

        assertThat(response.demandVelocity()).isEqualTo(12);
        assertThat(response.unitsSoldLast24Hours()).isEqualTo(7);
    }

    private static CreateProductRequest request(String sku) {
        return new CreateProductRequest(sku, "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 4, 5, 1);
    }
}
