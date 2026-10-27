CREATE UNIQUE INDEX uk_products_sku_lower
    ON products (LOWER(sku));
