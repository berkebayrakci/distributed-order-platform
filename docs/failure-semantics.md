# Orchestration Failure Semantics

The orchestration Camel routes classify failures instead of applying one catch-all policy.

## Business failures

Deterministic rejections, such as a missing Catalog product mapping, are not retried. The related operation is moved to `FAILED` and the CRM callback is written to the durable callback outbox.

## Validation and protocol failures

Invalid synchronous requests fail their related operation without retry. Malformed or unsupported Subscriber result messages are copied to the existing result DLQ (`subscriber.product.result.dlq` or `subscriber.customer.result.dlq`) and the original message is acknowledged only after that DLQ publish succeeds.

Examples include missing operation IDs, incomplete result items, duplicate item references, unsupported product types, invalid JSON, empty Catalog responses, and Catalog HTTP 4xx responses.

## Transient infrastructure failures

Catalog connectivity/HTTP 5xx failures, recoverable or transient database failures, transaction acquisition/timeouts, and RabbitMQ connectivity/I/O/timeouts use Camel redelivery. Integrity violations, conversion bugs, and other non-transient exceptions are deliberately excluded from this policy.

- Maximum redeliveries: `3` (`4` total attempts)
- Initial delay: `250 ms`
- Backoff multiplier: `2`
- Maximum delay: `2 seconds`

After retries are exhausted, the exception remains unhandled. For RabbitMQ result consumers, the broker's configured dead-letter routing makes the failed message operationally visible rather than acknowledging it as successful.

The RabbitMQ consumer retry layer is explicitly limited to its initial delivery (`maximumRetryAttempts=1`) so it does not multiply Camel's retry count. Exhausted messages are rejected without requeue and therefore follow the queue's dead-letter configuration.

## Unexpected software defects

There is no `onException(Exception.class)` or `onException(RuntimeException.class)` handler. Programming defects such as null dereferences are not converted into business failures and remain visible to Camel and the caller or consumer container.

The system provides bounded at-least-once processing; it does not claim exactly-once execution.
