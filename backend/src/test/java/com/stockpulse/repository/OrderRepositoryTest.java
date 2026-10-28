package com.stockpulse.repository;

import com.stockpulse.entity.Order;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryTest {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void sumsRecentProcessedQuantitiesAndExcludesOlderOrders() {
        Product product = productRepository.save(product("SKU-RECENT", 20));
        Product otherProduct = productRepository.save(product("SKU-OTHER", 20));
        Order first = orderRepository.save(new Order(product, 2, "recent-1", 18));
        Order second = orderRepository.save(new Order(product, 3, "recent-2", 15));
        orderRepository.save(new Order(otherProduct, 9, "other-1", 11));
        entityManager.flush();

        setCreatedAt(first, Instant.now().minus(Duration.ofHours(23)));
        setCreatedAt(second, Instant.now().minus(Duration.ofHours(25)));

        List<OrderRepository.SalesAggregate> result =
                orderRepository.sumProcessedQuantitiesByProductIdsSince(
                        List.of(product.getId()), Instant.now().minus(Duration.ofHours(24)));

        assertThat(result).singleElement().satisfies(aggregate -> {
            assertThat(aggregate.getProductId()).isEqualTo(product.getId());
            assertThat(aggregate.getUnitsSold()).isEqualTo(2L);
        });
    }

    @Test
    void returnsNoAggregateForProductWithoutSales() {
        Product product = productRepository.save(product("SKU-NONE", 20));

        assertThat(orderRepository.sumProcessedQuantitiesByProductIdsSince(
                List.of(product.getId()), Instant.now().minus(Duration.ofHours(24)))).isEmpty();
    }

    private void setCreatedAt(Order order, Instant createdAt) {
        entityManager.createNativeQuery("update orders set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", order.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private static Product product(String sku, int stock) {
        return new Product(sku, sku, ProductCategory.HOME, new BigDecimal("10.00"),
                stock, 5, 1);
    }
}
