# SHIP-03 — QD Shipping and integrated Order protocol

Base: develop 85352d9. TD Order interface 87d3d02 and real wiring 6a5f336 are merged into feature/ship-03-delivery-attempts / PR #54. The earlier fail-closed handoff is superseded: prepare/verify now invoke real persisted Shipping hooks. QD did not replace TD-owned production code with a local guard; Order changes come from TD's integration commit.

## Implemented behavior

Operations are REPORT_DELIVERY_FAILURE and RETRY_DELIVERY using TD's single shipping.dto.DeliveryAttemptOperation enum. Failure records Shipper attestation (reason <=1000, reference <=255, both nonblank) with server actor/time, leaves attempt 1/2 unchanged and moves Shipment SHIPPING -> DELIVERY_FAILED. Retry only accepts failed attempt 1, starts SHIPPING/attempt 2 immediately and increments the Shipment version once. A second failure cannot retry again.

Order stays SHIPPING with unchanged version/history/event for both operations. Payment, inventory, voucher, buyer ReturnRequest, refund and restock are untouched. No fake OrderStatusChangedEvent is published. Actual second-attempt delivery still uses SHIP-02 Order lifecycle and Payment.collectCod, with full wrong-amount rollback.

## Persistence and guards

V20261018110000__qd_ship03_delivery_failures.sql creates append-only shipment_delivery_failures. Unique Shipment/attempt and Shipment/source-version prevent duplicate evidence; Shipment/actor FKs, range/nonblank/bounded fields and required timestamp constrain rows. A BEFORE UPDATE OR DELETE trigger rejects evidence changes/removal. Source Order is resolved through Shipment, so no independent client-supplied Order/evidence association exists. No applied migration changed.

ShipmentAttemptService is MANDATORY + SHIPPER. lockForOperation and requireOperationEvidence reload persisted Shipment/evidence and enforce assignment, ACTIVE/SHIPPER, source/target state/version, max attempts, pickup/no-delivery evidence and per-attempt chronology. ShipmentDeliveryFacts wraps the existing unchanged ShipmentFulfillmentFacts and relevant DeliveryFailureEvidence. Failure source has no current evidence; retry source/target retains immutable attempt-1 evidence; failure target contains its persisted current-attempt evidence. Target checks use original version + 1, and evidence source version matches the original token. Caller-created facts are never accepted by Order verify.

DeliveryAttemptCoordinator rejects outer transactions before opening a new TransactionTemplate, then calls TD prepare -> Shipping mutation/audit -> TD verify -> result reload. Locks remain Order -> Shipment -> Account through commit. Order authority refreshes/validates lifecycle flags, original Order version and persisted Shipping target facts, without saving Order. Shipping audit or target-verify failure rolls back Shipment and evidence atomically.

## HTTP / UI

JSON and URL-encoded POST /shipper/orders/{orderId}/delivery-failed and /retry use DeliveryFailureRequest / RetryDeliveryRequest and return DeliveryAttemptResult for JSON. Forms redirect to assigned shipments. Both expected versions are required; strict JSON/form/query field rules reject caller actor/attempt/status/amount injection and duplicate parameters. CSRF and SHIPPER authorization remain enabled; persisted assignment and eligibility are rechecked under locks. Result returns unchanged Order SHIPPING/version and actual Shipment status/version/attempt.

The detail page offers failure on SHIPPING and retry only for failed attempt 1, with server-provided versions and CSRF. Attempt reads use one owner-scoped query, so reassignment cannot produce a null/unboxing error between separate ownership/count reads. Acceptance tests exercise all four JSON/form combinations, actual failure -> retry -> second failure, hidden exhausted retry and rejected third retry.

## Verification and acceptance

Earlier Shipping-only focused run passed 97 tests before TD wiring (7 architecture + 22 Shipping + 60 Order + 8 fulfillment). The fixture's initial INACTIVE status error was corrected to schema-supported LOCKED before that passing run. TD independently reported 504 unit + 126 targeted PostgreSQL tests on 6a5f336; this is author-reported, not QD full verification.

QD independently ran Java 21 .\mvnw.cmd -q -Ppostgres-it clean verify on the real integrated production code plus final HTTP acceptance cases: PASS, exit 0; 1,424 tests (504 unit/architecture + 920 PostgreSQL), zero failures/errors/skips/flakes, Failsafe timeout=false. ShipmentFulfillmentIT 25, ShipmentAttemptMigrationIT 2, ShipperFulfillmentIT 13 and ShipperOrderLifecycleIT 92 all PASS. git diff --check passed. Verified code/test source is 130cb96; the following report commit changes documentation only. Intermediate full runs were superseded/stopped for new source or TD wiring and are not substituted for this final run. Existing Hikari connection-close and Surefire 30-second shutdown warnings remain a test teardown limitation; XML reports and Maven exit show no test failure/deadlock. Exact-head CI and HP final review remain required before merge.

Coverage includes immutable evidence/FK/unique checks, deployed/fresh Flyway upgrade and no-op repeat; failure/retry/exhaustion and both original tokens; audit and target-verification rollback; stale/duplicate/wrong-assignee requests; actual attempt-2 COD success/wrong amount; duplicate failure/retry and failure-vs-delivery in both schedules; governance account/role revocation in both schedules with multi-role actors and PostgreSQL blocking evidence; strict DTO/CSRF/role/outer-transaction rejection and JSON/form UI. Full acceptance and security/lifecycle review still belong to HP before merge.

## Merge-time coordination

Keep SHIP-03 independent of buyer RECEIVE_RETURN #53, SHIP-04 and PAY-02. Current migration sorts after #53's proposed V20261018090000 if that PR merges first; recheck latest develop and migration order before merge. When #53 and this migration coexist, rerun ReturnReceiptMigrationIT: its current Phase-1 upgrade assertion assumes exactly one pending migration, while receipt + Shipping evidence together add two. TD/QD should update that pending-count assertion as a compatibility test change when syncing the two branches, without editing either applied migration.

No merge performed. Do not rely on the historical fail-closed wording in the earlier Order handoff; docs/SHIP-03-integration-validation.md records TD wiring and this report describes the current integrated behavior.
