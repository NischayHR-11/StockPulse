package com.stockpulse.service;

import com.stockpulse.advisor.SuggestionDraft;
import com.stockpulse.advisor.SuggestionStrategy;
import com.stockpulse.advisor.SuggestionPolicyValidator;

import com.stockpulse.exception.ConflictException;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.dto.request.ResolveSuggestionRequest;
import com.stockpulse.entity.Suggestion;
import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.entity.SuggestionType;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionSource;
import com.stockpulse.repository.SuggestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private SuggestionRepository suggestionRepository;
    @Mock
    private SuggestionStrategy suggestionStrategy;
    @Mock
    private SuggestionPolicyValidator policyValidator;
    @InjectMocks
    private SuggestionService suggestionService;

    @Test
    void generateDoesNotCreateDuplicatePendingSuggestion() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId);
        SuggestionDraft draft = new SuggestionDraft(SuggestionType.REORDER, new BigDecimal("10.00"),
                null, 8, null, new BigDecimal("0.95"), "Low stock.", SuggestionTrigger.LOW_INVENTORY);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(productRepository.averageDemandVelocityByCategory(product.getCategory())).thenReturn(1.5);
        when(suggestionStrategy.generate(product, new BigDecimal("1.5"), SuggestionTrigger.LOW_INVENTORY))
                .thenReturn(new com.stockpulse.advisor.SuggestionGeneration(
                        java.util.List.of(draft), SuggestionSource.RULE_BASED));
        when(suggestionRepository.findByProductIdAndTypeAndTriggerAndStatus(
                productId, SuggestionType.REORDER, SuggestionTrigger.LOW_INVENTORY, SuggestionStatus.PENDING))
                .thenReturn(Optional.of(new Suggestion(product, SuggestionType.REORDER,
                        new BigDecimal("10.00"), null, 8, null, new BigDecimal("0.95"),
                        "Existing.", SuggestionTrigger.LOW_INVENTORY)));

        assertThat(suggestionService.generate(productId, SuggestionTrigger.LOW_INVENTORY)).isEmpty();
        verify(suggestionRepository, never()).save(any(Suggestion.class));
    }

    @Test
    void generatePersistsNewPendingSuggestion() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId);
        SuggestionDraft draft = new SuggestionDraft(SuggestionType.REORDER, new BigDecimal("10.00"),
                null, 8, null, new BigDecimal("0.95"), "Low stock.", SuggestionTrigger.LOW_INVENTORY);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(productRepository.averageDemandVelocityByCategory(product.getCategory())).thenReturn(1.5);
        when(suggestionStrategy.generate(product, new BigDecimal("1.5"), SuggestionTrigger.LOW_INVENTORY))
                .thenReturn(new com.stockpulse.advisor.SuggestionGeneration(
                        java.util.List.of(draft), SuggestionSource.GEMINI));
        when(suggestionRepository.findByProductIdAndTypeAndTriggerAndStatus(
                productId, SuggestionType.REORDER, SuggestionTrigger.LOW_INVENTORY, SuggestionStatus.PENDING))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var responses = suggestionService.generate(productId, SuggestionTrigger.LOW_INVENTORY);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(responses.get(0).recommendedQuantity()).isEqualTo(8);
        assertThat(responses.get(0).suggestionSource()).isEqualTo(SuggestionSource.GEMINI);
    }

    @Test
    void approvingPriceSuggestionUpdatesProductPrice() {
        UUID suggestionId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        Suggestion suggestion = new Suggestion(product, SuggestionType.PRICE_ADJUSTMENT,
                new BigDecimal("10.00"), new BigDecimal("11.00"), null,
                com.stockpulse.entity.RecommendationDirection.INCREASE,
                new BigDecimal("0.95"), "Low stock.", SuggestionTrigger.LOW_INVENTORY);
        when(suggestionRepository.findByIdForUpdate(suggestionId)).thenReturn(Optional.of(suggestion));
        when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));

        var response = suggestionService.resolve(suggestionId,
                new ResolveSuggestionRequest(ResolveSuggestionRequest.Resolution.APPROVE));

        assertThat(response.status()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(product.getCurrentPrice()).isEqualByComparingTo("11.00");
    }

    @Test
    void resolvingAlreadyResolvedSuggestionIsRejected() {
        UUID suggestionId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        Suggestion suggestion = new Suggestion(product, SuggestionType.REORDER,
                new BigDecimal("10.00"), null, 5, null, new BigDecimal("0.95"),
                "Low stock.", SuggestionTrigger.LOW_INVENTORY);
        suggestion.reject();
        when(suggestionRepository.findByIdForUpdate(suggestionId)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> suggestionService.resolve(suggestionId,
                new ResolveSuggestionRequest(ResolveSuggestionRequest.Resolution.APPROVE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approvingReorderSuggestionIncreasesStock() {
        UUID suggestionId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        Suggestion suggestion = new Suggestion(product, SuggestionType.REORDER,
                new BigDecimal("10.00"), null, 8, null, new BigDecimal("0.95"),
                "Low stock.", SuggestionTrigger.LOW_INVENTORY);
        when(suggestionRepository.findByIdForUpdate(suggestionId)).thenReturn(Optional.of(suggestion));
        when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));

        var response = suggestionService.resolve(suggestionId,
                new ResolveSuggestionRequest(ResolveSuggestionRequest.Resolution.APPROVE));

        assertThat(response.status()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(product.getStockLevel()).isEqualTo(10);
    }

    @Test
    void rejectingSuggestionDoesNotMutateProduct() {
        UUID suggestionId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        Suggestion suggestion = new Suggestion(product, SuggestionType.PRICE_ADJUSTMENT,
                new BigDecimal("10.00"), new BigDecimal("11.00"), null,
                com.stockpulse.entity.RecommendationDirection.INCREASE,
                new BigDecimal("0.95"), "Low stock.", SuggestionTrigger.LOW_INVENTORY);
        when(suggestionRepository.findByIdForUpdate(suggestionId)).thenReturn(Optional.of(suggestion));

        var response = suggestionService.resolve(suggestionId,
                new ResolveSuggestionRequest(ResolveSuggestionRequest.Resolution.REJECT));

        assertThat(response.status()).isEqualTo(SuggestionStatus.REJECTED);
        assertThat(product.getCurrentPrice()).isEqualByComparingTo("10.00");
        verify(productRepository, never()).findByIdForUpdate(product.getId());
    }

    @Test
    void approvingStaleSuggestionDoesNotMutateProduct() {
        UUID suggestionId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        Suggestion suggestion = new Suggestion(product, SuggestionType.PRICE_ADJUSTMENT,
                new BigDecimal("9.00"), new BigDecimal("11.00"), null,
                com.stockpulse.entity.RecommendationDirection.INCREASE,
                new BigDecimal("0.95"), "Stale.", SuggestionTrigger.LOW_INVENTORY);
        when(suggestionRepository.findByIdForUpdate(suggestionId)).thenReturn(Optional.of(suggestion));
        when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> suggestionService.resolve(suggestionId,
                new ResolveSuggestionRequest(ResolveSuggestionRequest.Resolution.APPROVE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("stale");
        assertThat(product.getCurrentPrice()).isEqualByComparingTo("10.00");
    }

    private static Product product(UUID id) {
        Product product = new Product("SKU-" + id, "Test product", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 4);
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
            return product;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not prepare product test fixture.", exception);
        }
    }
}
