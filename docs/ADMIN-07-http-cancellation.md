# ADMIN-07 — Ops order read and exceptional cancellation

The read foundation remains in governance. The separate operations module provides POST /admin/orders/{id}/cancel and /manager/orders/{id}/cancel for JSON and URL-encoded forms, avoiding governance -> Order -> Shipping -> governance cycles.

OperationsCancellationService delegates directly to OrderLifecycleService.cancelForOps with no @Transactional annotation or outer transaction. The merged ORD-OPS-01 boundary starts its own transaction, rejects existing transactions and owns persisted ACTIVE ADMIN/MANAGER authorization, system-wide scope including restricted Shops, Order/Shipment version/state guards, stock/voucher release, Shipping hooks, audit/history and after-commit events. The HTTP coordinator writes no business tables and does not duplicate lifecycle rules.

Input is limited to expectedOrderStatus, expectedOrderVersion, explicit EXPECTED_ABSENT/EXPECTED_PRESENT, expectedShipmentVersion (mandatory for present, null for absent), and canonical UNABLE_TO_FULFILL reasonCode. Resource identity is the route; actor/owner/roles/target status/free text are not accepted. Existing strict JSON decoding rejects unknown fields; form/query parameters also reject unknown or repeated fields. CSRF remains enabled for both representations.

The detail page offers a CSRF-protected cancellation form for NEW/CONFIRMED with no Shipment or clean ready ASSIGNED evidence; original persisted versions and presence are posted. Server checks remain authoritative against stale pages. The form disappears for terminal/physical fulfillment states. Success redirects to the detail page; JSON returns the lifecycle outcome.

PostgreSQL HTTP tests use real Order/Shipping/Payment/inventory boundaries: ADMIN/MANAGER cancellation, duplicate conflict, unchanged Payments, stock/history/audit, denied roles/CSRF, invalid/forged fields, persisted inactive Ops Account, explicit presence/version conflict after reassignment, HTML form/CSRF and outer-transaction rejection. Existing ORD-OPS-01 E2E tests remain intact.

No migration or RETURN-01 receipt/refund feature is added. RETURN-01 PR #49 is independently reviewed; it is not a dependency of this cancellation integration.

Verification on 2026-10-13, Java 21: .\mvnw.cmd -q -Ppostgres-it clean verify PASS, exit 0; 1,185 tests, 0 failures/errors/skips (423 unit/architecture + 762 PostgreSQL). OperationsCancellationIT: six invocations PASS; OperationsOrderIT: one PASS; production OpsOrderCancellationIT: 60 PASS. git diff --check and staged whitespace check passed. Synchronized develop: fc78b41.

Sync verification on 2026-10-16: merged latest develop fb6bb9f without conflicts. Java 21 .\mvnw.cmd -q -Ppostgres-it clean verify PASS, exit 0; 1,189 tests (423 unit/architecture + 766 PostgreSQL), zero failures/errors/skips, Failsafe timeout=false. No ADMIN-07 business logic changes or migration added. git diff --check passed; behind=0 at verification. Exact-head CI must be checked on the pushed head before merge. #36 follows only after #37 merges; #49 remains held until #36.
