package com.stockpulse.advisor;

import com.stockpulse.entity.SuggestionSource;

import java.util.List;

public record SuggestionGeneration(List<SuggestionDraft> drafts, SuggestionSource source) {
    public SuggestionGeneration {
        if (drafts == null || source == null) {
            throw new IllegalArgumentException("Suggestion generation result must be complete.");
        }
    }
}
