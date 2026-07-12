# PostgreSQL and RabbitMQ Cheatsheet

This guide assumes the project is running from WSL with infrastructure started
by `docker compose up -d` or `./start.sh`.

## Connection details

| Service | Host | Port | Database / vhost | Username | Password |
|---|---|---:|---|---|---|
| PostgreSQL | `127.0.0.1` | `5432` | `order_system` | `postgres` | `postgres` |
| RabbitMQ AMQP | `127.0.0.1` | `5672` | `/` | `order_app` | `order_app_dev` |
| RabbitMQ Management | `http://127.0.0.1:15672` | `15672` | `/` | `order_app` | `order_app_dev` |

These are development credentials. Override them outside local development.

## PostgreSQL

### Connect without installing psql in WSL

```bash
docker exec -it order-postgres psql -U postgres -d order_system
```

### Connect using a locally installed psql client

```bash
psql "postgresql://postgres:postgres@127.0.0.1:5432/order_system"
```

### Useful psql commands

```text
\conninfo                    Show the active connection
\dn                          List schemas
\dt crm.*                    List CRM tables
\dt catalog.*                List Catalog tables
\dt subscriber.*             List Subscriber tables
\dt orchestrator.*           List Orchestrator tables
\d crm.product_order         Describe a table
\x on                        Toggle expanded output
\q                           Exit psql
```

### Useful queries

```sql
SELECT * FROM crm.customer_request ORDER BY created_at DESC;
SELECT * FROM crm.product_order ORDER BY created_at DESC;
SELECT * FROM crm.product_order_item ORDER BY id DESC;

SELECT * FROM catalog.product_code_mapping ORDER BY source_product_code;
SELECT * FROM catalog.order_product_instance_mapping
ORDER BY universal_product_key, source_item_ref;

SELECT * FROM subscriber.customer ORDER BY created_at DESC;
SELECT * FROM subscriber.customer_product ORDER BY created_at DESC;

SELECT * FROM orchestrator.product_order ORDER BY created_at DESC;
SELECT * FROM orchestrator.customer_request ORDER BY created_at DESC;
SELECT * FROM orchestrator.interface_log ORDER BY id DESC;
SELECT * FROM orchestrator.operation_trace_event ORDER BY id DESC;
```

### Inspect one operation across schemas

Replace `1112` with the operation ID returned by the API.

```sql
SELECT * FROM crm.product_order WHERE order_id = 1112;
SELECT * FROM orchestrator.product_order WHERE order_id = 1112;
SELECT * FROM orchestrator.interface_log
WHERE operation_id = 1112 ORDER BY step_no;
SELECT * FROM catalog.order_product_instance_mapping
WHERE universal_product_key = 1112 ORDER BY source_item_ref;
```

### Backup and restore

```bash
docker exec order-postgres pg_dump -U postgres -d order_system -Fc \
  > order_system.dump

cat order_system.dump | docker exec -i order-postgres \
  pg_restore -U postgres -d order_system --clean --if-exists
```

The restore command is destructive to matching database objects. Keep backups.

## RabbitMQ

### Open the management UI

Open <http://127.0.0.1:15672> and sign in with:

```text
Username: order_app
Password: order_app_dev
```

### Check broker status and listeners

```bash
docker exec order-rabbitmq rabbitmq-diagnostics -q ping
docker exec order-rabbitmq rabbitmq-diagnostics listeners
docker exec order-rabbitmq rabbitmq-diagnostics -q check_port_listener 5672
```

### List queues and consumers

```bash
docker exec order-rabbitmq rabbitmqctl list_queues \
  name messages_ready messages_unacknowledged consumers arguments

docker exec order-rabbitmq rabbitmqctl list_consumers
docker exec order-rabbitmq rabbitmqctl list_connections \
  user peer_host peer_port state channels
```

Expected application queues:

```text
subscriber.product.command.queue
subscriber.product.result.queue
subscriber.customer.command.queue
subscriber.customer.result.queue
```

Expected dead-letter queues:

```text
subscriber.product.command.dlq
subscriber.product.result.dlq
subscriber.customer.command.dlq
subscriber.customer.result.dlq
```

### Use the RabbitMQ HTTP API

```bash
curl -u order_app:order_app_dev \
  http://127.0.0.1:15672/api/overview | jq

curl -u order_app:order_app_dev \
  http://127.0.0.1:15672/api/queues/%2F | jq \
  '.[] | {name, messages, messages_ready, messages_unacknowledged, consumers}'
```

### Inspect logs

```bash
docker logs --tail 200 order-rabbitmq
docker logs -f order-rabbitmq
```

### Purge a queue

```bash
docker exec order-rabbitmq rabbitmqctl purge_queue QUEUE_NAME
```

Purging permanently deletes all ready messages in that queue. Do not purge a
command or result queue merely to hide a processing failure; inspect it first.

## Docker and network troubleshooting

```bash
docker compose ps
docker compose logs --tail 200 postgres rabbitmq
nc -vz 127.0.0.1 5432
nc -vz 127.0.0.1 5672
curl -u order_app:order_app_dev http://127.0.0.1:15672/api/health/checks/ready
```

Recreate only RabbitMQ after changing its definitions:

```bash
docker compose up -d --force-recreate rabbitmq
```

Recreate all infrastructure without deleting PostgreSQL data:

```bash
docker compose down
docker compose up -d
```

Delete all PostgreSQL development data and initialize from scratch:

```bash
docker compose down -v
docker compose up -d
```

The `-v` command is destructive. Use it only when the local database can be discarded.
