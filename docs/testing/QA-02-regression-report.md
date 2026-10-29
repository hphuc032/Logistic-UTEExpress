# QA-02 regression report — Q02-F05 production correction

Current PR #68 continuation (2026-10-29): [gate report](QA-02-PR68-gate-report.md),
[source-bound evidence](QA-02-PR68-gate-evidence.json). The Q02-F05 report below is
a historical snapshot; its original counts and publication limits are preserved.

Base develop: `798535b32ff81a011f34cfa01bbeb2d2dd0cb981`. Exact tested source HEAD: `b6c832785d87c3af10008a7c357389ef685f2123`.
Branch: `fix/q02-f05-buyer-cancellation`; worktree: `C:/Logistic-UTEExpress/.work/q02-f05-fix`.
Owner/executor: TD. HP retains final acceptance and merge authority.
Q02-F05: **FIXED, LOCAL REGRESSION VERIFIED**. QA-02 remains **IN PROGRESS**, pending
publication/exact-head CI, broader original-plan comparison and HP final acceptance.
No QA-02 COMPLETED, remote CI or merge is claimed.

## Approval, source and isolation

Authority: user task states “HP đã chính thức APPROVED quyền triển khai production fix
Q02-F05” and specifies route/USER-only/status/version/ownership/transaction requirements.
`origin/develop` was fetched before creating the isolated worktree, at base above.
The original checkout remains on `test/qa-02-order-payment-regression` at
`d1cfaeaee2a5bb8afa4edab29efbf050a209620e`, including its uncommitted red reproduction,
reports/evidence and protected `tatus --short`. Original QA file hashes were recorded
and rechecked; no reset, clean, stash, deletion, edit or commit was applied there.
Protected-file SHA-256: `f4ce4efb9b7e36af6006529687b600abf8640da96b918a06fce2ed2b065804a4`.
Only selected QA tests/reports were copied into the separate fix worktree. The historical
1951/12 PASS and 8-case red reproduction remain historical, not this fix's test totals.


## Route, DTO and transaction decision

HTTP contract: `POST /buyer/orders/{orderId}/cancel`, JSON body
`{"expectedOrderVersion":0}`, success HTTP 200 `{"result":"OK"}`. HP approved production
implementation and left version placement open. JSON body was selected from existing
mutation DTO conventions; no header/query/form alternative or extra business fields.
Only authenticated USER authority is accepted, followed by locked persisted active USER
eligibility and Buyer ownership. VENDOR-only/Admin/Manager/Shipper are denied; foreign
and missing Order return 404 before state/version disclosure; stale/non-NEW/replay return
409. Missing/null/negative body version, unknown fields and conflicting query parameters
reject. Existing CSRF is mandatory. SecurityConfig is unchanged.

`OrderLifecycleService.cancelForBuyer` owns a fresh transaction and rejects an existing
outer transaction, including one holding Account. BuyerCancellationAuthority (MANDATORY)
scopes the Order lock to principal-derived `buyer_id`, refreshes under lock, then locks
active Account and rechecks persisted USER. Order state/version and release markers are
checked while locked. Existing Order-internal `OpsOrderAuthority.release` supplies only
inventory/marker/voucher effects; no Ops authorization/audit, Vendor authority or Shipping
operation is invoked. Restoration logic is reused rather than copied. The shared writer
persists Order/history and synchronous inbox in that transaction; committed events and
private broker dispatch occur only after commit. COD Payment evidence and financial/item/
commission/discount snapshots remain unchanged; no refund or new idempotency ledger.
The row lock plus expected version permits one successful cancellation. Replay conflicts
without a second stock/quota/history/inbox effect.


## Executed regression

Focused final command:

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dit.test=BuyerCancellationIT,Int02CommerceFlowIT,ReturnToSenderIT,VendorRevenueIT,AdminRevenueIT,VendorOrderIT' verify
```

Focused PASS exit 0: 682 unit/architecture + 199 PostgreSQL (6 classes), zero failures/
errors/skips/flakes. BuyerCancellationIT 33, Int02CommerceFlowIT 4, ReturnToSenderIT 17,
VendorOrderIT 73, VendorRevenueIT 46, AdminRevenueIT 26.
Earlier focused PASS (125 PostgreSQL) predates the Buyer financial checkpoint; not added.
Initial attempt failed test compilation because Import was missing. The import was fixed;
repository/EntityManager injection follows existing OrderEligibilityService ObjectProvider
conventions so persistence-free HTTP test contexts do not require unrelated fixtures.
No test was disabled or assertion weakened; no flaky retry flags were used.

Required full commands, executed in the fix worktree with Java 21:

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/notifications.test.cjs
git diff --check
```

Fresh full result: Maven exit **0**, Surefire **682** / 72 classes +
PostgreSQL Failsafe **1303** / 62 classes = **1985 Java tests**.
Node exit **0**, **12/12 PASS**. Zero failures/errors/skips/flakes/rerun markers;
Failsafe timeout=false, completed=1303. The success result attribute is `null` per the
installed Surefire API 3.5.6, failureMessage=nil. Focused counts overlap and are not added.
Environment: Windows PowerShell, Temurin Java 21.0.12.1+1 LTS, Maven Wrapper 3.9.11,
Spring Boot 4.1.1, PostgreSQL 17.6 Testcontainers on Docker Desktop Linux engine,
Node 22.11.0; JVM tests UTC, financial periods Asia/Ho_Chi_Minh.
Fresh/upgrade migration suites and Hibernate validate PASS; 27 fresh migrations through
`20261027100000`, populated history/checksum preservation and repeat migrate=0 verified.
No migration, financial formula, SQL projection or forbidden owner-module source changed.


