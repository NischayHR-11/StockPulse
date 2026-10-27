package com.stockpulse.service;

import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.exception.ConflictException;
import com.stockpulse.dto.request.CreateProductRequest;
import com.stockpulse.dto.response.ProductResponse;
import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.ProductStatus;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.stockpulse.event.InventoryChangedEvent;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
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
        return products.stream().map(ProductMapper::toResponse).toList();
    }

    public ProductResponse findById(UUID id) {
        return ProductMapper.toResponse(getProduct(id));
    }

    @Transactional
    public ProductResponse updateStock(UUID id, int stockLevel) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.updateStock(stockLevel);
        eventPublisher.publishEvent(new InventoryChangedEvent(id));
        return ProductMapper.toResponse(product);
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

}
