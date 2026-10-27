package com.stockpulse.controller;

import com.stockpulse.dto.response.InventorySnapshotResponse;
import com.stockpulse.service.InventorySnapshotService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/inventory-snapshots")
public class InventorySnapshotController {
    private final InventorySnapshotService snapshotService;

    public InventorySnapshotController(InventorySnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping
    public List<InventorySnapshotResponse> findByProduct(@PathVariable UUID productId) {
        return snapshotService.findByProduct(productId);
    }
}
