ALTER TABLE product_order ADD COLUMN correlation_id UUID;
UPDATE product_order SET correlation_id = gen_random_uuid() WHERE correlation_id IS NULL;
ALTER TABLE product_order ALTER COLUMN correlation_id SET NOT NULL;

ALTER TABLE customer_request ADD COLUMN correlation_id UUID;
UPDATE customer_request SET correlation_id = gen_random_uuid() WHERE correlation_id IS NULL;
ALTER TABLE customer_request ALTER COLUMN correlation_id SET NOT NULL;

ALTER TABLE interface_log ADD COLUMN correlation_id UUID;
ALTER TABLE operation_trace_event ADD COLUMN correlation_id UUID;

CREATE TABLE processed_event (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result_json TEXT,
    CONSTRAINT uq_orchestrator_processed_event UNIQUE (consumer_name, event_id)
);

CREATE INDEX idx_orchestrator_processed_event_correlation
    ON processed_event (correlation_id);
