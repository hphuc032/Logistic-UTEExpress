# QA-02 cancelled-paid refund — local implementation review

Owner TD; HP final acceptance; QD migration/seed review. Branch `fix/qa02-cancelled-paid-refund`.
Base develop `fa48cb69cef92276f0dddf0d2bd396eee098f2f3` includes merged/approved #61/#63/#64/#67.
HP's current task explicitly adds cancelled-paid refund to QA-02; VNPAY Sandbox stays PAY-03 DEFERRED.
Outcome: **READY FOR TD PATCH REVIEW**. Whole QA-02 acceptance remains **BLOCKED** on the
publication/integration, shared-seed and HP gates below; this is not QA-02 completion.

## Inspected gap and bounded implementation

Existing returned-COD refund works, but its guards require RETURNED/approved receipt and the
append-only ledger requires a ReturnRequest. Cancelled-paid refund is NOT IMPLEMENTED at base.
The shared revenue projection also recognizes only returned-COD refund evidence.

New route: `POST /admin/orders/{orderId}/refunds/cancelled-paid`, JSON
`{"expectedOrderVersion":1,"proofReference":"..."}`, required `Idempotency-Key`.
No client amount/payment/actor/status. Returned-COD route and canonical payload stay unchanged.
Current persisted ACTIVE ADMIN only; original-ADMIN exact replay; global operation/key lock,
Order -> optional ReturnRequest -> Payments/refund -> Account locks. Reject an outer transaction.

Require CANCELLED, matching version, no delivery marker/history, cancellation history and
once-only release marker, and no ReturnRequest. Require exactly one valid persisted PAID Payment,
positive whole-VND amount equal to persisted grand total, nonexpired, and paid before cancellation.
ONLINE evidence must have a nonblank persisted provider reference; no callback or PAID writer is added.
Amount is the full persisted grand total including shipping. No Payment, Order, stock, voucher,
Shipment or cancellation-history rewrite; Order stays CANCELLED and its version is unchanged.
Successful financial evidence/audit commit together; a distinct financial event is after-commit only.
No fake lifecycle transition or repeated cancellation notification is produced.

## Draft migration reservation (QD review before merge/shared use)

`V20261028081110__td_qa02_cancelled_paid_refunds.sql`, timestamp from inspected UTC clock.
Latest fetched develop has 27 migrations through `20261027100000`; no version collision found.
TD owns refunds; database convention permits owner migrations with QD ordering review before merge.
Only a new migration extends operation-specific nullable receipt fields and cancellation snapshots;
existing successful returned-COD rows/checksums and append-only/unique/composite-FK protections remain.
No applied migration or shared schema/data is edited. Only disposable PostgreSQL receives this draft.

## Preserved baseline evidence

`.work/qa02-postmerge-20261028-145420/`: focused 52 unit + 522 PostgreSQL PASS;
full 686 unit + 1320 PostgreSQL = 2006 PASS; Node 33 PASS; exits 0, zero F/E/S, timeout=false.
The original Master Plan was located at `C:/Users/Tender/Documents/UTEExpress_Master_Project_Plan.docx`,
SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
FR-24 / I6 / PAY-02 require the additional scenario; current HP decision settles its QA-02 scope.
Existing full shared seed is absent; partial shipping seed repeated without duplicate rows/audits,
while combined clean startup fails closed for a missing approved Shop. A scoped synthetic refund
seed module will remain separate from QD's shared orchestration; no existing owner seed is overwritten.

## Acceptance matrix

Statuses below distinguish merged develop from this uncommitted patch. PASS means executed
local evidence, not merged acceptance. The original scope question is superseded by HP's
current instruction; no additional VNPAY Sandbox implementation is required for QA-02.

