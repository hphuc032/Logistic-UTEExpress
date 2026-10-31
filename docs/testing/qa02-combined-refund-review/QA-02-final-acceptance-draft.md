> Historical combined-verification record. TD subsequently authorized this sanitized Draft review publication only. Publication HEAD/CI is separate from the tested combined tree; no approval, merge or HP final acceptance is claimed.

# Draft QA-02 Final Acceptance Report for HP — combined refund integration

**Decision: BLOCKED for final acceptance. QA-02 remains IN PROGRESS.**
The disposable combined candidate passes the checks below; public #69 still needs owner adoption
of the operation-specific assertions, QD's verdict and HP's scope decision. During that verification, no commit, push,
merge, PR approval, publication or QA-02 status change was performed.

## Sources and review/integration compatibility

Current inspected develop: `fa48cb69cef92276f0dddf0d2bd396eee098f2f3`; merged PR #67 remains integrated.
GitHub inspection/review evidence: `evidence/github-sources.json` (including HP's exact-head
[conditional review](https://github.com/hphuc032/Logistic-UTEExpress/pull/70#pullrequestreview-5683889322)).

| PR | Exact HEAD | Merge state | Review state |
|---|---|---|---|
| #68 | `e79b760d1e48f0532e6cb132e76ffa44e53dbf78` | Open; unmerged | No submitted review |
| #69 | `69addc0f9d96105fcc4aab067bd968812515c3b4` | Draft/open; unmerged | No submitted review |
| #70 | `688884cd33f4b2f4dc62f5d33a4c4002e9146660` | Open; unmerged | HP COMMENTED conditional business pass; no approval |

| Compatibility | Status | Evidence / limitation |
|---|---|---|
| #68 application + #69 | PASS | src/POM/Maven/workflows identical; #68 latest HEAD is not an ancestor because of later evidence-only commits |
| #69 + #70 file/migration composition | PASS | Disjoint changed paths, no competing #69 migration; no owner branch edited |
| Unchanged #69 blanket refund assertion on valid cancelled-paid fixture | FAIL | Real SQL probe returns one invalid row solely because Order remains CANCELLED |
| Proposed operation-specific assertions/runtime rehearsal | PASS local | Separate adapter over unchanged #69 runner; not an accepted upstream fix |
| QD scoped migration/seed approval | NOT RUN | No QD review submission exists at inspection; this report does not approve owner work |
| Combined GitHub CI | NOT RUN | No combined commit/PR exists; upstream CI is separate |
| Post-merge develop acceptance | NOT RUN | #68/#69/#70 remain unmerged |

Existing upstream CI: #68 run40672602359, #69 runs40672692391/40672692444,
#70 runs40675787674/40675787743 SUCCESS at the inspected HEADs. None is combined-source CI.

## Exact tested candidate identity

**Source SHA: Git tree `bd0644ba373c5273a09e8af4898300dc4bdd8680`**, not a fabricated merge commit.
SHA-256 frozen file manifest: `7a21a0278f3be9a214423c59a463f3e3fa66f131946c0bbec0728feda45d7662`.
The tree-hash algorithm was checked against the real upstream #69 tree.
Composition: exported #69 + latest #68 evidence/identical app changes + disjoint #70 files +
`scripts/qa/rehearse_combined_refunds.py`, the explicit proposed test adapter.
There is no new Git worktree, branch, commit or merge. Existing worktree changes are preserved.

Every source file remains identical before/after final focused, full and seed execution.
The source tree hashes actual exported bytes, including Windows Git archive CRLF conversion.
The #69 runner equals its upstream Git blob after LF normalization; both raw hashes are recorded.
The exported-source hash guard replaces the runner's Git-clean-checkout guard for this
commit-free candidate; application authentication, roles, sessions and CSRF remain enabled.
Master Plan DOCX SHA-256 independently rechecked:
`18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
I6 distinguishes returned REFUNDED from cancelled CANCELLED; I7 supplies release/revenue rules.
The current HP decision defers VNPAY Sandbox to PAY-03.

## Refund evidence matrix

| Requirement | Returned-COD | Cancelled-paid | Fresh combined evidence |
|---|---|---|---|
| Successful completion and correct Order status | PASS: REFUNDED | PASS: CANCELLED/version unchanged | Real authenticated seed endpoints; independent IT success cases |
| Valid PAID evidence and same-Order ledger | PASS: COD PAID | PASS: synthetic persisted ONLINE PAID prerequisite | Exact amount/timestamps, existing Payment unchanged; no provider/callback verification claimed |
| Old refund records preserved | PASS | PASS | Old ledger row compared before/after new completion; populated migration upgrade |
| Proof/reference and amount limits | PASS | PASS | Returned IT62, cancelled IT61; blank/invalid proof, excess/mismatched/fractional/zero/forged amount cases |
| Authorization, ownership boundary, CSRF | PASS | PASS | Persisted ACTIVE ADMIN; non-ADMIN/anonymous/CSRF denials, real-cookie/rendered token cases |
| Duplicate/concurrent request protection | PASS | PASS | Fresh PostgreSQL blocker schedules; simultaneous same-key real HTTP completion/replay, one ledger/audit |
| Atomic rollback | PASS | PASS | Fault-stage integration tests; zero effects/events on rollback, retry succeeds |
| Inventory/voucher and snapshots | PASS: no return/refund restock; usage stays REDEEMED | PASS: cancellation releases once; refund/replay leave RELEASED/stock/history/inbox unchanged | Existing returned fixture preserved; new product returns to50, original promoted product remains38 |
| Revenue and commission | PASS | PASS | Vendor A and global Admin checks below; signed/invalid-evidence cases in fresh full suite |
| Notification semantics | PASS | PASS for existing-state preservation | Inbox/history unchanged by cancelled-paid refund; distinct financial event after commit; no new financial inbox policy claimed |
| Actual refund browser acceptance | NOT RUN | NOT RUN | Seed uses real HTTP, not browser UI; historical #67 screenshots are separate |

Returned-COD recorded refund is185000 (170000 merchandise); cancelled-paid is205000
(190000 merchandise). Both Payments remain PAID and both ledgers SUCCEEDED. Only the returned
operation has ReturnRequest evidence and an Order status REFUNDED.

Seed actual negative HTTP codes: Buyer/Manager/Vendor403; anonymous401; missing CSRF403;
missing proof400; amount override400; stale version409; different-key duplicate409;
same-key changed proof409. The two simultaneous requests return200 with replay=false/true,
each amount205000, and exactly one new ledger/audit.

## Shared seed and financial results

Clean PostgreSQL17.6 startup applies **28 numbered migrations**;29 history rows include
the schema creation marker. All constraints are validated. Fresh disposable fixtures:
10 OTP-verified actors,4 shops,14 products,10 Orders/Payments/items/usages,2 refunds,
48 notifications and 42 audit rows. The original nine scenarios and records are retained.

| Metric | Verified Vendor A and Admin global |
|---|---:|
| Delivered Sales | 340000 |
| Collected merchandise | 530000 |
| Merchandise refunds | 360000 |
| Net Collected Revenue | 170000 |
| Net Commission | 11900 |
| Net Revenue After Commission | 158100 |

Every asserted metric is complete with zero unknown events. Shipping is excluded and the
undelivered cancellation earns/reverses no commission. Cash net and delivered-sales metrics
retain their distinct documented definitions; signed values are not clamped.

Checkpoint-backed second invocation PASS: **34 table fingerprints/counts unchanged**, a legitimate
product edit retained, and **0 second-pass POSTs**. Fingerprint:
`57a7fc70c4eea97faa19a256b13962f169973571d08d2e992642fc228988f957`.
This is the existing completed in-memory checkpoint scope, not lost-checkpoint recovery or a
cross-process resumable shared installer. Its acceptance remains with TD/QD/HP.

The initial ADMIN role bootstrap remains #69's explicit exception. The new paid prerequisite
uses one clearly labelled synthetic Payment in the newly owned database. Neither bootstrap is
a production authorization path, VNPAY callback or real transfer. HP must review these policies.
The owned app/container were stopped/removed; existing manual app/data were left untouched.

## Fresh regression on combined source

| Check | Unit | PostgreSQL | Other | Exit | Failures/errors/skips | Timeout |
|---|---:|---:|---|---:|---|---|
| Final focused | 11 | 130 | Same final tree | 0 | 0/0/0 | false |
| Full clean verify | 690 | 1388 | **2078 Java total** | 0 | 0/0/0 | false |
| All Node/frontend | — | — | 33 PASS | 0 | 0/0/0 | false |
| Combined seed + checkpoint rerun | — | — | PASS;10 Orders,2 refund operations | 0 | No failed corrected checks | No timeout |

Final focused: 2026-10-28T15:06:39.557163+00:00 to 2026-10-28T15:07:47.257490+00:00.
Full: 2026-10-28T15:07:47.569049+00:00 to 2026-10-28T15:15:59.203174+00:00. Node: 2026-10-28T15:16:00.796613+00:00 to 2026-10-28T15:16:00.939561+00:00.
No flaky/rerun XML markers. Fresh source tests include form fix5, Buyer cancellation50,
returned-COD62, cancelled-paid61, migration upgrades, lifecycle/RTS, stock/voucher,
revenue, authorization/CSRF, notification inbox and socket after commit.
No PR #70-only results are used as combined regression proof.

Commands (Maven/Node cwd=`candidate`; JAVA_HOME=Temurin21.0.12.101-hotspot):

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=ArchitectureTest,CancelledPaidRefundPayloadTest' '-Dit.test=ManualCodRefundIT,CancelledPaidRefundIT,CancelledPaidRefundMigrationIT,PromotionBrowserFormsIT' clean verify
python -B scripts/qa/rehearse_combined_refunds.py --disposable-demo --output <runtime>/evidence/seed-attempt1
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/buyer-cancellation.test.cjs src/test/js/notifications.test.cjs
```

The seed executes before the final focused/full sequence so Maven clean cannot disturb a
running JAR. All final results bind to the same final tree. Earlier exploratory focused
690+376 passed on tree5e230e9 before a proposal-only brace typo correction. Its Java/test/POM
objects equal the final candidate; the final focused run was repeated on the final tree.
The original draft/AST error and all logs are retained. No tests were weakened or failures hidden.

Final diagnostics: 209 WARN/WARNING lines,
0 closed-connection lines,
0 fork-shutdown lines. Negative constraint/lock-timeout tests
and existing auth configuration warnings remain visible; process/Failsafe timeout is false.
Full suite details, exact commands and hashes are in the evidence JSON, not inferred counts.

## Migration observations and required QD changes

See [QD handoff](QD-shared-seed-handoff.md) for the exact10-point seed correction and migration
review list. V20261028081110 keeps old returned-COD nonnull requirements in its conditional
CHECK, preserves all old rows/checksums, keeps unique/FK/append-only protections, and adds
distinct cancellation snapshots/unchanged result version. Fresh/populated upgrade and rerun
tests pass. PostgreSQL's original cross-column CHECK name is verified, not guessed.
These are technical observations, **not QD migration approval**.

The original #69 blanket REFUNDED/one-refund assertion is a confirmed integration failure
with the new valid operation. Its public source remains unchanged. The local adapter proves
a proposed correction; it does not silently close the finding or make #69 merge-ready.

## Blockers and owners / HP acceptance conditions

| Blocker | Status | Owner | Smallest next action and verification |
|---|---|---|---|
| Shared seed operation-specific assertions/manifest absent from #69 | FAIL upstream; proposal PASS local | QD with TD | Adopt reviewed adapter logic; rerun clean combined seed, finance, duplicates and checkpoint checks |
| QD scoped migration/seed verdict | NOT RUN | QD | Review conditional CHECK/null FK/uniqueness/old ledger compatibility and issue exact-head verdict |
| Fixture/bootstrap/checkpoint scope acceptance | NOT RUN | HP/TD/QD | Explicitly accept owned-DB initial ADMIN and synthetic PAID prerequisites and bounded checkpoint semantics |
| #68/#69/#70 technical/final reviews and integration | NOT RUN | TD/QD/HP | Review exact sources and dependencies; publication/merge need separate authorization |
| Combined published-head CI | NOT RUN | TD after authorization | Publish approved changes/evidence, verify exact-head CI; upstream successes are not substitutes |
| Latest develop post-merge full regression | NOT RUN | TD | After owner merges, clean Java/PostgreSQL + Node and shared seed on exact develop SHA |
| Final QA-02 acceptance | NOT RUN | HP | Resolve required evidence/owner gates and provide explicit final acceptance |

No invented finding IDs or silent closures. Q02-F05 remains RESOLVED with fresh Buyer50 coverage;
the Buyer UI fix #67 stays integrated. The shared-seed mismatch is not given an invented ID.
Documentation follow-up: DASH-01's historical COD-only event descriptions should be reconciled
with the approved cancelled-paid operation after review; current SQL/DTO formula definitions
and the verified I7 cash metrics are stated explicitly here.

**VNPAY Sandbox, gateway callbacks and real transfer verification remain PAY-03 DEFERRED**;
they are not mandatory missing QA-02 implementation under HP's decision.

Evidence: [sanitized packet](QA-02-combined-evidence.json), `evidence/source-freeze.json`,
`evidence/github-sources.json`, `evidence/focused-final*`, `evidence/full*`, `evidence/node*`,
`evidence/seed-attempt1/result.json`. Raw logs/XML/HTTP bodies stay local/private; no passwords,
OTPs, cookies, tokens or reusable hashes are proposed for publication.

HP acceptance/signature: **not supplied**. QA-02 remains **IN PROGRESS**.
TD authorized Draft review publication after that verification. Owner adoption, integration and HP acceptance remain open.
