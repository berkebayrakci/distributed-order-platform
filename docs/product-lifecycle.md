# Subscriber Product Lifecycle

Subscriber/Charging product instances retain their rows throughout their lifecycle. Normal business processing never
physically deletes product history.

## Statuses and transitions

```text
PENDING -> ACTIVE -> SUSPENDED -> ACTIVE
PENDING -> CANCELLED
PENDING -> FAILED
ACTIVE|SUSPENDED -> EXPIRED
ACTIVE -> TERMINATED
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

Automatic expiry scheduling and administrative removal are intentionally not part of this phase.

## Normal add-on removal

Normal removal follows CRM -> Orchestration -> Charging -> Orchestration -> CRM callback. A `REMOVE` order identifies
one add-on product instance and includes a mandatory reason. Charging transitions only `ACTIVE` instances to
`TERMINATED`, records the termination order, UTC timestamp, and reason, and retains the product row.

A replay of the same removal order is successful without applying the transition again. A different order targeting an
already terminated instance fails deterministically. Normal removal does not support tariffs, campaigns, suspended
instances, or administrative forced termination.
