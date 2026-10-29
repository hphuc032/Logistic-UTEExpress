# QA-01 — Current source-bound evidence (2026-10-29)

Branch `test/qa-01-security`, PR #66. Synced develop `fa48cb69cef92276f0dddf0d2bd396eee098f2f3`; current tested merge `6e93d44614b1574ab2d310bae9a54ae57c385893`. Normal merge, behind0 at sync; no rebase/force push. Previous QĐ APPROVED `7aa74a215f2539012344d252ffd7e125c8fe64d3` is a distinct, historical review. The following publication commit changes only QA-01 reports/matrix/generated JSON; its final SHA/CI is linked in the PR and must preserve these executable inputs.

## Exact fresh execution

Runner SHA `6e93d44614b1574ab2d310bae9a54ae57c385893`, tested source `6e93d44614b1574ab2d310bae9a54ae57c385893`, schema2, collected `2026-10-29T03:53:36.219618+00:00`. Script SHA-256 `4005a275ed21d1d657f9f8c5cb0ad663f83be08074fe66cb10926d494a21036f`; same approved implementation as the earlier runner, actually executed from the new clean source checkout.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:DOCKER_CONTEXT='default'
$env:DOCKER_HOST='npipe:////./pipe/docker_engine'
python -B scripts/qa/verify_operations.py --checkout D:/UTEExpress/.work/qa01/verification
# Runner executes these fixed commands, in order:
# D:/UTEExpress/.work/qa01/verification/mvnw.cmd -q -Ppostgres-it clean verify
# node --test --test-reporter=tap src/test/js/notifications.test.cjs
python -B -m unittest discover -s scripts/qa/tests -v
node --test --test-reporter=tap src/test/js/notifications.test.cjs src/test/js/buyer-cancellation.test.cjs
git diff --check
```

Java21/Maven3.9.11, Linux Docker29.4.3/PostgreSQL17.6, Node24.14.0/Python3.14.2. Docker was initially unavailable, started before the run; no successful evidence is inferred from preflight failures. Both runner/source clean and unchanged revisions were checked by the CLI itself. No imported/stale XML was used. Maven clean verify includes package; no separate final clean-test/package command is invented.

| Fresh gate | Classes | Tests | Failure/error/skip | Exit |
|---|---:|---:|---|---:|
| Unit/architecture | 75 | 694 | 0/0/0 | 0 |
| PostgreSQL | 64 | 1346 | 0/0/0 | 0 |
| Java total | 139 | 2040 | 0/0/0 | 0 |
| Runner notification Node | separate | 12 | 0/0/0 | 0 |
| Combined notification + cancellation Node | separate | 33 | 0/0/0 | 0 |
| Runner self-tests | tooling | 17 | 0/0/0 | 0 |

No flaky/rerun markers; Failsafe timeout=false, completed=1346. XML testcase counts and 139 per-suite SHA-256 values were independently checked against generated metadata; Failsafe summary and raw Maven/Node log hashes also matched. Combined Node overlaps the notification-only run; totals are not added. Final log literals: forced-fork=0, closed-connection=0. Expected fault-injection logs remain private.

## Current application inputs and delta

| Input | Git object at tested source |
|---|---|
| `src/main` | `bbd3dda5d2949b131a20d674b271f9c0edcb84f2` |
| `src/test` | `1dccd4f7ed415525a1f4a1fdaf5199a4d3930301` |
| `pom.xml` | `89ba1f2c4fdbd5aafc0e9dfe909034b52990452f` |
| `.github/workflows/ci.yml` | `971a3a8e054d00b7212a625d586020f1784d44b9` |
| `scripts/qa/verify_operations.py` | `fd78feadf5d8ae37e48dd509b4148a3ebb518672` |
| `scripts/security/qa01-semgrep.yml` | `9cc7450020f1277c14e4c2b0c7e79ca782aa2c85` |

PR67 includes real Order HTML/read/JS/test and CI changes. The full fresh result is therefore not presented as mere equality with the old application source. These narrower security boundary objects did remain identical to QĐ-approved7aa74a2:

| Unchanged boundary | Git object |
|---|---|
| `src/main/java/com/uteexpress/security` | `b04a4a6ddea63abb1c1e006edeaad360d712b1f6` |
| `src/main/java/com/uteexpress/identity` | `4428373685affdba040e7fbe45f5a1065f3894bd` |
| `src/main/java/com/uteexpress/account` | `77e91cc65796d96deb9aef844dc11394db00a9c3` |
| `src/main/java/com/uteexpress/notification` | `4d744d7bc78963e2a712b7af6a869d24098844f3` |
| `src/main/resources/application.yml` | `4c7d37e92a83a2d1612c8263748021caf897b823` |
| `pom.xml` | `89ba1f2c4fdbd5aafc0e9dfe909034b52990452f` |

## Portable sanitized runner metadata

[QA-01-runner-evidence.json](QA-01-runner-evidence.json) is the exact generated manifest for this new run; SHA-256 `4d5df38345bf938c3554e2da24e337c4515847c9ec56c5cbe9813a3c0f39c436`. It contains only whitelisted source/runner identities, timestamps, suite names/counts/hashes, exit/timeout summaries and local-log digests. No raw XML system properties, request details, logs, JWT, OTP/password or credential values are copied. Narrow docs/testing/.gitattributes -text/CR-at-EOL handling preserves original bytes on Windows/Linux; Git blob equality is checked before publication.

Raw XML is under `.work/qa01/verification/target/surefire-reports/` and `failsafe-reports/`; raw logs/manifest under its `target/qa-03/`. Stable new-run private metadata/logs are also under `.work/qa01/20261029-final-sync/`. Earlier verification reports/logs were preserved under `.work/qa01/pre-oct29-verification/` before clean.

| Private artifact | SHA-256 |
|---|---|
| `.work/qa01/verification/target/qa-03/operations-evidence.json` | `4d5df38345bf938c3554e2da24e337c4515847c9ec56c5cbe9813a3c0f39c436` |
| `.work/qa01/verification/target/qa-03/maven.log` | `139ed106efb78b814e407a76cfaf0d671f872c10e7f68dedc00b251f4843ae46` |
| `.work/qa01/verification/target/qa-03/node.log` | `d6eab4120114ec688508ee8bd0a8d6ca14b5e248c3c748f37fd7248c85af013f` |
| `.work/qa01/verification/target/failsafe-reports/failsafe-summary.xml` | `8119917fa2339a1d8be44a43b5dceb4fd8426f77410ee059f2e149c065d2ccf4` |
| `.work/qa01/20261029-final-sync/runner.log` | `fd9e14806451ccc7dbcc1f24c6a1bcd11e89821d698b3a2d6387c74d8bdecce9` |
| `.work/qa01/20261029-final-sync/tool-tests.log` | `8c2b4694ed17fef5e8e73678a92fb528e82a5352d831a4ce63d5e85b03dd1d3f` |
| `.work/qa01/20261029-final-sync/node-combined.log` | `3eb181f9e8a9380bb8dbc059c633677e92a2d80c2d61a41c0f177e0dc1e05df7` |
| `.work/qa01/20261029-final-sync/semgrep.json` | `aa6f94aa0c4424142655d4dc4fc5bb88e06843a628c051a65aba462265634b18` |
| `.work/qa01/20261029-final-sync/resolved-libraries.txt` | `5b38efb194e97c48b392d7ac5f7427fc2f2ac00013c755440855fef1890c00c6` |

## Offline SAST / dependencies / remaining gaps

Semgrep1.155.0,7 checked-in rules on exact source 6e93d44: 607 main targets,5 candidates,0 scanner errors, exit0. Metrics/version network checks disabled; no code upload. Review triage applies to unchanged constant SQL paths below; this is limited pattern coverage, not exhaustive taint/exploit/SCA assurance.

| Candidate source | Line | Triage |
|---|---:|---|
| `src/main/java/com/uteexpress/governance/repository/OpsDashboardReadRepository.java` | 35 | Fixed SQL fragment; request values bound |
| `src/main/java/com/uteexpress/governance/repository/OpsDashboardReadRepository.java` | 48 | Fixed SQL fragment; request values bound |
| `src/main/java/com/uteexpress/governance/repository/OpsDashboardReadRepository.java` | 49 | Fixed SQL fragment; request values bound |
| `src/main/java/com/uteexpress/notification/repository/NotificationRepository.java` | 75 | Fixed SQL fragment; request values bound |
| `src/main/java/com/uteexpress/notification/repository/NotificationRepository.java` | 91 | Fixed SQL fragment; request values bound |

Packaged resolved dependency inventory: 109 library JAR names, no upgrade. No usable local dependency CVE scanner/database was available. Dependency SCA NOT RUN; no clean-CVE claim or automatic deferral. Live SMTP/timing, deployed TLS/proxy/NAT/cookie/storage ACLs, distributed rate limits/load/restart and wider browser/seed/backup/release acceptance remain classified in the current Security Report/Findings with owners and closure conditions. NOT RUN is not PASS; HP's formal decision is still required where applicable. Source/config review remains bounded, not a new full-history entropy scan.

## Historical evidence / review chain

- Original baseline:798535b,1950 Java +12 Node, newly executed at that time.
- Previous full source:3c4fb55,2023 Java +12 Node; [old manifest](https://github.com/hphuc032/Logistic-UTEExpress/blob/7aa74a215f2539012344d252ffd7e125c8fe64d3/docs/testing/QA-01-runner-evidence.json), SHA-256 `43175396d5e7b7929191e2a2de90f55ab3583e4d5c079eb558ad25d33e5a7ed7`.
- QĐ independently scoped878 Java +12 Node at89600fd, then APPROVED static delta at7aa74a2. This does not independently rehash HP's private files or approve the new source.
- [Prior complete handoff](https://github.com/hphuc032/Logistic-UTEExpress/blob/7aa74a215f2539012344d252ffd7e125c8fe64d3/docs/testing/QA-01-evidence-manifest.md) retains all old reproductions, failed sandbox/Docker attempts, earlier SHA/hash tables and sync history. Those values are historical and never substituted for this fresh run.
- New published HEAD/CI and final QĐ delta verdict belong to PR #66. No merge, deployment, migration rewrite, formal NOT RUN deferral or QA-01 COMPLETED is performed here.
