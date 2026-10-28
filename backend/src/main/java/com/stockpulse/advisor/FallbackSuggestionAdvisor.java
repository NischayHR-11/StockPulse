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
import com.stockpulse.entity.SuggestionSource;

@Primary
@Component
public class FallbackSuggestionAdvisor implements SuggestionAdvisor {
    private static final Logger log = LoggerFactory.getLogger(FallbackSuggestionAdvisor.class);

    private final RuleBasedSuggestionStrategy ruleBasedStrategy;
    private final AiAdvisorClient aiAdvisorClient;
    private final SuggestionPolicyValidator policyValidator;
    private final String provider;

    public FallbackSuggestionAdvisor(RuleBasedSuggestionStrategy ruleBasedStrategy,
                                     AiAdvisorClient aiAdvisorClient,
                                     SuggestionPolicyValidator policyValidator,
                                     @Value("${app.advisor.provider:rule}") String provider) {
        this.ruleBasedStrategy = ruleBasedStrategy;
        this.aiAdvisorClient = aiAdvisorClient;
        this.policyValidator = policyValidator;
        this.provider = provider;
    }

    @Override
    public List<SuggestionDraft> suggest(Product product, BigDecimal categoryAverageDemand,
                                         SuggestionTrigger trigger) {
        return generate(product, categoryAverageDemand, trigger).drafts();
    }

    @Override
    public SuggestionGeneration generate(Product product, BigDecimal categoryAverageDemand,
                                         SuggestionTrigger trigger) {
        if (!"ai".equalsIgnoreCase(provider)) {
            log.info("Using deterministic rule-based advisor because provider is configured as '{}'.", provider);
            return new SuggestionGeneration(ruleBasedStrategy.suggest(product, categoryAverageDemand, trigger),
                    SuggestionSource.RULE_BASED);
        }
        try {
            List<SuggestionDraft> drafts = aiAdvisorClient.suggest(product, categoryAverageDemand, trigger);
            policyValidator.validate(product, categoryAverageDemand, trigger, drafts);
            log.info("Gemini advisor returned policy-valid recommendations.");
            return new SuggestionGeneration(drafts, SuggestionSource.GEMINI);
        } catch (AiAdvisorException exception) {
            log.warn("AI advisor unavailable; using deterministic fallback. category={}, status={}, detail={}",
                    exception.getCategory(), exception.getHttpStatus(), exception.getDetail());
            return new SuggestionGeneration(ruleBasedStrategy.suggest(product, categoryAverageDemand, trigger),
                    SuggestionSource.RULE_BASED);
        } catch (RuntimeException exception) {
            log.error("Unexpected AI advisor failure; using deterministic fallback.", exception);
            return new SuggestionGeneration(ruleBasedStrategy.suggest(product, categoryAverageDemand, trigger),
                    SuggestionSource.RULE_BASED);
        }
    }
}
