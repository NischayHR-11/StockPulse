package com.stockpulse.dto.response;

import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.entity.SuggestionType;
import com.stockpulse.entity.RecommendationDirection;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SuggestionResponse(
        UUID id,
        UUID productId,
        SuggestionType type,
        SuggestionStatus status,
        BigDecimal currentPrice,
        BigDecimal proposedPrice,
        Integer recommendedQuantity,
        RecommendationDirection direction,
        BigDecimal confidence,
        String rationale,
        SuggestionTrigger trigger,
        SuggestionSource suggestionSource,
        Instant createdAt,
        Instant resolvedAt
) { }
