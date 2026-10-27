package com.stockpulse.dto.request;

import jakarta.validation.constraints.NotNull;

public record ResolveSuggestionRequest(@NotNull Resolution resolution) {
    public enum Resolution {
        APPROVE,
        REJECT
    }
}
