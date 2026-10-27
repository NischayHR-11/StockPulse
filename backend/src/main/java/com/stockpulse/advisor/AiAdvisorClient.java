package com.stockpulse.advisor;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.SuggestionTrigger;

import java.math.BigDecimal;
import java.util.List;

public interface AiAdvisorClient {
    List<SuggestionDraft> suggest(Product product, BigDecimal categoryAverageDemand,
                                  SuggestionTrigger trigger);
}
