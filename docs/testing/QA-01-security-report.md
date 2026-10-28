# QA-01 — Security acceptance and regression

Owner/executor: Hoàng Phúc (HP). Intended reviewer: Quốc Đạt (QĐ).
Recommendation: **TECHNICALLY READY — PENDING QĐ REVIEW**.
Publication/sync follow-up is recorded at the end; the initial local handoff below
remains historical evidence for its stated source.
No independent QĐ acceptance review, approved PR, CI acceptance, merge, completed milestone or
production security/release sign-off is claimed.

## Scope and source of truth

Master Task Board QA-01: Sprint S5, P0, dependency INT-02, branch
`test/qa-01-security`; deliverable Security Test Report; Auth/OTP/RBAC/ownership/
upload/CSRF regression and evidence. H1/H2 actor permissions, H3–H8 controls,
K/L/M ownership/workflow/DoD and actual N T01–T50 were reviewed. Only applicable
security checks are acceptance claims; QA-02 commerce, QA-03 operations, UI-02
responsive and REL deployment remain their owners' work.

The actual original available document is
`D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256
`18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
The `(1).docx` filename was not found in the repository, D:/JavaWeb or the user's
Downloads/Documents/Desktop. The available original contains the exact QA-01 Task
Board contract and H8 five-minute rule; a different unseen revision is not assumed.

