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
