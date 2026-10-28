# QD scoped review — cancelled-paid refunds and shared-seed integration

Master Plan: original UTEExpress_Master_Project_Plan.docx (QA-02 Commerce,
QA-03 Ops/T48 clean migration+seed, PAY-02 cancelled-paid cases; PAY-03 deferred).
Reviewed TD HEAD: `688884cd33f4b2f4dc62f5d33a4c4002e9146660` (PR #70).
QD review scope: migration ordering, constraints, old data, locks/idempotency,
rollback and shared-seed compatibility. HP retains business/final acceptance.

## Independent exact-head verification

QD ran Java 21/PostgreSQL 17.6 clean verify on the exact unmodified #70 source:
**690 unit +205 PostgreSQL PASS**, eight PG classes, zero failures/errors/skips,
Maven exit 0. This is a scoped independent run, not TD's 2,073-test full run.
Fifteen real PostgreSQL blocking observations were emitted by the seven key/Order
contender schedules and eight ADMIN-revocation schedules. Manifest records actual
XML/log checksums; no private log bodies, cookies or fixture secrets are published.

New migration V20261028081110 is the only migration diff. All 27 earlier files
retain their Git object IDs. Fresh and populated upgrade tests pass: existing
returned-COD ledger values, Orders, Payments, ReturnRequests and applied checksums
are compared and preserved; repeated migrate executes zero migrations. Existing
Order/Payment/Return uniqueness, composite FKs and append-only UPDATE/DELETE/
TRUNCATE protections remain. Receipt columns become nullable only under the
cancelled-paid operation CHECK. Returned-COD requires receipt fields and version+1;
cancelled-paid requires null receipt fields, cancellation timestamp/order evidence
and unchanged result Order version. Test results do not imply an arbitrary
production data migration audit beyond these fixtures.

New operation has a distinct canonical encoding and operation/key advisory lock
namespace. The old returned-COD canonical bytes/format are unchanged; helper
visibility changes do not change serialization. Replay checks original ADMIN plus
fresh persisted authorization. Advisory key -> Order/Return -> Payment/Refund ->
ADMIN Account locks serialize mutations; implicit ledger FKs occur after the
explicit locks. UNIQUE constraints remain the database duplicate guarantee.
Scoped tests include same/different keys, actor/payload collisions, rollback,
governance races and unchanged cancellation inventory/voucher/payment effects.

No Critical/High technical blocker found in this scope. Approval is not whole
QA-02/QA-03 or post-merge acceptance. Business correctness remains HP scope.

## Combined seed assertions and provenance

The shared runner supports optional `--include-qa02-refunds` in addition to the
mandatory `--disposable-demo`. It creates a newly owned loopback DB named
qa02_refund_seed and runs TD's unmodified test-classpath RefundAcceptanceSeed twice
as separate application invocations against that SAME DB, alongside all shared
HTTP fixtures. TD's Java fixture is not added to the production JAR.

Fixture provenance is explicit: shared accounts use real registration/OTP/HTTP
with an initial ADMIN role-link bootstrap; TD's test module additionally creates
fictional identity/catalog and synthetic persisted ONLINE PAID prerequisites.
This is not VNPAY Sandbox, a callback signature/transfer test or a production PAID
writer. No direct SQL Order/Shipment lifecycle fabrication is added by QD.

Assertions are operation-specific: returned-COD -> Order REFUNDED with approved
receipt; cancelled-paid -> Order CANCELLED with no ReturnRequest/receipt and no
second stock/voucher release. Both keep Payment PAID and immutable one-shot ledger
facts. The original shared returned refund is hashed before and after adding TD's
fixtures and must remain byte-equivalent. Total combined refunds are three (two
returned-COD and one cancelled-paid), not the old single-operation count of one.

Shared Vendor A and filtered Admin Shop A still assert their original known money.
Global metrics additionally assert delivered goods720000, refunds550000 and net
commission25200. TD's intentionally PAID NEW ONLINE negative fixture must make
global collection/net-collected/net-after-commission unavailable (null value,
unknownEvents=1). It is not silently dropped or counted as valid revenue.

Rerun scope stays explicit: shared module uses its completed in-process checkpoint;
TD module is invoked in two independent application processes. All 34-table
fingerprints must match before/after the combined rerun, including a legitimate
product edit. This does not add cross-process recovery to the shared module.

## Remaining acceptance gates

- HP acceptance of initial shared ADMIN bootstrap: **PENDING**, no recorded decision
  in supplied messages/GitHub reviews at review time.
- HP acceptance of same-process/checkpoint shared rerun scope: **PENDING**.
- #68 is still unmerged; #69 includes its history. Sync #69 after #68 actually merges;
  do not assume TD's proposed scoped approval is a completed GitHub approval/merge.
- #70 is unmerged. Combined candidate evidence does not mean develop contains it.
- Q03-G01 browser/manual/responsive 375/768/1440: **NOT RUN / BLOCKED** by Computer
  Use kernel initialization failure. No screenshots or manual PASS invented.
- ADMIN-06/T41c remains DEFERRED / ACCEPTED SCOPE EXCEPTION, Manager writes DENY ALL.
- QA-02 IN PROGRESS, QA-03 BLOCKED; post-merge integrated source regression and
  TD review/HP final acceptance remain required. Release follow-ups NOT RUN and
  VNPAY Sandbox PAY-03 DEFERRED are tracked separately.

## Completed combined verification and retained finding

Combined source `1d8f923b77f4dc747b4a72874b61364f33abcf36`: full clean verify **690 unit +1,388 PostgreSQL =2,078 PASS**, zero failures/errors/skips, exit0/no timeout. Complete Node33 and tooling17 PASS. The approved runner separately records Notifications12, which overlap Node33. All139 suite report hashes and raw full logs were rechecked; the seed JAR checksum is unchanged after verify completed. These runs are distinct from exact #70 independent690+205 and TD-reported2073; do not add overlapping tests or relabel their sources.

Combined run `run-d671ae0ae88e` PASS:15 Orders,3 refunds, existing returned ledger hash unchanged, independent TD fixture invocations1/2 and shared checkpoint rerun preserve all34-table fingerprints. Complete global money720000/550000/25200 and deliberate global collection-unavailable diagnostics pass. Own disposable container removed.

**Q70-S01 / Medium / OPEN — not repeatably clean combined fixture.** Earlier SAME-source run `run-9b427dbb1046` fails with CONFLICT in TD fixture create(line149) -> CancelledPaidRefundPayments.lockPaid(line46). It is retained as FAIL. The second observed run is PASS, but its valid snapshots cannot prove the exact first-run failed predicate. SQL CURRENT_TIMESTAMP vs JVM lifecycle time is a hypothesis only. OwnerTD with QD evidence support: capture sanitized persisted predicates/timing before cleanup, investigate and resolve or obtain an explicit HP disposition. No production guard/test assertion was weakened; no confirmed production refund defect is alleged. The initial runner preflight also refused an accidentally generated untracked Python cache before executing tests; cache was moved into ignored .work, then the clean-source run completed. This preflight failure is not counted as a test PASS.

Conclusion: database scope PASS with no Critical/High finding; combined execution PASS observed with one OPEN Medium fixture finding; **not READY FOR HP final acceptance**, no GitHub approval or merge authorized. Scoped review posted as COMMENTED, not APPROVED: https://github.com/hphuc032/Logistic-UTEExpress/pull/70#pullrequestreview-5685181122

## Subsequent diagnosed clock-fixture update

Historical source identities and FAIL artifacts above remain unchanged. New reproduction proves paid_at after cancelled_at in the synthetic fixture; test-only candidate fix and new2078 Java/PostgreSQL +33Node +20tooling verification are documented in [diagnostic follow-up](../qa03-refund-diagnostics/report.md). Q70-S01 is fix-candidate verified, pending TD review/adoption; no production guard was relaxed, no GitHub approval/merge or whole QA completion is implied.
