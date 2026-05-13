# Distributed Order Platform

Enterprise-style distributed order orchestration platform built with Spring Boot, RabbitMQ, PostgreSQL, Apache Camel, Docker and React.

This project simulates real-world enterprise integration architectures where customer orders flow through multiple distributed systems asynchronously.

Instead of a simple CRUD application, the platform focuses on:

- Distributed order orchestration
- Asynchronous messaging
- Runtime ownership mapping
- Event-driven architecture
- Failure handling and retries
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
                          |
                          v
                +------------------+
                |   RabbitMQ       |
                |   Event Queue    |
                +---------+--------+
                          |
                          v
                +------------------+
                |  Orchestrator    |
                | Apache Camel ESB |
                +----+--------+----+
                     |        |
                     |        |
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
- Publishing events to RabbitMQ
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
- Order submission
- Order monitoring
- Service interaction visualization

Tech:
- React
- TypeScript

---

# Key Enterprise Concepts Demonstrated

## Event-Driven Architecture

Services communicate asynchronously using RabbitMQ instead of direct synchronous calls.

Benefits:
- Loose coupling
- Better scalability
- Retry capabilities
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
- TypeScript

## Infrastructure
- Docker
- Docker Compose

---

# Example Flow

```text
1. Customer submits order from CRM UI

2. CRM Service publishes order event

3. RabbitMQ distributes event

4. Orchestrator consumes event

5. Catalog Service maps product definitions

6. Subscriber Service provisions customer

7. Async response generated

8. Final order state updated
```

---

# Running the Project

## Prerequisites

- Java 21
- Docker
- Docker Compose
- Node.js
- Gradle

---

## Start Infrastructure

```bash
docker compose up -d
```

---

## Start Backend Services

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
npm install
npm run dev
```

---

# Future Improvements

Planned enterprise features:

- JWT authentication
- Kubernetes deployment
- Retry queues / DLQ
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
