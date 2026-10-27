package com.stockpulse.entity;

import com.stockpulse.entity.Product;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_snapshots")
public class InventorySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "stock_level", nullable = false)
    private int stockLevel;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    protected InventorySnapshot() { }

    public InventorySnapshot(Product product, int stockLevel, String reason, String referenceId) {
        if (product == null || reason == null || reason.isBlank() || reason.length() > 40
                || referenceId == null || referenceId.isBlank() || referenceId.length() > 100) {
            throw new IllegalArgumentException("Snapshot product, reason, and reference are required.");
        }
        if (stockLevel < 0) {
            throw new IllegalArgumentException("Snapshot stock level cannot be negative.");
        }
        this.product = product;
        this.stockLevel = stockLevel;
        this.reason = reason;
        this.referenceId = referenceId;
    }

    @PrePersist
    void onCreate() {
        capturedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public int getStockLevel() { return stockLevel; }
    public String getReason() { return reason; }
    public String getReferenceId() { return referenceId; }
    public Instant getCapturedAt() { return capturedAt; }
}
