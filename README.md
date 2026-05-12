# Distributed Order Platform

Enterprise-style distributed order orchestration platform built with Spring Boot, RabbitMQ, PostgreSQL, and event-driven microservice architecture.

This project simulates a telecom-style middleware/order management environment where customer and product operations are processed asynchronously across multiple services.

---

# Architecture

```text
CRM
  ↓
Orchestrator Service
  ↓
RabbitMQ
  ↓
Subscriber Service
  ↓
Catalog Service
```

The platform demonstrates:

- Distributed order orchestration
- Event-driven communication
- Runtime product ownership tracking
- Product code translation
- Async callbacks
- Trace/correlation logging
- Enterprise-style operational flows

---

# Services

## CRM Service

Responsible for:
- receiving customer requests
- receiving product order requests
- exposing operation status APIs
- callback handling

Default port:

```text
8081
```

---

## Orchestrator Service

Acts as middleware / orchestration layer.

Responsible for:
- operation lifecycle management
- routing requests
- RabbitMQ messaging
- distributed logging
- trace event generation
- failure handling
- force-complete operations

Default port:

```text
8084
```

---

## Subscriber Service

Represents downstream provisioning/subscriber domain.

Responsible for:
- customer creation
- subscriber item creation
- target item reference generation
- runtime ownership state

Default port:

```text
8083
```

---

## Catalog Service

Responsible for:
- product code translation
- runtime ownership mapping
- universal product key generation

Default port:

```text
8082
```

---

# Database Design

## Static Translation Table

```text
catalog.product_code_mapping
```

Used for:

```text
source_product_code → target_product_code
```

Example:

| source_product_code | target_product_code |
|---|---|
| 1893 | 100074239 |
| 41001 | 90041001 |

---

## Runtime Ownership Mapping

```text
catalog.order_product_instance_mapping
```

Tracks customer-owned runtime items.

Columns:

| column |
|---|
| universal_product_key |
| source_product_code |
| target_product_code |
| product_type |
| source_item_ref |
| target_item_ref |
| created_at |

`universal_product_key` is generated once per successful product order and shared across all items within the same order.

---

# Technologies

- Java 21
- Spring Boot
- Apache Camel
- RabbitMQ
- PostgreSQL
- Gradle
- Docker
- React
- REST APIs

---

# Features

- Async order processing
- Event-driven architecture
- RabbitMQ queues
- Distributed operation tracking
- Interface logs
- Trace event logs
- Runtime ownership mapping
- Product translation layer
- Force-complete operations
- Failure propagation
- Customer existence validation
- Multi-service architecture

---

# RabbitMQ Queues

```text
subscriber.product.command.queue
subscriber.product.result.queue

subscriber.customer.command.queue
subscriber.customer.result.queue
```

---

# Running the Project

## Start infrastructure

```bash
docker compose up -d
```

---

## Load database

```bash
docker exec -i order-postgres psql -U postgres < database/01_create_database.sql

docker exec -i order-postgres psql -U postgres -d order_system < database/02_schemas_tables.sql

docker exec -i order-postgres psql -U postgres -d order_system < database/03_seed_data.sql
```

---

## Start services

### Catalog Service

```bash
cd catalog-service
gradle clean bootRun
```

### Subscriber Service

```bash
cd subscriber-service
gradle clean bootRun
```

### Orchestrator Service

```bash
cd orchestration-service
gradle clean bootRun
```

### CRM Service

```bash
cd crm-service
gradle clean bootRun
```

---

# Example API Requests

## Create Customer

```bash
curl -X POST http://localhost:8081/api/customers \
-H "Content-Type: application/json" \
-d '{
  "customerId":"CUST9001",
  "firstName":"Berke",
  "lastName":"Bayrakci"
}'
```

---

## Create Product Order

```bash
curl -X POST http://localhost:8081/api/orders \
-H "Content-Type: application/json" \
-d '{
  "customerId":"CUST9001",
  "products":[
    {
      "sourceProductCode":"1893",
      "sourceItemRef":"SRC-9001",
      "productType":"TARIFF"
    },
    {
      "sourceProductCode":"41001",
      "sourceItemRef":"SRC-9002",
      "productType":"ADDON"
    }
  ]
}'
```

---

# Operation Tracking

## Check operation status

```bash
curl http://localhost:8081/api/operations/1111
```

---

## Check orchestrator logs

```bash
curl http://localhost:8084/api/orchestrator/operations/1111/logs
```

---

## Check trace events

```bash
curl http://localhost:8084/api/orchestrator/operations/1111/trace-events
```
# SQL Examples

## View CRM Product Orders

```sql
SELECT *
FROM crm.product_order
ORDER BY order_id DESC;
```

---

