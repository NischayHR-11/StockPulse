package com.stockpulse.service;

import com.stockpulse.exception.ConflictException;
import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.event.InventoryChangedEvent;
import com.stockpulse.entity.InventorySnapshot;
import com.stockpulse.repository.InventorySnapshotRepository;
import com.stockpulse.dto.request.CreateOrderRequest;
import com.stockpulse.dto.response.OrderResponse;
import com.stockpulse.entity.Order;
import com.stockpulse.repository.OrderRepository;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventorySnapshotRepository snapshotRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private OrderService orderService;

    @Test
    void processDecreasesStockAndCreatesSnapshot() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 5);
        when(orderRepository.findByIdempotencyKey("order-1")).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any(InventorySnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.process(new CreateOrderRequest(productId, 2, " order-1 "));

        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.remainingStock()).isEqualTo(3);
        assertThat(product.getStockLevel()).isEqualTo(3);
        assertThat(product.getStatus()).isEqualTo(com.stockpulse.entity.ProductStatus.ACTIVE);

        ArgumentCaptor<InventorySnapshot> captor = ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getStockLevel()).isEqualTo(3);
        assertThat(captor.getValue().getReason()).isEqualTo("ORDER_PROCESSED");
        assertThat(captor.getValue().getReferenceId()).isEqualTo("order-1");
        verify(eventPublisher).publishEvent(new InventoryChangedEvent(productId));
    }

    @Test
    void processRejectsUnknownProduct() {
        UUID productId = UUID.randomUUID();
        when(orderRepository.findByIdempotencyKey("order-1")).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.process(new CreateOrderRequest(productId, 1, "order-1")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).save(any(Order.class));
        verify(snapshotRepository, never()).save(any(InventorySnapshot.class));
        verify(eventPublisher, never()).publishEvent(any(InventoryChangedEvent.class));
    }

    @Test
    void processRejectsInsufficientStockWithoutCreatingOrderOrSnapshot() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 1);
        when(orderRepository.findByIdempotencyKey("order-1")).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.process(new CreateOrderRequest(productId, 2, "order-1")))
                .isInstanceOf(ConflictException.class);
        assertThat(product.getStockLevel()).isEqualTo(1);
        verify(orderRepository, never()).save(any(Order.class));
        verify(snapshotRepository, never()).save(any(InventorySnapshot.class));
    }

    @Test
    void processRejectsNonPositiveQuantityBeforeLoadingProduct() {
        UUID productId = UUID.randomUUID();

        assertThatThrownBy(() -> orderService.process(new CreateOrderRequest(productId, 0, "order-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order quantity must be positive.");
        verify(productRepository, never()).findByIdForUpdate(any(UUID.class));
    }

    @Test
    void repeatedIdenticalRequestDoesNotDecreaseStockAgain() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 3);
        Order existing = new Order(product, 1, "order-1", 2);
        when(orderRepository.findByIdempotencyKey("order-1")).thenReturn(Optional.of(existing));

        OrderResponse response = orderService.process(new CreateOrderRequest(productId, 1, "order-1"));

        assertThat(response.quantity()).isEqualTo(1);
        assertThat(product.getStockLevel()).isEqualTo(3);
        verify(productRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(snapshotRepository, never()).save(any(InventorySnapshot.class));
    }

    @Test
    void processingAllRemainingStockMarksProductOutOfStock() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 2);
        when(orderRepository.findByIdempotencyKey("order-1")).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any(InventorySnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orderService.process(new CreateOrderRequest(productId, 2, "order-1"));

        assertThat(product.getStockLevel()).isZero();
        assertThat(product.getStatus()).isEqualTo(com.stockpulse.entity.ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void reusedKeyForDifferentRequestIsRejected() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 3);
        Order existing = new Order(product, 1, "order-1", 2);
        when(orderRepository.findByIdempotencyKey("order-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.process(new CreateOrderRequest(productId, 2, "order-1")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void replayReturnsOriginalRemainingStockAfterProductChanges() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 3);
        Order existing = new Order(product, 1, "order-1", 2);
        when(orderRepository.findByIdempotencyKey("order-1")).thenReturn(Optional.of(existing));

        product.updateStock(0);
        OrderResponse response = orderService.process(new CreateOrderRequest(productId, 1, "order-1"));

        assertThat(response.remainingStock()).isEqualTo(2);
    }

    @Test
    void rechecksIdempotencyAfterProductLockBeforeDecrementing() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 3);
        Order existing = new Order(product, 1, "order-1", 2);
        when(orderRepository.findByIdempotencyKey("order-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        OrderResponse response = orderService.process(new CreateOrderRequest(productId, 1, "order-1"));

        assertThat(response.remainingStock()).isEqualTo(2);
        assertThat(product.getStockLevel()).isEqualTo(3);
        verify(orderRepository, never()).save(any(Order.class));
        verify(snapshotRepository, never()).save(any(InventorySnapshot.class));
    }

    private static Product product(UUID id, int stock) {
        Product product = new Product("SKU-" + id, "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), stock, 2, 1);
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
            return product;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not prepare product test fixture.", exception);
        }
    }
}
