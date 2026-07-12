ALTER TABLE product_order ADD COLUMN action VARCHAR(20) NOT NULL DEFAULT 'ADD';
ALTER TABLE product_order ADD COLUMN product_instance_id BIGINT;
ALTER TABLE product_order ADD COLUMN termination_reason VARCHAR(255);

ALTER TABLE product_order ADD CONSTRAINT ck_orchestrator_product_order_action
    CHECK (action IN ('ADD', 'REMOVE'));
ALTER TABLE product_order ADD CONSTRAINT ck_orchestrator_product_order_action_shape
    CHECK (
        (action = 'ADD' AND product_instance_id IS NULL AND termination_reason IS NULL)
        OR
        (action = 'REMOVE' AND product_instance_id > 0
            AND termination_reason IS NOT NULL AND BTRIM(termination_reason) <> '')
    );
