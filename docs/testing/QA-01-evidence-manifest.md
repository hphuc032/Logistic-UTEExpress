# QA-01 evidence manifest

Sanitized metadata/digests only. Logs/XML can contain synthetic credentials, cookies,
request details and system properties; all raw artifacts remain ignored/local and must
be inspected before sharing. No token, OTP, credential, private media, ENV dump or
generated build output is committed.

## Identity and baseline

- Branch `test/qa-01-security`; local only.
- Base develop/INT-02 merge `798535b32ff81a011f34cfa01bbeb2d2dd0cb981`; clean tree and merge ancestry verified after fetch.
- Later approved develop `d2f722d4075999b418e3092ccc75de3c49ec9e57` was merged normally at
  `85f790b3e9ae5b3bcedbe8658d904b906b3dbc46` (PRs63/64);
  initial baseline and final source are intentionally different. TD cancellation and
  QĐ context cleanup were imported intact, not authored or rewritten by QA-01.
- Baseline newly executed on `798535b32ff81a011f34cfa01bbeb2d2dd0cb981`: 682 unit/72
  classes + 1268 PG/61 classes = 1950
  Java,0 failures/errors/skips/flaky/rerun, timeout=false; Node12, exits0.
- Main remediation commit `6ff8f41e50cf7c25bca2d6e78c1ddd7a52131c86`.
- Isolated-profile fixture correction `63960dc5b35cb7cbfc447cb1c864c9cad796b9a1`.
- Encoded-route fix and its two unit/one PG regressions
  `3c4fb5555b1ce580ed03dab592806c59f9bdf62a`.
- Final executable/test commit `3c4fb5555b1ce580ed03dab592806c59f9bdf62a`; clean before run and collection.
- Later report-only commit changes only the four QA-01 reports; use
  `git diff 3c4fb5555b1ce580ed03dab592806c59f9bdf62a..HEAD --name-only` plus input objects below to verify identity.
- Actual Master Plan `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`,
  SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
  `(1).docx` not found; no binary/alternate revision copied into Git.

## Reproducible gates

```powershell
git rev-parse HEAD
git status --porcelain=v1
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:DOCKER_CONTEXT = 'default'
$env:DOCKER_HOST = 'npipe:////./pipe/docker_engine'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/notifications.test.cjs
# In the separate clean runner checkout at b71534f (documented full mode):
python -B scripts/qa/verify_operations.py --checkout 'D:\UTEExpress\.work\qa01\verification'
# Semgrep installed locally; telemetry/version network check disabled:
New-Item -ItemType Directory -Path .work/qa01 -Force | Out-Null
$env:SEMGREP_SEND_METRICS = 'off'
$env:SEMGREP_ENABLE_VERSION_CHECK = '0'
$env:SEMGREP_SETTINGS_FILE = 'D:\UTEExpress\.work\qa01\semgrep-settings.yml'
semgrep scan --config scripts/security/qa01-semgrep.yml --metrics=off --disable-version-check --oss-only --json --output .work/qa01/semgrep-final.json src/main
git diff --check
```

Java21/Maven3.9.11/PG17.6 Docker Desktop29.4.3; Node24.14.0/Python3.14.2/Semgrep1.155.0.
Initial Git fetch failed DNS; retry succeeded before branch creation. Docker initially
stopped; started Desktop without deleting user DB/containers. Oracle javapath's own
`java -version` probe hung; exact direct JDK21 worked. Only that positively identified
probe was stopped, not a user application. First sandbox Semgrep attempt failed EACCES
socketpair; permitted offline rerun succeeded with metrics disabled.
After the late develop sync, Docker's desktop-linux pipe was unavailable even though
Desktop reported running. A failed attempt's unit692 PASS and72 PostgreSQL class
initialization errors are retained under `sync-docker-attempt/`. The live default
Linux engine pipe was then verified and selected only through process environment
variables above; no global Docker context, production config or user data was changed.
One subsequent full run at3c4fb55 was interrupted by a Docker Desktop app/quit event
at2026-10-27T17:38Z. The engine and live container disappeared during AdminRevenueIT.
After validating the exact fork's jar/time identity and Maven-parent verification
checkout, only that stranded QA-01 fork was stopped (Maven exit1). Logs/partial XML
are retained under docker-interrupted/. Desktop was reopened and the full clean gate
rerun; no other JVM, business source, container/volume or production config was removed.

