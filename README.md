# Distributed Order Platform

Distributed order orchestration prototype built with Spring Boot, RabbitMQ, PostgreSQL, Apache Camel, Docker and React.

This project simulates real-world enterprise integration architectures where customer orders flow through multiple distributed systems asynchronously.

Instead of a simple CRUD application, the platform focuses on:

- Distributed order orchestration
- Asynchronous messaging
- Runtime ownership mapping
- Event-driven architecture
- Bounded message retries and dead-letter queues
- Service-to-service communication
- Operational tracing and observability

---

# Business Scenario

In large enterprise environments, a customer order rarely stays inside a single system.

For example:

- CRM receives the customer request
- Orchestrator routes the order
- Catalog maps products between systems
- Subscriber service provisions the customer
- Multiple downstream systems process the request asynchronously

This project simulates that architecture.

The system processes distributed customer/product orders while maintaining traceability and orchestration between services.

---

# Architecture

```text
                +------------------+
                |     CRM UI       |
                |      React       |
                +---------+--------+
                          |
                          v
                +------------------+
                |   CRM Service    |
                |  Spring Boot     |
                +---------+--------+
                          | HTTP
                          v
                +------------------+
                |  Orchestrator    |
                | Apache Camel ESB |
                +----+--------+----+
                     |        | RabbitMQ
                     | HTTP   |
          +----------+        +-----------+
          |                               |
          v                               v

+------------------+          +------------------+
| Catalog Service  |          | Subscriber Svc   |
| Product Mapping  |          | Provisioning     |
+------------------+          +------------------+

```

---

# Microservices

## CRM Service

Responsible for:

- Receiving customer orders
- Creating order requests
- Submitting operations to the orchestrator over authenticated internal HTTP
- Providing REST APIs for UI interactions

Tech:
- Spring Boot
- PostgreSQL
- RabbitMQ

---

## Orchestrator Service

Acts as the enterprise orchestration layer.

Responsible for:

- Consuming order events
- Routing flows between services
- Handling orchestration logic
- Managing async communication
- Providing operation tracing

Tech:
- Apache Camel
- Spring Boot
- RabbitMQ

---

## Catalog Service

Responsible for:

- Product translation
- Runtime ownership mapping
- Universal product key generation

The service stores relationships between source and target product definitions across distributed systems.

Example:
- CRM product code
- Downstream system product code
- Shared universal product identity

Tech:
- Spring Boot
- PostgreSQL

---

## Subscriber Service

Responsible for:

- Customer provisioning simulation
- Order completion handling
- Async response generation

Tech:
- Spring Boot
- PostgreSQL

---

## CRM UI

Frontend application for interacting with the platform.

Features:
- Customer creation
- Product-order submission

Tech:
- React
- JavaScript

---

# Key Enterprise Concepts Demonstrated

## Event-Driven Architecture

Subscriber commands and results flow asynchronously through durable RabbitMQ queues. Catalog lookups and CRM-to-orchestrator submission use synchronous HTTP.

Benefits:
- Loose coupling
- Better scalability
- Bounded retry with dead-letter queues
- Failure isolation

---

## Distributed Order Orchestration

The orchestrator coordinates flows between multiple systems similar to telecom ESB platforms.

---

## Runtime Mapping

The platform dynamically stores ownership relationships between distributed product definitions.

This simulates real enterprise integration challenges where multiple systems use different identifiers.

---

## Traceability

Operations can be tracked across services to understand:
- Request flow
- Service interactions
- Processing stages
- Failures and retries

---

## Failure Isolation

A downstream service failure does not immediately break the entire platform.

The architecture is designed around asynchronous processing and decoupled communication.

---

# Technologies

## Backend
- Java 21
- Spring Boot
- Apache Camel
- RabbitMQ
- PostgreSQL
- Gradle

## Frontend
- React
- JavaScript

## Infrastructure
- Docker
- Docker Compose

---

# Example Flow

```text
1. Customer submits order from CRM UI

2. CRM Service submits the operation to the orchestrator over HTTP

3. Orchestrator validates product mappings through Catalog

4. Orchestrator publishes a durable Subscriber command through RabbitMQ

5. Subscriber provisions the products idempotently

6. Subscriber publishes the result through RabbitMQ

7. Orchestrator stores the runtime mapping and final state

8. Orchestrator atomically stores the terminal order and a durable callback outbox record

9. The callback worker delivers the authenticated CRM callback with a stable event ID and bounded retries

10. CRM transactionally records the event ID and applies the state transition; duplicate callbacks return success
```

---

# Running the Project

## Prerequisites

- Java 21
- Docker
- Docker Compose
- Node.js
- The repository's Gradle wrapper

---

## Start Infrastructure

```bash
docker compose up -d
```

On first start, PostgreSQL automatically creates the `order_system` schemas, tables and seed mappings. Existing `pgdata` volumes are not reinitialized; use a migration tool before changing schemas in a persistent environment.

---

## Start Backend Services

### Start everything from WSL

```bash
./start.sh
```

The script starts PostgreSQL and RabbitMQ, launches all four Spring services,
waits for their health endpoints, and follows their logs in one terminal.
Press `Ctrl+C` to stop the application processes. Infrastructure remains
running by default; use `./start.sh --stop-infra-on-exit` to stop it as well.

To include the UI after running `npm ci` in `crm-ui`:

```bash
./start.sh --with-ui
```

### Start services individually

Example:

```bash
cd crm-service
./gradlew bootRun
```

Repeat for:
- orchestrator-service
- catalog-service
- subscriber-service

---

## Start Frontend

```bash
cd crm-ui
npm ci
npm run dev
```

## Configuration

Service URLs, database credentials, RabbitMQ credentials, the allowed UI origin, and the internal API key can be overridden with environment variables. Local defaults are provided for development. Set a strong shared `INTERNAL_API_KEY` outside local development.

## API and Infrastructure Tools

- Import `postman/distributed-order-platform.postman_collection.json` into Postman for the customer, order, observability, catalog, and health workflows.
- See `docs/operations-cheatsheet.md` for PostgreSQL, RabbitMQ Management UI, CLI, HTTP API, backup, and troubleshooting commands.

---

# Future Improvements

Planned enterprise features:

- JWT authentication
- Kubernetes deployment
- Inbox deduplication for asynchronous consumers
- Prometheus + Grafana monitoring
- Distributed tracing
- Circuit breakers
- CI/CD pipelines
- OpenShift deployment
- Role-based authorization
- Saga orchestration patterns

---

# Why This Project Exists

This project was built to simulate real enterprise middleware and distributed system environments instead of basic CRUD applications.

It focuses on operational thinking, asynchronous architecture, orchestration and enterprise integration patterns commonly used in large-scale backend systems.

---

# Author

Berke Bayrakçı
