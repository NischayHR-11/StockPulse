package com.stockpulse.service;

import com.stockpulse.exception.ResourceNotFoundException;
import com.stockpulse.dto.response.InventorySnapshotResponse;
import com.stockpulse.repository.InventorySnapshotRepository;
import com.stockpulse.repository.ProductRepository;
import com.stockpulse.mapper.InventorySnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventorySnapshotService {
    private final ProductRepository productRepository;
    private final InventorySnapshotRepository snapshotRepository;

    public InventorySnapshotService(ProductRepository productRepository,
                                    InventorySnapshotRepository snapshotRepository) {
        this.productRepository = productRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public List<InventorySnapshotResponse> findByProduct(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        return snapshotRepository.findAllByProductIdOrderByCapturedAtDesc(productId)
                .stream().map(InventorySnapshotMapper::toResponse).toList();
    }

}
