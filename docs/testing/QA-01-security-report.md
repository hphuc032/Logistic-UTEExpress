# QA-01 — Current security acceptance handoff (2026-10-29)

Owner: Hoàng Phúc (HP). Reviewer: Quốc Đạt (QĐ). PR: https://github.com/hphuc032/Logistic-UTEExpress/pull/66.
Recommendation: **TECHNICALLY READY — PENDING QĐ REVIEW** of the new HEAD.
This report does not authorize merge, formal risk deferral, release sign-off or QA-01 COMPLETED.

## Source, review and Master Plan

- Previous QĐ APPROVED: `7aa74a215f2539012344d252ffd7e125c8fe64d3`; its scoped independent evidence was878 Java/PostgreSQL +12 Node. That approval is historical and is not relabelled as approval of the new source.
- Latest synced develop: `fa48cb69cef92276f0dddf0d2bd396eee098f2f3` (PR #67); normal clean merge, tested source `6e93d44614b1574ab2d310bae9a54ae57c385893`, branch `test/qa-01-security`. Behind=0 at sync.
- Full runner actually executed this merge SHA, not the old3c4fb55 source. Runner SHA is also `6e93d44614b1574ab2d310bae9a54ae57c385893`, script SHA-256 `4005a275ed21d1d657f9f8c5cb0ad663f83be08074fe66cb10926d494a21036f`.
- A later report/evidence-only commit is the published PR HEAD. Its exact SHA/CI is recorded in PR #66; executable inputs must remain the object IDs in the manifest.
- Master Plan read: `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`. The requested `(1).docx` filename was unavailable; the actual original contract and source identity remain explicit.
- QA-01: S5/P0, dependency INT-02; H1–H8/K/L/M/N and relevant T01–T11/T36–T47, ownership/commerce cases mapped in [the matrix](QA-01-security-matrix.md). No expansion into QA-02/QA-03/UI/REL completion.

## Actual integration delta and security boundaries

The three new develop commits are PR #67 Buyer cancellation UI plus evidence. Changes include BuyerOrderController, BuyerOrderService, HTML-only BuyerOrderPageDetail, the order detail template, buyer-cancellation.js, cancellation regressions and CI's combined Node command. HP imported the already merged owner implementation; no TD/QĐ production code was rewritten by this follow-up.

The HTML cancellation control is advisory: owner-scoped read in the existing read-only REPEATABLE_READ service, persisted version, current USER/persisted ACTIVE USER checks. The existing command still rechecks ownership/state/version under locks. Same-origin POST uses the rendered masked CSRF token; safe text feedback and disabled/in-flight behavior do not replace server authorization. Public JSON detail does not expose the HTML action version. No new wildcard exemption, JWT change or lifecycle writer was added.

Security, Identity, Account and Notification Java package objects, application.yml and POM are identical to7aa74a2. App main/test/CI objects changed through PR67, so fresh full regression was required and was performed. The existing QA-01 fixes remain:

- F01: positive OTP TTL <=5 minutes, attempts1..5, resend>=60s; old longer-lived records cannot bypass the current cap.
- F02: one bounded single-instance socket-peer budget across five registration/OTP/reset POST routes, after CSRF; generic429/Retry-After and fail-closed saturation.
- F05: native MVC-compatible parsed path matcher prevents encoded-route bypass; context paths and legitimate/safe/unrelated routes remain covered.
- F04 fixture correction retains the production guard and persistence-disabled HTTP profile exclusions. No applied migration changed.

## Fresh verification and actual results

Oracle Java21(+35), Maven Wrapper3.9.11, Docker Desktop Linux29.4.3, PostgreSQL17.6 Testcontainers, Node24.14.0, Python3.14.2, Semgrep1.155.0.

| Execution at 6e93d44 | Actual result |
|---|---|
| `python -B scripts/qa/verify_operations.py --checkout D:/UTEExpress/.work/qa01/verification` | Full supported CLI, exit0; generated provenance schema2 |
| Runner's exact Maven command: `mvnw.cmd -q -Ppostgres-it clean verify` | 694 unit/architecture (75 classes) + 1346 PostgreSQL (64 classes) = **2040 Java PASS**; test/package/verify included |
| Runner notification Node command | 12 PASS, exit0 |
| `node --test --test-reporter=tap src/test/js/notifications.test.cjs src/test/js/buyer-cancellation.test.cjs` | **33 PASS**, exit0; both client suites; overlaps the notification-only run |
| `python -B -m unittest discover -s scripts/qa/tests -v` | 17 tooling tests PASS, exit0; separate from application counts |
| `git diff --check` | PASS; final publication checked separately |

All Java failures/errors/skips/flaky/rerun markers=0; Failsafe timeout=false. The old2023 result and QĐ's878 are historical overlapping runs, never added to these totals. Current BuyerCancellationIT: 50 cases; Qa01OtpSecurityIT: 11; Qa01CookieCsrfIT: 15; real NotificationSocketIT: 29. Reused suites cover role/service guards, ownership, CSRF, uploads/XSS/SQL, OTP races, JWT revocation and atomic inbox/after-commit hints. HP made no test weakening, skips or production guard changes to obtain this result.

Docker was initially off; Desktop was started and API readiness verified before the full run. Raw Oct28 reports were preserved before clean. Final Maven log literal diagnostics: forced-fork 0, closed-connection 0; expected rollback/fault-injection logs are not asserted absent.

## Findings, source scan and unexecuted checks

No new confirmed Critical/High blocker was found in this HP delta inspection/fresh regression. This is not QĐ's final independent verdict. Existing F01/F02/F05 remediations remain supported; five SQL Semgrep candidates are fixed internal fragments with bound values, not demonstrated SQL injection. Offline scan on this exact source:7 rules/607 production targets/5 candidates/0 scanner errors, exit0. It is limited pattern coverage, not a complete taint audit or SCA.

Packaged dependency inventory: 109 library JAR names. No dependency upgrade or clean-CVE claim. Existing bounded credential/config review and manual PR67 code-delta inspection are not a new full-history entropy scan; no secret values are published.

| Check | Status/classification | Owner / closure requirement |
|---|---|---|
| Application security regression, PostgreSQL locks/transactions and WS | PASS on the current tested source | HP evidence; QĐ must review the new delta/HEAD |
| Dependency CVE/SCA | NOT RUN — verification gap; not a confirmed vulnerability or a clean-CVE result | HP/QĐ: run an available dependency scanner with a usable DB, or explicitly record scope/risk/owner/time-bound deferral. No such new deferral is authorized by this report |
| Live SMTP and network timing uniformity | NOT RUN — deployment/live-service verification | HP/REL: verify the real mail path and approved response/timing expectations; generic HTTP contract tests are narrower |
| Deployed HTTPS, proxy trust/NAT, cookie flags and storage ACLs | NOT RUN in a deployed environment | HP/REL: environment-specific acceptance. Unit/HTTP tests prove configured application behavior only |
| Multi-instance/distributed abuse limits, load and restart behavior | NOT RUN — operational limitation | HP/REL: single-instance limiter resets on restart; agree deployment limits/acceptance separately |
| Browser/manual Ops, full seed, backup/restore and release rehearsal | NOT rerun by QA-01 — separate QA/UI/REL ownership | Preserve each owner's exact-source evidence/scope decisions; importing PR67 browser evidence does not create new HP browser acceptance |
| Dependency/operational formal acceptance or deferral | PENDING HP decision where required | NOT RUN is not converted to PASS, accepted risk or COMPLETED by this update |

Prior HP QA-03 scope decisions (automated baseline accepted; Manager writes DENY ALL exception) retain their exact QA-03 scope. They do not imply positive Manager limited-write capability, global operational PASS or a new QA-01 dependency/SCA risk acceptance.

## Evidence and acceptance boundary

[Fresh generated manifest](QA-01-runner-evidence.json), SHA-256 `4d5df38345bf938c3554e2da24e337c4515847c9ec56c5cbe9813a3c0f39c436`, binds source/runner SHA, script/log/XML hashes and actual counts. [Evidence manifest](QA-01-evidence-manifest.md) and [findings](QA-01-findings.md) distinguish prior approvals, old runs and pending checks. Raw logs, system properties, credentials and build artifacts remain ignored/local. The narrow -text attribute preserves the generated JSON's original bytes.

QĐ must review the post-sync delta and new published HEAD/CI. HP retains formal acceptance/deferral and merge decisions. No merge into develop/main, auto-merge, deployment, migration edit or QA-01 completion is performed here. [Prior approved report](https://github.com/hphuc032/Logistic-UTEExpress/blob/7aa74a215f2539012344d252ffd7e125c8fe64d3/docs/testing/QA-01-security-report.md) remains available at7aa74a2.
