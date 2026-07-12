# Catalog Product Validity

Catalog owns product-level validity configuration. The product lookup API returns the product type, configuration version, validity policy, renewable/stackable flags, and primary-tariff requirement together with the source-to-target code mapping.

Seeded examples are database configuration:

- Tariffs `1823` and `1893`: `FIXED_DURATION`, `1 YEARS`.
- Add-ons `41001` and `50001`: `FIXED_DURATION`, `3 MONTHS`.

These values are not universal Java rules. A later migration may configure different products with different durations or `NON_EXPIRING` validity without changing application code.

`FIXED_DURATION` requires a positive amount and a unit (`DAYS`, `MONTHS`, or `YEARS`). `NON_EXPIRING` requires both amount and unit to be null.

This phase does not calculate activation/expiry timestamps and does not expire or remove product instances. Charging remains responsible for applying the configured policy when lifecycle processing is implemented.