Buyer regressions include real JWT, owned 200, foreign/missing 404, non-USER 403,
anonymous 401, missing/invalid CSRF 403, request validation, stale/non-NEW 409, current
persisted role/account denial, replay once-only, multi-item quantities/snapshots, unchanged
COD, inbox/history/voucher write failure rollback and retry. Both real HTTP contention
schedules assert backend PIDs, worker Order SQL and `pg_blocking_pids`: winner commit
returns 200 then waiter 409; winner pre-commit rollback returns injected 500 then waiter
200. External reads see pre-commit state; inbox exists inside the transaction while no
committed event/private broker send exists. Rollback emits none; final success emits two
private recipient sends and one lifecycle event. This verifies dispatch timing, not a
new browser socket-delivery/load guarantee. Existing NotificationSocketIT runs in full.

Actual JWT BUYER_CANCEL commerce variant checks foreign denial, successful cancellation,
replay 409, stock/quota once, immutable snapshots/UNPAID/no-refund and all six metrics
through Vendor and Admin shop-filter shared projections, complete known zero after cancel.
DIRECT/RETRY/RTS, global/multi-shop/multi-item, payment attempts, signed cross-period refund,
commission snapshots, shipping exclusion, precision/unknown evidence and Manager denial
remain covered by existing suites in the same full run. No separate financial SQL added.

## Evidence and remaining gates

Full start UTC `2026-10-27T09:09:31.072862+00:00`; collection `2026-10-27T09:19:54.421426+00:00`.
All executable/test/build input SHA-256 values and source HEAD were unchanged after
Maven/Node. Evidence JSON SHA-256 (canonical UTF-8/LF) `9366f2177c691832def01ad6acbefae1685c185fc0b64485be229ebb5dc876f5`; per-artifact hashes in
[manifest](QA-02-evidence-manifest.md). Reports were completed after execution. A
subsequent documentation-only publication commit has a separate final PR HEAD; no
new full run or exact-head CI on that documentation commit is claimed.

Diagnostics: 484 closed-connection lines and 1 30-second fork-shutdown lines.
They are disclosed separately from exit/XML success; clean teardown is not claimed.
Original Master Plan/Task Board absent; HP's explicit contract governs this correction.
No push, GitHub PR, remote message, CI execution or HP final acceptance claimed.
Local PR title/body prepared under `.work/qa02-fix/PR-body.md`; TD must authorize publication.
Browser/manual deployment, SMTP/gateway, ONLINE refund/PAY-03 and multi-instance load
are not run or introduced by this fix. See [findings](QA-02-findings.md).

## PR #68 regression continuation — 2026-10-29

Tested HEAD and published PR HEAD are both
`c593352492184da2a9ca46ebea4a55b802ac6416`. The existing full process completed
normally at `2026-10-29T03:52:32.388756+00:00`: **697 Surefire +1326 PostgreSQL
Failsafe =2023 PASS**, 0 failures/errors/skips/flakes, actual Maven exit **0**,
timeout=false, BUILD SUCCESS. Existing focused **697+138 PASS** and same-source
Node **33/33 PASS** were verified without rerun and are not added to the full total.
All 137 full suite XML hashes, summary/logs and 763 input-file hashes were checked.

CI [#155](https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40813357695)
SUCCESS is associated with that exact PR HEAD; actual checkout is synthetic merge
`da7329eefd66d4556d99b124625a72038c2d9b5c`. It passes 697 unit tests in each
test/package invocation plus 33 Node tests, without PostgreSQL integration.
Live PR remains OPEN/unmerged/mergeable; fix publication is verified. Its body still
contains the older source/counts, recorded as Low Q68-DOC-01.

Delegated technical review of controller binding, CSRF/validation, authorization,
ownership and business invariants is PASS; details, test boundaries and diagnostics
are in the linked gate report. Formal GitHub owner reviews remain absent and HP
acceptance is pending. Q03-G01 browser/manual and #70/#69 integrated/postmerge
execution are NOT RUN here. Historical FAIL attempts remain FAIL in the evidence.

Recommendation: **READY FOR HP MERGE DECISION** for #68. QA-02 remains IN PROGRESS;
no approval/merge or overall acceptance claimed. Evidence edits are local only.

## HP-requested handoff supplement

See the [separate-run comparison](qa02-pr68-handoff/run-comparison.md) and
[Git/input provenance](qa02-pr68-handoff/provenance.json). New 2023 PASS is bound
to exact `c593352` by the runner's before/after clean Git state, tree, actual
subprocess exit, hashed raw log/XML and 763 prior inputs checked against immutable
Git blobs. The older 2011 PASS remains on `8bab8dd`; historical raw log/XML are
not available for fresh rehashing, which is explicitly distinguished from the new
run's independently checked raw artifacts. HP scoped code review PASS is verified
as GitHub COMMENTED at 03:59:51Z, not APPROVED/final acceptance. Publication will
use a separate documentation branch so the code HEAD and its CI identity stay intact.
