CREATE TABLE processed_event (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result_json TEXT,
    CONSTRAINT uq_subscriber_processed_event UNIQUE (consumer_name, event_id)
);

CREATE INDEX idx_subscriber_processed_event_correlation ON processed_event (correlation_id);
