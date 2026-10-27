package com.stockpulse.mapper;

import com.stockpulse.dto.response.SuggestionResponse;
import com.stockpulse.entity.Suggestion;

public final class SuggestionMapper {
    private SuggestionMapper() { }

    public static SuggestionResponse toResponse(Suggestion suggestion) {
        return new SuggestionResponse(suggestion.getId(), suggestion.getProduct().getId(),
                suggestion.getType(), suggestion.getStatus(), suggestion.getCurrentPrice(),
                suggestion.getProposedPrice(), suggestion.getRecommendedQuantity(),
                suggestion.getDirection(), suggestion.getConfidence(), suggestion.getRationale(),
                suggestion.getTrigger(), suggestion.getCreatedAt(), suggestion.getResolvedAt());
    }
}
