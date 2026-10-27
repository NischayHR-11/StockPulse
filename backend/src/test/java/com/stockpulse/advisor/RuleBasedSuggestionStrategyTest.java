package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.SuggestionType;
import com.stockpulse.entity.SuggestionTrigger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedSuggestionStrategyTest {
    private final RuleBasedSuggestionStrategy strategy = new RuleBasedSuggestionStrategy();

    @Test
    void lowStockWithDemandProducesReorderAndPriceSuggestions() {
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 4);

        var drafts = strategy.suggest(product, new BigDecimal("1.5"), SuggestionTrigger.LOW_INVENTORY);

        assertThat(drafts).extracting(SuggestionDraft::type)
                .containsExactly(SuggestionType.PRICE_ADJUSTMENT, SuggestionType.REORDER);
        assertThat(drafts.get(0).proposedPrice()).isEqualByComparingTo("11.00");
        assertThat(drafts.get(1).recommendedQuantity()).isEqualTo(13);
    }

    @Test
    void healthyStockProducesNoSuggestions() {
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 20, 5, 4);

        var drafts = strategy.suggest(product, new BigDecimal("3"), SuggestionTrigger.INITIAL);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).direction()).isEqualTo(
                com.stockpulse.entity.RecommendationDirection.HOLD);
        assertThat(drafts.get(0).proposedPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void demandSpikeProducesFivePercentIncreaseWhenStockIsNotLow() {
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 11);

        var drafts = strategy.suggest(product, new BigDecimal("5"), SuggestionTrigger.DEMAND_SPIKE);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).proposedPrice()).isEqualByComparingTo("10.50");
        assertThat(drafts.get(0).direction())
                .isEqualTo(com.stockpulse.entity.RecommendationDirection.INCREASE);
    }

    @Test
    void lowStockHasPriorityOverDemandSpike() {
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 11);

        var drafts = strategy.suggest(product, new BigDecimal("5"), SuggestionTrigger.LOW_INVENTORY);

        assertThat(drafts.get(0).proposedPrice()).isEqualByComparingTo("11.00");
    }

    @Test
    void zeroCategoryAverageDoesNotCreateDemandSpike() {
        Product product = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 100);

        var drafts = strategy.suggest(product, BigDecimal.ZERO, SuggestionTrigger.INITIAL);

        assertThat(drafts.get(0).direction())
                .isEqualTo(com.stockpulse.entity.RecommendationDirection.HOLD);
    }

    @Test
    void thresholdBoundariesUseStrictComparisons() {
        Product atStockThreshold = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("9.995"), 5, 5, 10);
        Product atDemandThreshold = new Product("SKU-002", "Test product", ProductCategory.HOME,
                new BigDecimal("9.995"), 10, 5, 10);

        var stockBoundary = strategy.suggest(atStockThreshold, new BigDecimal("5"), SuggestionTrigger.MANUAL);
        var demandBoundary = strategy.suggest(atDemandThreshold, new BigDecimal("5"), SuggestionTrigger.MANUAL);

        assertThat(stockBoundary.get(0).direction())
                .isEqualTo(com.stockpulse.entity.RecommendationDirection.HOLD);
        assertThat(demandBoundary.get(0).direction())
                .isEqualTo(com.stockpulse.entity.RecommendationDirection.HOLD);
        assertThat(strategy.suggest(
                new Product("SKU-003", "Test product", ProductCategory.HOME,
                        new BigDecimal("9.995"), 10, 5, 11),
                new BigDecimal("5"), SuggestionTrigger.MANUAL).get(0).proposedPrice())
                .isEqualByComparingTo("10.49");
    }

    @Test
    void rejectsInvalidInputsAndProtectsReorderOverflow() {
        Product invalidProduct = new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 1, 5, 1);

        assertThatThrownBy(() -> strategy.suggest(invalidProduct, new BigDecimal("-1"),
                SuggestionTrigger.MANUAL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> strategy.suggest(invalidProduct, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);

        Product maximumThreshold = new Product("SKU-002", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 0, Integer.MAX_VALUE, 1);
        assertThatThrownBy(() -> strategy.suggest(maximumThreshold, BigDecimal.ONE,
                SuggestionTrigger.LOW_INVENTORY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }
}