| Requirement | Develop at fa48cb6 | Local patch | Implementation / executed evidence | Remaining action / owner |
|---|---|---|---|---|
| Returned-COD full refund and negatives | PASS | PASS | Existing `ManualCodRefundIT` 62; unchanged route/canonical bytes, receipt/PAID/amount/ADMIN guards | Include final integrated run after publication / TD |
| Cancelled-paid refund before delivery | NOT IMPLEMENTED | PASS | New `CancelledPaidRefundService`, `CancelledPaidRefundPayments`, `CancelledPaidRefundIT`; real checkout and Buyer cancellation around synthetic persisted PAID evidence | Review/publish/integrate / TD, HP |
| Valid PAID, timestamps, reference, full amount | NOT IMPLEMENTED for cancellation | PASS | Missing/UNPAID/expired/late/invalid collection, mismatched/zero/fractional amount, delivery/history/release and money guards | No gateway verification is claimed |
| Authorization, ownership boundary and CSRF | Returned-COD PASS | PASS | ADMIN only; USER/VENDOR/MANAGER/SHIPPER and anonymous denied; real signed JWT plus GET-issued cookie/rendered masked CSRF; current persisted role/account rechecked | No security configuration changed |
| Proof, client forgery and stale version | Returned-COD PASS | PASS | Blank/NUL/malformed proof, missing key, duplicate headers, forged amount/actor/payment/status/return fields and stale version rejected | Proof is recorded manual evidence, not third-party transfer verification |
| Duplicate/concurrent refund protection | Returned-COD PASS | PASS | Seven real PostgreSQL contender schedules plus eight role/account revocation schedules; actual blocker PIDs/SQL, not sleep-selected races | Preserve unique Order/Payment/key and same-original-ADMIN replay |
| Atomic rollback and after-commit visibility | Returned-COD PASS | PASS | Injected faults after ledger/audit and before commit; retry reuses key; separate-connection observer sees committed ledger/audit | No event on rollback/replay |
| Inventory/voucher and financial snapshots | Returned-COD PASS | PASS | Cancellation releases once; refund/replay preserve Order/items/Payment/stock/usage/Shipment/history/inbox | Returned voucher remains REDEEMED; cancelled voucher remains RELEASED |
| Revenue | Returned-COD PASS | PASS | Existing Vendor/Admin revenue suites plus cancellation collection/refund net-zero, signed cross-period refund, shipping exclusion, zero unearned commission and unavailable invalid collection evidence | No current catalog/policy recalculation |
| Notification transaction semantics | PASS for existing lifecycle | PASS for preservation | Cancellation inbox/history unchanged; distinct financial event after commit only; no second lifecycle notification | No new financial inbox policy is introduced |
| Fresh/upgrade migration | No cancellation ledger operation | PASS | New migration suite fresh + populated 27-migration upgrade, prior checksums/results preserved, rerun=0, conditional evidence/FKs/uniqueness/immutability | QD ordering/schema review before shared use/merge |
| Reproducible refund seed | NOT IMPLEMENTED | PASS, scoped module | New disposable-only driver and test-classpath fixture module; two executions; six scenarios; negative checks and exact all-business-table rerun digest | QD orchestration review; this is not the full M6 seed |
| Full Java/PostgreSQL and Node regression | PASS, preserved baseline | PASS | Final 690 unit + 1383 PostgreSQL; 33 Node; exact input digest below | Fresh develop run still required after this patch is merged / TD |
| Exact-head CI for local patch | NOT RUN | NOT RUN | Uncommitted/unpublished branch cannot have a CI run | Publish after TD approval; HP reviews CI |
| VNPAY Sandbox/callback verification | NOT RUN | NOT RUN, PAY-03 DEFERRED | No callback, sandbox call or production PAID writer added | PAY-03 / TD; explicitly outside approved QA-02 scope |

## Source and commands

During test execution, local HEAD was the base develop SHA plus the uncommitted patch.
The subsequent publication commit SHA and CI result are recorded in the review PR.
The executable-input digest binds all tracked/untracked nonignored `src/`, `scripts/qa/`,
Maven and workflow inputs. Documentation-only edits are excluded from that digest.
Final input digest: `3415093463360ea0b76576a4e4b5b40cc48d5f516e457a14a9d0a8504e3ecbcf`.
This hashes the tested Windows working-tree bytes. Git normalizes some text files to LF;
every staged executable input was compared with the tested file and differed only by CRLF
normalization where applicable. Both the canonical-LF tested-input digest and staged Git-blob
digest are `f332e1dccd768d627a4d38e4f2095abe5e85f736792c20ff5e19f780f7e60b75`.
The manifest's individual file hashes describe working-tree bytes, not normalized blob bytes.
Raw manifests/logs/XML are private local evidence under
`.work/qa02-postmerge-20261028-145420/`; they are not publication files.

