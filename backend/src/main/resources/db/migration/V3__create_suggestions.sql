CREATE TABLE suggestions (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_price NUMERIC(12, 2),
    proposed_price NUMERIC(12, 2),
    recommended_quantity INTEGER CHECK (recommended_quantity IS NULL OR recommended_quantity > 0),
    direction VARCHAR(20),
    confidence NUMERIC(3, 2) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    rationale VARCHAR(500) NOT NULL,
    trigger_reason VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_suggestions_product_created
    ON suggestions(product_id, created_at DESC);
CREATE INDEX idx_suggestions_product_type_status
    ON suggestions(product_id, type, status);
CREATE INDEX idx_suggestions_product_type_trigger_status
    ON suggestions(product_id, type, trigger_reason, status);
