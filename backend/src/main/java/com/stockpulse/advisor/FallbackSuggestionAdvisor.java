package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.SuggestionTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Primary
@Component
public class FallbackSuggestionAdvisor implements SuggestionAdvisor {
    private static final Logger log = LoggerFactory.getLogger(FallbackSuggestionAdvisor.class);

    private final RuleBasedSuggestionStrategy ruleBasedStrategy;
    private final AiAdvisorClient aiAdvisorClient;
    private final String provider;

    public FallbackSuggestionAdvisor(RuleBasedSuggestionStrategy ruleBasedStrategy,
                                     AiAdvisorClient aiAdvisorClient,
                                     @Value("${app.advisor.provider:rule}") String provider) {
        this.ruleBasedStrategy = ruleBasedStrategy;
        this.aiAdvisorClient = aiAdvisorClient;
        this.provider = provider;
    }

    @Override
    public List<SuggestionDraft> suggest(Product product, BigDecimal categoryAverageDemand,
                                         SuggestionTrigger trigger) {
        if (!"ai".equalsIgnoreCase(provider)) {
            return ruleBasedStrategy.suggest(product, categoryAverageDemand, trigger);
        }
        try {
            return aiAdvisorClient.suggest(product, categoryAverageDemand, trigger);
        } catch (AiAdvisorException exception) {
            log.warn("AI advisor unavailable; using deterministic fallback: {}", exception.getMessage());
            return ruleBasedStrategy.suggest(product, categoryAverageDemand, trigger);
        } catch (RuntimeException exception) {
            log.error("Unexpected AI advisor failure; using deterministic fallback.", exception);
            return ruleBasedStrategy.suggest(product, categoryAverageDemand, trigger);
        }
    }
}
