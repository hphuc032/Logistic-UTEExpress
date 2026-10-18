# SHIP-03 QD implementation handoff

Branch: feature/ship-03-delivery-attempts. Base develop 85352d9 plus TD feature/ship-03-order-boundary at 87d3d02. QD reuses TD's DeliveryAttemptOperation enum; no duplicate enum and no TD Order production edits.

## Implemented QD surface

ShipmentAttemptService (MANDATORY + SHIPPER):
- lockForOperation(orderId, expectedShipmentVersion, operation)
- requireOperationEvidence(orderId, originalShipmentVersion, operation)
- reportFailure(orderId, expectedShipmentVersion, failureReason, evidenceReference)
- retryDelivery(orderId, expectedShipmentVersion)

The hooks return ShipmentDeliveryFacts(shipment, failure), wrapping unchanged ShipmentFulfillmentFacts and DeliveryFailureEvidence. Evidence fields: id, shipmentId, attemptNumber, sourceShipmentVersion, shipperId, failureReason, evidenceReference, recordedAt. Source report has no current failure; source retry includes persisted attempt-1 failure. Target report includes current persisted failure; target retry includes retained attempt-1 evidence. Both hooks re-read DB, enforce active assigned SHIPPER under Order -> Shipment -> Account locks and retain eligibility through commit. Caller-constructed DTOs cannot authorize operations.

Failure keeps attempt 1 or 2, inserts immutable evidence and increments Shipment version once; retry only accepts failed attempt 1 and moves SHIPPING/attempt 2 with version +1. Evidence sourceShipmentVersion binds failure to its source token; retry validates the preceding failure version. Second-attempt failure requires retained first-attempt evidence. Wrong source/status/version/assignment/evidence conflicts. Order is checked SHIPPING/ready in Shipping too; only TD authority validates the expected Order token and lifecycle flags.

Migration V20261018110000__qd_ship03_delivery_failures.sql adds shipment_delivery_failures, unique Shipment/attempt and Shipment/source-version, actor/Shipment FKs, text and range checks, server timestamp field and an UPDATE/DELETE rejection trigger. No applied migration changed. It sorts after current develop and proposed receipt migration V20261018090000 if #53 merges first; recheck actual latest version before merge.

DeliveryAttemptCoordinator has no @Transactional wrapper: rejects outer transaction, opens TransactionTemplate, invokes TD prepare -> Shipping mutation -> TD verify -> reload result with original tokens. Response reports unchanged Order SHIPPING/version and actual Shipment state/version/attempt. DTOs DeliveryFailureRequest, RetryDeliveryRequest, DeliveryAttemptResult use the agreed fields/limits. JSON/form endpoints and CSRF/strict parameter allowlists use the agreed /delivery-failed and /retry routes. Forms use persisted versions and only offer retry for failure attempt 1.

## Required TD integration — still BLOCKED

TD at 87d3d02 unconditionally throws CONFLICT after Order guards. Presence of these QD hooks DOES NOT remove that rejection. TD must wire Order prepare to lockForOperation and verify to requireOperationEvidence, validate the returned persisted facts/evidence and preserve unchanged Order version/history/event. QD intentionally does not edit OrderPlacementService or ShipperOrderAuthority. Current legitimate HTTP requests remain fail-closed, and a regression explicitly tests that no mutation/evidence escapes.

TD can merge this QD branch into its integration branch and own the small Order wiring diff, then run real successful prepare/mutate/verify F01-F17 integration. Do not mark end-to-end SHIP-03 complete or merge this branch into develop while that fail-closed WIP remains. No reflection dispatch, fabricated Order guard, test-only authority in production or direct Order/Payment write was introduced.

## Verification status

Focused Java 21 PostgreSQL verification of foundation before final adapter tests: ArchitectureTest + ShipmentFulfillmentIT + ShipperOrderLifecycleIT + ShipperFulfillmentIT PASS, 97 tests (7 + 22 + 60 + 8), no failures/errors/skips. An earlier fixture used INACTIVE, rejected by existing users CHECK; fixed to LOCKED before the passing run. This is not an end-to-end failure/retry success claim.

Full Java 21 -Ppostgres-it clean verify is running for the final hooks/DTO/coordinator/form/migration source. Final report/CI must be recorded after completion. New tests include failure/retry/exhaustion, append-only SQL checks, audit rollback, concurrent duplicate failure, validation/assignment/locked actor, fresh/upgrade Flyway and endpoint fail-closed/role/CSRF/forged-input/outer-transaction checks. Test fixture sets Order states to focus Shipping; it does not pretend to implement TD's lifecycle authority.

Remaining acceptance after TD wires guards: successful real-coordinator failure/retry and attempt-2 COD/wrong amount; deterministic concurrent retry/failure-vs-delivery, Account/role revocation in both schedules and multi-role lock evidence; end-to-end UI behavior and full PostgreSQL on integrated exact head. Existing SHIP-02 regressions remain, but do not replace those SHIP-03 acceptance cases. This is an integration handoff, not merge-ready SHIP-03.
