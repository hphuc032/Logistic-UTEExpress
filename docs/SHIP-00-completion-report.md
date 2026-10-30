# SHIP-00 — Shipping providers, rates and quotes

Owner: Quốc Đạt. Reviewer: Tiến Đạt.
Branch: `feature/ship-00-rates`.

## Delivered behavior

- Admin and Manager configure shipping providers and destination/service rates
  through their separate `/admin` and `/manager` routes.
- Provider codes and route keys are immutable after creation. Updates require
  the latest optimistic-lock version.
- Rates are nonnegative whole VND. A route is unique by provider, service code
  and destination region.
- Disabling a provider blocks every associated quote. Disabling a rate blocks
  that route. Historical rows remain intact; no hard-delete endpoint exists.
- `ShippingQuoteService` now reads the active database rate and returns its
  version. Missing, inactive or malformed routes fail; there is no zero-fee
  fallback.
- Mutations resolve the authenticated account ID server-side and append audit
  entries in the same transaction.
- Synthetic seed data requires both the `demo` profile and explicit
  `uteexpress.demo.shipping.enabled=true`; it is excluded from `prod`.
  Reruns preserve edits and do not duplicate rows or audit entries.

## Verification

- Java 21 `clean test`: PASS.
- Java 21 `-Ppostgres-it verify`: PASS.
- PostgreSQL coverage includes database quotes, inactive provider/rate
  behavior, duplicate routes, whole-VND constraints, stale versions,
  transaction rollback, real JWT login/audit attribution, idempotent seed,
  clean migration validation and foreign-key deletion protection.
- MVC coverage includes the complete role matrix, CSRF, validation, escaped
  output and server-owned actor fields.
- Rendered Thymeleaf provider/rate pages and forms were inspected at desktop
  and 375px. Tables scroll inside their responsive container.

## Integration order

The branch contains ADMIN-04 because both tasks add ops navigation and test
fixtures. Merge order: AUTH-02 (already in develop), ADMIN-04 PR #11, then
retarget/rebase this branch to updated develop before merging SHIP-00.

Suggested commit:
`feat(shipping): complete SHIP-00 configuration and quote flow`.
