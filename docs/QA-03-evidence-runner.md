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

15 tests PASS using isolated temporary fixtures. Tests cover count/secret sanitization, required-suite omission, empty/malformed/entity XML, contradictory counts, duplicate suites, skip/flaky/rerun/timeout/incomplete summaries, invalid Node results, command order, failed-run stale-PASS removal, preserved failure logs, changed revision/dirty source and safe CLI errors. These are QA-tool tests, not Java/PostgreSQL business tests. A parser-only check against the real 9f81a66 final-head XML also confirms compatibility; that reuses existing artifacts and is not another full-suite run. No production tests or constraints are weakened.
