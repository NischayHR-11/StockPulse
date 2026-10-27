package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuggestionPolicyValidatorTest {
    private final RuleBasedSuggestionStrategy rules = new RuleBasedSuggestionStrategy();
    private final SuggestionPolicyValidator validator = new SuggestionPolicyValidator(rules);

    @Test
    void acceptsDeterministicDrafts() {
        Product product = product();
        var drafts = rules.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY);

        validator.validate(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY, drafts);
    }

    @Test
    void rejectsMismatchedPriceAndDirection() {
        Product product = product();
        SuggestionDraft invalid = new SuggestionDraft(
                SuggestionType.PRICE_ADJUSTMENT, product.getCurrentPrice(), new BigDecimal("12.00"), null,
                com.stockpulse.entity.RecommendationDirection.HOLD, new BigDecimal("0.9"),
                "Untrusted provider output.", SuggestionTrigger.LOW_INVENTORY);

        assertThatThrownBy(() -> validator.validate(product, BigDecimal.ONE,
                SuggestionTrigger.LOW_INVENTORY, List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsContradictoryFieldsAndDatabaseIncompatibleMoney() {
        Product product = product();
        SuggestionDraft invalid = new SuggestionDraft(
                SuggestionType.REORDER, product.getCurrentPrice(), new BigDecimal("11.00"), 8,
                null, new BigDecimal("0.9"), "Contradictory fields.", SuggestionTrigger.LOW_INVENTORY);

        assertThatThrownBy(() -> validator.validate(product, BigDecimal.ONE,
                SuggestionTrigger.LOW_INVENTORY, List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Product product() {
        return new Product("SKU-001", "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 1);
    }
}
