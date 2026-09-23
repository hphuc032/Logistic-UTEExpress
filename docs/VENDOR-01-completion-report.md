# VENDOR-01 — Shop registration foundation

## Contract

VENDOR-01 adds `uteexpress.shops` and the authenticated application flow under
`/user/shop/**`. A Shop application belongs to exactly one account, begins in
`PENDING`, and stores no rejection reason, logo, or banner. The owner comes from
`CurrentAccountIdProvider`; browser input cannot select an owner, status, role,
version, rejection reason, or storage key.

The database and service both enforce one Shop per account. Slugs are trimmed,
lowercased with `Locale.ROOT`, validated against the canonical ASCII slug format,
and protected by a database unique constraint. Database unique constraints remain
the authority for concurrent owner and slug submissions; persistence conflicts are
returned as the public `CONFLICT` application error without SQL details.

## Security and roles

`GET /user/shop`, `GET /user/shop/register`, and `POST /user/shop/register` use the
existing USER/VENDOR `/user/**` policy. `ShopRegistrationService` repeats this rule
with method security and the shared `RoleCode` authority contract. POST remains CSRF
protected. Thymeleaf renders Shop-controlled values with `th:text`.

Submitting an application does not change `user_roles`. An ACTIVE ROLE_USER remains
ROLE_USER while the Shop is PENDING. JWT claims, JWT cookies, login/logout,
tokenVersion, and OTP behavior are unchanged.

## Database

Migration `V20260923100100__hp_vendor01_shops.sql` follows the merged SHIP-00
migration. It creates named PK, user FK, unique owner, unique slug, status, canonical
slug, nonblank, rejection consistency, and nonnegative version constraints. The FK
to users has no delete cascade. `Shop` uses `Instant` timestamps and a nullable Java
`@Version Long` backed by database default `0`.

## Deferred work

- ADMIN-03 owns approval/rejection, rejection reason, governance audit, VENDOR role
  assignment, and any related token-version policy.
- VENDOR-02 owns post-approval Shop management and any resubmission policy for a
  rejected application.
- Image/storage work owns logo and banner upload; their keys remain null here.
- The new shops parent table unblocks PROD-00; VENDOR-01 creates no product schema.
- `orders.shop_id -> shops.id` is now unblocked but remains a deferred FK. A new
  migration requires coordination with the ORD owner; the merged ORD-01 migration
  and Order entities were not changed.

## Verification

The non-database suite passes 179 tests, including VENDOR-01 entity, service, MVC,
CSRF, role matrix, mass-assignment, XSS, and architecture coverage. PostgreSQL 17.6
Testcontainers verifies Flyway, Hibernate `ddl-auto=validate`, named constraints,
foreign keys, canonical slug and unique rules, initial version, authenticated JWT
ownership, unchanged USER role, and concurrent duplicate submissions. The final
command results and CI link are recorded in the pull request and task completion
report.

