package com.stockpulse.advisor;

import com.stockpulse.entity.SuggestionType;
import com.stockpulse.entity.RecommendationDirection;
import com.stockpulse.entity.SuggestionTrigger;

import java.math.BigDecimal;

public record SuggestionDraft(
        SuggestionType type,
        java.math.BigDecimal currentPrice,
        BigDecimal proposedPrice,
        Integer recommendedQuantity,
        RecommendationDirection direction,
        BigDecimal confidence,
        String rationale,
        SuggestionTrigger trigger
) { }
