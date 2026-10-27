CREATE TABLE orders (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    remaining_stock INTEGER NOT NULL CHECK (remaining_stock >= 0),
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_orders_product_id ON orders(product_id);

CREATE TABLE inventory_snapshots (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    stock_level INTEGER NOT NULL CHECK (stock_level >= 0),
    reason VARCHAR(40) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_inventory_snapshots_product_captured
    ON inventory_snapshots(product_id, captured_at DESC);
