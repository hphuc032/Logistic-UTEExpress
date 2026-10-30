# QA-01 — Application Security Acceptance close-out (2026-10-29)

Owner: Hoàng Phúc (HP). Reviewer: Quốc Đạt (QĐ). PR: https://github.com/hphuc032/Logistic-UTEExpress/pull/66.
Scoped disposition: **APPLICATION SECURITY REMEDIATION MERGED — ACCEPTED WITH DEFERRED GATES**.
Overall QA-01 is **not COMPLETED**. PR #66 application-security remediation is accepted for integration; dependency and operational/release gates below remain outstanding. This close-out records HP's existing decision and does not add a waiver or claim absolute security.

## Verified integration / acceptance record

| Identity or decision | Verified evidence |
|---|---|
| Develop at this close-out's fetch / documentation branch base | `b538dbc3af17ff80802fa33eea187705c40059fe`; PR #66 merge is an ancestor. This is a dated snapshot, not an assertion about future develop commits |
| Executable source actually tested | `6e93d44614b1574ab2d310bae9a54ae57c385893` |
| Final PR #66 HEAD reviewed by QĐ | `026a29deb6695e23380d0a95c5da68564add004e` |
| PR #66 merge commit | `b538dbc3af17ff80802fa33eea187705c40059fe`, GitHub MERGED at2026-10-29T04:13:24Z |
| Exact-head standard CI | [40814999100 SUCCESS](https://github.com/hphuc032/Logistic-UTEExpress/actions/runs/40814999100): combined Node, Maven test and package. This CI does not execute the PostgreSQL IT profile |
| Final QĐ approval | [APPROVED on026a29d,2026-10-29T04:09:08Z](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#pullrequestreview-5692747388), scoped technical delta/code/evidence review |
| HP scoped acceptance and formal NOT-RUN disposition | [2026-10-29T04:13:15Z decision](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#issuecomment-6487721811): application-security integration accepted, SCA deferred without production waiver, operational checks still NOT RUN |

Git verification shows6e93d44→026a29d changes only five QA-01 report/evidence files. The reviewed HEAD and merge commit have identical entire repository trees; application/test/POM/workflow/QA/security-script input objects also match the full-tested source. No evidence is relabelled as a new run on the merge or documentation commit.

### Evidence attribution and limits

| Party | Evidence accepted in this scope | Explicit limit |
|---|---|---|
| HP | Source-bound694 unit/architecture +1,346 PostgreSQL =2,040 Java PASS at6e93d44;33 combined Node and17 tooling PASS; published schema-v2 provenance | A prior execution, not rerun for this documentation-only close-out |
| QĐ / datcaq | Exact-head026a29d independent33 Node +17 tooling PASS, static delta and public manifest/hash/count consistency review; GitHub APPROVED | No new Java/PostgreSQL run at this HEAD; no rehash of HP's private raw XML/logs. Earlier878 Java+12 Node at89600fd remains separate historical scoped evidence |
| TD / Tender0603 | Imported PR67 cancellation implementation/regression and [separate buyer-cancellation browser packet](QA-02-browser-acceptance-PR67.md), executable source c5901c326ff92fee0fde0f8a4c66633a003cfc81 | TD-owned and TD-attested browser results are not HP's2,040 run or QĐ's independent Node/tooling results; do not sum or promote them to global T50/QA-02 completion |

QĐ's private receipt/log files are not available on this machine; their outcome is attributed to the authenticated GitHub review, not claimed independently rehashed here. HP's available local artifacts were checked again without executing tests; see the evidence manifest.

## Historical source context and Master Plan

- Historical QĐ APPROVED before the final sync: `7aa74a215f2539012344d252ffd7e125c8fe64d3`; its scoped independent evidence was878 Java/PostgreSQL +12 Node. That approval is historical and is not relabelled as approval of the new source.
- Latest synced develop: `fa48cb69cef92276f0dddf0d2bd396eee098f2f3` (PR #67); normal clean merge, tested source `6e93d44614b1574ab2d310bae9a54ae57c385893`, branch `test/qa-01-security`. Behind=0 at sync.
- Full runner actually executed this merge SHA, not the old3c4fb55 source. Runner SHA is also `6e93d44614b1574ab2d310bae9a54ae57c385893`, script SHA-256 `4005a275ed21d1d657f9f8c5cb0ad663f83be08074fe66cb10926d494a21036f`.
- The subsequent five-file report/evidence publication commit was026a29d, now approved and merged as verified above; the following run details retain their original source identity.
- Supplied Master Plan read: `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`. The original Task Board row and acceptance/ownership sections were checked again for this close-out; the external DOCX remains unchanged.
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

No new confirmed Critical/High blocker was found in the recorded HP delta inspection/regression. QĐ's subsequent scoped technical approval is verified above; neither result is a production security verdict. Existing F01/F02/F05 remediations remain supported; five SQL Semgrep candidates are fixed internal fragments with bound values, not demonstrated SQL injection. Offline scan on the tested source:7 rules/607 production targets/5 candidates/0 scanner errors, exit0. It is limited pattern coverage, not a complete taint audit or SCA.

Packaged dependency inventory: 109 library JAR names. No dependency upgrade or clean-CVE claim. Existing bounded credential/config review and manual PR67 code-delta inspection are not a new full-history entropy scan; no secret values are published.

## Deferred and operational follow-up gates

[HP's recorded decision](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#issuecomment-6487721811) formally defers dependency SCA to a separate security/release gate and routes the other NOT RUN checks to their operational/release gates. **DEFERRED describes scheduling/acceptance scope; execution remains NOT RUN.** This is not a finding of zero vulnerabilities, risk acceptance of an unknown CVE, a production waiver, a deadline or a closure of those gates. The status label above does not expand that decision.

Reuse the existing Master Plan task IDs and published Q03 gates below; no new task ID is introduced. Owners come from the Plan's Master Task Board/M2 and existing QA-03/seed records. Target-environment PASS requires actual source/build/environment-bound evidence, not this documentation change.

| Existing task/gate | Owner / reviewer | Current execution and disposition | Concrete closure and evidence | Production/release relation |
|---|---|---|---|---|
| QA-01 / FR-38 dependency CVE/SCA; carry to REL-01/REL-02 | HP security owner; QĐ review; QĐ coordinates REL-01 | **DEFERRED by HP; scan NOT RUN** | Dated scanner/tool and vulnerability-DB versions, resolved transitive/runtime dependency scope, reproducible scan report and CVE applicability/severity triage; remediate actionable findings or obtain specific documented HP decisions. The existing109-library inventory and Semgrep patterns are not SCA. No specific CVE risk acceptance is recorded | HP explicitly did not waive dependency vulnerability review before production release; close this gate before release security acceptance |
| AUTH-03/AUTH-04 + QA-01 live SMTP / timing; REL-01 | HP auth/security; QĐ security review and deployment coordination | **NOT RUN; deferred to operational/release verification** | Verify real approved SMTP/TLS delivery and failure behavior using fictional accounts; record sanitized delivery outcomes and status/body/redirect plus timing samples for known/unknown/ineligible accounts under an HP/QĐ-agreed method. No raw OTP/password/JWT or assumed constant-time guarantee | Local mocks/generic-response tests do not accept the live service; complete applicable live-service acceptance on the target build before production |
| REL-01 / Q03-R01 / T49 deployed TLS, proxy, trusted IP, cookies and storage ACLs | QĐ deployment owner; HP security review | **NOT RUN; deferred deployment gate** | Target-host HTTPS/proxy configuration; prove untrusted forwarding headers cannot spoof the effective peer, record intended proxy/NAT treatment, Secure/HttpOnly/SameSite and CSRF behavior, filesystem ownership/permissions and protected media access. Attach redacted headers/config/ACL checks and build/environment identity | Required target-environment security evidence before production; test flags/MockMvc do not prove deployed settings |
| QA-01 limiter operations carried to REL-01 / Q03-R01 | HP security contract; QĐ/operator deployment; TD reviews commerce effects if exercised | **NOT RUN; deferred operations gate** | Agree deployment topology/workload and expected limits, then measure concurrent peers/NAT, restart/failure recovery, load and multi-instance behavior. Record throttling/auth/side-effect results. Current in-memory budget resets on restart and is not distributed; any alternate design needs separate owner review | No distributed/load/restart-resilience sign-off. Validate the chosen production topology and protection before production; no numeric SLA or exception is invented here |
| UI-02 / Q03-G01 / T50 broader browser/manual acceptance | HP UI owner/coordinator; QĐ Ops owner/reviewer; group executes relevant flows | **Not rerun/accepted by QA-01; broader gate remains open in its owner packet** | Source-bound Register→Order→Delivery→Review and negative-role/foreign-owner journeys, notification recovery, 375/768/desktop evidence and named manual sign-off. TD's narrow PR67 buyer-cancellation browser evidence stays separately attributed | Applies to demo/UI/release acceptance; Node/HTTP tests and PR67's narrow browser result do not close global T50. QA-02/QA-03 status is unchanged |
| QA-03 / Q03-G02 / T48b / SEED_MANIFEST | HP identities/catalog and coordination; TD Commerce; QĐ Ops/orchestration | **Full seed not accepted by this close-out; existing gate remains open** | Execute the agreed ordered seed on an empty migrated DB and rerun idempotently; record dataset/source, natural keys/counts and lifecycle/financial/ownership consistency without secrets. Existing roles/test fixtures are insufficient | Required complete demo/clean-host acceptance under the Plan/REL-01; no seed implementation or QA-03 status change here |
| REL-01 / Q03-R02 off-host DB + uploads recovery | QĐ/operator; HP review | **Target/off-host verification NOT RUN; deferred release gate** | Restore both DB and uploads off-host, check fingerprints/references, ACLs and agreed recovery objectives. Existing QA-03 local DB-only rehearsal is narrower and is not discarded or promoted to off-host PASS | Required recovery evidence for applicable production/release acceptance; no new recovery deadline/objective is set here |
| REL-01 Q03-R03 and REL-02 release rehearsal / T49–T50 | QĐ deployed operations; HP release owner; TD REL-02 reviewer | **NOT RUN by QA-01; existing release gates retained** | Sanitized deployed log/alert and media-reconciliation evidence; clean-host README/ENV/migration/storage checks; source-bound rehearsal and release checklist after required REL-01/UI/DOC dependencies. Follow the existing runbook, not ad hoc deletion of uncertain media | Final production/release acceptance remains separate. This close-out creates no develop→main PR, tag, deployment or release approval |

The QA-03 packet's recorded local PASS results and accepted scope exceptions retain their exact source/scope; its broader open-gate rows are referenced, not edited. No QA-02 or QA-03 status is changed. HP retains the decision to close each gate from new evidence; no new deadline or blanket risk acceptance is assigned.

Prior HP QA-03 scope decisions (automated baseline accepted; Manager writes DENY ALL exception) retain their exact QA-03 scope. They do not imply positive Manager limited-write capability or global operational PASS. The QA-01 SCA/operational deferral is sourced separately to HP's PR #66 decision above, not inferred from QA-03.

## Evidence and acceptance boundary

[Fresh generated manifest](QA-01-runner-evidence.json), SHA-256 `4d5df38345bf938c3554e2da24e337c4515847c9ec56c5cbe9813a3c0f39c436`, binds source/runner SHA, script/log/XML hashes and actual counts. [Evidence manifest](QA-01-evidence-manifest.md) and [findings](QA-01-findings.md) distinguish prior approvals, old runs and pending checks. Raw logs, system properties, credentials and build artifacts remain ignored/local. The narrow -text attribute preserves the generated JSON's original bytes.

QĐ's final review and HP's application-scope merge are now recorded above. Remaining release/security gates are not PASS, and overall QA-01 remains open. This documentation PR makes no production change, new merge/release decision, deployment or QA-01 completion. [Historical report](https://github.com/hphuc032/Logistic-UTEExpress/blob/7aa74a215f2539012344d252ffd7e125c8fe64d3/docs/testing/QA-01-security-report.md) remains available at7aa74a2.


## Master Task Board and scope boundary

[Repository Master Task Board status register](../MASTER_TASK_BOARD.md) records QA-01's scoped disposition and reuses the existing gate/task IDs. At the audited base no Master Plan DOCX or Task Board was tracked in this repository. The supplied external DOCX was read and hashed; it remains unchanged. The register is the first version-controlled status overlay for that original board, not a second normative plan or a recreation of other tasks' current statuses. QA-02/QA-03 files and status records are untouched.
