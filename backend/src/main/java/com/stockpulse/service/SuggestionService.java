package com.stockpulse.service;

import com.stockpulse.advisor.SuggestionStrategy;
import com.stockpulse.advisor.SuggestionPolicyValidator;

import com.stockpulse.exception.ConflictException;
import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.entity.Product;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.dto.request.ResolveSuggestionRequest;
import com.stockpulse.dto.response.SuggestionResponse;
import com.stockpulse.entity.Suggestion;
import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.repository.SuggestionRepository;
import com.stockpulse.mapper.SuggestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SuggestionService {
    private final ProductRepository productRepository;
    private final SuggestionRepository suggestionRepository;
    private final SuggestionStrategy suggestionStrategy;
    private final SuggestionPolicyValidator policyValidator;

    public SuggestionService(ProductRepository productRepository, SuggestionRepository suggestionRepository,
                             SuggestionStrategy suggestionStrategy,
                             SuggestionPolicyValidator policyValidator) {
        this.productRepository = productRepository;
        this.suggestionRepository = suggestionRepository;
        this.suggestionStrategy = suggestionStrategy;
        this.policyValidator = policyValidator;
    }

    @Transactional
    public List<SuggestionResponse> generate(UUID productId, SuggestionTrigger trigger) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        Double averageDemandValue = productRepository.averageDemandVelocityByCategory(product.getCategory());
        java.math.BigDecimal averageDemand = averageDemandValue == null
                ? java.math.BigDecimal.ZERO : java.math.BigDecimal.valueOf(averageDemandValue);
        List<com.stockpulse.advisor.SuggestionDraft> drafts =
                suggestionStrategy.suggest(product, averageDemand, trigger);
        policyValidator.validate(product, averageDemand, trigger, drafts);
        return drafts.stream()
                .filter(draft -> suggestionRepository.findByProductIdAndTypeAndTriggerAndStatus(
                        productId, draft.type(), trigger, SuggestionStatus.PENDING).isEmpty())
                .map(draft -> suggestionRepository.save(new Suggestion(product, draft.type(),
                        draft.currentPrice(), draft.proposedPrice(), draft.recommendedQuantity(),
                        draft.direction(), draft.confidence(), draft.rationale(), draft.trigger())))
                .map(SuggestionMapper::toResponse)
                .toList();
    }

    public List<SuggestionResponse> findByProduct(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        return suggestionRepository.findAllByProductIdOrderByCreatedAtDesc(productId)
                .stream().map(SuggestionMapper::toResponse).toList();
    }

    @Transactional
    public SuggestionResponse resolve(UUID suggestionId, ResolveSuggestionRequest request) {
        Suggestion suggestion = suggestionRepository.findByIdForUpdate(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found: " + suggestionId));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ConflictException("Only pending suggestions can be resolved.");
        }
        if (request.resolution() == ResolveSuggestionRequest.Resolution.APPROVE) {
            Product product = productRepository.findByIdForUpdate(suggestion.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: "
                            + suggestion.getProduct().getId()));
            if (suggestion.getCurrentPrice() == null
                    || product.getCurrentPrice().compareTo(suggestion.getCurrentPrice()) != 0
                    || (suggestion.getStockLevelAtCreation() != null
                    && product.getStockLevel() != suggestion.getStockLevelAtCreation())) {
                throw new ConflictException("Suggestion is stale because product state has changed.");
            }
            suggestion.approve();
            if (suggestion.getProposedPrice() != null) {
                product.updatePrice(suggestion.getProposedPrice());
            } else if (suggestion.getRecommendedQuantity() != null) {
                long resultingStock = (long) product.getStockLevel() + suggestion.getRecommendedQuantity();
                if (resultingStock > Integer.MAX_VALUE) {
                    throw new ConflictException("Accepted reorder would exceed the supported stock limit.");
                }
                product.updateStock((int) resultingStock);
            }
        } else {
            suggestion.reject();
        }
        return SuggestionMapper.toResponse(suggestion);
    }

}
