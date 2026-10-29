# HP-authorized integration validation and Q70-S01 residual risk

Master Plan QA-02/QA-03 and T48; user-relayed HP decision of 2026-10-29 authorizes
candidate integration VALIDATION. Q70-S01 remains OPEN/Medium. No GitHub approval,
develop merge, finding closure or whole-QA completion is authorized by these results.

## Source and dependency protocol

Validation stack: develop fa48cb6 -> #68 e79b760 -> #70 688884c -> #69 01718cc.
Composition f82c5b7 contains every exact head as an ancestor; all application/test/
runner objects initially match #69. Test-only child Clock instrumentation then
freezes source f9c6fd68e1273a8e8ab3b9deb8e13876ce0b17d5. Production main objects remain
unchanged. All27 old migration files remain byte/object-identical;28 SQL files,
including V20261028081110, no duplicate version name. Fresh migration/upgrade tests
cover old returned-COD preservation, operation-specific nullability/FKs/uniqueness
and immutable evidence. This local composition is not a GitHub merge into develop.

Source freeze/dependency acknowledgement was requested from TD/HP in
https://github.com/hphuc032/Logistic-UTEExpress/pull/70#issuecomment-6476469259 .
No TD source sign-off is invented; see manifest for actual acknowledgement state.

## Fresh execution protocol

The normal run and -2s run each use a new owned loopback PostgreSQL17.6 database,
a matching packaged application and the existing explicit demo opt-in. Both run
the full shared HTTP fixtures plus the TD module twice in separate application
processes. The shared module itself still uses its same-process completed
checkpoint, not lost-checkpoint recovery. Every run records actual run ID, UTC
start/finish, process/child exits, timeout and cleanup state plus script/JAR/log/
fixture/configuration hashes. Fingerprint equality is within each disposable
DB before/after rerun, not an equality between two independently created databases.

The Clock source is byte-identical to TD's reviewed reference (source SHA256
0a38d078dd3f93f7d8f7225083d89b9c541a732b22e540609b6983fddde4fe23).
It stays on the test classpath and is explicitly injected only into TD child
processes. The primary shared HTTP application is unmodified; production JAR
contains neither qa02 seed nor investigation classes. Controlled runs require
an actual activation marker in EACH child log; missing marker fails validation.
Normal runs reject an unexpected controlled marker. CLI rejects skew mode without
refund fixtures before allocating resources; supported constant modes are0/-2.

Operation assertions remain separate: returned-COD Order REFUNDED with approved
receipt, cancelled-paid Order CANCELLED without ReturnRequest, both Payment PAID
with one SUCCEEDED immutable ledger result. The original returned ledger hash,
stock/voucher, snapshots/history and all34-table rerun fingerprints are preserved.
No Order/Payment/Inventory/Shipping business mutation logic is added for validation.
Synthetic PAID and initial ADMIN bootstrap provenance remains explicit, not a
provider callback or real transfer. Financial assertions exclude shipping and
retain deliberate global unknown/null diagnostics for invalid negative fixtures.

## Controlled reproduction comparison

Published TD pre-fix controlled run-f5162d53820c at ff83d54 is reference evidence,
not a new QD pre-fix execution. Its captured paid_at after cancellation produces
expected CONFLICT with other prerequisites valid. TD controlled candidate
run-45c69b98c79b at d06320f passes. The new QD integrated-source controlled run
checks the same marker/constant -2s mechanism, fixed timestamp chronology and
operation/rerun invariants. Natural pre-fix PASS remains part of the historical
record; no universal clock/zero-flake guarantee is claimed. The ORIGINAL historical
run-9b427dbb1046 failed predicate remains NOT CAPTURED and its exact root cause
UNPROVEN. New controlled evidence does not retrospectively identify that predicate.

## Residual risk and recommendation for HP