Also reviewed CONTRIBUTING.md, architecture/security, SEC-02, the four INT-02 reports,
current security/identity/upload boundaries, governance/ownership contracts and tests.
Historical report claims (including SEC-02's lack of WebSocket and abuse limiter) are
treated as dated snapshots, not current production state.

- Initial base/baseline develop: `798535b32ff81a011f34cfa01bbeb2d2dd0cb981` (INT-02 #62 merge verified in Git).
- During final audit, approved develop advanced to `d2f722d4075999b418e3092ccc75de3c49ec9e57` with Buyer cancellation
  #63 and QA-03 context cleanup #64. It was normally merged at
  `85f790b3e9ae5b3bcedbe8658d904b906b3dbc46`, without conflicts or owner-code rewrites.
- Branch: `test/qa-01-security`, created from origin/develop with a clean tree.
- Final executable/test source: `3c4fb5555b1ce580ed03dab592806c59f9bdf62a`.
- The clean verification checkout is `.work/qa01/verification` at the same source SHA;
  the task workspace's four untracked reports were retained and backed up. The
  subsequent documentation commit changes only these four QA-01 reports;
  executable inputs and evidence identities are rechecked unchanged.
- Existing INT-02 commits/attribution and ignored local evidence were preserved.

## Confirmed defects and narrow remediations

**QA01-F01 — Medium, resolved:** H8 requires OTP expiry after five minutes. Both
purposes were actually issued with ten-minute expiry, and configuration permitted
larger lifetimes/attempt counts and sub-minute cooldown. Correct default to PT5M;
fail startup for TTL >5 minutes, attempts >5 or cooldown <60 seconds (stricter
policies remain possible). Verification also caps old longer-lived rows at sent_at
plus the current TTL, without migration/data rewrites. Both purposes accept at 299s,
reject at 300s and keep expired/legacy tokens unconsumed. Existing purpose separation,
HMAC, normalization, cooldown, failed attempts, BCrypt and JWT invalidation remain.

**QA01-F02 — Medium, resolved within single-instance scope:** persisted account
cooldown/token attempts existed, but HTTP had no IP budget. Repeated unknown identities
and cross-purpose/route requests never throttled. Add one bounded synchronized budget
for POST register/verify/resend/forgot/reset, after CSRF and before business/mail work.
Defaults: 20 valid-CSRF requests/minute/transport peer, 10,000 retained windows;
positive bounded configuration, fail-closed saturation and expired-window reclamation.
Return safe 429 ErrorResponse + Retry-After, independently of account existence.
Forwarded headers do not establish a peer. No Redis, new OTP system, account lockout,
login framework or role-policy exemption is introduced.

The new PostgreSQL tests first ran against unchanged production and produced six
expected security-contract failures (2 TTL, 4 admission assertions), 0 errors/skips.
Two mail/log assertions already passed. Raw failure evidence is retained privately.
One initial test compile error (compound var declaration) was corrected and retained.

**QA01-F05 — Medium, resolved (QA-01 candidate regression):** final adversarial review
showed that raw URI equality in the newly added filter missed percent-encoded route
letters accepted by MVC. A real PG/MockMvc test reproduced an exhausted peer receiving
302 instead of429 through `/%66orgot-password`. Replace raw equality with Spring
Security's parsed POST PathPatternRequestMatcher at the same filter boundary. Encoded
letters/hyphen across the five routes share the budget; non-root context and unrelated
routes/safe methods are covered. CSRF ordering, firewall and public routes are unchanged.
This defect was introduced and closed locally during QA-01; no published branch is
claimed affected. One independent read-only pre-patch investigation and candidate
review challenged alternate representations and compatibility.

Earlier, the first full remediated run stopped at 126 context errors because the isolated
HTTP test profile still configured PT10M. Correct only that fixture to PT5M, preserving
DB exclusions/security and guard strength. Whole unit rerun passed; final full evidence
below is from the subsequent clean run, never that failed attempt.

## Added acceptance evidence

34 deterministic acceptance cases were added: Qa01OtpSecurityIT (11 PostgreSQL),
Qa01CookieCsrfIT (15 PostgreSQL), OtpRequestRateLimiterTest (5 unit), and one unsafe
OTP configuration case in OtpSecurityTest, plus OtpRequestLimitFilterTest (2 unit).
Existing authorization/upload/socket suites
were reused. The HTTP test profile and three older OTP fixtures were aligned with the
five-minute policy without weakening their assertions or persistence exclusions.

## Security controls and actors

| Area | Evidence-backed result |
|---|---|
| Auth/JWT/BCrypt | PASS: active credentials, current persisted roles, signature/issuer/exact audience/claims/version, generic inactive/bad-credential denial, password hash and reset/change/logout/role/lock revocation |
| Cookies/stateless auth | PASS: Secure on approved HTTPS setting, HttpOnly/Lax/path/TTL; explicit local HTTP exception; rendered CSRF and flash session cannot restore auth without JWT; no JWT browser-storage writes found |
| OTP | PASS: both purpose-specific HMAC flows, five-minute/current-policy cap, max attempts/cooldown/latest/consumed semantics, real PG concurrent consume, normalized uniqueness and generic response contract |
| RBAC/service guards | PASS: six actors Guest/User/Vendor/Manager/Admin/Shipper across existing URL+service suites; internal roles do not implicitly become buyers; explicit extra USER role remains valid |
| Ownership/BOLA | PASS: profile/address/cart/items/Buyer Orders/returns, Vendor Shop/Product/Orders/revenue, assigned Shipper, Review/media and own notification inbox/read; rejected writes preserve state |
| CSRF | PASS: public auth and unsafe commands, missing/invalid/browser-mismatched token, real rendered masked header/parameter, POST/PATCH/DELETE; no global disable/exemption introduced |
| Upload | PASS: JPEG/PNG/WebP extension+MIME+actual decode, 2MiB avatar/5MiB product-review, 5/3 image caps, 4096 dimensions, generated keys/root confinement, ownership and compensation/UNKNOWN maintenance contract |
| XSS/query/binding/errors | PASS in mapped cases: escaped plain text, safe notification text APIs, parameterized query values/sort whitelist, DTO/form allowlists and safe errors |
| Private STOMP/inbox | PASS: real socket Origin/CSRF/JWT/destination/recipient checks, expiry/logout/lock/role revocation, inbox same business TX, after-commit hints, rollback/offline/retry/dedupe |

PASS denotes the concrete tested contracts, not exhaustive proof of every possible
exploit or deployment configuration. See the traceability matrix for exact suites.
HTTP JWT validation retains the approved configured 60s clock tolerance; socket expiry
is strict. A mismatched browser token/cookie is rejected; stateless CSRF is not claimed
to be bound to an authentication HttpSession. PATCH/DELETE positive admission uses a
test-only handler; real business owner/state flows remain covered by their existing suites.

## Execution and exact results

Java Oracle 21 (+35), Maven Wrapper 3.9.11, Docker Desktop Linux 29.4.3,
PostgreSQL 17.6 Testcontainers, Node 24.14.0, Python 3.14.2, Semgrep 1.155.0.

| Execution | Source | Actual result |
|---|---|---|
| Baseline full clean verify | `798535b32ff81a011f34cfa01bbeb2d2dd0cb981` | 682 unit + 1268 PG = 1950 Java; exit 0 |
| Baseline Node | same | 12/12 PASS; exit 0 |
| OTP focused remediation | intermediate QA-01 candidate, before sync/path fix | 27 unit/architecture +21 PG PASS; includes AUTH-03/04 |
| Cookie/CSRF focused | intermediate QA-01 candidate | 14 unit/architecture/upload/cookie +15 PG PASS |
| Test-profile regression | 63960dc fixture correction | 688 unit PASS |
| Encoded-route focused remediation | 3c4fb55 executable content | 14 unit/architecture +26 PG PASS; includes legitimate routes and real cookie CSRF |
| Final `.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify` | `3c4fb5555b1ce580ed03dab592806c59f9bdf62a` | 694 unit/75 classes + 1329 PG/64 classes = 2023 Java; exit 0 |
| Final `node --test --test-reporter=tap src/test/js/notifications.test.cjs` | same | 12/12 PASS; exit 0 |
| Approved external QA-03 runner full mode | same source, runner `b71534fb5eb70d94642f6f8804ff2d0f9b0de392` | 2023 Java +12 Node PASS; separate fresh run, exits 0 |
| Runner's own self-tests | same pinned runner | 17/17 PASS; separate tooling tests |
| `git diff --check` | final handoff | PASS |

Final failures/errors/skips/flaky-rerun XML markers=0; Failsafe timeout=false.
Clean verify includes test/package; separate final package/test commands are not
fabricated. Baseline/focused/failed/full counts are not added together. Raw reports
were preserved before each clean. The intermediate pre-sync full run at 63960dc passed
1981 Java tests. The first post-sync full run at85f790b passed2020 Java but was
superseded after the encoded-route defect was discovered. After develop advanced, a
first environmental attempt passed 692 unit
tests but could not initialize PostgreSQL classes because Docker's desktop-linux pipe
was absent. That attempt is retained, not counted as successful verification. Reconnecting
to the live default Docker pipe for these processes allowed both fresh final gates.
The approved QA-03 context-cleanup change is included; diagnostic counts from each
final log and the earlier teardown observations are recorded in the manifest.
A later gate on the path-fixed source was interrupted when Docker Desktop received
an app/quit request mid-suite. The active PG container lost its connection; this was
not treated as an application assertion/schema failure or a passing run. The exact
stranded QA-01 fork was identified through its Maven parent/verification checkout
before termination. Docker was restarted without deleting user data; raw partial
evidence is retained under docker-interrupted/, followed by fresh clean verification.

## Offline source/dependency assessment and limitations

Seven checked-in local Semgrep rules scanned 605 production
targets, exit 0, no scanner errors; 5 SQL-concatenation candidates
were individually triaged as fixed SQL fragments with bound values. No QĐ/TD query was
silently rewritten. Semgrep is a limited pattern scan, not a full taint/exploit/SCA audit;
11 default ignore-pattern targets were skipped. Its actual scan revision is `3c4fb5555b1ce580ed03dab592806c59f9bdf62a`;
production/rule Git object IDs match the final executable source exactly.

Local redacted tracked-source inventory read 874 text files:
9 sensitive-assignment candidates, all reviewed synthetic test
fixtures; no tracked credential filenames or historical commits touching the checked
sensitive filename patterns. Mandatory production ENV and example placeholders were
reviewed; test-only JWT/OTP keys are not reproduced here. This is not a full-history
content/entropy scan or a claim that all possible credentials are impossible.
Resolved packaged dependency inventory: 109 library
JARs. **Dependency CVE/SCA scan: NOT RUN** — no usable local vulnerability database/
SCA scanner was available; Semgrep OSS pattern rules do not substitute for it. No
major dependency upgrade or claim of vulnerability-free dependencies is made.

QA-01 CI: NOT RUN, since this local branch has not been pushed and no PR is authorized.
Newly merged QA-03 reports explicitly reference the approved external evidence runner
`b71534f`. It was pinned in a separate clean checkout and used in its documented full
`--checkout` mode against the clean QA-01 verification checkout. Its tooling branch
was not merged into the application. The runner's fresh 17 self-tests and generated
schema-v2 evidence are recorded separately from application test counts.
Browser/responsive, live SMTP/constant-time network behavior, TLS/proxy/storage ACLs,
full T49 deployment/restart and release/load/multi-instance acceptance are not run here.
The IP limiter resets on restart and is not distributed or a login/DDoS guarantee;
shared NAT/proxy address trust and external abuse protection need deployment review.
Manager commission positive-write limits remain a proposed business policy in the
Plan; approved ADMIN-06 denies Manager writes. This safe denial is verified; QA-01
does not implement TD financial policy or silently label a missing positive capability PASS.

## Handoff

No confirmed Critical/High QA-01 finding remains open in tested application scope.
Core mandatory runtime controls pass; scanner/deployment limitations remain explicit.
QĐ must review this security change/report. HP retains merge authorization after PR/CI
and review gates; the task is not marked COMPLETED. Only local QA-01 commits were made;
no push, PR creation/approval, merge into develop/main, applied migration edit, security weakening,
production deployment or user database/file deletion occurred.

## PR #66 — QĐ scoped review and final sync (2026-10-28)

PR: https://github.com/hphuc032/Logistic-UTEExpress/pull/66, targeting develop.
QĐ independently reviewed `89600fd15775672d667a600cbf1d3d634bb482b4` and recorded
[scoped technical PASS](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#pullrequestreview-5677736966):
878 distinct Java/PostgreSQL +12 Node PASS, with no new confirmed Critical/High
blocker in the reviewed scope. This is not an independent full2023-case rerun,
verification of HP's private raw files, a dependency SCA result or final APPROVED.

To close GATE-QA01-SYNC, approved develop
`e88685213d3af20729d2e78c56568f44e0a2922c` was normally merged at
`f76c3bb2f3cdb266e3430f4f98436d2b9740489e`, without conflicts, rebase or force push.
Behind count became0. The seven imported files are approved QA-03 tooling, its
workflow and evidence/docs. `src/main`, `src/test`, `pom.xml` and the existing
`.github/workflows/ci.yml` retain exactly the Git objects recorded above and reviewed
by QĐ. The imported runner script SHA-256 also matches the previously executed runner.

Post-sync checks at f76c3bb: runner self-tests17/17 and Node12/12 PASS; diff check
PASS. The first sandboxed tooling attempt could not create its temporary fixtures;
the permitted rerun passed with unchanged tests. No new full PostgreSQL run is claimed
on this merge SHA: the2023-case evidence remains bound to3c4fb55 and input equality
is explicit. Final-head CI and final delta-review status are recorded in the PR;
the documentation/evidence commit after this merge does not change executable inputs.

[Portable runner evidence](QA-01-runner-evidence.json) is the exact sanitized,
tool-generated schema-v2 manifest from that full run. It contains counts, source/
runner identities and hashes, with no raw logs, XML properties or credentials.
Raw artifacts stay local. Final QĐ APPROVED and HP merge authorization remain pending;
QA-01 is not marked COMPLETED and this PR is not merged.

While publishing the follow-up, develop advanced again to
`72558c7dba1b69e847e5c87f9d580732ab1b185a` (QA-03 acceptance docs PR #65).
It was normally merged at `a12de4a6d30686ab62553a5f7bcc251ec9f10fa7`, importing
12 documentation/evidence files only. Behind again became0; all four executable
input objects and the runner script hash remain unchanged. The final follow-up
also makes Git preserve this QA-01 manifest's exact generated bytes with a narrowly
scoped documentation attribute. JSON data/provenance is unchanged; this prevents
Windows CRLF normalization from invalidating the published raw-byte checksum.
