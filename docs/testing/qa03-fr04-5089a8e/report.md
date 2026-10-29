# QA-03 FR-04 fresh full regression — pending review

Executed source `5089a8ed17e5f8486e44847a99b309ff13d0d3b8`, tree `89c3882d470f81466b902cd3a38d7f3f272c889b`.
Accepted develop / PR75 merge `a5620f1971d3b684d9b05d7444f5e90442404656`.
Remote publication verified before execution: `6a899eb8e90d45f63919f4b4a9e1b43533ba00ff`; source/test/tooling/workflow objects equal. This packet publication is docs only, not another execution.

HP authorized FR04 only for QD execution in this task. TD is reviewer. FR05/06/07 were NOT RUN. No seed, controlled-skew execution or independent reruns took place.
Master Plan SHA-256 is recorded in source-proof.json. Separate detached execution worktree; clean index/worktree before and after. No source/security/refund/migration/test assertion changes.

## First-attempt results

| Suite | Tests | Failure | Error | Skip | Exit |
|---|---:|---:|---:|---:|---:|
| Java unit/architecture | 709 | 0 | 0 | 0 | 0 |
| PostgreSQL integration, all 67 IT classes | 1419 | 0 | 0 | 0 | 0 |
| Node, all 4 test files including assignment | 48 | 0 | 0 | 0 | 0 |
| Python QA tooling | 23 | 0 | 0 | 0 | 0 |

Java total **2128**, represented by **144 JUnit XML** (77 unit +67 integration). Node/tooling are separate; not added into a Java total. No retry, excluded failing test or skipped PostgreSQL gate. No fresh test failure occurred. This is local execution evidence, not GitHub CI PostgreSQL.

Execution UTC: `2026-10-29T14:59:52.438780+00:00` to `2026-10-29T15:16:22.165876+00:00`. Per-command times/durations/exits and hashes are machine-readable in execution-receipt.json.

- `cmd /d /c mvnw.cmd --batch-mode --no-transfer-progress -Duser.timezone=UTC -Ppostgres-it clean verify` — exit 0, 2026-10-29T15:00:10.678598+00:00 → 2026-10-29T15:16:16.366969+00:00
- `node --test src/test/js/app.test.cjs src/test/js/buyer-cancellation.test.cjs src/test/js/notifications.test.cjs src/test/js/shipment-assignment.test.cjs` — exit 0, 2026-10-29T15:16:16.656976+00:00 → 2026-10-29T15:16:17.282276+00:00
- `python -B -m unittest discover -s scripts/qa/tests -v` — exit 0, 2026-10-29T15:16:17.297102+00:00 → 2026-10-29T15:16:19.202163+00:00

## Environment and cleanup

Java21.0.2, Maven Wrapper3.9.11, Node22.21.0, Python3.13.9, Docker29.4.1; actual version receipt retained. JAVA_TOOL_OPTIONS and MAVEN_OPTS set `-Duser.timezone=UTC`; both committed Surefire/Failsafe profiles configure UTC. **All 144 actual JUnit fork properties confirm UTC**.
Fresh auth preflight used owned disposable PostgreSQL17.6 with SCRAM; correct credentials passed, wrong password denied SQLSTATE28P01, owned container removed. No auth/clean-worktree guard bypass.
Full suite uses actual `postgres:17.6` Testcontainers; 79 PostgreSQL container start IDs found in the fresh Maven log, all inspected absent after execution. Pre-existing/unrelated containers were not deleted. Migration/upgrade tests were part of the full suite; historical migrations unchanged.

## Evidence/privacy

Raw logs/XML/runner receipts are local private files. Sanitized manifest contains SHA-256 for 182 artifacts; private archive CRC and every decompressed artifact hash verified. Raw data is not uploaded to GitHub. Package is NOT DELIVERED; restricted-channel transfer still requires recipient acknowledgement. Logs/XML contain test configuration and are intentionally treated as sensitive.
See execution-receipt.json, source-proof.json, raw-artifact-hashes.json and private-package-receipt.json. Historical FAIL/preflight evidence is unchanged.

## Verdict and next decision

**PENDING REVIEW**; technical execution green. TD must review source identity, complete class inventory, UTC/auth/cleanup evidence, raw hashes and invariants covered by full regression. HP decides FR04 acceptance before the group progresses to TD-owned FR05. No final FR04 PASS, approval, merge or QA03 completion is claimed.
G01 human acceptance remains PENDING. Q70-S01 remains OPEN/Medium with ACCEPTED RISK—LIMITED. PR69 stays Draft.
