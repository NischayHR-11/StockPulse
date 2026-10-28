package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class FallbackSuggestionAdvisorTest {
    @Test
    void usesRuleBasedAdviceWhenAiProviderFails() {
        RuleBasedSuggestionStrategy rules = Mockito.mock(RuleBasedSuggestionStrategy.class);
        AiAdvisorClient ai = Mockito.mock(AiAdvisorClient.class);
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 1);
        SuggestionDraft ruleDraft = new SuggestionDraft(
                com.stockpulse.entity.SuggestionType.PRICE_ADJUSTMENT,
                new BigDecimal("10.00"), new BigDecimal("11.00"), null,
                com.stockpulse.entity.RecommendationDirection.INCREASE,
                new BigDecimal("0.95"), "Rule fallback.", SuggestionTrigger.LOW_INVENTORY);
        when(ai.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .thenThrow(new AiAdvisorException("timeout"));
        when(rules.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .thenReturn(List.of(ruleDraft));

        FallbackSuggestionAdvisor advisor = new FallbackSuggestionAdvisor(rules, ai,
                Mockito.mock(SuggestionPolicyValidator.class), "ai");

        assertThat(advisor.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .containsExactly(ruleDraft);
        assertThat(advisor.generate(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY).source())
                .isEqualTo(SuggestionSource.RULE_BASED);
    }

    @Test
    void usesRulesWithoutCallingAiWhenRuleProviderSelected() {
        RuleBasedSuggestionStrategy rules = Mockito.mock(RuleBasedSuggestionStrategy.class);
        AiAdvisorClient ai = Mockito.mock(AiAdvisorClient.class);
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 1);
        when(rules.suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL)).thenReturn(List.of());

        FallbackSuggestionAdvisor advisor = new FallbackSuggestionAdvisor(rules, ai,
                Mockito.mock(SuggestionPolicyValidator.class), "rule");

        assertThat(advisor.suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL)).isEmpty();
        Mockito.verifyNoInteractions(ai);
        assertThat(advisor.generate(product, BigDecimal.ONE, SuggestionTrigger.MANUAL).source())
                .isEqualTo(SuggestionSource.RULE_BASED);
    }

    @Test
    void usesRulesWhenAdvisorThrowsUnexpectedRuntimeFailure() {
        RuleBasedSuggestionStrategy rules = Mockito.mock(RuleBasedSuggestionStrategy.class);
        AiAdvisorClient ai = Mockito.mock(AiAdvisorClient.class);
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 1);
        SuggestionDraft ruleDraft = new SuggestionDraft(
                com.stockpulse.entity.SuggestionType.REORDER,
                new BigDecimal("10.00"), null, 13, null,
                new BigDecimal("0.95"), "Rule fallback.", SuggestionTrigger.LOW_INVENTORY);
        when(ai.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .thenThrow(new IllegalStateException("unexpected"));
        when(rules.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .thenReturn(List.of(ruleDraft));

        FallbackSuggestionAdvisor advisor = new FallbackSuggestionAdvisor(rules, ai,
                Mockito.mock(SuggestionPolicyValidator.class), "ai");

        assertThat(advisor.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .containsExactly(ruleDraft);
    }

    @Test
    void usesRulesWhenProviderDraftViolatesPolicy() {
        RuleBasedSuggestionStrategy rules = Mockito.mock(RuleBasedSuggestionStrategy.class);
        AiAdvisorClient ai = Mockito.mock(AiAdvisorClient.class);
        SuggestionPolicyValidator validator = Mockito.mock(SuggestionPolicyValidator.class);
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 1);
        SuggestionDraft ruleDraft = new SuggestionDraft(
                com.stockpulse.entity.SuggestionType.REORDER,
                new BigDecimal("10.00"), null, 13, null,
                new BigDecimal("0.95"), "Rule fallback.", SuggestionTrigger.LOW_INVENTORY);
        when(ai.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .thenReturn(List.of(ruleDraft));
        Mockito.doThrow(new IllegalArgumentException("policy mismatch"))
                .when(validator).validate(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY,
                        List.of(ruleDraft));
        when(rules.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .thenReturn(List.of(ruleDraft));

        FallbackSuggestionAdvisor advisor = new FallbackSuggestionAdvisor(rules, ai, validator, "ai");

        assertThat(advisor.suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY))
                .containsExactly(ruleDraft);
        Mockito.verify(rules).suggest(product, BigDecimal.ONE, SuggestionTrigger.LOW_INVENTORY);
    }

    @Test
    void marksPolicyValidatedAiAdviceAsGemini() {
        RuleBasedSuggestionStrategy rules = Mockito.mock(RuleBasedSuggestionStrategy.class);
        AiAdvisorClient ai = Mockito.mock(AiAdvisorClient.class);
        SuggestionPolicyValidator validator = Mockito.mock(SuggestionPolicyValidator.class);
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 2, 5, 1);
        SuggestionDraft aiDraft = new SuggestionDraft(
                com.stockpulse.entity.SuggestionType.REORDER,
                new BigDecimal("10.00"), null, 13, null,
                new BigDecimal("0.95"), "AI recommendation.", SuggestionTrigger.MANUAL);
        when(ai.suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL))
                .thenReturn(List.of(aiDraft));

        FallbackSuggestionAdvisor advisor = new FallbackSuggestionAdvisor(rules, ai, validator, "ai");

        assertThat(advisor.generate(product, BigDecimal.ONE, SuggestionTrigger.MANUAL).source())
                .isEqualTo(SuggestionSource.GEMINI);
        Mockito.verify(validator).validate(product, BigDecimal.ONE, SuggestionTrigger.MANUAL,
                List.of(aiDraft));
        Mockito.verifyNoInteractions(rules);
    }
}
