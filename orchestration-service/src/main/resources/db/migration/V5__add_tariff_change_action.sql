ALTER TABLE product_order ADD COLUMN existing_product_instance_id BIGINT;
ALTER TABLE product_order ADD COLUMN new_product_code VARCHAR(50);

ALTER TABLE product_order DROP CONSTRAINT ck_orchestrator_product_order_action;
ALTER TABLE product_order DROP CONSTRAINT ck_orchestrator_product_order_action_shape;

ALTER TABLE product_order ADD CONSTRAINT ck_orchestrator_product_order_action
    CHECK (action IN ('ADD', 'REMOVE', 'CHANGE'));
ALTER TABLE product_order ADD CONSTRAINT ck_orchestrator_product_order_action_shape
    CHECK (
        (action = 'ADD' AND product_instance_id IS NULL
            AND existing_product_instance_id IS NULL AND new_product_code IS NULL
            AND termination_reason IS NULL)
        OR
        (action = 'REMOVE' AND product_instance_id > 0
            AND existing_product_instance_id IS NULL AND new_product_code IS NULL
            AND termination_reason IS NOT NULL AND BTRIM(termination_reason) <> '')
        OR
        (action = 'CHANGE' AND product_instance_id IS NULL
            AND existing_product_instance_id > 0
            AND new_product_code IS NOT NULL AND BTRIM(new_product_code) <> ''
            AND termination_reason IS NOT NULL AND BTRIM(termination_reason) <> '')
    );
