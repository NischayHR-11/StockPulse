package com.stockpulse.event;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryRecommendationTriggerListenerTest {
    @Test
    void createsLowInventoryAndDemandSpikeTriggersAfterInventoryChange() {
        ProductRepository products = Mockito.mock(ProductRepository.class);
        SuggestionService suggestions = Mockito.mock(SuggestionService.class);
        UUID id = UUID.randomUUID();
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 11);
        when(products.findById(id)).thenReturn(Optional.of(product));
        when(products.averageDemandVelocityByCategory(ProductCategory.HOME)).thenReturn(5.0);

        new InventoryRecommendationTriggerListener(products, suggestions)
                .handle(new InventoryChangedEvent(id));

        verify(suggestions).generate(id, SuggestionTrigger.LOW_INVENTORY);
        verify(suggestions).generate(id, SuggestionTrigger.DEMAND_SPIKE);
    }
}