Environment: Windows, Java 21.0.12.1, Maven Wrapper 3.9.11, PostgreSQL 17.6
Testcontainers/Docker, Node 22.11.0; application/test JVM timezone UTC.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dit.test=CancelledPaidRefundIT,CancelledPaidRefundMigrationIT,ManualCodRefundIT,RefundLedgerMigrationIT,ReturnReceiptMigrationIT,ReturnToSenderMigrationIT,VendorRevenueIT,AdminRevenueIT' clean verify
python scripts/qa/refund_acceptance_seed.py --execute
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/buyer-cancellation.test.cjs src/test/js/notifications.test.cjs
git diff --check
```

The seed requires the current packaged JAR and compiled test helper. Focused `clean verify`
above provides both; an alternative build is `.\mvnw.cmd -DskipTests package`.
The executed final seed used `--output .work/qa02-postmerge-20261028-145420/refund-seed-final-3`.
The Python runner records the exact full/Node commands, return codes and timestamps;
it does not rerun the already verified, unchanged develop baseline.

## Focused verification and preserved development failures

Final focused log: `refund-focused-final-4.log`; XML archived in `refund-focused-final-4/`.
The final focused command executes **all 690** unit/HTTP tests before the selected
PostgreSQL classes; earlier runs selected only 57 units and are retained separately.
Exit **0**, Surefire **690**, Failsafe **205**, zero failures/errors/skips; Failsafe timeout=false.
PostgreSQL suites: cancelled-paid **61**, returned-COD **62**, Vendor revenue **46**,
Admin revenue **26**, old refund migration **3**, receipt migration **3**,
Return-to-Sender migration **2**, new refund migration **2**. Counts overlap the full run.

Earlier attempts remain under separate `refund-focused-*` paths. They exposed an incorrect
assumed PostgreSQL CHECK name, fixture code-case/commission uniqueness mistakes, an invalid
test Payment enum literal, and an inconsistent Order fixture rejected by `ck_orders_money`.
These were corrected using the actual schema/enum; the Order arithmetic DB guard is now
asserted explicitly. No constraint, applied migration, authorization or existing test was
weakened. The first full attempt then found missing persistence dependencies in the
HTTP-only `test` profile: Surefire 690, errors 126, failures/skips 0; PostgreSQL/Node
were NOT RUN. Its log/XML and original input digest remain at `refund-full-final*`.
The private evidence collector also initially assumed Failsafe reports existed after
an early Surefire failure; this collector error is preserved and corrected separately.
Only the two new services now follow existing `OrderEligibilityService` ObjectProvider
wiring: real dependencies remain mandatory on mutation, while the persistence-free
HTTP context can boot. No existing test mocks/assertions or security profile changed.
The final focused run verifies that correction. Passing superseded runs are not summed.

## Final full regression

| Executed check | Tests | Failures / errors / skips | Exit | Timeout |
|---|---:|---|---:|---|
| Surefire, 74 classes | 690 | 0 / 0 / 0 | 0, full Maven command | false |
| Failsafe PostgreSQL, 64 classes | 1383 | 0 / 0 / 0 | 0, same command | false |
| Java total, one full run | **2073** | **0 / 0 / 0** | **0** | **false** |
| All frontend/Node files | **33** | **0 / 0 / 0**, cancelled/todo 0 | **0** | **false** |
| Final refund seed, two application invocations | 6 scenarios + negative/replay checks | No failed checks | 0 / 0 | No timeout |

Full Maven execution: **2026-10-28 10:32:05.509–10:40:18.478 UTC**.
Node: **10:40:19.473–10:40:19.609 UTC**. Inputs before/after are identical and match
the final seed digest. Local HEAD remains fa48cb6 plus the uncommitted patch; this
is not represented as a new commit SHA or a run on unmodified develop.

Evidence: `refund-full-final2.log`, `refund-full-final2/{surefire-reports,failsafe-reports}`,
`refund-node-final2.log`, `refund-final2-regression-results.json`, and
`refund-final2-source-{before,after}.json` under the private evidence root.
The [sanitized evidence manifest](QA-02-cancelled-paid-refund-evidence.json) records
commands, counts, suite identities/XML hashes, source binding and seed/CI provenance.
Focused/full runs overlap and are not added. No flaky/rerun XML markers were found.

Flyway/Hibernate validation passes: **28 numbered migrations**, latest
`20261028081110`; fresh schema applications, populated upgrade preservation and
repeat migrate=0 are verified. The validation log's 29 entries include the schema
creation marker; there is no extra versioned/repeatable migration.

Diagnostics are retained, not suppressed: **208 WARN/WARNING lines**, including
82 existing AuthenticationProvider/UserDetailsService configuration warnings,
negative-case constraint violations, unsupported-method checks and seven expected
SQL lock-timeout messages. These do not mean the Maven/Failsafe process timed out.
There are **0 closed-connection lines and 0 fork-shutdown lines** in this final log.
No test deadlock or flake was observed; no security configuration was changed to silence logs.

Patch inventory: **24 files** — 12 production Java files, one new TD migration,
seven test Java files (including three migration inventories and the test-only seed helper),
one Python seed driver, and three documentation/evidence files. The evidence manifest's
`changedFiles` maps every other changed file to its SHA-256; it excludes its own hash.
Existing applied migrations, security/Shipping/Admin production modules, shared seeders
and PR #67 browser artifacts are unchanged. The whitelist/whitespace/generated-file/secret-
pattern review passes with no findings; the index is empty. Raw logs and build output remain ignored.

## Reproducible synthetic refund seed

`scripts/qa/refund_acceptance_seed.py` starts a new owned loopback PostgreSQL 17.6 container,
generates credentials/secrets in memory, migrates the empty database, runs the test-classpath
`qa02.seed.RefundAcceptanceSeed` twice, checks row counts/digests and removes only its owned
container. Production startup/JAR contains no seed helper or new PAID writer. The explicit
`--execute` flag, disposable database-name/loopback checks and non-production profile guards
are mandatory. Never point this module at a shared or production database.

| Fixture | Order | Payment | Refund | Voucher |
|---|---|---|---|---|
| returned-ready | RETURNED | COD PAID via real Shipper delivery | None, eligible | REDEEMED |
| returned-refunded | REFUNDED | COD PAID | SUCCEEDED | REDEEMED |
| cancelled-paid-ready | CANCELLED before delivery | Synthetic persisted ONLINE PAID | None, eligible | RELEASED |
| cancelled-paid-refunded | CANCELLED before delivery | Synthetic persisted ONLINE PAID | SUCCEEDED | RELEASED |
| cancelled-unpaid-negative | CANCELLED | COD UNPAID | Rejected | RELEASED |
| paid-new-negative | NEW | Synthetic persisted ONLINE PAID | Rejected | REDEEMED |

All Orders are created by real Cart/Checkout services. Return/Shipping/Cancellation transitions
use their public services. Only fresh synthetic Payment prerequisites are set to ONLINE/PAID
in this explicitly owned disposable fixture database. This is not a simulated gateway callback.
The helper also exercises MANAGER denial, blank proof denial and both successful refund replays.

Final seed result: `refund-seed-final-3/seed-results.json`, **PASS**, two application exits **0**.
Six users and six user-role links (five role codes), one shop/category/provider/rate/policy, six products/orders/items/payments/
vouchers/usages, two return requests/shipments/refunds, 22 status histories, 32 notifications
and 10 audits. All **262** PostgreSQL constraints validated. Rerun preserves exact business-table
values, including password hashes/roles, financial and lifecycle progress, stock and a deliberate
catalog edit. Private values are hashed in memory, not exported. Owned container removed.

This TD refund fixture module is a proposed input to shared seed stage 10. Existing Product and
Shipping seeders and `SEED_MANIFEST.md` are unchanged. QD owns orchestration; HP owns identity/
catalog fixtures. During the final fetch, QD's draft
[PR #69](https://github.com/hphuc032/Logistic-UTEExpress/pull/69) became available at
`69addc0f9d96105fcc4aab067bd968812515c3b4`; it includes form-fix dependency PR #68.
Its source-bound report describes 10 actors, four shops, 14 products and nine Order scenarios,
returned-COD refund, and a completed-checkpoint validate-and-preserve rerun. These are QD's
published execution results on `8bab8dd9358a9f6fef022ec2c4e489e2f0c26fb5`, not a new execution
on develop or this patch. The runner contains no cancelled-paid scenario and asserts exactly
one returned refund with a REFUNDED Order, so it must not be treated as supporting this
new ledger operation yet. PR #69 remains Draft/unmerged and its bootstrap/rerun semantics
need TD/HP review. No owner branch/file is modified or shared data overwritten.

QD handoff: retain PR #69's existing fixtures and assertions for returned-COD; add an explicitly
synthetic valid PAID-before-cancellation fixture using this reviewed TD operation, then extend
operation-specific ledger/Order assertions and Vendor/Admin financial expectations. Review
the owned-database Payment fixture provenance with HP; never add a fake provider callback or
production PAID writer. Run the combined seed from clean PostgreSQL, rerun with edits/progress
preserved, validate all constraints, and bind the result to the integrated source. The final
combined M6 result remains NOT RUN; it cannot be inferred by adding two separate seed results.
This handoff is prepared for TD/QD review; no external message or publication is sent.

## Publication and acceptance gates

PR #67 is merged at `fa48cb69cef92276f0dddf0d2bd396eee098f2f3` (2026-10-28 07:39:26 UTC).
Q02-F05 remains resolved by #63 and Q02-A01 by #67; fresh full `BuyerCancellationIT`
is **50 PASS**, `BuyerOrderIT` **35 PASS** and `VendorOrderIT` **73 PASS**.
The Master Plan is now located (Q02-F01's missing-document cause),
but locating it does not close whole-plan acceptance. Existing finding IDs are not repurposed.

GitHub Actions has **no run** for literal develop SHA fa48cb6 (fresh API query). Existing
[PR #67 CI run 40648207619](https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40648207619)
is SUCCESS for PR HEAD `502cf5f`; its tested merge tree equals the final develop tree. It is
historical integration evidence, not exact-head CI for this uncommitted patch. The current
workflow triggers on pull requests only and does not run the PostgreSQL profile.

Remaining gates, in order:

1. Review the verified local patch/evidence / TD.
2. QD review of new migration ordering/constraints and refund seed module handoff.
3. Review-only publication authorized by TD; request QD and HP review on the published PR.
4. CI on the published patch, HP review, and a fresh integrated develop regression after merge.
5. QD/HP/TD complete shared M6 seed coverage; HP final QA-02 acceptance.

No new refund browser workflow or real payment-provider transfer was executed. The merged
Buyer cancellation browser evidence remains separate (22 automated + 3 TD-attested manual,
36 preserved screenshots); it is not evidence of the new refund endpoint.
QA-02 remains **IN PROGRESS**. The implementation phase stopped before publication;
TD subsequently authorized commit/push and a non-Draft review PR, without approval or merge.

### Review-publication authorization and preflight

TD explicitly authorized publication of these 24 files for technical review. No application,
test, migration or seed input changed after the recorded passing regression: executable-input
digest remains `3415093463360ea0b76576a4e4b5b40cc48d5f516e457a14a9d0a8504e3ecbcf`.
Existing raw evidence, earlier failures/corrections and original pre-publication report/manifest
are preserved under the ignored evidence root. CI NOT RUN entries above are historical
pre-publication snapshots; use the PR's published HEAD and Actions run for current CI status.

Fresh comparison against PR #69 HEAD `69addc0f9d96105fcc4aab067bd968812515c3b4` finds no
overlapping changed paths and no migration added/modified by that PR. Existing returned-COD
rows and the old API/operation remain compatible with this new conditional CHECK migration.
However, #69's `refund_bound_to_paid_order` assertion assumes every refunded Order is REFUNDED,
and its refund-count/money assertions assume one returned-COD refund. QD must extend those
assertions by operation before adding cancelled-paid fixtures. Combined seed execution remains
NOT RUN. PR #69 is neither modified nor merged by this publication.

Requested review roles: TD (`Tender0603`) technical review, QD (`datcaq`) migration/seed
compatibility review, HP (`hphuc032`) final review. GitHub does not permit requesting a PR
author's own review; TD's review role is retained explicitly in the PR description when
publication is authored by `Tender0603`. Publication is not acceptance or merge authorization.

## Existing finding reconciliation

This supplements historical reports without silently rewriting their original execution state.

| Finding | Severity | Current assessment | Owner / fix / evidence |
|---|---|---|---|
| Q02-F01 | Medium | Master Plan located; overall acceptance/Task Board handoff not automatically closed | TD/HP; original DOCX hash above, current HP scope decision |
| Q02-F02 | Low | RESOLVED, preserved | TD; original QA matcher/exception/fixture corrections, passing regression |
| Q02-F03 | Low | Prior teardown issue mitigated; no recurrence in final run; historical root-cause closure not asserted | QD/QA-03; #64, 0 closed-connection/fork-shutdown lines now |
| Q02-F04 | Medium | OPEN publication/CI/final-acceptance gate for new patch and whole QA-02 | TD publication, HP acceptance |
| Q02-F05 | High | RESOLVED; no recurrence after #67 integration or this patch | TD/HP; #63, fresh Buyer cancellation 50 and full 2073 PASS |
| Q02-F06 | Low | RESOLVED environment limitation; no recurrence | TD; Node 33 PASS, exit 0 |
| Q02-F07 | Low | RESOLVED historical test import error | TD; full clean compilation/regression PASS |
| Q02-A01 | High | RESOLVED for Buyer cancellation control | TD/HP; #67 merged/approved, historical 25 browser cases and 36 preserved screenshots; fresh Buyer tests PASS |

No existing unresolved Critical/High finding is silently closed. The newly approved
cancelled-paid scope gap is locally fixed/verified but still unmerged; it has no invented
finding ID. QD shared-seed integration and TD/HP publication/acceptance remain explicit gates.
