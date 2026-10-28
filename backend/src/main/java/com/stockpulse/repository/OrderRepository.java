package com.stockpulse.repository;

import com.stockpulse.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    interface SalesAggregate {
        UUID getProductId();
        Long getUnitsSold();
    }

    @Query("""
            select o.product.id as productId, coalesce(sum(o.quantity), 0) as unitsSold
            from Order o
            where o.product.id in :productIds
              and o.createdAt >= :since
              and o.status = com.stockpulse.entity.OrderStatus.PROCESSED
            group by o.product.id
            """)
    List<SalesAggregate> sumProcessedQuantitiesByProductIdsSince(
            @Param("productIds") Collection<UUID> productIds,
            @Param("since") Instant since);
}