| Final gate | Classes | Tests | Failures | Errors | Skips | Exit |
|---|---:|---:|---:|---:|---:|---:|
| Surefire | 75 | 694 | 0 | 0 | 0 | 0 |
| PostgreSQL Failsafe | 64 | 1329 | 0 | 0 | 0 | 0 |
| Java total | 139 | 2023 | 0 | 0 | 0 | 0 |
| Node | separate | 12 | 0 | 0 | 0 | 0 |

Failsafe timeout=false; XML flaky/rerun markers=0. Collection `2026-10-27T17:56:42.892922+00:00`.
Direct clean verify includes test/package; no skipped-test flags or fabricated extra
commands. QA-01's local collector checks clean Git/recorded SHA, exit files, exact XML
testcase counts/status and Node TAP; it is not an approved QA-03 runner or attestation.
The separate approved QA-03 runner then executed its full CLI on the same clean
checkout, freshly running both commands and generating schema-v2 evidence itself.
Runner SHA `b71534fb5eb70d94642f6f8804ff2d0f9b0de392`; script SHA-256 `4005a275ed21d1d657f9f8c5cb0ad663f83be08074fe66cb10926d494a21036f`.
Tested source `3c4fb5555b1ce580ed03dab592806c59f9bdf62a`; 2023 Java,12 Node, exits0,
timeout=false and zero failures/errors/skips/flaky/rerun markers. Its own17 tooling
tests PASS. `--plan` was also inspected but is not test verification. The runner branch
was not merged into the application; merged QA-03 reports authorize this external mode.
The generated artifact and its raw-log hashes were checked against actual bytes.
Baseline/focused/direct/runner totals overlap and are never summed.

Final log literal diagnostic occurrences: fork shutdown warning
0; closed-connection message
0. Expected fault-injection/error logs and
teardown warnings are not hidden by zero test-failure reporting. Runner log occurrences:
forced-fork 0, closed-connection
0. Pre-sync639 log:1 forced-fork,
524 closed-connection occurrences. The approved QA-03 listener is now included; these
observations describe the measured runs, not a universal clean-shutdown guarantee.

## Focused and failure evidence

- `otp-reproduction-compilation.log`: initial new-test compound-var compile failure,
  resolved; not a security reproduction or successful test run.
- `otp-reproduction.log` + `reproduction-reports/`:8 new cases,6 security-policy
  assertion failures,0 errors/skips on unchanged production; two log cases pass.
- `otp-remediation-focused.log`:27 unit/architecture +21 PG PASS (AUTH03 five,
  AUTH04 six, new OTP security ten); exit0.
- `cookie-csrf-focused.log`:14 unit architecture/upload/cookie +15 new PG cases PASS;
  exit0.
- `full-attempt1/`: first remediated full stopped at688 unit cases,126 context errors,
  no assertion failures/skips due stale PT10M test profile. Logs/XML/source SHA retained.
- `profile-regression.log`: fixture corrected toPT5M,688 unit cases PASS,exit0.
- `prior-int02/`: old INT-02 raw reports preserved before baseline clean; historical only.
- `baseline-reports/`: fresh QA-01 baseline XML preserved before subsequent test runs.
- `pre-sync/`: reports and successful639 full1981 results retained as history.
- `sync-docker-attempt/`: environmental failure after develop sync, not acceptance.
- `docker-interrupted/`: partial3c4fb55 run terminated only after confirmed Docker
  Desktop exit/container loss; Maven exit1, not a successful/incomplete PASS artifact.
