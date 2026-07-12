CREATE TABLE processed_callback_event (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    operation_id BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_processed_callback_event_event_id UNIQUE (event_id)
);

CREATE INDEX idx_processed_callback_event_operation
    ON processed_callback_event (operation_type, operation_id);
