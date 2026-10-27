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
    void rejectsInvalidConfidenceFromProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/advisor", exchange -> {
            byte[] body = """
                    {"suggestions":[{"type":"PRICE_ADJUSTMENT","proposedPrice":11.00,
                    "direction":"INCREASE","confidence":1.5,"rationale":"Invalid confidence"}]}
                    """.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        HttpAiAdvisorClient client = new HttpAiAdvisorClient(new ObjectMapper(),
                "http://localhost:" + server.getAddress().getPort() + "/advisor", "test-key", 1000);
        Product product = new Product("SKU-1", "Test", ProductCategory.HOME,
                new BigDecimal("10.00"), 10, 5, 1);

        assertThatThrownBy(() -> client.suggest(product, BigDecimal.ONE, SuggestionTrigger.MANUAL))
                .isInstanceOf(AiAdvisorException.class)
                .hasMessageContaining("confidence");
    }
}
