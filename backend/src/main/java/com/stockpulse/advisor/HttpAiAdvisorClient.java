package com.stockpulse.advisor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.RecommendationDirection;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class HttpAiAdvisorClient implements AiAdvisorClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final Duration timeout;

    public HttpAiAdvisorClient(ObjectMapper objectMapper,
                               @Value("${app.advisor.ai.endpoint:}") String endpoint,
                               @Value("${app.advisor.ai.api-key:}") String apiKey,
                               @Value("${app.advisor.ai.timeout-ms:1500}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public List<SuggestionDraft> suggest(Product product, BigDecimal categoryAverageDemand,
                                         SuggestionTrigger trigger) {
        if (endpoint.isBlank() || apiKey.isBlank()) {
            throw new AiAdvisorException("AI advisor is not configured.");
        }
        try {
            String payload = objectMapper.writeValueAsString(new AiRequest(product, categoryAverageDemand, trigger));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiAdvisorException("AI advisor returned HTTP " + response.statusCode() + ".");
            }
            return parseAndValidate(response.body(), product, trigger);
        } catch (AiAdvisorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiAdvisorException("AI advisor request failed.", exception);
        }
    }

    private List<SuggestionDraft> parseAndValidate(String body, Product product, SuggestionTrigger trigger) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode suggestions = root.isArray() ? root : root.get("suggestions");
            if (suggestions == null || !suggestions.isArray() || suggestions.isEmpty()) {
                throw new AiAdvisorException("AI advisor response did not contain suggestions.");
            }
            List<SuggestionDraft> drafts = new ArrayList<>();
            for (JsonNode node : suggestions) {
                drafts.add(toDraft(node, product, trigger));
            }
            return drafts;
        } catch (AiAdvisorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiAdvisorException("AI advisor returned malformed JSON.", exception);
        }
    }

    private SuggestionDraft toDraft(JsonNode node, Product product, SuggestionTrigger trigger) {
        SuggestionType type = enumValue(node, "type", SuggestionType.class);
        BigDecimal currentPrice = decimalValue(node, "currentPrice", false);
        BigDecimal proposedPrice = decimalValue(node, "proposedPrice", type == SuggestionType.PRICE_ADJUSTMENT);
        Integer quantity = integerValue(node, "recommendedQuantity", type == SuggestionType.REORDER);
        RecommendationDirection direction = enumValue(node, "direction", RecommendationDirection.class);
        BigDecimal confidence = decimalValue(node, "confidence", true);
        String rationale = textValue(node, "rationale");
        if (currentPrice == null) {
            currentPrice = product.getCurrentPrice();
        }
        if (currentPrice.signum() <= 0) {
            throw new AiAdvisorException("AI suggestion currentPrice must be positive.");
        }
        if (type == SuggestionType.PRICE_ADJUSTMENT && direction == null) {
            throw new AiAdvisorException("AI price suggestion must include direction.");
        }
        if (type == SuggestionType.REORDER && direction != null) {
            throw new AiAdvisorException("AI reorder suggestion must not include price direction.");
        }
        return new SuggestionDraft(type, currentPrice, proposedPrice, quantity, direction,
                confidence, rationale, trigger);
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 500) {
            throw new AiAdvisorException("AI suggestion requires valid " + field + ".");
        }
        return value.asText();
    }

    private static BigDecimal decimalValue(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (!required) return null;
            throw new AiAdvisorException("AI suggestion requires " + field + ".");
        }
        if (!value.isNumber()) {
            throw new AiAdvisorException("AI suggestion field " + field + " must be numeric.");
        }
        BigDecimal result = value.decimalValue();
        if (required && !"confidence".equals(field) && result.signum() <= 0) {
            throw new AiAdvisorException("AI suggestion field " + field + " must be positive.");
        }
        if ("confidence".equals(field) && (result.signum() < 0 || result.compareTo(BigDecimal.ONE) > 0)) {
            throw new AiAdvisorException("AI confidence must be between 0 and 1.");
        }
        return result;
    }

    private static Integer integerValue(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (!required) return null;
            throw new AiAdvisorException("AI suggestion requires " + field + ".");
        }
        if (!value.canConvertToInt() || value.asInt() < 1) {
            throw new AiAdvisorException("AI suggestion field " + field + " must be a positive integer.");
        }
        return value.asInt();
    }

    private static <T extends Enum<T>> T enumValue(JsonNode node, String field, Class<T> type) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            if ("direction".equals(field)) return null;
            throw new AiAdvisorException("AI suggestion requires " + field + ".");
        }
        try {
            return Enum.valueOf(type, value.asText());
        } catch (IllegalArgumentException exception) {
            throw new AiAdvisorException("AI suggestion has an invalid " + field + ".");
        }
    }

    private record AiRequest(ProductContext product, BigDecimal categoryAverageDemand,
                             SuggestionTrigger trigger) {
        AiRequest(Product product, BigDecimal categoryAverageDemand, SuggestionTrigger trigger) {
            this(new ProductContext(product.getSku(), product.getName(), product.getCategory().name(),
                    product.getCurrentPrice(), product.getStockLevel(), product.getReorderThreshold(),
                    product.getDemandVelocity()), categoryAverageDemand, trigger);
        }
    }

    private record ProductContext(String sku, String name, String category, BigDecimal currentPrice,
                                  int stockLevel, int reorderThreshold, int demandVelocity) { }
}
