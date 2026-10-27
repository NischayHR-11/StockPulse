package com.stockpulse.entity;

import jakarta.persistence.*;
import com.stockpulse.exception.InsufficientStockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(name = "uk_products_sku", columnNames = "sku"))
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductCategory category;

    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "stock_level", nullable = false)
    private int stockLevel;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;

    @Column(name = "demand_velocity", nullable = false)
    private int demandVelocity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() { }

    public Product(String sku, String name, ProductCategory category, BigDecimal currentPrice,
                   int stockLevel, int reorderThreshold, int demandVelocity) {
        if (sku == null || sku.isBlank() || name == null || name.isBlank() || category == null) {
            throw new IllegalArgumentException("Product identity and category are required.");
        }
        if (currentPrice == null || currentPrice.signum() <= 0
                || stockLevel < 0 || reorderThreshold < 0 || demandVelocity < 0) {
            throw new IllegalArgumentException("Product price and inventory values are invalid.");
        }
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.currentPrice = currentPrice;
        this.stockLevel = stockLevel;
        this.reorderThreshold = reorderThreshold;
        this.demandVelocity = demandVelocity;
        this.status = stockLevel == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void updateStock(int newStockLevel) {
        if (newStockLevel < 0) {
            throw new IllegalArgumentException("Stock level cannot be negative.");
        }
        this.stockLevel = newStockLevel;
        if (newStockLevel == 0) this.status = ProductStatus.OUT_OF_STOCK;
        else if (this.status == ProductStatus.OUT_OF_STOCK) this.status = ProductStatus.ACTIVE;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive.");
        }
        if (quantity > stockLevel) {
            throw new InsufficientStockException("Insufficient stock for product: " + sku);
        }
        updateStock(stockLevel - quantity);
    }

    public void updatePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero.");
        }
        this.currentPrice = newPrice;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public ProductCategory getCategory() { return category; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public int getStockLevel() { return stockLevel; }
    public int getReorderThreshold() { return reorderThreshold; }
    public int getDemandVelocity() { return demandVelocity; }
    public ProductStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
