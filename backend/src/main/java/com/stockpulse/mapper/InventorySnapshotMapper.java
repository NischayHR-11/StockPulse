package com.stockpulse.mapper;

import com.stockpulse.dto.response.InventorySnapshotResponse;
import com.stockpulse.entity.InventorySnapshot;

public final class InventorySnapshotMapper {
    private InventorySnapshotMapper() { }

    public static InventorySnapshotResponse toResponse(InventorySnapshot snapshot) {
        return new InventorySnapshotResponse(snapshot.getId(), snapshot.getProduct().getId(),
                snapshot.getStockLevel(), snapshot.getReason(), snapshot.getReferenceId(),
                snapshot.getCapturedAt());
    }
}
