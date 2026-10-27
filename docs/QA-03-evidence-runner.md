# QA-03 preparation — reproducible local evidence runner

QD-owned tooling only. No production Java, applied migration, financial formula, JWT/CSRF/Origin, Order/Payment/Shipping logic or existing feature HEAD is changed. This prepares QA-03; INT-02/group acceptance is still required before calling QA-03 complete.

## Usage

Python 3.11+ standard library, Java 21/JAVA_HOME, Maven Wrapper, Docker engine and Node (the project CI uses Node 22). No pip packages. The target checkout must contain WS-02, its client tests and the existing Ops/review/migration suites; an old develop without WS-02 fails before tests are run. No feature is merged automatically.

```powershell
python -B scripts/qa/verify_operations.py --plan
python -B scripts/qa/verify_operations.py --checkout "C:\path\to\approved-checkout"
```

Without --checkout it verifies this repository. --plan only prints the fixed commands/coverage requirements; it is not a successful test run. The runner can live in a separate tooling checkout while testing an approved/frozen feature or integration checkout. It never checks out, rewrites or commits target source.

Preflight checks Java 21, Node >=18 and a responding Docker engine (15-second bounds); Node 22 remains the CI choice. Maven has a 30-minute local command budget and Node tests a two-minute budget; a timeout prevents evidence publication. The runner rejects dirty/untracked source, remembers the exact Git revision, runs Maven `-q -Ppostgres-it clean verify` followed by Node `--test --test-reporter=tap`, then rechecks clean source and the unchanged revision before publishing. Maven clean prevents stale reports from a prior branch. An existing evidence JSON is removed at invocation start, so a failed attempt cannot leave an old PASS artifact looking current. Nonzero process exit, missing/duplicate/malformed/empty suites, skip/failure/error/flaky/rerun cases, missing required Ops suites, inconsistent Failsafe summary, timeout and empty/failed/skipped Node summaries prevent publication.

`target/qa-03/operations-evidence.json` contains source SHA, UTC collection time, per-suite counts/hashes and exit/summary information. Raw maven.log/node.log are kept alongside for local diagnosis and remain ignored by Git. Raw XML properties, env values, testcase text, exception payloads and request details do not enter the JSON. The CLI does not echo private exception text. Do not upload raw local logs without review. Output remains within the target checkout.

Required coverage includes baseline/fresh DB, shipment assignment/attempt migration, full Shipper flow, real WebSocket, notification recovery, real Review writer and Review migration. Architecture checks and all other suites produced by full clean verify also contribute. Counts are derived, never hardcoded to a previously reported total. Larger required acceptance matrices can be agreed with HP/TD separately; passing this tool is not proof that every business scenario exists.

This is local evidence, not a trusted-runner signature, production/clean-machine deployment sign-off, multi-instance/load claim, GitHub approval or branch protection override. Environment/dependency provenance is not cryptographically attested. Review and release gates remain independent.

## Tool verification

```powershell
python -B -m unittest discover -s scripts/qa/tests -v
```

15 tests PASS using isolated temporary fixtures. Tests cover count/secret sanitization, required-suite omission, empty/malformed/entity XML, contradictory counts, duplicate suites, skip/flaky/rerun/timeout/incomplete summaries, invalid Node results, command order, failed-run stale-PASS removal, preserved failure logs, changed revision/dirty source and safe CLI errors. These are QA-tool tests, not Java/PostgreSQL business tests. The original parser-only check against real 9f81a66 XML reused artifacts and was not a new full-suite run. A fresh end-to-end execution is now recorded below. No production tests or constraints are weakened.


## Fresh end-to-end verification on frozen review candidate

Runner source revision: c0922db0175e98a74c873a25585fdbc069d83f31 (tooling branch synchronized with develop e6d94c5). The runner Python source SHA-256 is 5ce1f478ef4b1dcfd62d73f924482d5203231bfe5b67c11d1a7c9f8fab845d8b; the final commit adds evidence/docs only. Target source is frozen PR #59 HEAD 9d63e5af59b5c6fc79a7b21fe087d621aa42df1f, not the tooling branch's business source. It remains a review candidate, not an accepted/merged release.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
python -B scripts/qa/verify_operations.py --checkout "C:\path\to\frozen-dash02-checkout"
```

The actual CLI ran both fixed commands and published evidence itself, exit0: **682 unit/architecture + 1,241 PostgreSQL = 1,923 PASS**, no failures/errors/skips/flaky cases, Failsafe timeout=false. **12 Node tests PASS**, fail/cancelled/skipped/todo=0. Target Git HEAD and clean working tree were rechecked successfully. This is a fresh runner execution, not imported XML from the prior manual run, and must not be added to that overlapping total.

The exact generated JSON is copied to QA-03-runner-e2e-evidence.json (SHA-256 c7722afbaca1495c4fd399144162db3312d0dffe672ed51a9fc98f9344faa49c). Raw local maven.log and node.log stay ignored under the target checkout's target/qa-03. Existing container/Hikari/fork teardown diagnostics are preserved locally; successful exit/XML status is not a claim that infrastructure warnings were diagnosed.

Three additional real CLI smoke checks on disposable Git fixtures passed: dirty checkout rejected without test execution; missing WS-02 checkout removes stale evidence then rejects before commands; non-root checkout rejected. These supplement the 15 automated tool tests and are not business-suite cases. No target source or production module was edited.

Tooling is ready for HP/TD review after exact-head CI. This does not complete INT-02 or QA-03, approve/merge PR #59, deploy monitoring, or change any reviewer/branch-protection gate.


## HP final tooling follow-up — manifest v2

The runner now embeds runner_sha, runner_script_sha256, tested_source_sha (and legacy tested_revision), actual command exit codes, per-suite/summary hashes, raw-local-log hashes and derived test counts in the generated manifest itself. It requires clean runner and target checkouts and checks both again before publication; changing either the runner revision or its Python file aborts without PASS evidence. No log contents are published. Manifest manifest_schema_version is 2. Legacy frozen-#59 evidence above remains historical and is not replaced by a claim that its SHA was current develop.

17 automated tool tests PASS, including runner revision/file mutation during execution and provenance/log-hash assertions. Fresh execution against exact current develop will be recorded after completion on a separate clean target checkout. Tooling completion still requires TD scoped review/GitHub approval and HP decision; whole QA-03 acceptance remains dependent on INT-02 and the Ops acceptance matrix.
