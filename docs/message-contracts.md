# RabbitMQ Message Contracts

Customer messages and product `ADD` messages use envelope version `1`. Product `REMOVE` commands and results use
version `2` because their payload adds `action`, `productInstanceId`, and `reason`:

```json
{
  "eventId": "4a393c7b-fc5c-46eb-a27f-b9a239213c01",
  "eventType": "ProductCommand",
  "eventVersion": 2,
  "correlationId": "1072257a-cfde-41f1-9614-7600d63f70b8",
  "causationId": "93ef4094-73b5-4319-a772-0a65026b1918",
  "producer": "orchestration-service",
  "occurredAt": "2026-07-13T00:00:00Z",
  "payload": {}
}
```

Supported event types are `ProductCommand`, `CustomerCommand`, `ProductResult`, and `CustomerResult`. Product consumers
accept versions `1` and `2`; customer consumers accept version `1`. Missing fields, unexpected event types, and other
versions are rejected so RabbitMQ retry and DLQ rules apply. Version `1` product messages without an action are treated
as `ADD` for rolling compatibility.

Each `ProductCommand` item includes the resolved Catalog `productVersion`, `validityType`, `validityAmount`, and `validityUnit`. Charging persists this snapshot so later Catalog changes cannot alter an already activated instance's expiry date.

A version `2` `REMOVE` command carries one `productInstanceId`, no activation items, and a mandatory termination reason.
Its successful result also carries the action and instance ID but no runtime-mapping items. Orchestration therefore
finalizes removal without calling Catalog to create a new runtime mapping.

`correlationId` remains stable for the complete HTTP, RabbitMQ, trace-log, and CRM callback flow. Every new message receives a new `eventId`; its `causationId` points to the HTTP request event or RabbitMQ command that directly caused it.

Subscriber command consumers transactionally store `(consumer_name, event_id)` together with their database effects and serialized result envelope. Duplicate commands replay that stored result without repeating provisioning. Orchestration result consumers record the result event in the same transaction as terminal state and callback outbox creation.

Delivery remains at-least-once with idempotent consumers; the project does not claim exactly-once delivery.
