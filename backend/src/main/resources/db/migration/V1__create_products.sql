CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(30) NOT NULL,
    current_price NUMERIC(12, 2) NOT NULL CHECK (current_price > 0),
    stock_level INTEGER NOT NULL CHECK (stock_level >= 0),
    reorder_threshold INTEGER NOT NULL CHECK (reorder_threshold >= 0),
    demand_velocity INTEGER NOT NULL CHECK (demand_velocity >= 0),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku)
);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_category ON products(category);
