# PAY-02 PR #56 — final QD technical review

Review date: 2026-10-23. Exact reviewed/tested HEAD: **7b0383d96156eae7fd6e9595a83ac2ce85668490**. Base: develop a11ac9a398a51adb59447b8f65287ba788bbf38f. PR: https://github.com/hphuc032/Logistic-UTEExpress/pull/56

**Verdict: PASS in QD's migration/schema, locking, idempotency, concurrency and rollback scope. No Critical/High blocker found within the reviewed code and executed tests.** This is final technical review evidence, not HP's business/authorization acceptance or a claim that every possible concurrent caller has been proven safe. No merge or GitHub approval submitted.

## Independent full verification on exact final HEAD

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd -q -Ppostgres-it '-Dlogging.level.org.hibernate.SQL=DEBUG' clean verify
```

Maven exit 0. XML reports independently summed: **597 unit/architecture tests (65 classes) + 1,054 PostgreSQL tests (50 classes) = 1,651 tests**, zero failures/errors/skips. Failsafe timeout=false and flakes=0. Flyway log confirms 26 migrations validated. The run emitted 27 PAY02 blocking observations. Existing Hikari/test JVM teardown diagnostics did not cause a test failure or nonzero Maven exit. These are QD's own executed results on final HEAD, not a repetition of TD's report. Prior 8287e4e targeted 350-PostgreSQL review is superseded for final verification by this full run.

Final GitHub check: PR open, exact HEAD unchanged, behind=0, mergeable=true, mergeable_state=clean; Maven test and package check completed SUCCESS. git diff --check PASS and reviewed tree clean before recording this report. TD branch/source was not changed by QD.

## Diff and migration

Compared 8287e4e -> 7b0383d: Payment refund production code, RefundOrderAuthority, OrderLifecycleServiceImpl and V20261022090000 ledger SQL are unchanged from the independently reviewed source. New production diff is the develop SHIP-04 dashboard. Fixture fix 641f694 changes only ProductDiscoveryIT, PublicCatalogIT and VendorProductIT cleanup, avoiding TRUNCATE CASCADE into immutable financial ledger. No trigger/constraint was weakened or migration rewritten. All three classes passed independently in the full run (7/7/9 tests).

Ledger reservation, version/dependency order, composite Payment/Return+Order FKs and matching unique targets, individual UNIQUE Order/Payment/Return plus global operation/key, positive integral VND amount, required proof/server actor/time/original/result versions and canonical digest checks remain valid. UPDATE/DELETE/TRUNCATE and cascade-truncate protection remain intact. RefundLedgerMigrationIT 3 cases passed; Receipt and Shipping upgrade regressions also passed, including checksum/history preservation and repeat-migrate no-op. No historical refund backfill or Payment/receipt rewrite.

## Actual Account lock and governance compatibility

Inspected PESSIMISTIC_WRITE declaration and verified generated SQL INSIDE ManualCodRefundIT's XML system-out:

```sql
select ... from uteexpress.users ue1_0 where ue1_0.id=? for no key update of ue1_0
```

Thus the production Identity API acquires PostgreSQL **FOR NO KEY UPDATE**, not merely an annotation assumed to mean FOR SHARE. This conflicts with account restriction/role-writer UPDATE locks while allowing FK KEY SHARE. The fresh READ_COMMITTED transaction locks Account before persisted ACTIVE/ADMIN membership reread and retains it through commit. Refund does not acquire Role after Account; existing governance writers use Role -> target Account before modifying membership.

Account-FK tests exercised real ledger/history/audit FK inserts in both schedules and showed ADMIN revocation waiting until both transactions commit. Governance role revocation/account restriction tests passed in both schedules, including committed replay and multi-role actor setup. Bounded backend waits are observed through pg_blocking_pids, not inferred from elapsed sleeps. No test deadlock/lock-timeout failure occurred. Stronger same-ADMIN serialization is the deliberate current Identity API behavior; no new FOR SHARE API is needed for the tested contract. HP can use this SQL/concurrency evidence for the principle acceptance stated in the user-supplied review request.

## Idempotency, transaction and rollback

Global key namespace and operation/key advisory identity are independent of actor/Order/payload. Canonical bytes and digest plus exact key and original ADMIN govern immutable committed replay. Same-key/different payload/Order/Admin and different-key duplicate refund conflict. Authorized replay runs before stale first-execution lifecycle guards, without any subsequent Account -> Order lock inversion. Current eligibility is required even on replay. Database uniqueness/composite FKs remain authoritative beyond the cooperative advisory protocol.

Fresh READ_COMMITTED public coordinator rejects outer transaction; internal hooks are MANDATORY. Key -> Order -> ReturnRequest -> Payment -> existing Refund -> Account -> writes on first execution. Payment authority validates successful exact COD collection; Order lifecycle alone records REFUNDED/history/event after commit. Five independently rerun injected failure stages (ledger, Order, history, audit, beforeCommit) rolled back all effects and allowed key reuse; no early event. Payment PAID, Return APPROVED/receipt, Shipment/Inventory/Voucher remain unchanged. Full suite includes the integrated dashboard, Return and Shipping regressions.

ManualCodRefundIT: 62 tests passed, 27 blocking observations. Relevant acceptance includes same-ADMIN replay, foreign ADMIN collision, cross-Order same-key/different-key races and rollback, stale ORIGINAL versions, missing/invalid legacy proof, zero/ambiguous/unpaid/ONLINE evidence denial, API/service ADMIN scope, CSRF/forged payload/header/query denial and generic refund bypass protection. SQL/state assertions establish atomic financial evidence and lifecycle effects in the tested scope.

## Handoff

QD final technical scope PASS on 7b0383d. HP still owns final business/amount/authorization acceptance and the merge decision. If HEAD or develop changes, re-evaluate the new diff, synchronization and relevant gates before merge. No external message, PR comment, GitHub approval or merge was performed by this report.
