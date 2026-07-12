# Catalog Product Validity

Catalog owns product-level validity configuration. The product lookup API returns the product type, configuration version, validity policy, renewable/stackable flags, and primary-tariff requirement together with the source-to-target code mapping.

Seeded examples are database configuration:

- Tariffs `1823` and `1893`: `FIXED_DURATION`, `1 YEARS`.
- Add-ons `41001` and `50001`: `FIXED_DURATION`, `3 MONTHS`.

These values are not universal Java rules. A later migration may configure different products with different durations or `NON_EXPIRING` validity without changing application code.

`FIXED_DURATION` requires a positive amount and a unit (`DAYS`, `MONTHS`, or `YEARS`). `NON_EXPIRING` requires both amount and unit to be null.

Orchestration copies the resolved product version and validity fields into the product command. Charging applies that snapshot once during activation, stores `activatedAt` and `expiresAt` as UTC instants, and retains the exact version and validity values used. Calendar units use calendar arithmetic (`plusYears`, `plusMonths`, and `plusDays`), including leap-day and month-end adjustment.

Existing product instances are never recalculated from later Catalog values. This phase still does not schedule expiry, transition expired products, or remove product instances.