**Propose ACCEPTED RISK, narrowly scoped to the uncaptured historical disposable
fixture failure, conditional on HP disposition and owner integration acceptance.**
Do not assert unqualified RESOLVED for that original unknown predicate. The known
clock-mixing fixture mechanism is corrected and validated in the candidate;
production invalid chronology is still rejected. Q70-S01 remains OPEN/Medium in
current metadata until HP explicitly decides its disposition.

Grounds: source-bound fresh regression and standard/controlled runs; one-shot
immutable ledger/atomic rollback/concurrency coverage; operation-specific state,
stock/voucher and refund/replay checks; before/after bytecode and unchanged guard;
preserved FAIL artifacts and safe future-failure diagnostics. The proposal accepts
no invalid production Payment evidence, gateway assurance or guard relaxation.

| Residual | Boundary / mitigation | Owner / disposition |
|---|---|---|
| Exact original failed predicate unavailable | Preserve historical FAIL and raw hashes; future failures capture persisted predicates before cleanup; do not retrofit a cause | HP decides limited acceptance; TD/QD retain diagnostics |
| Temporal coverage bounded | Normal and constant child -2s exercised; no claim about all wall-clock discontinuities | Keep production chronology guard fail-closed |
| Shared checkpoint scope | Shared module same-process; TD module independent-process rerun | HP final seed-scope decision remains separate |
| Synthetic PAID / initial ADMIN fixture | Owned disposable environment, explicit provenance; no production PAID writer/gateway claim | HP fixture policy; PAY03/VNPAY remains DEFERRED |
| Validation source not merged develop | Exact ancestry/inventory frozen; execute fresh accepted-source verification after any authorized integration/change as required | Owners integrate, TD review, HP final acceptance |
| Manual/browser G01 missing | Previous supported Computer Use runtime fails before UI access; no screenshots/viewport PASS | QD+HP/UI; QA03 remains BLOCKED |

Send this packet for HP scoped integration/disposition review and TD source/technical
confirmation. This is not READY FOR WHOLE QA02/QA03 FINAL ACCEPTANCE: G01, HP
fixture/checkpoint/remaining requirements and authorized integration remain tracked.
Manager commission writes DENY ALL remain the accepted deferred exception. Release
follow-ups NOT RUN, PAY03/VNPAY DEFERRED. No finding/QA completion or merge by QD.

## Completed fresh results

Tested source `f9c6fd68e1273a8e8ab3b9deb8e13876ce0b17d5`: **690 unit +1388 PostgreSQL =2078 PASS**, zero failures/errors/skips, exit0/no timeout. All139 XML report hashes and full logs independently rechecked; JAR unchanged after verification. Complete Node33 and tooling23 PASS; Notifications12 overlap Node33.

Fresh normal run `run-9cf67ceff50e` and controlled -2s `run-a26593cef3ef` both PASS, wrapper/each child exit0, no timeout, owned containers removed. Each has15Orders/3refunds, two independent TD invocations, correct operation states/timing and34-table fingerprints unchanged. Normal child markers are absent; both controlled child markers confirmed. The shared HTTP application has no Clock marker and the production JAR excludes test-only classes. Build/run receipts include exact class/source/log/result hashes and UTC start/finish.

No new owner source acknowledgement was recorded on GitHub after the freeze request when checked. TD source/technical confirmation remains explicitly pending; do not label the composition as a completed team sign-off. No historical original predicate is invented, finding remains OPEN, recommendation is for HP decision only.

## TD confirmation and HP decision handoff (2026-10-29)

TD explicitly confirmed all five requested source/evidence points for `f9c6fd68e1273a8e8ab3b9deb8e13876ce0b17d5`, relayed by the user. This is source and published-evidence review, **not a new independent PostgreSQL execution**. No corresponding GitHub confirmation URL was supplied or observed; `td-confirmation.json` records this provenance. TD supports only the bounded residual-risk proposal. Prior pending-ack statements above describe the earlier publication snapshot.

