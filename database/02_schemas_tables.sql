CREATE SCHEMA IF NOT EXISTS crm;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS subscriber;
CREATE SCHEMA IF NOT EXISTS orchestrator;

CREATE SEQUENCE IF NOT EXISTS orchestrator.operation_id_seq START WITH 1111 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS orchestrator.trace_step_seq START WITH 11111 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS catalog.universal_product_key_seq START WITH 44444442132132 INCREMENT BY 1;

CREATE TABLE crm.product_order (
    order_id BIGINT PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE crm.product_order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES crm.product_order(order_id),
    source_product_code VARCHAR(50) NOT NULL,
    source_item_ref VARCHAR(100) NOT NULL,
    product_type VARCHAR(30) NOT NULL CHECK (product_type IN ('TARIFF','CAMPAIGN','ADDON'))
);
CREATE INDEX idx_crm_order_item_order_id ON crm.product_order_item(order_id);
CREATE UNIQUE INDEX ux_crm_order_item_source_ref ON crm.product_order_item(order_id, source_item_ref);

CREATE TABLE crm.customer_request (
    request_id BIGINT PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    status VARCHAR(30) NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_crm_customer_request_customer ON crm.customer_request(customer_id);

-- Static translation only: source code -> target code.
CREATE TABLE catalog.product_code_mapping (
    source_product_code VARCHAR(50) PRIMARY KEY,
    target_product_code VARCHAR(50) NOT NULL UNIQUE
);

-- Runtime mapping. One universal_product_key is generated per successful product order
-- and reused for every item row inserted for that order.
CREATE TABLE catalog.order_product_instance_mapping (
    universal_product_key BIGINT NOT NULL,
    source_product_code VARCHAR(50) NOT NULL,
    target_product_code VARCHAR(50) NOT NULL,
    product_type VARCHAR(30) NOT NULL CHECK (product_type IN ('TARIFF','CAMPAIGN','ADDON')),
    source_item_ref VARCHAR(100) NOT NULL,
    target_item_ref VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (universal_product_key, source_item_ref)
);
CREATE INDEX idx_catalog_runtime_universal_key ON catalog.order_product_instance_mapping(universal_product_key);
CREATE INDEX idx_catalog_runtime_source_ref ON catalog.order_product_instance_mapping(source_item_ref);
CREATE INDEX idx_catalog_runtime_target_ref ON catalog.order_product_instance_mapping(target_item_ref);

CREATE TABLE subscriber.customer (
    customer_id VARCHAR(50) PRIMARY KEY,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE subscriber.customer_product (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL REFERENCES subscriber.customer(customer_id),
    target_product_code VARCHAR(50) NOT NULL,
    target_item_ref VARCHAR(100) NOT NULL UNIQUE,
    product_type VARCHAR(30) NOT NULL CHECK (product_type IN ('TARIFF','CAMPAIGN','ADDON')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriber_customer_product_customer ON subscriber.customer_product(customer_id);
CREATE UNIQUE INDEX ux_subscriber_one_active_tariff ON subscriber.customer_product(customer_id) WHERE active = true AND product_type = 'TARIFF';
CREATE UNIQUE INDEX ux_subscriber_one_active_campaign ON subscriber.customer_product(customer_id) WHERE active = true AND product_type = 'CAMPAIGN';

CREATE TABLE orchestrator.product_order (
    order_id BIGINT PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    crm_callback_url TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
    error_message TEXT,
    universal_product_key BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_orchestrator_product_order_status ON orchestrator.product_order(status);
CREATE INDEX idx_orchestrator_product_order_universal_key ON orchestrator.product_order(universal_product_key);

CREATE TABLE orchestrator.customer_request (
    request_id BIGINT PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    crm_callback_url TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_orchestrator_customer_request_status ON orchestrator.customer_request(status);

CREATE TABLE orchestrator.interface_log (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    trace_event_id VARCHAR(50) NOT NULL,
    step_no INT NOT NULL,
    interface_name VARCHAR(100) NOT NULL,
    direction VARCHAR(20) NOT NULL CHECK (direction IN ('START','END')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    request_payload TEXT,
    response_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_interface_log_operation_id ON orchestrator.interface_log(operation_id);
CREATE INDEX idx_interface_log_trace_event_id ON orchestrator.interface_log(trace_event_id);

CREATE TABLE orchestrator.operation_trace_event (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    trace_event_id VARCHAR(50) NOT NULL,
    step_no INT NOT NULL,
    description VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(trace_event_id)
);
CREATE INDEX idx_operation_trace_event_operation_id ON orchestrator.operation_trace_event(operation_id);
