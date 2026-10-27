package com.stockpulse.entity;

import com.stockpulse.entity.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suggestions")
public class Suggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SuggestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SuggestionStatus status;

    @Column(name = "proposed_price", precision = 12, scale = 2)
    private BigDecimal proposedPrice;

    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "recommended_quantity")
    private Integer recommendedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RecommendationDirection direction;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(nullable = false, length = 500)
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 30)
    private SuggestionTrigger trigger;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Suggestion() { }

    public Suggestion(Product product, SuggestionType type, BigDecimal currentPrice,
                      BigDecimal proposedPrice, Integer recommendedQuantity,
                      RecommendationDirection direction, BigDecimal confidence,
                      String rationale, SuggestionTrigger trigger) {
        if (product == null || type == null || trigger == null
                || rationale == null || rationale.isBlank() || rationale.length() > 500) {
            throw new IllegalArgumentException("Suggestion product, type, trigger, and rationale are required.");
        }
        if (currentPrice != null && currentPrice.signum() <= 0
                || proposedPrice != null && proposedPrice.signum() <= 0
                || recommendedQuantity != null && recommendedQuantity <= 0) {
            throw new IllegalArgumentException("Suggestion values must be positive.");
        }
        this.product = product;
        this.type = type;
        this.currentPrice = currentPrice;
        this.proposedPrice = proposedPrice;
        this.recommendedQuantity = recommendedQuantity;
        this.direction = direction;
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1.");
        }
        this.confidence = confidence;
        this.rationale = rationale;
        this.trigger = trigger;
        this.status = SuggestionStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void approve() {
        ensurePending();
        status = SuggestionStatus.ACCEPTED;
        resolvedAt = Instant.now();
    }

    public void reject() {
        ensurePending();
        status = SuggestionStatus.REJECTED;
        resolvedAt = Instant.now();
    }

    private void ensurePending() {
        if (status != SuggestionStatus.PENDING) {
            throw new IllegalStateException("Only pending suggestions can be resolved.");
        }
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public SuggestionType getType() { return type; }
    public SuggestionStatus getStatus() { return status; }
    public BigDecimal getProposedPrice() { return proposedPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public Integer getRecommendedQuantity() { return recommendedQuantity; }
    public RecommendationDirection getDirection() { return direction; }
    public BigDecimal getConfidence() { return confidence; }
    public String getRationale() { return rationale; }
    public SuggestionTrigger getTrigger() { return trigger; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
