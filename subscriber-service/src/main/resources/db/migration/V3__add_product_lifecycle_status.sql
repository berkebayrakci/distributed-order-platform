ALTER TABLE customer_product ADD COLUMN status VARCHAR(20);
ALTER TABLE customer_product ADD COLUMN expired_at TIMESTAMPTZ;
ALTER TABLE customer_product ADD COLUMN terminated_at TIMESTAMPTZ;
ALTER TABLE customer_product ADD COLUMN termination_reason VARCHAR(255);
ALTER TABLE customer_product ADD COLUMN activation_order_id BIGINT;
ALTER TABLE customer_product ADD COLUMN termination_order_id BIGINT;
ALTER TABLE customer_product ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE customer_product
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'TERMINATED' END,
    terminated_at = CASE WHEN active THEN NULL ELSE activated_at END,
    termination_reason = CASE WHEN active THEN NULL ELSE 'LEGACY_INACTIVE_MIGRATION' END;

ALTER TABLE customer_product ALTER COLUMN status SET NOT NULL;
ALTER TABLE customer_product ALTER COLUMN activated_at DROP NOT NULL;
ALTER TABLE customer_product DROP CONSTRAINT ck_subscriber_validity_shape;

DROP INDEX IF EXISTS idx_subscriber_customer_product_expiry;
DROP INDEX IF EXISTS ux_subscriber_one_active_tariff;
DROP INDEX IF EXISTS ux_subscriber_one_active_campaign;
ALTER TABLE customer_product DROP COLUMN active;

ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_product_status
    CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED', 'CANCELLED', 'FAILED'));
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_product_activation_state
    CHECK (
        (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED') AND activated_at IS NOT NULL)
        OR
        (status IN ('PENDING', 'CANCELLED', 'FAILED') AND activated_at IS NULL)
    );
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_validity_shape
    CHECK (
        (validity_type = 'FIXED_DURATION' AND validity_amount > 0 AND validity_unit IS NOT NULL)
        OR
        (validity_type = 'NON_EXPIRING' AND validity_amount IS NULL AND validity_unit IS NULL)
    );
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_product_expiry_shape
    CHECK (
        (validity_type = 'FIXED_DURATION'
            AND status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED')
            AND expires_at IS NOT NULL)
        OR
        (validity_type = 'FIXED_DURATION'
            AND status IN ('PENDING', 'CANCELLED', 'FAILED')
            AND expires_at IS NULL)
        OR
        (validity_type = 'NON_EXPIRING' AND expires_at IS NULL)
    );
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_product_expired_state
    CHECK ((status = 'EXPIRED' AND expired_at IS NOT NULL) OR (status <> 'EXPIRED' AND expired_at IS NULL));
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_product_terminated_state
    CHECK (
        (status = 'TERMINATED' AND terminated_at IS NOT NULL
            AND termination_reason IS NOT NULL AND BTRIM(termination_reason) <> '')
        OR
        (status <> 'TERMINATED' AND terminated_at IS NULL AND termination_reason IS NULL AND termination_order_id IS NULL)
    );
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_product_lifecycle_dates
    CHECK (
        (expires_at IS NULL OR activated_at IS NULL OR expires_at >= activated_at)
        AND (expired_at IS NULL OR activated_at IS NULL OR expired_at >= activated_at)
        AND (terminated_at IS NULL OR activated_at IS NULL OR terminated_at >= activated_at)
    );
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_activation_order_id
    CHECK (activation_order_id IS NULL OR activation_order_id > 0);
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_termination_order_id
    CHECK (termination_order_id IS NULL OR termination_order_id > 0);
ALTER TABLE customer_product ADD CONSTRAINT ck_subscriber_lifecycle_version
    CHECK (version >= 0);

CREATE INDEX idx_subscriber_customer_product_lifecycle
    ON customer_product (status, expires_at);
CREATE UNIQUE INDEX ux_subscriber_one_active_tariff
    ON customer_product (customer_id)
    WHERE status = 'ACTIVE' AND product_type = 'TARIFF';
CREATE UNIQUE INDEX ux_subscriber_one_active_campaign
    ON customer_product (customer_id)
    WHERE status = 'ACTIVE' AND product_type = 'CAMPAIGN';
