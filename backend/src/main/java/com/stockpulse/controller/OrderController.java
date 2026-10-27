package com.stockpulse.controller;

import com.stockpulse.dto.request.CreateOrderRequest;
import com.stockpulse.dto.response.OrderResponse;
import com.stockpulse.dto.request.ProductOrderRequest;
import com.stockpulse.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse process(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.process(request);
    }

    @PostMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse processForProduct(@PathVariable UUID productId,
                                           @Valid @RequestBody ProductOrderRequest request) {
        return orderService.process(new CreateOrderRequest(productId, request.quantity(), request.idempotencyKey()));
    }
}