## View CRM Product Order Items

```sql
SELECT *
FROM crm.product_order_item
ORDER BY id DESC;
```

---

## View CRM Customer Requests

```sql
SELECT *
FROM crm.customer_request
ORDER BY id DESC;
```

---

## View Product Translation Table

```sql
SELECT *
FROM catalog.product_code_mapping
ORDER BY source_product_code;
```

---

## View Runtime Product Ownership Mapping

```sql
SELECT *
FROM catalog.order_product_instance_mapping
ORDER BY created_at DESC;
```

---

## View Subscriber Customers

```sql
SELECT *
FROM subscriber.customer
ORDER BY created_at DESC;
```

---

## View Subscriber Runtime Products

```sql
SELECT *
FROM subscriber.customer_product
ORDER BY id DESC;
```

---

## View Orchestrator Product Orders

```sql
SELECT *
FROM orchestrator.product_order
ORDER BY order_id DESC;
```

---

## View Operation Trace Events

```sql
SELECT *
FROM orchestrator.operation_trace_event
ORDER BY id DESC;
```

---

## View Interface Logs

```sql
SELECT *
FROM orchestrator.interface_log
ORDER BY id DESC;
```

---

# Order-Based Queries

## View One Product Order

```sql
SELECT *
FROM crm.product_order
WHERE order_id = 1111;
```

---

## View Product Order Items

```sql
SELECT *
FROM crm.product_order_item
WHERE order_id = 1111
ORDER BY id;
```

---

## View Orchestrator Logs For One Operation

```sql
SELECT *
FROM orchestrator.interface_log
WHERE operation_id = 1111
ORDER BY step_no;
```

---

## View Trace Events For One Operation

```sql
SELECT *
FROM orchestrator.operation_trace_event
WHERE operation_id = 1111
ORDER BY step_no;
```

---

## View Runtime Product Mapping For One Order

```sql
SELECT *
FROM catalog.order_product_instance_mapping
WHERE source_item_ref IN (
    SELECT source_item_ref
    FROM crm.product_order_item
    WHERE order_id = 1111
)
ORDER BY created_at;
```

---

## View Subscriber Runtime Products For One Order

```sql
SELECT sp.*
FROM subscriber.customer_product sp
JOIN catalog.order_product_instance_mapping cm
  ON cm.target_item_ref = sp.target_item_ref
WHERE cm.source_item_ref IN (
    SELECT source_item_ref
    FROM crm.product_order_item
    WHERE order_id = 1111
)
ORDER BY sp.id;
```

---

# Full End-To-End Tracking Query

```sql
SELECT
    po.order_id,
    po.customer_id,
    po.status AS orchestrator_status,

    crm_po.status AS crm_status,

    ote.step_no,
    ote.trace_event_id,
    ote.description,

    il.interface_name,
    il.direction,
    il.status AS interface_status,
    il.request_payload,
    il.response_payload,
    il.error_message,
    il.created_at AS interface_created_at,

    poi.source_product_code,
    pcm.target_product_code AS translated_target_product_code,
    poi.product_type,
    poi.source_item_ref,

    cm.universal_product_key,
    cm.target_item_ref,

    sp.active AS subscriber_item_active,
    sp.created_at AS subscriber_item_created_at

FROM orchestrator.product_order po

LEFT JOIN crm.product_order crm_po
    ON crm_po.order_id = po.order_id

LEFT JOIN crm.product_order_item poi
    ON poi.order_id = po.order_id

LEFT JOIN catalog.product_code_mapping pcm
    ON pcm.source_product_code = poi.source_product_code

LEFT JOIN catalog.order_product_instance_mapping cm
    ON cm.source_item_ref = poi.source_item_ref

LEFT JOIN subscriber.customer_product sp
    ON sp.target_item_ref = cm.target_item_ref

LEFT JOIN orchestrator.operation_trace_event ote
    ON ote.operation_id = po.order_id

LEFT JOIN orchestrator.interface_log il
    ON il.operation_id = po.order_id
   AND il.trace_event_id = ote.trace_event_id

WHERE po.order_id = 1111

ORDER BY ote.step_no, poi.id;
```
---

# Future Improvements

- Retry policies
- Dead-letter queues
- OpenTelemetry tracing
- Prometheus/Grafana monitoring
- Kubernetes deployment
- CI/CD pipeline
- Flyway migrations
- Distributed transaction patterns
- Idempotency handling
- Swagger/OpenAPI documentation
- Integration tests
- Authentication/authorization

---

# Project Goal

The goal of this project is to simulate realistic enterprise middleware patterns used in distributed order management and provisioning systems. It focuses on orchestration, async communication, operational traceability, and runtime ownership tracking rather than simple CRUD operations.
