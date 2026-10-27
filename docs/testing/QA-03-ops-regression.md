# QA-03 Ops regression and test-context lifecycle follow-up

## Status and exact revisions

This is automated local regression evidence and a test-only mitigation for INT-02
F05 (LOW). It does not close the separate QA-02 Q02-F05 (HIGH), whose reproduction
and source were not available for this review. It does not mark all QA-03 complete.

- Base develop: `798535b32ff81a011f34cfa01bbeb2d2dd0cb981`.
- Tested implementation: `47dd298d37988be345e025c7008b3038bda6fa65`.
- Clean runner checkout: `b71534fb5eb70d94642f6f8804ff2d0f9b0de392` (PR #61).
- Java 21 full `-Ppostgres-it clean verify`: 686 unit/architecture tests in 73
  classes and 1,268 PostgreSQL tests in 61 classes; 1,954 total, zero
  failures/errors/skips, Maven exit 0. Node: 12 PASS, exit 0.
- Focused precursor: 11 unit/architecture + 42 PostgreSQL PASS, exit 0.
- Generated manifest: [QA-03-context-lifecycle-evidence.json](QA-03-context-lifecycle-evidence.json).
  It records exact runner/source revisions, script/log/report hashes and exit codes.
  Raw log hashes were recomputed and matched before publication. The baseline log
  hash matches the manifest retained by approved PR #61. No raw tokens, test
  properties, host paths or private payloads are published here.

Final documentation commits do not alter the tested implementation. GitHub CI is
separate from this local full-suite evidence; review the final PR check status.

## Diagnosis and narrow mitigation

The baseline full run passed assertions but retained application contexts whose
class-scoped JUnit Testcontainers databases had stopped. Its shutdown dump showed
SpringApplicationShutdownHook waiting in HikariPool.shutdown while 16 old
connection-adder threads were backing off in HikariPool's PoolEntryCreator.
This supports a cached-context/container lifetime mismatch; it is not evidence
of a production business deadlock or proof of every possible teardown cause.

A test-only TestExecutionListener now evicts/closes an already-loaded context
after each `@Testcontainers` class. Registration lives in test resources. It does
not create a missing context, alter plain unit/web-test caching, change assertions,
suppress logs or extend Maven timeouts. The listener's order is immediately above
the standard DirtiesContext listener so reverse after-class callbacks clean up
before that listener; existing explicit dirty-context behavior is retained.

Four unit tests cover loaded/unloaded contexts, non-container isolation, ordering
and test-resource registration. The full unchanged PostgreSQL suite exercises
real context destruction. This suite executes test classes sequentially. This
change is not a design for parallel classes sharing contexts, nor a guarantee that
JUnit container shutdown always follows Spring bean destruction. A future move
to parallel execution or shared containers needs its own lifecycle design.

The [Spring context-cache documentation](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html)
explains cached contexts and explicit eviction. The
[Spring Boot Testcontainers lifecycle documentation](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
distinguishes JUnit-managed container lifetimes from containers managed as Spring beans.

## Observed before/after (one full run per revision)

| Diagnostic occurrence | Base develop | Tested mitigation |
|---|---:|---:|
| Failed-to-validate closed-connection warnings | 492 | 0 |
| Connection-refused log occurrences | 219 | 0 |
| Forced-fork shutdown diagnostics | 1 | 0 |
| Failsafe shutdown dumps | Present | 0 |

Counts are text occurrences, not distinct incidents. The candidate has no observed
recurrence in the focused and full runs, but repeated-run/CI review remains useful.
The [comparison](QA-03-context-lifecycle-comparison.json) records source/log hashes.
No production reliability, load or multi-instance conclusion follows from these counts.
INT-02 F05 can be reassessed by reviewers against this mitigation and evidence;
its historical finding document is preserved unchanged.

## Automated Ops evidence mapping

These are executed existing suites, not new manual acceptance claims. Counts
below overlap the full-suite total and must not be added to it a second time.

| QA-03 area | Executed suites (test counts) |
|---|---|
| Assignment and fulfillment | ShipmentAssignmentIT (6), ShipmentFulfillmentIT (25), ShipperFulfillmentIT (16) |
| Failure/retry and return-to-sender | ShipperFulfillmentIT (16), ShipperOrderLifecycleIT (92), ReturnToSenderIT (16) |
| Durable inbox and WebSocket | NotificationIT (6), NotificationRecoveryIT (7), NotificationSchedulerIT (1), NotificationSocketIT (29); Node client (12) |
| Review/media and Return race | ReviewIntegrationIT (25), OrderEligibilityIT (26) |
| Fresh/upgrade and immutable schema | DatabaseBaselineIT (1), ReviewFoundationMigrationIT (3), NotificationMigrationIT (1), ShipmentAttemptMigrationIT (3), ReturnToSenderMigrationIT (2), ReturnReceiptMigrationIT (3), RefundLedgerMigrationIT (3) |
| Ops/Admin/financial regression | OpsDashboardReadIT (28), AdminRevenueIT (26), VendorRevenueIT (46), ManualCodRefundIT (62) |
| Integrated HTTP/environment | Int02CommerceFlowIT (3), Int02HttpEnvironmentIT (5) |

Testcontainers use isolated test databases. They are not evidence that a clean
production host has passed full ENV validation, upload persistence/restart, ACL,
backup/restore or production monitoring. Manual responsive/browser acceptance,
production operations and final INT-02/QA-03 sign-off remain separate gates.
PR #61 remains tooling-only and subject to HP final acceptance/branch protection.