- `synced-85/`: fresh2020-case full PASS at85f790b, preserved before the encoded-path
  fix; superseded by the final source gates, never reused as acceptance of that fix.
- `encoded-route-reproduction.log` and `encoded-route-red-reports/`: actual PG test
  fails once (expected429,actual302),0 errors/skips before parser correction.
- `encoded-route-remediation.log`: focused parsed-route, budget, architecture and
  PostgreSQL cookie/CSRF/OTP verification after correction; exit0.

All paths above are under ignored `.work/qa01/`. Direct final XML is preserved under
`.work/qa01/synced-direct/reports/surefire-reports/` and `failsafe-reports/`; per-suite counts/hashes
and Failsafe summary are in final-evidence.json. The runner's fresh XML is separately
under `.work/qa01/verification/target/surefire-reports/` and `failsafe-reports/`, with
generated JSON/raw logs under its `target/qa-03/`. Their hashes and per-suite counts
are in approved-runner-evidence.json. Primary workspace target reports are historical,
not the final3c4fb55 acceptance evidence. All raw XML/logs remain ignored/local.

## Scanner, credentials and dependencies

Semgrep actual revision `3c4fb5555b1ce580ed03dab592806c59f9bdf62a`, matching the final executable source.
Scanned production/rule objects:
src/main `6aecbbd6816e6798c53ccdb7b64d08529260ea6f`, rule blob `9cc7450020f1277c14e4c2b0c7e79ca782aa2c85`. Seven OSS rules,
605 targets,5 candidates,0 scanner errors,exit0;11 default
ignore-pattern files skipped. Approximate parser line coverage is scanner metadata,
not complete source-review/taint coverage. No suppression/owner source change added.

| Candidate source | Line at scanned source | Triage |
|---|---:|---|
| `src/main/java/com/uteexpress/governance/repository/OpsDashboardReadRepository.java` | 35 | Fixed SQL + bound values; reviewed |
| `src/main/java/com/uteexpress/governance/repository/OpsDashboardReadRepository.java` | 48 | Fixed SQL + bound values; reviewed |
| `src/main/java/com/uteexpress/governance/repository/OpsDashboardReadRepository.java` | 49 | Fixed SQL + bound values; reviewed |
| `src/main/java/com/uteexpress/notification/repository/NotificationRepository.java` | 75 | Fixed SQL + bound values; reviewed |
| `src/main/java/com/uteexpress/notification/repository/NotificationRepository.java` | 91 | Fixed SQL + bound values; reviewed |

Tracked-source pattern inventory: 874 readable text files,
9 reviewed synthetic test-password candidates,0 credential
filenames; limited sensitive-filename history check0 commits. Example credential values
were classified as placeholders without copying values. Test-only keys in POM/test YAML
remain separated from mandatory production ENV. This is not full-history entropy scanning.
Packaged dependency inventory has 109 library JAR names;
**CVE/SCA verification NOT RUN**, no usable local vulnerability DB/SCA scanner found.

