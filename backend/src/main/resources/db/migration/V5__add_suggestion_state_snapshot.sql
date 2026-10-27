ALTER TABLE suggestions
    ADD COLUMN stock_level_at_creation INTEGER;

UPDATE suggestions s
SET stock_level_at_creation = p.stock_level
FROM products p
WHERE s.product_id = p.id;

ALTER TABLE suggestions
    ADD CONSTRAINT ck_suggestions_stock_level_at_creation
    CHECK (stock_level_at_creation IS NULL OR stock_level_at_creation >= 0);
