# RabbitMQ Message Contracts

All Subscriber command and result messages use envelope version `1`:

```json
{
  "eventId": "4a393c7b-fc5c-46eb-a27f-b9a239213c01",
  "eventType": "ProductCommand",
  "eventVersion": 1,
  "correlationId": "1072257a-cfde-41f1-9614-7600d63f70b8",
  "causationId": "93ef4094-73b5-4319-a772-0a65026b1918",
  "producer": "orchestration-service",
  "occurredAt": "2026-07-13T00:00:00Z",
  "payload": {}
}
```

Supported event types are `ProductCommand`, `CustomerCommand`, `ProductResult`, and `CustomerResult`. Consumers reject missing fields, unexpected event types, and versions other than `1`; RabbitMQ retry and DLQ rules then apply.

`correlationId` remains stable for the complete HTTP, RabbitMQ, trace-log, and CRM callback flow. Every new message receives a new `eventId`; its `causationId` points to the HTTP request event or RabbitMQ command that directly caused it.

Subscriber command consumers transactionally store `(consumer_name, event_id)` together with their database effects and serialized result envelope. Duplicate commands replay that stored result without repeating provisioning. Orchestration result consumers record the result event in the same transaction as terminal state and callback outbox creation.

Delivery remains at-least-once with idempotent consumers; the project does not claim exactly-once delivery.
