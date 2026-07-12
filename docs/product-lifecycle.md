# Subscriber Product Lifecycle

Subscriber/Charging product instances retain their rows throughout their lifecycle. Normal business processing never
physically deletes product history.

## Statuses and transitions

```text
PENDING -> ACTIVE -> SUSPENDED -> ACTIVE
PENDING -> CANCELLED
PENDING -> FAILED
ACTIVE|SUSPENDED -> EXPIRED
ACTIVE|SUSPENDED -> TERMINATED
```

`EXPIRED`, `TERMINATED`, `CANCELLED`, and `FAILED` are terminal. Invalid transitions fail before changing any lifecycle
or audit fields.

Activation records the activation order and UTC activation/expiry instants. Termination requires a positive termination
order ID, UTC termination instant, and non-blank reason. Expiry and termination retain the original activation details.
JPA optimistic locking prevents concurrent updates from silently overwriting each other.

## Legacy migration

Migration `V3__add_product_lifecycle_status.sql` maps legacy active rows to `ACTIVE`. Legacy inactive rows become
`TERMINATED`, use their last known activation timestamp as the safest available termination timestamp, and carry the
explicit reason `LEGACY_INACTIVE_MIGRATION`. Historical order IDs remain null when the old schema did not retain them.
The obsolete `active` column is removed so status is the only lifecycle source of truth.

Automatic expiry scheduling and product removal workflows are intentionally not part of this phase.