| Private artifact reference | SHA-256 |
|---|---|
| `.work/qa01/baseline-evidence.json` | `3099c48b89d6d6b812a779c083c08dab1fe6f9f3f470c3939554b73277151da1` |
| `.work/qa01/final-evidence.json` | `19b425a86418af09e7e31726c836ac9b510e344a85af6c15238e09edbcbeea29` |
| `.work/qa01/baseline-maven.log` | `ceba2f0249121322d92158ce181a6c709335691a7b7365f2c7863fcf608b3ce8` |
| `.work/qa01/final-maven.log` | `e6fa41e9817968aad5e81c95ca1c4d92434de1716c16ae0d5ca1faf1e210edaa` |
| `.work/qa01/final-node.log` | `338f313c107b525e6e9c22bcdcafd3b002c124b3e9139d3fe9259ec6d92d9ce4` |
| `.work/qa01/otp-reproduction.log` | `d59a75895be1269e63cb5af8214f782f6aec1ad815fe0a5baa787f64a1527e2a` |
| `.work/qa01/otp-remediation-focused.log` | `99464c489f290bf490859fcd411a433e24c25a69b3fff2af37229657450ebf8e` |
| `.work/qa01/cookie-csrf-focused.log` | `2313ca5c26a0feb327db6feca1979bda6f5c3ccee06accbb6d51a6e8c0e51a5d` |
| `.work/qa01/profile-regression.log` | `51f3f153942a7d62cd575aaa2504b3b935fb25e41b7d33110a909f9578c10b73` |
| `.work/qa01/semgrep-final.json` | `dadac3db03dd7cf0f9b1dabd5be7505bfb494a8df6928aa769b390cf10c8b5cd` |
| `.work/qa01/semgrep-triage.json` | `fda857947ae867250a4a19bed5142021c9c44f3d20c473454ea93e0e6b910bc3` |
| `.work/qa01/source-audit.json` | `16a1e4e67ca92f3aae39a4efca5898efcc946c89618df4a141769d60ba78cd03` |
| `.work/qa01/resolved-libraries.txt` | `5b38efb194e97c48b392d7ac5f7427fc2f2ac00013c755440855fef1890c00c6` |
| `.work/qa01/env-template-audit.json` | `37a8668bb86d702d02fc5ca76618bbe8e54cd4ff4fd3089df254f8e19c24ade7` |
| `.work/qa01/startup-log-secret-check.json` | `21fe89ce0b131cc273c4913f0dc1aef74585dcd53803a545b429393a1e9d872e` |
| `.work/qa01/approved-runner-evidence.json` | `43175396d5e7b7929191e2a2de90f55ab3583e4d5c079eb558ad25d33e5a7ed7` |
| `.work/qa01/approved-runner.log` | `bb974e8ebbadcaeee8f45368bee7d5fe13fc7ebd04bb8e3656553057571873e8` |
| `.work/qa01/runner-tool-tests.log` | `a0361444988f389f3d06c0fb66f4952071c754fd44c95c574b7b055cf64f3ff4` |
| `.work/qa01/runner-plan.json` | `d22b5cb2befa148c85ab77e2d1a934d38cfc398319ac466055e2b5e31f0bcd43` |
| `.work/qa01/verification/target/qa-03/maven.log` | `1dc3aab470855e1707dadb6f0916e0479887415c2da9daca4e0d0db317e323c8` |
| `.work/qa01/verification/target/qa-03/node.log` | `3f6e619a50ed5f1de0019b83f626f48a72fb3ead529287d5f736b747b51abbae` |
| `.work/qa01/encoded-route-reproduction.log` | `51f3e3dc4efc0238697efef2fb8c99fda381c74ec5e5043f0d1c93cd87a8b9e5` |
| `.work/qa01/encoded-route-remediation.log` | `f41db6c32124f76be0f941cf8958fb3f9728d46b5e1b632297f888c3cb6c4605` |
| `.work/qa01/synced-85/final-evidence.json` | `1426d0943ed4ef17e13ef23339ce400f8c306435f2ecfd3d06069032f19b746f` |

## Tested executable Git input objects

| Input | Object ID |
|---|---|
| `src/main` | `6aecbbd6816e6798c53ccdb7b64d08529260ea6f` |
| `src/test` | `1b260ab9c8d7f9591b4c306d440d888e4db50c58` |
| `pom.xml` | `89ba1f2c4fdbd5aafc0e9dfe909034b52990452f` |
| `.github/workflows/ci.yml` | `97dc4d2f0d66997d78aaeb967c62f1c58c4a91b8` |

## QA-01 file scope relative to latest approved develop

