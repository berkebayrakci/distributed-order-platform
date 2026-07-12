ALTER TABLE customer_product ADD COLUMN product_version INTEGER;
ALTER TABLE customer_product ADD COLUMN validity_type VARCHAR(30);
ALTER TABLE customer_product ADD COLUMN validity_amount INTEGER;
ALTER TABLE customer_product ADD COLUMN validity_unit VARCHAR(20);
ALTER TABLE customer_product ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE customer_product ADD COLUMN expires_at TIMESTAMPTZ;

UPDATE customer_product
SET product_version = 1,
    validity_type = 'NON_EXPIRING',
    validity_amount = NULL,
    validity_unit = NULL,
    activated_at = created_at AT TIME ZONE 'UTC'
WHERE activated_at IS NULL;

ALTER TABLE customer_product ALTER COLUMN product_version SET NOT NULL;
ALTER TABLE customer_product ALTER COLUMN validity_type SET NOT NULL;
ALTER TABLE customer_product ALTER COLUMN activated_at SET NOT NULL;

ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_product_version
    CHECK (product_version > 0);
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_validity_type
    CHECK (validity_type IN ('FIXED_DURATION', 'NON_EXPIRING'));
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_validity_unit
    CHECK (validity_unit IS NULL OR validity_unit IN ('DAYS', 'MONTHS', 'YEARS'));
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_validity_shape
    CHECK (
        (validity_type = 'FIXED_DURATION' AND validity_amount > 0 AND validity_unit IS NOT NULL AND expires_at IS NOT NULL)
        OR
        (validity_type = 'NON_EXPIRING' AND validity_amount IS NULL AND validity_unit IS NULL AND expires_at IS NULL)
    );

CREATE INDEX idx_subscriber_customer_product_expiry
    ON customer_product (active, expires_at);
