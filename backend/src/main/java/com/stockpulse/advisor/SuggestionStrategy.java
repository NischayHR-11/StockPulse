package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.SuggestionTrigger;
import java.math.BigDecimal;

import java.util.List;
import com.stockpulse.entity.SuggestionSource;

public interface SuggestionStrategy {
    List<SuggestionDraft> suggest(Product product, BigDecimal categoryAverageDemand,
                                  SuggestionTrigger trigger);

    default SuggestionGeneration generate(Product product, BigDecimal categoryAverageDemand,
                                          SuggestionTrigger trigger) {
        return new SuggestionGeneration(suggest(product, categoryAverageDemand, trigger),
                SuggestionSource.RULE_BASED);
    }
}
