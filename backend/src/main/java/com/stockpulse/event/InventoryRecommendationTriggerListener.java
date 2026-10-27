package com.stockpulse.event;

import com.stockpulse.entity.Product;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.service.SuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Component
public class InventoryRecommendationTriggerListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryRecommendationTriggerListener.class);

    private final ProductRepository productRepository;
    private final SuggestionService suggestionService;

    public InventoryRecommendationTriggerListener(ProductRepository productRepository,
                                                  SuggestionService suggestionService) {
        this.productRepository = productRepository;
        this.suggestionService = suggestionService;
    }

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(InventoryChangedEvent event) {
        try {
            Product product = productRepository.findById(event.productId()).orElse(null);
            if (product == null) {
                log.warn("Skipping recommendation trigger for missing product {}", event.productId());
                return;
            }
            if (product.getStockLevel() < product.getReorderThreshold()) {
                suggestionService.generate(product.getId(), SuggestionTrigger.LOW_INVENTORY);
            }
            Double average = productRepository.averageDemandVelocityByCategory(product.getCategory());
            if (average != null && average > 0
                    && BigDecimal.valueOf(product.getDemandVelocity())
                    .compareTo(BigDecimal.valueOf(average).multiply(BigDecimal.valueOf(2))) > 0) {
                suggestionService.generate(product.getId(), SuggestionTrigger.DEMAND_SPIKE);
            }
        } catch (RuntimeException exception) {
            log.error("Automatic recommendation trigger failed for product {}", event.productId(), exception);
        }
    }
}
