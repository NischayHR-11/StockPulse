package com.stockpulse.controller;

import com.stockpulse.dto.request.ResolveSuggestionRequest;
import com.stockpulse.dto.response.SuggestionResponse;
import com.stockpulse.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionType;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class SuggestionController {
    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping("/products/{productId}/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SuggestionResponse> generate(@PathVariable UUID productId,
                                              @RequestParam(defaultValue = "MANUAL") SuggestionTrigger trigger) {
        return suggestionService.generate(productId, trigger);
    }

    @PostMapping("/products/{productId}/suggest-pricing")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SuggestionResponse> generatePricing(@PathVariable UUID productId) {
        return suggestionService.generate(productId, SuggestionTrigger.MANUAL).stream()
                .filter(suggestion -> suggestion.type() == SuggestionType.PRICE_ADJUSTMENT)
                .collect(Collectors.toList());
    }

    @PostMapping("/products/{productId}/suggest-reorder")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SuggestionResponse> generateReorder(@PathVariable UUID productId) {
        return suggestionService.generate(productId, SuggestionTrigger.MANUAL).stream()
                .filter(suggestion -> suggestion.type() == SuggestionType.REORDER)
                .collect(Collectors.toList());
    }

    @GetMapping("/products/{productId}/suggestions")
    public List<SuggestionResponse> findByProduct(@PathVariable UUID productId) {
        return suggestionService.findByProduct(productId);
    }

    @PatchMapping({"/suggestions/{suggestionId}", "/pricing-suggestions/{suggestionId}",
            "/reorder-suggestions/{suggestionId}"})
    public SuggestionResponse resolve(@PathVariable UUID suggestionId,
                                      @Valid @RequestBody ResolveSuggestionRequest request) {
        return suggestionService.resolve(suggestionId, request);
    }
}
