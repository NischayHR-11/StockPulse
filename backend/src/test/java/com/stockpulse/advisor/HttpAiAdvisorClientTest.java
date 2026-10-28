package com.stockpulse.advisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.SuggestionTrigger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpAiAdvisorClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesSuccessfulGeminiResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-2.5-flash:generateContent", exchange -> {
            byte[] body = """
                    {"candidates":[{"content":{"parts":[{"text":"{\\"suggestions\\":[{\\"type\\":\\"PRICE_ADJUSTMENT\\",\\"proposedPrice\\":11.00,\\"direction\\":\\"INCREASE\\",\\"confidence\\":0.9,\\"rationale\\":\\"Low stock\\"}]}"}]}}]}
                    """.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        HttpAiAdvisorClient client = clientForServer();
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 1);

        var drafts = client.suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL);

        assertThat(drafts).singleElement().satisfies(draft -> {
            assertThat(draft.type()).isEqualTo(com.stockpulse.entity.SuggestionType.PRICE_ADJUSTMENT);
            assertThat(draft.proposedPrice()).isEqualByComparingTo("11.00");
            assertThat(draft.confidence()).isEqualByComparingTo("0.9");
        });
    }

    @Test
    void rejectsInvalidConfidenceFromGemini() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-2.5-flash:generateContent", exchange -> {
            byte[] body = """
                    {"candidates":[{"content":{"parts":[{"text":"{\\"suggestions\\":[{\\"type\\":\\"PRICE_ADJUSTMENT\\",\\"proposedPrice\\":11.00,\\"direction\\":\\"INCREASE\\",\\"confidence\\":1.5,\\"rationale\\":\\"Invalid confidence\\"}]}"}]}}]}
                    """.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 1);

        assertThatThrownBy(() -> clientForServer().suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL))
                .isInstanceOf(AiAdvisorException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejectsMalformedGeminiResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-2.5-flash:generateContent", exchange -> {
            byte[] body = "{\"candidates\":[]}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 1);

        assertThatThrownBy(() -> clientForServer().suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL))
                .isInstanceOf(AiAdvisorException.class)
                .hasMessageContaining("candidates");
    }

    @Test
    void rejectsGeminiProviderError() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-2.5-flash:generateContent", exchange -> {
            byte[] body = "{\"error\":{\"message\":\"unavailable\"}}".getBytes();
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 1);

        assertThatThrownBy(() -> clientForServer().suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL))
                .isInstanceOf(AiAdvisorException.class)
                .hasMessageContaining("HTTP 503")
                .satisfies(error -> {
                    AiAdvisorException exception = (AiAdvisorException) error;
                    assertThat(exception.getCategory())
                            .isEqualTo(AiAdvisorException.Category.PROVIDER_ERROR);
                    assertThat(exception.getHttpStatus()).isEqualTo(503);
                    assertThat(exception.getDetail()).isEqualTo("unavailable");
                });
    }

    @Test
    void convertsGeminiTimeoutToAdvisorFailure() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-2.5-flash:generateContent", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();

        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 1);
        HttpAiAdvisorClient client = new HttpAiAdvisorClient(new ObjectMapper(), "test-key",
                "http://localhost:" + server.getAddress().getPort(), "gemini-2.5-flash",
                50, HttpClient.newHttpClient());

        assertThatThrownBy(() -> client.suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL))
                .isInstanceOf(AiAdvisorException.class)
                .hasMessage("Gemini advisor request timed out.")
                .satisfies(error -> assertThat(((AiAdvisorException) error).getCategory())
                        .isEqualTo(AiAdvisorException.Category.TIMEOUT));
    }

    @Test
    void categorizesAuthenticationFailureWithoutExposingRequestDetails() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-2.5-flash:generateContent", exchange -> {
            byte[] body = """
                    {"error":{"message":"API key is invalid; do not retry with key=secret-value"}}
                    """.getBytes();
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 1);

        assertThatThrownBy(() -> clientForServer().suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL))
                .isInstanceOf(AiAdvisorException.class)
                .satisfies(error -> {
                    AiAdvisorException exception = (AiAdvisorException) error;
                    assertThat(exception.getCategory())
                            .isEqualTo(AiAdvisorException.Category.AUTHENTICATION);
                    assertThat(exception.getDetail()).contains("API key is invalid");
                    assertThat(exception.getDetail()).doesNotContain("secret-value");
                });
    }

    private HttpAiAdvisorClient clientForServer() {
        return new HttpAiAdvisorClient(new ObjectMapper(), "test-key",
                "http://localhost:" + server.getAddress().getPort(), "gemini-2.5-flash",
                5000, HttpClient.newHttpClient());
    }
}
