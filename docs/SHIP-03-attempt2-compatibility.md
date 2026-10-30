# SHIP-03 delivery attempt compatibility

Scope: Shipping guards accept delivery/evidence at attempt 1 or 2 with max_attempts exactly 2. Pickup remains attempt 0 and initial start-shipping remains attempt 0 -> 1. Persisted assignment, ACTIVE/SHIPPER eligibility, expected versions/source state, pickup/delivery evidence and transaction/audit guards are preserved. No new endpoint, migration, failure/retry mutation, Payment write or Order status writer is introduced.

Dependency: TD PR #50 (65dafea) merged into develop at 6a89bac. On 2026-10-16 this QD branch was rebased onto that develop commit. The PR diff now contains only QD Shipping guards, integration tests and this report; no unmerged TD dependency remains.

Regression coverage:
- Existing first-attempt HTTP/COD/security/concurrency tests remain unchanged.
- Second-attempt delivery through real Order/Shipping/Payment coordinator succeeds, collects COD and rejects duplicate stale delivery without further effects.
- Wrong second-attempt COD amount leaves the full Order, Shipment, Payment, history and Shipment audit snapshot unchanged; subsequent correct delivery succeeds.

Second-attempt fixture performs real pickup/start-shipping, then sets attempt_count=2 and version=4 in test-only SQL. It does not exercise a production retry action and does not establish F01/F02/F04 or complete SHIP-03. Failure evidence, retry routes, role-revocation locking and the new Shipping-only Order guard still require HP/TD contract agreement. These tests cover the F03/F11 delivery compatibility portion only.

Verification on 2026-10-15: Java 21 .\mvnw.cmd -q -Ppostgres-it clean verify completed with exit 0. XML report totals are recorded in the PR; ShipperFulfillmentIT passed all 8 tests including the two new attempt-2 regressions. git diff --check passed. Teardown emitted connection warnings after container shutdown; Failsafe result and report failure/error/skip counts were checked separately.

Rebase verification on 2026-10-16: Java 21 .\mvnw.cmd -q -Ppostgres-it clean verify PASS, exit 0; 1,182 tests (423 unit/architecture + 759 PostgreSQL), zero failures/errors/skips, Failsafe timeout=false. The host suspended during the run; execution resumed and all reports completed successfully. git diff --check passed. Recheck exact-head CI before ready for review.