- `M docs/security.md`
- `A scripts/security/qa01-semgrep.yml`
- `M src/main/java/com/uteexpress/common/exception/ErrorCode.java`
- `M src/main/java/com/uteexpress/identity/service/EmailVerificationService.java`
- `M src/main/java/com/uteexpress/identity/service/OtpProperties.java`
- `M src/main/java/com/uteexpress/identity/service/PasswordResetService.java`
- `A src/main/java/com/uteexpress/security/OtpRequestLimitProperties.java`
- `M src/main/java/com/uteexpress/security/SecurityConfig.java`
- `A src/main/java/com/uteexpress/security/dto/RequestBudgetDecision.java`
- `A src/main/java/com/uteexpress/security/filter/OtpRequestLimitFilter.java`
- `A src/main/java/com/uteexpress/security/service/OtpRequestRateLimiter.java`
- `M src/main/resources/application.yml`
- `M src/test/java/com/uteexpress/identity/service/EmailVerificationServiceTest.java`
- `M src/test/java/com/uteexpress/identity/service/OtpSecurityTest.java`
- `M src/test/java/com/uteexpress/identity/service/PasswordResetServiceTest.java`
- `A src/test/java/com/uteexpress/security/OtpRequestLimitFilterTest.java`
- `A src/test/java/com/uteexpress/security/OtpRequestRateLimiterTest.java`
- `A src/test/java/com/uteexpress/security/Qa01CookieCsrfIT.java`
- `A src/test/java/com/uteexpress/security/Qa01OtpSecurityIT.java`
- `M src/test/resources/application-test.yml`

The final four reports are added separately. No applied migration, TD/QĐ production
file, financial formula/Order lifecycle, QA CI workflow or branch protection is changed
by QA-01. The approved develop imports are excluded from this QA-01-only list.
No push, remote PR/comment/approval, merge into develop/main, production deployment or user-file/DB deletion.
Browser/manual SMTP/proxy TLS/ACL/restart/load/multi-instance acceptance remains outside
this executed local QA-01 scope. Current recommendation: technically ready, pending QĐ.

## Portable evidence and post-review synchronization

The initial local-only handoff above is historical. PR #66 has since been published;
QĐ's COMMENTED review at89600fd reports independent scoped878 Java/PostgreSQL +12
Node PASS and requires a final sync/CI/delta-review gate before final approval.

- Latest synced develop: `e88685213d3af20729d2e78c56568f44e0a2922c`.
- Normal merge commit: `f76c3bb2f3cdb266e3430f4f98436d2b9740489e`, behind0, no conflicts.
- Delta from89600fd: seven added QA-03 tooling/workflow/evidence files only.
- Application/test/POM/existing CI objects remain exactly the four IDs listed above.
- Imported `scripts/qa/verify_operations.py` SHA-256:
  `4005a275ed21d1d657f9f8c5cb0ad663f83be08074fe66cb10926d494a21036f`, identical to
  the approved runner used for the2023-case run.
- Fresh post-sync commands: `python -B -m unittest discover -s scripts/qa/tests -v`
  (17 PASS, exit0), and Node TAP command above (12 PASS, exit0), at f76c3bb.
  Private logs: `.work/qa01/final-sync/runner-tool-tests-permitted.log` and `node.log`.
  The sandbox-only attempt failed creating temp fixtures (17 cases/34 setup-cleanup
  errors); its separate log is retained, not counted as passing application evidence.
- `git diff --check`: PASS. No fresh full PostgreSQL rerun or relabelled source SHA
  is claimed; final CI runs on the published descendant and is linked in PR #66.

Portable artifact: [QA-01-runner-evidence.json](QA-01-runner-evidence.json).
SHA-256: `43175396d5e7b7929191e2a2de90f55ab3583e4d5c079eb558ad25d33e5a7ed7`.
This is a byte-for-byte copy of the generated schema-v2 manifest, not transcribed
counts. Its source remains3c4fb55, runner remains b71534f,2023 Java +12 Node, exits0,
timeout=false. Only identities, suite counts/hashes and provenance are included;
raw log/XML contents, system properties and credential values are absent. The raw
files' availability to QĐ is not implied by publishing their metadata/hashes.

The follow-up commit changes these two reports and adds only this sanitized JSON.
Final QĐ approval and HP merge decision remain independent, pending gates.
