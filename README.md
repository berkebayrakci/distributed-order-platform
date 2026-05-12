# Generic Multi-Channel Order System

A sanitized telecom-style multi-channel order demo.

## Services

- `crm-service` - CRM-facing API and UI state
- `orchestration-service` - Camel-based orchestrator
- `catalog-service` - product code/catalog mapping
- `subscriber-service` - subscriber/customer and product ownership
- `crm-ui` - React/Vite UI
- PostgreSQL + RabbitMQ via Docker Compose

## Start infrastructure

```bash
docker compose up -d
```

Create DB manually once:

```bash
docker exec -it order-postgres psql -U postgres
CREATE DATABASE order_system;
\c order_system
```

Then from project root:

```bash
docker exec -i order-postgres psql -U postgres -d order_system < database/02_schemas_tables.sql
docker exec -i order-postgres psql -U postgres -d order_system < database/03_seed_data.sql
```

## Start services

```bash
cd catalog-service && gradle bootRun
cd subscriber-service && gradle bootRun
cd orchestration-service && gradle bootRun
cd crm-service && gradle bootRun
cd crm-ui && npm install && npm run dev
```

Ports:

- CRM: 8081
- Catalog: 8082
- Subscriber: 8083
- Orchestrator: 8084
- RabbitMQ UI: 15672

## Create customer

```bash
curl -X POST http://localhost:8081/api/customers -H "Content-Type: application/json" -d '{"customerId":"CUST9001","firstName":"Demo","lastName":"Customer"}'
```

Check customer request:

```bash
curl http://localhost:8081/api/customers/requests/1111
```

## Create product order

```bash
curl -X POST http://localhost:8081/api/orders -H "Content-Type: application/json" -d '{
  "customerId":"CUST9001",
  "products":[
    {"sourceProductCode":"1893","sourceItemRef":"REF-9001","productType":"TARIFF"},
    {"sourceProductCode":"41001","sourceItemRef":"REF-9002","productType":"ADDON"}
  ]
}'
```

## Useful SQL

```sql
SELECT * FROM crm.customer_request ORDER BY request_id DESC;
SELECT * FROM crm.product_order ORDER BY order_id DESC;
SELECT * FROM subscriber.customer;
SELECT * FROM subscriber.customer_product;
SELECT * FROM catalog.order_product_instance_mapping ORDER BY id DESC;
SELECT operation_id, trace_event_id, step_no, interface_name, direction, status, error_message
FROM orchestrator.interface_log
ORDER BY id DESC;
```