`build-provenance.json` is an immutable intermediate build snapshot: its `full_regression_state = RUNNING; no early PASS` was captured before completion and is not current execution status. Final completed results are in `full-run-manifest.json`, `supplemental-tests.json`, seed results and `run-crosschecks.json`. Its original bytes/hash are preserved; no new test run is claimed by this documentation update.

Responsibility: TD owns Payment/Refund/business-invariant investigation; QD owns fixture/runner diagnostics and source-bound evidence; HP owns formal disposition, integration permission and final acceptance. Reopening/escalation triggers are enumerated in `residual-risk-proposal.json`: unexplained clean-seed CONFLICT, invariant/duplicate/rollback/fingerprint mismatch, provenance or Clock-marker mismatch, timeout/incomplete evidence, or guard/scope changes. Preserve failed evidence, block affected acceptance and refer to TD/QD/HP; never weaken eligibility guards.

**Submitted for HP formal risk decision; not applied. Q70-S01 OPEN / Medium.** Original historical predicate remains unproven; no universal clock-skew or invalid chronology acceptance. PRs #68/#69/#70 remain unapproved/unmerged by QD, #69 Draft, QA02 IN PROGRESS, QA03 BLOCKED, HP Final Acceptance PENDING, PAY03/VNPAY DEFERRED.

## Direct TD acknowledgement and remaining handoff

