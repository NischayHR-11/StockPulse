package com.stockpulse.service;

import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.entity.InventorySnapshot;
import com.stockpulse.repository.InventorySnapshotRepository;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventorySnapshotServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventorySnapshotRepository snapshotRepository;
    @InjectMocks
    private InventorySnapshotService snapshotService;

    @Test
    void findByProductReturnsCapturedSnapshots() {
        UUID productId = UUID.randomUUID();
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 4, 2, 1);
        when(productRepository.existsById(productId)).thenReturn(true);
        when(snapshotRepository.findAllByProductIdOrderByCapturedAtDesc(productId))
                .thenReturn(List.of(new InventorySnapshot(product, 4, "ORDER_PROCESSED", "order-1")));

        var responses = snapshotService.findByProduct(productId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).stockLevel()).isEqualTo(4);
        assertThat(responses.get(0).reason()).isEqualTo("ORDER_PROCESSED");
    }

    @Test
    void findByProductRejectsUnknownProduct() {
        UUID productId = UUID.randomUUID();
        when(productRepository.existsById(productId)).thenReturn(false);

        assertThatThrownBy(() -> snapshotService.findByProduct(productId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
