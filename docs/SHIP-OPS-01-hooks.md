# Shipping hooks for ORD-OPS-01

Based on develop 34f114c (SHIP-02 #42 merged), matching the signatures proposed in TD's HP-approved commit 3112842.

ShipmentFulfillmentService exposes two ADMIN/MANAGER internal methods, both MANDATORY:

- Optional<ShipmentFulfillmentFacts> lockForOpsCancellation(Long orderId): locks Shipment with FOR UPDATE and returns complete persisted physical facts or absence.
- void cancelLockedForOpsOrder(Long orderId, Long expectedShipmentVersion): reloads the locked row; null version explicitly expects absence; a present row requires the exact nonnegative original version and clean ASSIGNED evidence (no pickup/delivery, zero attempts, maxAttempts=2). Cancels once, increments version and appends SHIPMENT_CANCELLED audit with server-derived actor and fixed UNABLE_TO_FULFILL reason. Stale/duplicate/inconsistent evidence conflicts.

Caller must lock the existing Order first, then invoke the read hook, acquire persisted ACTIVE ADMIN/MANAGER eligibility in the Order-owned boundary, validate expected Order/Shipment state/version, and invoke the mutation hook. An absent Shipment needs no mutation/audit. Hooks intentionally do not acquire Account, Role or Product locks, invoke Identity eligibility, write Order, mutate inventory/vouchers/Payment or call Order lifecycle. Role annotations are a trusted authority guard; persisted eligibility and its retained Account lock belong to TD's orchestrator. Audit foreign-key integrity checks are not an application eligibility lock.

Authoritative absence depends on the caller retaining Order until commit; existing assignment/reassignment also locks Order first. Calling Shipment hooks without the required caller ordering is outside this internal contract. cancelForOps must start its own transaction and reject outer transactions; ADMIN HTTP/UI must not wrap it in @Transactional.

No migration, reason free-text input or HTTP route is added. ADMIN-07 #37 remains Draft until the real Order boundary is wired, tested and merged. TD must replace both fixture hooks with these production methods and run full end-to-end rollback/concurrency verification; Shipping-only tests are not an end-to-end Ops cancellation claim.

Verification (2026-10-13, Java 21): .\mvnw.cmd -q -Ppostgres-it clean verify PASS, exit 0, 1118 tests, zero failures/errors/skips. ShipmentFulfillmentIT: 18 cases, including role/transaction guards, system-wide ADMIN/MANAGER, explicit absence, stale/physical evidence, audit failure rollback, outer failure rollback and real Order-blocking/no-Account-lock concurrency. git diff --check passed.
