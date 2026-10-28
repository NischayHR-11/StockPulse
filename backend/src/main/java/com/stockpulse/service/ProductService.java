package com.stockpulse.service;

import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.exception.ConflictException;
import com.stockpulse.dto.request.CreateProductRequest;
import com.stockpulse.dto.response.ProductResponse;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.ProductStatus;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.repository.OrderRepository;
import com.stockpulse.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.stockpulse.event.InventoryChangedEvent;
import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(ProductRepository productRepository, OrderRepository orderRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku().trim())) {
            throw new ConflictException("A product with this SKU already exists.");
        }
        Product product = new Product(request.sku().trim(), request.name().trim(), request.category(),
                request.currentPrice(), request.stockLevel(), request.reorderThreshold(), request.demandVelocity());
        return ProductMapper.toResponse(productRepository.save(product));
    }

    public List<ProductResponse> findAll(ProductStatus status, ProductCategory category) {
        List<Product> products;
        if (status != null && category != null) products = productRepository.findAllByStatusAndCategory(status, category);
        else if (status != null) products = productRepository.findAllByStatus(status);
        else if (category != null) products = productRepository.findAllByCategory(category);
        else products = productRepository.findAll();
        Map<UUID, Long> unitsSoldByProduct = unitsSoldLast24Hours(products);
        return products.stream()
                .map(product -> ProductMapper.toResponse(product,
                        unitsSoldByProduct.getOrDefault(product.getId(), 0L)))
                .toList();
    }

    public ProductResponse findById(UUID id) {
        Product product = getProduct(id);
        return ProductMapper.toResponse(product,
                unitsSoldLast24Hours(List.of(product)).getOrDefault(id, 0L));
    }

    @Transactional
    public ProductResponse updateStock(UUID id, int stockLevel) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.updateStock(stockLevel);
        eventPublisher.publishEvent(new InventoryChangedEvent(id));
        return ProductMapper.toResponse(product,
                unitsSoldLast24Hours(List.of(product)).getOrDefault(id, 0L));
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Map<UUID, Long> unitsSoldLast24Hours(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        Instant since = Instant.now().minus(java.time.Duration.ofHours(24));
        return orderRepository.sumProcessedQuantitiesByProductIdsSince(
                        products.stream().map(Product::getId).toList(), since)
                .stream()
                .collect(Collectors.toMap(OrderRepository.SalesAggregate::getProductId,
                        aggregate -> aggregate.getUnitsSold() == null ? 0L : aggregate.getUnitsSold(),
                        Long::sum));
    }

}
