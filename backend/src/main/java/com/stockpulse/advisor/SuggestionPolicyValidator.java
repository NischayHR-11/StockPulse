package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.RecommendationDirection;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SuggestionPolicyValidator {
    private static final int MAX_PRICE_PRECISION = 12;
    private static final int MAX_PRICE_SCALE = 2;

    private final RuleBasedSuggestionStrategy ruleBasedStrategy;

    public SuggestionPolicyValidator(RuleBasedSuggestionStrategy ruleBasedStrategy) {
        this.ruleBasedStrategy = ruleBasedStrategy;
    }

    public void validate(Product product, BigDecimal categoryAverageDemand,
                          SuggestionTrigger trigger, List<SuggestionDraft> drafts) {
        if (product == null || categoryAverageDemand == null || trigger == null
                || drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("Suggestion policy inputs must be valid.");
        }
        List<SuggestionDraft> expected = ruleBasedStrategy.suggest(product, categoryAverageDemand, trigger);
        Map<SuggestionType, SuggestionDraft> expectedByType = byType(expected);
        Map<SuggestionType, SuggestionDraft> actualByType = byType(drafts);
        if (expectedByType.size() != drafts.size() || expectedByType.size() != actualByType.size()
                || !expectedByType.keySet().equals(actualByType.keySet())) {
            throw new IllegalArgumentException("Suggestions do not match the applicable business policy.");
        }
        for (SuggestionType type : expectedByType.keySet()) {
            validateDraft(product, trigger, expectedByType.get(type), actualByType.get(type));
        }
    }

    private static Map<SuggestionType, SuggestionDraft> byType(List<SuggestionDraft> drafts) {
        Map<SuggestionType, SuggestionDraft> result = new EnumMap<>(SuggestionType.class);
        for (SuggestionDraft draft : drafts) {
            if (draft == null || draft.type() == null || result.put(draft.type(), draft) != null) {
                throw new IllegalArgumentException("Suggestion types must be present and unique.");
            }
        }
        return result;
    }

    private static void validateDraft(Product product, SuggestionTrigger trigger,
                                      SuggestionDraft expected, SuggestionDraft actual) {
        if (actual.currentPrice() == null
                || actual.currentPrice().compareTo(product.getCurrentPrice()) != 0
                || actual.trigger() != trigger
                || actual.confidence() == null
                || actual.confidence().compareTo(BigDecimal.ZERO) < 0
                || actual.confidence().compareTo(BigDecimal.ONE) > 0
                || actual.rationale() == null || actual.rationale().isBlank()
                || actual.rationale().length() > 500) {
            throw new IllegalArgumentException("Suggestion metadata is invalid.");
        }
        validateMoney(actual.currentPrice());
        if (actual.type() == SuggestionType.PRICE_ADJUSTMENT) {
            if (actual.recommendedQuantity() != null || actual.proposedPrice() == null
                    || actual.direction() == null
                    || actual.proposedPrice().compareTo(expected.proposedPrice()) != 0
                    || actual.direction() != expected.direction()) {
                throw new IllegalArgumentException("Pricing suggestion does not match the business policy.");
            }
            validateMoney(actual.proposedPrice());
        } else if (actual.type() == SuggestionType.REORDER) {
            if (actual.proposedPrice() != null || actual.direction() != null
                    || actual.recommendedQuantity() == null
                    || !actual.recommendedQuantity().equals(expected.recommendedQuantity())) {
                throw new IllegalArgumentException("Reorder suggestion does not match the business policy.");
            }
        } else {
            throw new IllegalArgumentException("Suggestion type is not supported.");
        }
    }

    private static void validateMoney(BigDecimal value) {
        if (value.signum() <= 0 || value.scale() > MAX_PRICE_SCALE
                || value.precision() > MAX_PRICE_PRECISION) {
            throw new IllegalArgumentException("Suggestion price exceeds supported monetary precision.");
        }
    }
}
