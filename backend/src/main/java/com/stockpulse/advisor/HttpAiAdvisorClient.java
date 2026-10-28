package com.stockpulse.advisor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.RecommendationDirection;
import com.stockpulse.entity.SuggestionTrigger;
import com.stockpulse.entity.SuggestionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class HttpAiAdvisorClient implements AiAdvisorClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    @Autowired
    public HttpAiAdvisorClient(ObjectMapper objectMapper,
                               @Value("${gemini.api-key:}") String apiKey,
                               @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                               @Value("${gemini.model:gemini-2.5-flash}") String model,
                               @Value("${app.advisor.ai.timeout-ms:1500}") long timeoutMs) {
        this(objectMapper, apiKey, baseUrl, model, timeoutMs,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build());
    }

    HttpAiAdvisorClient(ObjectMapper objectMapper, String apiKey, String baseUrl,
                        String model, long timeoutMs, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = httpClient;
    }

    @Override
    public List<SuggestionDraft> suggest(Product product, BigDecimal categoryAverageDemand,
                                         SuggestionTrigger trigger) {
        if (apiKey.isBlank()) {
            throw new AiAdvisorException("Gemini advisor is not configured.",
                    AiAdvisorException.Category.CONFIGURATION, null, null);
        }
        try {
            String payload = objectMapper.writeValueAsString(new GeminiRequest(
                    List.of(new GeminiContent(List.of(
                            new GeminiPart(buildPrompt(product, categoryAverageDemand, trigger))))),
                    new GenerationConfig("application/json", suggestionSchema())));
            URI endpoint = URI.create(baseUrl + "/models/" + model
                    + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = response.statusCode();
                throw new AiAdvisorException(
                        "Gemini advisor returned HTTP " + status + ".",
                        categoryForStatus(status), status, errorDetail(response.body()));
            }
            return parseAndValidate(response.body(), product, trigger);
        } catch (AiAdvisorException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AiAdvisorException("Gemini advisor request timed out.",
                    AiAdvisorException.Category.TIMEOUT, null, null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiAdvisorException("Gemini advisor request was interrupted.",
                    AiAdvisorException.Category.TIMEOUT, null, null, exception);
        } catch (IOException exception) {
            throw new AiAdvisorException("Gemini advisor network request failed.",
                    AiAdvisorException.Category.NETWORK, null, limitedMessage(exception), exception);
        } catch (Exception exception) {
            throw new AiAdvisorException("Gemini advisor request failed.",
                    AiAdvisorException.Category.UNKNOWN, null, limitedMessage(exception), exception);
        }
    }

    private List<SuggestionDraft> parseAndValidate(String body, Product product, SuggestionTrigger trigger) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                throw malformed("Gemini response did not contain candidates.");
            }
            JsonNode content = candidates.get(0).get("content");
            JsonNode parts = content == null ? null : content.get("parts");
            if (parts == null || !parts.isArray() || parts.isEmpty()
                    || !parts.get(0).hasNonNull("text")) {
                throw malformed("Gemini response did not contain generated JSON.");
            }
            JsonNode generated = objectMapper.readTree(parts.get(0).get("text").asText());
            JsonNode suggestions = generated.isArray() ? generated : generated.get("suggestions");
            if (suggestions == null || !suggestions.isArray() || suggestions.isEmpty()) {
                throw malformed("Gemini response did not contain suggestions.");
            }
            List<SuggestionDraft> drafts = new ArrayList<>();
            for (JsonNode node : suggestions) {
                drafts.add(toDraft(node, product, trigger));
            }
            return drafts;
        } catch (AiAdvisorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiAdvisorException("Gemini response was malformed.",
                    AiAdvisorException.Category.MALFORMED_RESPONSE, null,
                    limitedMessage(exception), exception);
        }
    }

    private static AiAdvisorException.Category categoryForStatus(int status) {
        if (status == 401 || status == 403) return AiAdvisorException.Category.AUTHENTICATION;
        if (status == 404) return AiAdvisorException.Category.INVALID_ENDPOINT_OR_MODEL;
        if (status == 429) return AiAdvisorException.Category.RATE_LIMIT_OR_QUOTA;
        if (status >= 500) return AiAdvisorException.Category.PROVIDER_ERROR;
        return AiAdvisorException.Category.UNKNOWN;
    }

    private String errorDetail(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            String message = error.path("message").asText(null);
            if (message != null) return sanitize(message);
        } catch (Exception ignored) {
            // Fall back to the bounded response text below.
        }
        return sanitize(body);
    }

    private static String limitedMessage(Throwable exception) {
        return sanitize(exception.getMessage());
    }

    private static AiAdvisorException malformed(String message) {
        return new AiAdvisorException(message, AiAdvisorException.Category.MALFORMED_RESPONSE,
                null, null);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        sanitized = sanitized.replaceAll("(?i)((?:api[-_ ]?)?key\\s*[=:]\\s*)[^,\\s]+", "$1[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)(bearer\\s+)[^,\\s]+", "$1[REDACTED]");
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240) + "...";
    }

    private static String buildPrompt(Product product, BigDecimal categoryAverageDemand,
                                      SuggestionTrigger trigger) {
        return """
                Generate StockPulse inventory recommendations for the supplied product.
                Return only the JSON object described by the response schema.
                Apply these authoritative business rules: if stock is below the reorder threshold,
                increase price by 10 percent and recommend max(1, reorderThreshold * 3 - stock)
                units; otherwise, if demand velocity is greater than twice category average demand,
                increase price by 5 percent; otherwise hold the current price. Low stock has priority.
                The current price must be returned exactly, prices must have at most two decimals,
                and the trigger must be represented by the application.
                Product context: %s
                Category average demand: %s
                Trigger: %s
                """.formatted(productContext(product), categoryAverageDemand, trigger);
    }

    private static String productContext(Product product) {
        return "sku=" + product.getSku()
                + ", name=" + product.getName()
                + ", category=" + product.getCategory()
                + ", currentPrice=" + product.getCurrentPrice()
                + ", stockLevel=" + product.getStockLevel()
                + ", reorderThreshold=" + product.getReorderThreshold()
                + ", demandVelocity=" + product.getDemandVelocity();
    }

    private static ObjectNode suggestionSchema() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode().put("type", "OBJECT");
        ObjectNode suggestions = schema.putObject("properties").putObject("suggestions");
        ObjectNode item = mapper.createObjectNode().put("type", "OBJECT");
        ObjectNode properties = item.putObject("properties");
        properties.putObject("type").put("type", "STRING")
                .putArray("enum").add("PRICE_ADJUSTMENT").add("REORDER");
        properties.putObject("currentPrice").put("type", "NUMBER");
        properties.putObject("proposedPrice").put("type", "NUMBER");
        properties.putObject("recommendedQuantity").put("type", "INTEGER");
        properties.putObject("direction").put("type", "STRING")
                .putArray("enum").add("INCREASE").add("HOLD");
        properties.putObject("confidence").put("type", "NUMBER");
        properties.putObject("rationale").put("type", "STRING");
        item.putArray("required").add("type").add("currentPrice").add("confidence").add("rationale");
        suggestions.put("type", "ARRAY").set("items", item);
        schema.putArray("required").add("suggestions");
        return schema;
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
            throw malformed("AI suggestion currentPrice must be positive.");
        }
        if (type == SuggestionType.PRICE_ADJUSTMENT && direction == null) {
            throw malformed("AI price suggestion must include direction.");
        }
        if (type == SuggestionType.REORDER && direction != null) {
            throw malformed("AI reorder suggestion must not include price direction.");
        }
        return new SuggestionDraft(type, currentPrice, proposedPrice, quantity, direction,
                confidence, rationale, trigger);
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 500) {
            throw malformed("AI suggestion requires valid " + field + ".");
        }
        return value.asText();
    }

    private static BigDecimal decimalValue(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (!required) return null;
            throw malformed("AI suggestion requires " + field + ".");
        }
        if (!value.isNumber()) {
            throw malformed("AI suggestion field " + field + " must be numeric.");
        }
        BigDecimal result = value.decimalValue();
        if (required && !"confidence".equals(field) && result.signum() <= 0) {
            throw malformed("AI suggestion field " + field + " must be positive.");
        }
        if ("confidence".equals(field) && (result.signum() < 0 || result.compareTo(BigDecimal.ONE) > 0)) {
            throw malformed("AI confidence must be between 0 and 1.");
        }
        return result;
    }

    private static Integer integerValue(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (!required) return null;
            throw malformed("AI suggestion requires " + field + ".");
        }
        if (!value.canConvertToInt() || value.asInt() < 1) {
            throw malformed("AI suggestion field " + field + " must be a positive integer.");
        }
        return value.asInt();
    }

    private static <T extends Enum<T>> T enumValue(JsonNode node, String field, Class<T> type) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            if ("direction".equals(field)) return null;
            throw malformed("AI suggestion requires " + field + ".");
        }
        try {
            return Enum.valueOf(type, value.asText());
        } catch (IllegalArgumentException exception) {
            throw malformed("AI suggestion has an invalid " + field + ".");
        }
    }

    private record GeminiRequest(List<GeminiContent> contents, GenerationConfig generationConfig) { }
    private record GeminiContent(List<GeminiPart> parts) { }
    private record GeminiPart(String text) { }
    private record GenerationConfig(String responseMimeType, JsonNode responseSchema) { }
}
