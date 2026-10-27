package com.stockpulse.event;

import java.util.UUID;

public record InventoryChangedEvent(UUID productId) { }
