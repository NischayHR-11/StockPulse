package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.SuggestionType;
import com.stockpulse.entity.RecommendationDirection;
import com.stockpulse.entity.SuggestionTrigger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedSuggestionStrategy implements SuggestionStrategy {
    @Override
    public List<SuggestionDraft> suggest(Product product, BigDecimal categoryAverageDemand,
                                         SuggestionTrigger trigger) {
        if (product == null || trigger == null || categoryAverageDemand == null
                || categoryAverageDemand.signum() < 0) {
            throw new IllegalArgumentException("Product, trigger, and category demand must be valid.");
        }
        if (product.getCurrentPrice() == null || product.getCurrentPrice().signum() <= 0
                || product.getStockLevel() < 0 || product.getReorderThreshold() < 0
                || product.getDemandVelocity() < 0) {
            throw new IllegalArgumentException("Product pricing and inventory values are invalid.");
        }
        List<SuggestionDraft> drafts = new ArrayList<>();
        boolean lowStock = product.getStockLevel() < product.getReorderThreshold();
        boolean demandSpike = categoryAverageDemand != null
                && categoryAverageDemand.signum() > 0
                && BigDecimal.valueOf(product.getDemandVelocity())
                .compareTo(categoryAverageDemand.multiply(BigDecimal.valueOf(2))) > 0;
        BigDecimal proposedPrice = product.getCurrentPrice();
        RecommendationDirection direction = RecommendationDirection.HOLD;
        String priceReason = "Demand and stock support holding the current price.";
        BigDecimal confidence = new BigDecimal("0.60");
        if (lowStock) {
            proposedPrice = product.getCurrentPrice().multiply(new BigDecimal("1.10"))
                    .setScale(2, RoundingMode.HALF_UP);
            direction = RecommendationDirection.INCREASE;
            priceReason = "Current stock is below the reorder threshold.";
            confidence = new BigDecimal("0.95");
        } else if (demandSpike) {
            proposedPrice = product.getCurrentPrice().multiply(new BigDecimal("1.05"))
                    .setScale(2, RoundingMode.HALF_UP);
            direction = RecommendationDirection.INCREASE;
            priceReason = "Demand velocity is above twice the category average.";
            confidence = new BigDecimal("0.85");
        }
        drafts.add(new SuggestionDraft(SuggestionType.PRICE_ADJUSTMENT, product.getCurrentPrice(),
                proposedPrice, null, direction, confidence, priceReason, trigger));

        if (lowStock) {
            long required = (long) product.getReorderThreshold() * 3 - product.getStockLevel();
            if (required > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Recommended reorder quantity exceeds the supported limit.");
            }
            int quantity = (int) Math.max(1L, required);
            drafts.add(new SuggestionDraft(SuggestionType.REORDER, product.getCurrentPrice(), null,
                    quantity, null, new BigDecimal("0.95"),
                    "Stock is below the reorder threshold.", trigger));
        }
        return drafts;
    }
}