The initial relayed receipt is now supplemented by the independently retrieved [TD GitHub confirmation](https://github.com/hphuc032/Logistic-UTEExpress/pull/70#issuecomment-6485644464), comment author `Tender0603`, created `2026-10-29T01:49:28Z`. This supersedes the earlier no-URL observation; its original publication remains in Git history. TD explicitly limits the acknowledgement to published source/provenance/evidence, without an independent JAR rebuild/byte comparison or a new PostgreSQL run.

QD's reviewed evidence does not identify an additional unresolved TD production defect beyond the tracked Q70-S01 uncertainty; this is not an exhaustive QA-02 sign-off. Historical `QA-02-findings.md` contains source-time OPEN publication/plan/shutdown entries and must not be interpreted as a current authoritative closure list without TD reconciliation. Q02-F05 was accepted/merged in #63; teardown mitigation #64 is separately recorded in QA03 acceptance findings.

| Task / criterion | Remaining action | Owner / related PR |
|---|---|---|
| Q70-S01 Medium | HP decide bounded disposition; retain unknown original predicate and reopen triggers | HP decision, TD commerce + QD evidence; #69/#70/#71 |
| QA-02 integrated acceptance | After HP authorizes dependency integration, verify accepted source: migration/old ledger, both refund operations, chronology, idempotency/concurrency/rollback, inventory/voucher and revenue; publish exact source/CI/results | TD + QD; #68 -> #70 -> #69 subject HP decision |
| QA-02 findings reconciliation | TD reconcile historical findings with merged fixes and current evidence, identify any further Master Plan gaps explicitly; HP decides completeness | TD/HP; historical QA-02-findings.md is not blanket acceptance |
| QA-03 Q03-G02 | Combined normal/skew/rerun evidence exists; HP fixture/bootstrap/checkpoint scope and final integrated-source acceptance remain separate | TD + QD, HP; #69 |
| QA-03 Q03-G01 | Browser/manual Ops and responsive evidence remains NOT RUN/BLOCKED; not a TD production bug | QD + HP/UI |
| PAY-03 | VNPAY Sandbox DEFERRED; not claimed QA-02 provider evidence | TD, HP policy |

No new production change, test execution, approval, merge, formal risk disposition or whole-QA completion is claimed by this documentation update.

## HP final risk decision and ordered integration

HP explicitly authorized **ACCEPTED RISK — LIMITED**, relayed by the user on 2026-10-29. This supersedes earlier OPEN/pending-disposition snapshots; historical evidence remains unchanged. Only the unknown original disposable-fixture CONFLICT predicate is accepted; it is **not RESOLVED**, production chronology/eligibility guards remain intact, and new Payment/Refund/concurrency/rollback/financial failures trigger reopening. Responsibilities and detailed reopening triggers remain applicable. See `hp-final-decision.json`.

Order authorized: **#68 -> #70 -> #69**, with a separate HP merge decision after each gate. All three remain unmerged when checked; #68 exact-head CI SUCCESS but owner approval remains pending, #70 compatibility must be checked after actual #68 integration, and #69 remains Draft until dependencies sync and final full Java/PostgreSQL, Node/tooling and normal/-2s combined rerun evidence on accepted source. Existing f9c6fd6 evidence is not relabelled as postmerge. HP must explicitly accept synthetic ADMIN/PAID bootstrap and checkpoint scope. `integration-gates.json` records PASS/PENDING/NOT RUN distinctly. QA02 IN PROGRESS, QA03 BLOCKED, PAY03 DEFERRED; no automatic approval/merge or QA completion.

## PR68 owner signoff / remaining publication gate

TD reports OWNER TECHNICAL SIGNOFF at `c593352492184da2a9ca46ebea4a55b802ac6416` and local697unit+1326PG=2023 PASS. QD verified exact-head CI run40813357695 SUCCESS and inspected the three-file disable binder/test delta. This is not a new independent PostgreSQL execution. The gate report `docs/testing/QA-02-PR68-gate-report.md` is still unavailable at that GitHub ref (HTTP404). HP scoped code review5692679850 explicitly requires source-bound manifest/log hashes and clarification of the exact tested source before final approval. Keep historical2011 tests on8bab8dd separate; do not infer that local2023 has been published or relabel the old evidence.

Await TD sanitized report/manifest publication and HP final #68 decision. #70 actual post68 migration/constraints/old-ledger compatibility is still pending; #69 sync/final verification follows actual dependency integration. QD sent the publication request; no PR approve/merge or QA completion.


## Current G01/browser and dependency update (2026-10-29)

This section supersedes earlier no-browser/no-screenshot observations, without changing historical execution artifacts. Read the original Master Plan again: QA03 depends INT02 and covers Assignment/failure/WS/review/migration/clean DB; T50 requires real UI at375/768/1440.

Installed Chrome155 with authorized Playwright fallback executed24 real automated UI cases PASS on5538405, with screenshots and source/dataset/JAR hashes. Human manual execution is NOT CLAIMED. Missing Ops assignment UI was reproduced on develop and repaired on a separate candidate; Draft PR75 contains implementation, retained attempts, full2059Java +41Node +17tooling PASS and browser evidence. HP/TD review and HP G01 acceptance are pending. Low OTP help-text5min/10min mismatch is HP-owned; security policy unchanged. Source5538405 does not include unmerged70 and is not the G02 final combined source.

PR68 is merged as a34e8cc. PR70 exact321ccf3 remains unmerged; QD scoped migration/DB/compatibility review PASS and exact-head hosted2124Java/33Node/17tooling evidence was verified (raw encrypted bundle hash checked, not decrypted). QD has not approved/merged70. Per HP instruction, PR69 remains Draft and is not source-synced or final-run before actual70 merge.

Current Q70-S01: OPEN / Medium, separate disposition ACCEPTED RISK — LIMITED. Historical predicate remains unproven. QA02 IN PROGRESS, QA03 BLOCKED, DOC73 paused. PROMO03 platform-management scope still needs HP/TD contract decision; vendor promotion UI is not proof of Admin/Manager authority. Release follow-ups remain separately NOT RUN.

Supplementary browser checks now cover Manager assign/reassign, Review short400/no-row and duplicate409/one-row, plus RTS identical replay302/different payload409 with unchanged facts at all3widths (9PASS). A separate earlier3FAIL RTS expectation is preserved as a harness defect: existing authoritative ReturnToSenderIT requires identical replay rather than universal409. No production change. Supplemental docs-only59a1df3 has identical source/test/build inputs and JAR to full-tested5538405. PR75 exact final evidence head is recorded in current gates; CI is checked separately rather than inherited from an older head.
