# PAY-02 independent QD review — 2026-10-23

Reviewed exact TD SHA 8287e4ee35caffbd380fbc66f3ae7210759f31e6, branch feature/pay-02-refund, based on develop 425dd6a. Verdict: no actionable blocker found in the reviewed migration/schema, advisory/idempotency, persisted authorization and rollback scope. This is a scoped technical review, not HP business/final acceptance, not a GitHub approval and not permission to merge.

## Independent verification

Executed from a clean working tree at the exact TD SHA with Java 21:

```powershell
.\mvnw.cmd -q -Ppostgres-it '-Dit.test=ManualCodRefundIT,RefundLedgerMigrationIT,ReturnManagementIT,ReturnReceiptMigrationIT,ShipperOrderLifecycleIT,ShipmentFulfillmentIT,ShipperFulfillmentIT,ShipmentAttemptMigrationIT' clean verify
```

Exit 0. XML reports: 597 Surefire unit/architecture + 350 targeted PostgreSQL = 947 tests, zero failures/errors/skips; Failsafe timeout=false, flakes=0. Counts are QD's actual selected suite, not TD's reported 372 PostgreSQL tests and not a full PostgreSQL suite.

| PostgreSQL class | Tests |
| --- | --- |
| ManualCodRefundIT | 62 |
| RefundLedgerMigrationIT | 3 |
| ReturnManagementIT | 149 |
| ReturnReceiptMigrationIT | 3 |
| ShipperOrderLifecycleIT | 92 |
| ShipmentFulfillmentIT | 25 |
| ShipperFulfillmentIT | 13 |
| ShipmentAttemptMigrationIT | 3 |

Observed 27 PAY02 blocking log entries from PostgreSQL backend/pg_blocking_pids checks. Reviewed and reran five fault-injection stages: ledger, Order, history, audit and beforeCommit; rollback and subsequent key reuse PASS. Existing Hikari/test JVM teardown diagnostics do not change the exit/report result. git diff --check passed on reviewed source. Exact-head GitHub Maven test and package completed SUCCESS at review time. No PAY-02 PR was found by the branch-filtered open-PR lookup; TD still needs a PR and HP final acceptance.

## Reviewed implementation

Migration V20261022090000__td_pay02_refund_ledger.sql matches QD reservation. Adds composite unique targets on Payment/Return and composite ledger FKs tying them to the same Order. Individual UNIQUE Order/Payment/Return and operation+key, required positive integral VND amount, timestamps, original/result versions, canonical bytes and digest, nonblank bounded proof/key. Ledger triggers reject UPDATE/DELETE/TRUNCATE including cascade truncate; no old migration changed. Fresh/Receipt/Shipping upgrades preserve prior checksums and Order/Payment/Return rows; receipt and Shipping migration regressions also passed. The new migration changes only Payment/Return key targets plus its own financial ledger/trigger.

ManualRefundPayload uses deterministic big-endian length-prefixed strict UTF-8, SHA-256, original versions and stripped proof. Exact key remains case/whitespace-sensitive; advisory ID hashes operation+key only. Full key/canonical bytes/digest AND original server ADMIN are compared for replay; a truncated advisory hash match cannot authorize replay. Immutable ledger produces the same committed amount/proof/IDs/time with replay flag changed.

Fresh READ_COMMITTED coordinator rejects outer transaction before locks. Key advisory lock precedes Order/Return/Payment/existing Refund; Account eligibility precedes writes and its implicit FK checks. Parent Order plus database uniqueness protects missing-ledger creation. Role guard is checked both at production entry and against persisted ACTIVE ADMIN; no Role row lock is acquired after Account. No browser amount/status/actor/paymentId authority; endpoint header/query/DTO allowlists and CSRF/role denial have regression coverage.

Payment validates exactly one successful unexpired COD record with authoritative positive grand_total; Order/Return original versions and approved immutable receipt/history are required. Dedicated lifecycle writes REFUNDED/history and publishes after commit. Payment remains PAID, ReturnRequest/receipt/version and Shipping/Inventory/Voucher facts unchanged. Ledger/audit/lifecycle/history are one DB transaction.

## Details HP/TD should retain in final review

1. Account lock is the existing Identity requireActiveAccountForUpdate JPA pessimistic-write path, not literal FOR SHARE from the design. On the tested PostgreSQL/Hibernate stack it behaves compatibly with FK KEY SHARE (the tests document NO KEY UPDATE), while serializing same-ADMIN operations more strongly. Real FK inserts and ADMIN revocation in both schedules passed. This is a documented implementation variation, not an observed integrity/deadlock blocker. Do not replace its SQL lock mode without rerunning those tests; HP should accept the stronger serialization explicitly.
2. Committed-key replay takes advisory key -> Account authorization -> immutable ledger read, WITHOUT acquiring Order/Return/Payment afterward. This avoids Account -> Order inversion and allows replay before stale lifecycle guards. No mutation of that branch into Account-then-Order is safe. Tests cover same key/different actor, different payload/Order, post-success versions and revoked/inactive replay.
3. PostgreSQL tests use latches/spies to hold real transaction stages and observe database waits. PASS is bounded to those tested schedules, not a proof that every possible external caller is deadlock-free. Public outer-transaction rejection must remain intact.
4. Migration tests establish unchanged receipt/failure schemas through the selected migration regressions. No full new PAY-02+SHIP-04-dashboard integration was run: #55 is a separate branch not present in TD base. Sync/retest any new develop before merge. Full PostgreSQL regression and HP final acceptance remain release gates.

No production files were edited during this review. Results are recorded on the QD documentation branch, not pushed onto TD's implementation branch. No merge, external teammate message or GitHub approval was submitted.
