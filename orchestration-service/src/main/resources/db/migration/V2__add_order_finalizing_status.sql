ALTER TABLE product_order
    DROP CONSTRAINT IF EXISTS product_order_status_check;

ALTER TABLE product_order
    DROP CONSTRAINT IF EXISTS ck_orchestrator_product_order_status;

ALTER TABLE product_order
    ADD CONSTRAINT ck_orchestrator_product_order_status
    CHECK (status IN ('IN_PROGRESS', 'FINALIZING', 'COMPLETED', 'FAILED'));
