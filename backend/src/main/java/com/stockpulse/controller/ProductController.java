package com.stockpulse.controller;

import com.stockpulse.dto.request.CreateProductRequest;
import com.stockpulse.dto.response.ProductResponse;
import com.stockpulse.dto.request.UpdateStockRequest;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.ProductStatus;
import com.stockpulse.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    public List<ProductResponse> findAll(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) ProductCategory category) {
        return productService.findAll(status, category);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable UUID id) {
        return productService.findById(id);
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse updateStock(@PathVariable UUID id, @Valid @RequestBody UpdateStockRequest request) {
        return productService.updateStock(id, request.stockLevel());
    }
}
