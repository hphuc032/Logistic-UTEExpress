# QD Ops compatibility rehearsal — preparation for QA-03

This is a disposable integration/verification branch, not a release or merge request. QA-03 depends on INT-02 per Master Plan; neither INT-02 nor QA-03 is marked complete by this rehearsal. No PR approval or business/security acceptance is implied. Do not merge this aggregate verification branch into develop; merge the reviewed feature PRs separately through group policy.

## Exact inputs

- develop: bce7ff09e2a91af4c86ac23aeedf0d5a802adc33.
- WS-02 PR #58: d5797248a86251f20b696c25033dba0c4fd88050.
- DASH-02 Phase 1 PR #59: c958510c3b05ad761400c5941198ad9c5a91ff7b.
- DASH-01 PR #60: 1e608133708e322e5ab741cf50803381cf63bc49.
- Combined source: 8c5b9eea4f6cc22aca0be32a376653d64b25617f on verify/qa-03-pending-integration.

All three merges were clean. No production code, test fixture, applied migration or constraint was edited for integration. Report/evidence files are added after the tested source commit. Original PR branches remain unchanged.

## Reproduction

Use Java 21, working Docker engine and PostgreSQL 17.6 Testcontainers. Check out the exact combined source before running Maven; never reuse target classes from a different branch.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd -q -Ppostgres-it clean verify
node --test src/test/js/notifications.test.cjs
```

The clean verify starts disposable PostgreSQL containers and runs the repository's full unit/architecture and PostgreSQL suites. Disposable DB verification is not clean-machine deployment/seed/volume-persistence acceptance. Retain Maven exit status and raw XML locally; sanitized per-class counts/report SHA-256 are in the companion evidence manifest. Manifests summarize this local run only, not CI or an independent cryptographic attestation that reports came from a trusted runner.

## Ops acceptance matrix

| Area | Automated evidence classes | Scope / limits |
| --- | --- | --- |
| Assignment | ShipmentAssignmentIT | Persisted selection, reassignment/audit, stale versions, revoked/locked Shipper, foreign read isolation, role/CSRF |
| Delivery / failure / retry | ShipperFulfillmentIT, ShipmentFulfillmentIT, ShipperOrderLifecycleIT, ShipmentAttemptMigrationIT | HTTP JSON/form guards, failure/retry preserve Order, attempt 2 COD delivery/rollback, duplicate/concurrency, append-only evidence |
| Shipping dashboard | ShipperFulfillmentIT | Assignment-scoped counts, real failure/retry/delivery without rewriting facts |
| Ops cancellation | OperationsCancellationIT, OperationsOrderIT, OpsOrderCancellationIT | Trusted lifecycle delegation, rollback, state/version/role checks |
| WebSocket | NotificationSocketIT, NotificationIT, NotificationCommitPublisherTest | Actual HTTP/STOMP, private scope, Vendor CONFIRM -> Buyer, rollback/dedup, malformed JWT, expiry/revocation, four-session Account contention/timeout/cleanup |
| Recovery | NotificationRecoveryIT, NotificationSchedulerIT | Committed history, late/out-of-order commit, concurrent workers, retry/backlog behavior; no ownership-transfer support |
| Reviews | ReviewIntegrationIT, OrderEligibilityIT, ReviewFoundationMigrationIT | Real writer/media atomicity, uniqueness, Order -> Account -> Review, both Review-vs-Return schedules, Unicode and cleanup diagnostics |
| Returns / refunds | ReturnManagementIT, ManualCodRefundIT, ReturnReceiptMigrationIT, RefundLedgerMigrationIT | Receipt-only and immutable COD refund ledger/idempotency/composite FK, rollback; no restock/gateway claim |
| Ops audit / metrics | OpsDashboardReadIT, OpsDashboardTest, OpsReportFilterTest | Role/persisted authorization, HCM windows, no fanout, repeatable snapshot, Admin-only account/audit; DASH-02 financial part still open |
| Vendor reporting | VendorRevenueIT, RevenueServiceTest | Unique successful payment among unsuccessful attempts, exact refund reference, independent event timing, snapshot/completeness, real SQL plans |
| Database / architecture | DatabaseBaselineIT, migration ITs, ArchitectureTest | Fresh/upgrade constraints, unchanged applied migrations, dependency cycle checks; no production migration execution |

Parameterized cases and overlapping fixture coverage are counted by their XML reports, not added again as separate test totals. Multiple integration tests together do not establish a single browser-driven checkout-through-refund scenario.

## Outstanding acceptance gates

1. HP/TD review and feature-PR approvals/CI on the final feature HEADs. A green combined local run does not substitute for those approvals or exact-head CI.
2. TD confirmation of WS recipient/Order contract, HP acceptance of strict JWT/shared-decoder delta. Recipient remains buyer plus Shop owner at projection time; ownership transfer unsupported.
3. HP-led INT-02 full-flow acceptance, including review/return/refund/WS behavior across merged develop. Future merge results must be verified again if sources change.
4. DASH-02 financial reuse contract with TD/HP: no governance -> Order dependency cycle, no copied financial SQL or new formula in this branch.
5. Responsive/manual browser checks, production-safe media reconciliation operation, alert/export setup, clean-machine deployment/ENV/seed/upload persistence and backup rehearsal remain separate work. No claim of deployed monitoring or multi-instance WebSocket support.
6. Existing Hikari closed-connection/container shutdown and fork teardown warnings must be recorded honestly with XML/exit/timeout status, not interpreted as extra passing checks or hidden by removing constraints.

## Results

On tested source `8c5b9ee`: **672 unit/architecture tests (72 classes) + 1,212 PostgreSQL tests (56 classes) = 1,884 PASS**. Zero failures/errors/skips/flaky cases; Failsafe timeout=false, flakes=0, Maven exit=0. Four Node client tests PASS separately. No production/test source edits or migration/constraint relaxation were necessary.

Local logs: qa03-combined-full-postgres.log and qa03-node-tests.log (ignored). Sanitized report counts and SHA-256 hashes: QA-03-pending-pr-evidence.json. Existing closed-connection/container teardown diagnostics and fork termination after System.exit(0) occurred; process exit and XML verification remained successful. This run does not diagnose/suppress those infrastructure warnings.

Only this report and the evidence manifest are added after verification; the tested source SHA remains the reproducible reference. No combined-head GitHub CI is claimed, because this verification branch is not a PR. The feature PRs require their own current HEAD CI and reviewers.
