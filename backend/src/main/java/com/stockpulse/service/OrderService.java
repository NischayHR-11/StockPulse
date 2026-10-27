package com.stockpulse.service;

import com.stockpulse.exception.ConflictException;
import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.entity.InventorySnapshot;
import com.stockpulse.repository.InventorySnapshotRepository;
import com.stockpulse.dto.request.CreateOrderRequest;
import com.stockpulse.dto.response.OrderResponse;
import com.stockpulse.entity.Order;
import com.stockpulse.repository.OrderRepository;
import com.stockpulse.entity.Product;
import com.stockpulse.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.stockpulse.event.InventoryChangedEvent;
import com.stockpulse.mapper.OrderMapper;

@Service
public class OrderService {
    private static final String SNAPSHOT_REASON = "ORDER_PROCESSED";

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(ProductRepository productRepository, OrderRepository orderRepository,
                        InventorySnapshotRepository snapshotRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse process(CreateOrderRequest request) {
        String idempotencyKey = request.idempotencyKey().trim();
        if (request.quantity() <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive.");
        }
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank.");
        }
        Order existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingOrder != null) {
            ensureSameRequest(existingOrder, request);
            return OrderMapper.toResponse(existingOrder);
        }

        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId()));
        existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingOrder != null) {
            ensureSameRequest(existingOrder, request);
            return OrderMapper.toResponse(existingOrder);
        }
        product.decreaseStock(request.quantity());

        Order order = orderRepository.save(new Order(product, request.quantity(), idempotencyKey,
                product.getStockLevel()));
        snapshotRepository.save(new InventorySnapshot(product, product.getStockLevel(),
                SNAPSHOT_REASON, idempotencyKey));
        eventPublisher.publishEvent(new InventoryChangedEvent(product.getId()));
        return OrderMapper.toResponse(order);
    }

    private static void ensureSameRequest(Order existingOrder, CreateOrderRequest request) {
        if (!existingOrder.getProduct().getId().equals(request.productId())
                || existingOrder.getQuantity() != request.quantity()) {
            throw new ConflictException("Idempotency key was already used for a different order.");
        }
    }

}
