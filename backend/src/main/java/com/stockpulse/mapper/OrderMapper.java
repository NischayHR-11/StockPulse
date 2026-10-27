package com.stockpulse.mapper;

import com.stockpulse.dto.response.OrderResponse;
import com.stockpulse.entity.Order;

public final class OrderMapper {
    private OrderMapper() { }

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getProduct().getId(), order.getQuantity(),
                order.getStatus(), order.getIdempotencyKey(), order.getCreatedAt(),
                order.getRemainingStock());
    }
}
