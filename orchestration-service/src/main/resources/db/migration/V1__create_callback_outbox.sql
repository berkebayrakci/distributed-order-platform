CREATE TABLE callback_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    operation_id BIGINT NOT NULL,
    callback_url VARCHAR(2048) NOT NULL,
    http_method VARCHAR(16) NOT NULL DEFAULT 'POST',
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_callback_outbox_event_id UNIQUE (event_id),
    CONSTRAINT uq_callback_outbox_operation UNIQUE (operation_type, operation_id),
    CONSTRAINT ck_callback_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY_PENDING', 'DELIVERED', 'DEAD')
    ),
    CONSTRAINT ck_callback_outbox_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    )
);

CREATE INDEX idx_callback_outbox_due
    ON callback_outbox (status, next_attempt_at);
