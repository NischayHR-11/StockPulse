package com.stockpulse.repository;

import com.stockpulse.entity.InventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, UUID> {
    List<InventorySnapshot> findAllByProductIdOrderByCapturedAtDesc(UUID productId);
}
