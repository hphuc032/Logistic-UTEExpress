# QA-02 / PR #68 gate — 2026-10-29

**READY FOR HP MERGE DECISION** for tested source
`c593352492184da2a9ca46ebea4a55b802ac6416`. The automated gate and delegated technical
review pass with no remaining technical blocker identified in this bounded fix.
This is a recommendation for HP's decision, not owner approval or merge acceptance.
HP's scoped code review is PASS at this exact HEAD, as recorded in
[review 5692679850](https://github.com/hphuc032/Logistic-UTEExpress/pull/68#pullrequestreview-5692679850)
on `2026-10-29T03:59:51Z`. Its GitHub state is **COMMENTED**, expressly not APPROVED.
HP final acceptance and merge remain **PENDING**. QA-02 stays **IN PROGRESS**.

## Source, publication and GitHub CI

- Local HEAD and live [PR #68](https://github.com/hphuc032/Logistic-UTEExpress/pull/68)
  HEAD both equal `c593352492184da2a9ca46ebea4a55b802ac6416`; tree
  `1d545a3350963edf8ca46d5cae7284ce018467cb`.
- PR base/develop: `fa48cb69cef92276f0dddf0d2bd396eee098f2f3`. Local ancestry is
  zero behind / six ahead. PR is OPEN, non-draft, mergeable=true, unmerged.
  The disable-binding fix is already published on `fix/qa03-promotion-form-binding`.
  The earlier publication-denied record remains historical; it is superseded by
  this live observation, without attributing how the remote update occurred.
- [CI #155](https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40813357695)
  is completed/SUCCESS for this PR HEAD. Job `123248627092` passes Node **33/33**,
  Maven test and Maven package (**697 unit tests each**, overlapping executions).
  Checkout is GitHub's synthetic merge
  `da7329eefd66d4556d99b124625a72038c2d9b5c`, explicitly merging this HEAD into
  the base above. CI does **not** run `postgres-it`; local PostgreSQL proof is separate.
- Live PR metadata, review submissions and threads were checked at
  `2026-10-29 03:52:19 UTC`. No formal reviews or inline review threads were present;
  HP and TD are requested reviewers. The PR description still names historical
  `e79b760`/`8bab8dd` and 2,011 tests; those are not the current HEAD/full result.
  This local report supplies the corrected facts; no PR text was changed.

## Existing focused PASS and completed full run

The focused gate was reused without rerunning it: **697 unit +138 PostgreSQL PASS**,
exit **0**, zero failures/errors/skips. Its PostgreSQL suites are
PromotionBrowserFormsIT 6, ProductPromotionIT 59 and VendorVoucherIT 73.

The already-running full command was allowed to finish; no replacement run was started:

```text
cmd /d /c mvnw.cmd -B --no-transfer-progress -o -Ppostgres-it clean verify
```

Java 21.0.12.1, Maven 3.9.11, PostgreSQL 17.6/Testcontainers. Start:
`2026-10-29T03:44:51.535402+00:00`; process completion:
`2026-10-29T03:52:32.388756+00:00` (10:52:32 Asia/Saigon).
The runner records the actual `Popen.wait()` return value **0**, timeout=false;
the log ends **BUILD SUCCESS**, Maven duration 07:38. This is not an inferred exit
code from log text. Wrapper process status is not separately claimed.

| Executed scope | Tests | Failures / errors / skips | Exit |
|---|---:|---|---:|
| Full Surefire, 74 suites | 697 | 0 / 0 / 0 | 0, full Maven process |
| Full PostgreSQL Failsafe, 63 suites | 1,326 | 0 / 0 / 0 | 0, full Maven process |
| Full Java total | **2,023** | **0 / 0 / 0** | **0** |
| Existing same-source Node run | 33 | 0 / 0 / 0 | 0 |

Focused tests overlap full tests and are not added. Node was already PASS on this
exact source using `--experimental-test-isolation=none`; its original log, exit code
and input hashes were verified without re-execution. CI independently passes normal
Node isolation. Prior 1,994/2,011 counts remain historical for their recorded sources.

All **137 full XML suite hashes** and counters were recomputed, as were the **77
focused XML hashes**, logs and final Failsafe summary. Summary: completed=1326,
errors=failures=skipped=flakes=0, timeout=false, failureMessage nil, result="null"
as emitted by Surefire 3.5.6. No flaky/rerun failure/error markers were present.
All **763 executable/test/build input files** match the earlier same-source manifest;
Git executable paths are unchanged. HEAD and clean worktree status were captured
before and after the full run, before these documentation edits.

Full log SHA-256:
`b9a8b718e1580b121e93463973771aabd5f0eb8a145907a9bf5112c820e7b0e1`.
The [evidence JSON](QA-02-PR68-gate-evidence.json) preserves command records,
suite counters/hashes, GitHub metadata, history and 227 artifact checksums.

## Delegated technical owner review

Review covers the entire PR production diff against the pinned develop, plus the
latest disable fix against `e79b760`. The only production changes are three form
binders in VendorVoucherController and VendorProductPromotionController. They call
`setHeaderPredicate(header -> false)` on ExtendedServletRequestDataBinder. No
security configuration, DTO, service, pricing/quota/lifecycle algorithm, dependency,
workflow or migration changes are introduced by PR #68.

| Boundary | Review and verified behavior | Result |
|---|---|---|
| Controller binding | Voucher and promotion create/update, plus promotion disable, exclude HTTP headers from form values. Existing parameter allowlists remain. Disable accepts only `version`; a same-name header cannot supply or override it. Voucher `id` remains an ignored non-property; resource selection uses the path and owned query. Promotion rejects submitted path identifiers even if they match the URL. | PASS |
| CSRF / validation | Global cookie/rendered-token CSRF remains active; no exemption. Real HTTP missing-CSRF returns 403. Bean validation, suppression checks, nonnegative required version and stale-version conflict remain. Header-only fields and forged business parameters write nothing. Create/update validation may deliberately redisplay HTTP 200 with errors; disable invalid input returns 400. | PASS |
| Authorization | `/vendor/**` requires VENDOR, and both management service boundaries retain method authorization. Anonymous/non-Vendor roles and unapproved shops are denied by existing integration coverage. Real HTTP USER denial remains 403. | PASS |
| Ownership | Principal-derived Account/approved Shop and scoped Product/Voucher/Promotion queries remain. Catalog owns Product authority and locks Account → Product → Shop → Promotion; Voucher locks Account → Shop → Voucher. Foreign Product, mismatched Promotion, foreign/PLATFORM voucher return 404 before mutation. | PASS |
| Business | Promotion schedule/overlap/version, historical pricing, checkout recalculation/rounding, voucher history/quota and atomic stock/cart/usage effects remain. Full Order/Payment/return/revenue suites also pass. | PASS |

The latest real HTTP disable case proves foreign 404, USER 403, no-CSRF 403,
header-only version 400, extra discount parameter 400, no premature deactivate,
valid redirect/deactivation with unchanged Product/discount, and stale replay 409.
The 11-case MVC binding suite adds invalid versions and header-versus-form checks;
its mocks are not presented as authentication/persistence proof. Six real HTTP
tests use actual login/JWT cookies, rendered CSRF and PostgreSQL.

Business regression includes these completed suites (all are subsets of 1,326):
ProductPromotionIT **59**, VendorVoucherIT **73**, VoucherCheckoutIT **57**,
PlaceOrderIT **56**, BuyerCancellationIT **50**, CodPaymentIT **47**,
ManualCodRefundIT **62**, ReturnManagementIT **149**, VendorRevenueIT **46**,
AdminRevenueIT **26**, Int02CommerceFlowIT **4**, ReturnToSenderIT **17**.
Assertions cover promotion-before-voucher calculation, immutable snapshots and
idempotent replay after disable, quota contention/release and rollback, signed
cross-period revenue/refunds, unknown financial evidence and shipping exclusion.
Migration suites run in full, including existing fresh/upgrade ledger checks;
this does not claim the separate PR #70 migration has been integrated or tested.

This agent review completes the delegated technical inspection. It does not
represent a human TD/HP attestation and does not create a formal GitHub review.

## HP-requested evidence handoff — 2026-10-29

The initial observation above at 03:52:19 UTC predates HP's scoped COMMENTED review.
That review has now been retrieved and verified. Its source review PASS is recorded
separately from pending final acceptance; no APPROVED review is inferred or submitted.

The [historical/new run table](qa02-pr68-handoff/run-comparison.md) and
[machine-readable comparison](qa02-pr68-handoff/run-comparison.json) keep the
2,011-test execution on `8bab8dd` separate from the new 2,023-test execution on
`c593352`. Exactly 11 new unit cases and one new real HTTP disable case explain
the increase; every other suite count is unchanged. Historical raw logs/XML could
not be located in accessible QA-02 workspaces, so their hashes and exit0 remain
original published records, not newly reverified raw results. Historical manifest,
immutable source and runner identity/CRLF hash were verified without alteration.

The [new provenance manifest](qa02-pr68-handoff/provenance.json) independently
matches all 763 prior input hashes to immutable `c593352` Git blobs and all 137
full XML hashes/counters to the original run record. It includes the actual command,
before/after clean Git identity, actual process return code, build metadata and
sanitized testcase names. [Runner copy](qa02-pr68-handoff/run_gate.py) is included
for inspection. XML paths/build coordinates alone are not presented as SHA proof.

Publication targets a separate documentation branch
`docs/qa02-pr68-evidence-20261029`, based on `c593352`. The code PR branch is not
updated. Its CI remains associated with the same exact code HEAD; documentation
publication and PR description edits do not constitute a new code HEAD or a new
PostgreSQL execution. Remote publication/description results are recorded in the
handoff receipt; until read-back succeeds, publication must not be called complete.

The initial local-only continuation statements below describe the earlier run.
This handoff is separately authorized to publish sanitized evidence and edit the
PR description. No findings are closed, and no approval/merge is authorized.

## Findings, diagnostics and remaining decisions

- **Q68-BIND-01 — FIXED, EXACT-SOURCE VERIFIED:** the disable binder still allowed
  transport-header participation after the original create/update fix. The published
  `c593352` closes this functional gap; full/focused/CI checks pass.
- **Q68-DOC-01 — Low, OPEN:** PR description is stale (old source/counts). Corrected
  report/evidence is prepared locally. This is a documentation follow-up for the
  author, not evidence that the published source is untested.
- Historical Docker-related full/focused FAIL attempts and the denied publication
  attempt are preserved in the evidence history. The fresh Docker-authorized full
  PASS supersedes their current environment block; their exit 1 records remain FAIL.
- Current full diagnostics: **201** lines matching ` WARN ` or `[WARNING]`, including
  configured authentication warnings and deliberately injected constraint failures;
  Byte Buddy dynamic-agent warnings also remain. **0** Maven `[ERROR]` lines,
  **0** closed-connection lines, **0** fork-shutdown diagnostic matches. No warning
  suppression was introduced. Historical Q02-F03 teardown cause remains unproven;
  its old 484/1 observations are not relabelled as current or silently resolved.
- Formal HP/TD GitHub owner signoff remains **PENDING**. There is no remaining
  identified automated/code blocker to presenting #68 for HP's decision. Merge
  acceptance still requires HP to assess this report and the outstanding owner
  signoff; mergeable=true is only GitHub's technical mergeability signal.
- **NOT RUN in this gate:** Q03-G01 browser/responsive/manual acceptance and PR
  #70/#69 integrated-source migration/combined-seed/postmerge gates. Real HTTP
  coverage does not substitute for them. Their recorded states are unchanged.
- QA-02 remains **IN PROGRESS**. Historical whole-plan/Task Board comparison and
  broader acceptance are not closed by this PR-specific regression. Existing
  Q02-F05 behavior is rechecked by 50 passing BuyerCancellation cases; no new
  refund, inventory, Shipping or payment-provider policy is activated.

Final documentation verification: `git diff --check` exit **0**; five changed/new
files are confined to `docs/testing/QA-02-*`. Existing historical evidence JSON,
production/test/build inputs, HEAD, index and remote refs are unchanged by this work.

This continuation changes local documentation/evidence only. No tests that already
passed were rerun; no source edit, commit, push, PR message/metadata mutation,
approval or merge occurred. These evidence edits are not a new published HEAD and
have no separate GitHub CI run. HP may now make the #68 merge decision; no #70/#69
or overall QA completion follows automatically.
