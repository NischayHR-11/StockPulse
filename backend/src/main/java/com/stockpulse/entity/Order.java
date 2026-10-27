package com.stockpulse.entity;

import com.stockpulse.entity.Product;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_orders_idempotency_key", columnNames = "idempotency_key"))
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "remaining_stock", nullable = false)
    private int remainingStock;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Order() { }

    public Order(Product product, int quantity, String idempotencyKey, int remainingStock) {
        if (product == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Order product and idempotency key are required.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive.");
        }
        if (remainingStock < 0) {
            throw new IllegalArgumentException("Remaining stock cannot be negative.");
        }
        this.product = product;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.remainingStock = remainingStock;
        this.status = OrderStatus.PROCESSED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public int getRemainingStock() { return remainingStock; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
