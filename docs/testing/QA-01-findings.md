# QA-01 findings and residual verification

## Current scoped disposition — 2026-10-29

**APPLICATION SECURITY REMEDIATION MERGED — ACCEPTED WITH DEFERRED GATES**. Final QĐ [APPROVED on026a29d](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#pullrequestreview-5692747388), [HP's application-scope acceptance/formal deferral](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#issuecomment-6487721811), exact-head CI40814999100 SUCCESS and merge b538dbc are verified. These events do not delete findings, convert NOT RUN to PASS or complete overall QA-01. Finding severities, reproduction/root-cause history and RESOLVED/false-positive classifications below are retained.

HP's available local evidence was revalidated, not rerun: portable checksum,139 suite XML hashes and9 private artifact hashes match. QĐ independently ran33 Node and17 tooling on026a29d and reviewed metadata; no new QĐ Java/PostgreSQL/full-suite execution is claimed.

Initial baseline `798535b32ff81a011f34cfa01bbeb2d2dd0cb981`; original remediation tests at3c4fb55. Current fresh-tested source `6e93d44614b1574ab2d310bae9a54ae57c385893` after develop fa48cb69cef92276f0dddf0d2bd396eee098f2f3. Severity describes demonstrated
impact, not a CVE/CVSS calculation. No account takeover, secret exfiltration or
production exploitation is inferred merely from a failed policy assertion.

## QA01-F01 — OTP lifetime/policy exceeds H8

- Severity: **Medium** — doubles the accepted challenge exposure window; five-attempt
  control already existed, so this is not evidence of unconstrained OTP guessing.
- Component: application OTP config, OtpProperties, EmailVerificationService and
  PasswordResetService. Owner: HP.
- Reproduction: fresh PG, issue both purposes using actual configured services,
  inspect expires_at-sent_at and require five minutes; run Qa01OtpSecurityIT against
  unchanged production. Both cases fail: actual10M vs expected5M.
- Expected: H8 five-minute expiry, at most5 failed attempts, resend >=60s; old records
  must not extend the current maximum challenge lifetime.
- Actual before fix: PT10M; properties accepted any positive TTL/attempt count and
  nonnegative cooldown. Verification trusted only persisted expires_at.
- Root cause: AUTH-03 configurable10M default drifted from Master Plan H8; late-expiry
  tests advanced11M and did not distinguish5M from10M.
- Risk: longer use of a leaked/intercepted code and unsafe overrides weakening policy.
- Remediation: defaultPT5M, fail-closed property ceilings/floor; verify against the
  earlier of persisted expires_at and sent_at+currentTTL. No schema rewrite/backfill.
- Status: **RESOLVED**, application-scope remediation approved by QĐ at026a29d and merged in PR #66; remaining gates are separate.
- Test evidence: Qa01OtpSecurityIT two purpose boundary +two legacy-row cases,
  OtpSecurityTest.unsafePolicyOverridesFailClosedInsteadOfExtendingTheOtpAttackWindow,
  existing AUTH-03/04 attempts/purpose/concurrent-consume tests, final clean full run.

## QA01-F02 — Missing transport-peer budget for public OTP paths

- Severity: **Medium** — cross-identity SMTP/resource abuse and request flooding;
  existing per-account cooldown/token attempts limit individual challenge abuse.
- Component: SecurityFilterChain/public registration/OTP/reset admission. Owner: HP.
- Reproduction: valid CSRF, configured budget3, issue4 unknown-email forgot POSTs;
  repeat while changing identity, spoofing forwarding headers or switching OTP routes.
  Actual 4th response was302/200, never429. Four new rate assertions failed on base.
- Expected: H8 account/IP limits, no account-existence-dependent throttle, no DB/mail
  effects after budget rejection.
- Root cause: per-account DB controls existed but the SEC-02-deferred HTTP IP layer
  was never implemented/configured; no deployment limiter was available to verify.
- Risk: many fresh identities can avoid a single account's cooldown/attempt counter.
- Remediation: one synchronized bounded fixed-window budget over five POST routes,
  after CSRF; socket remoteAddr only; safe429+Retry-After; no active-counter eviction;
  expired reclamation and fail-closed capacity/configuration.
- Status: **RESOLVED for this single-instance application boundary**, application-scope remediation approved by QĐ at026a29d and merged in PR #66; remaining gates are separate.
- Evidence: Qa01OtpSecurityIT four admission/no-side-effect cases and logging checks;
  OtpRequestRateLimiterTest 5 tests (concurrent cap,
  expiry rounding, capacity, clock rollback/unknown peers, invalid configuration);
  existing account cooldown/attempt and final full regression.
- Limit: 20/min/peer and10,000 peers are configurable operational defaults, not numeric
  values attributed to the Plan. Memory resets on restart; no cluster/global login/
  DDoS guarantee. Proxy trust/shared NAT and external abuse controls need deployment review.

## QA01-F03 — Offline SQL scanner candidates

- Severity: Informational; **not confirmed SQL injection**.
- Components/owners: OpsDashboardReadRepository (QĐ,3 candidates),
  NotificationRepository (QĐ,2 candidates).
- Reproduction: offline Semgrep7 local rules against src/main;5 findings, no errors.
- Expected: user values bound and SQL structure fixed/whitelisted.
- Actual/root cause: scanner flags any concatenation. Governance combines only fixed
  optional clauses and binds Shop/date/action/type/actor/target/page values. Notification
  concatenates the constant HISTORY_RECIPIENTS SQL and binds Order/recipient IDs.
- Risk: the patterns are review signals; no untrusted SQL structure was demonstrated.
- Remediation: manual source/dataflow triage; no owner query change or suppression.
- Owner: QĐ; QA triage HP. Status: **REVIEWED — FALSE POSITIVES in the shown paths**.
- Evidence: Semgrep sanitized path/line inventory, source inspection and runtime
  OpsDashboardReadIT/NotificationIT/ProductDiscoveryIT injection/scope regressions.

## QA01-F04 — Stale isolated test-profile TTL

- Severity: Informational, test configuration regression; owner HP.
- Component: src/test/resources/application-test.yml.
- Reproduction: first full run after guard change;688 unit cases reported126 context
  errors,0 assertion failures,0 skips; OtpProperties rejected PT10M.
- Expected: fixture uses accepted security policy while keeping persistence exclusions.
- Actual/root cause: test profile duplicated the old TTL independently of base YAML.
- Risk: prevents regression evidence, not evidence of production exploitation.
- Remediation: change that single fixture value to PT5M; retain guards/exclusions.
- Status: **RESOLVED**. Evidence: whole unit profile rerun688/688 and final clean gate.
  First failed logs/XML are preserved; they are not counted as successful tests.

## QA01-F05 — Encoded-path admission bypass in the QA-01 candidate

- Severity: **Medium** — circumvents the new peer budget, restoring the resource/SMTP
  abuse path already described in F02; it does not bypass account/token controls.
- Component/owner: OtpRequestLimitFilter, HP. Introduced locally by QA-01, not a
  claim about a published production release.
- Reproduction: exhaust the peer budget with canonical forgot-password POSTs, then
  POST `/%66orgot-password` with valid CSRF. Fresh PG/MockMvc expected429 but received302;
  one failed assertion,0 errors/skips. A small standalone controller probe also showed
  canonical200, exhausted429, encoded200 before the change.
- Expected: all representations MVC resolves to one protected OTP route share admission.
- Root cause: comparison of raw getRequestURI against literal paths while MVC parses
  and decodes path segments before controller dispatch.
- Risk: accepted encoded aliases could send further OTP work after the canonical
  peer allowance was exhausted, bypassing the intended shared HTTP abuse control.
- Remediation: use native Spring Security7.1.1 POST PathPatternRequestMatcher with
  the same five exact routes. No manual URLDecoder/double decoding, broader permit,
  CSRF bypass, forwarding trust or firewall changes.
- Status: **RESOLVED**, application-scope remediation approved by QĐ at026a29d and merged in PR #66; remaining gates are separate.
- Evidence: encodedRouteSegmentsCannotBypassTheSharedAdmissionBudget across encoded
  route letters/hyphen; OtpRequestLimitFilterTest (context-relative matching, safe
  methods and unrelated suffix/path exclusions); focused and final full gates. The
  previous2020-case full run is historical, not final-source acceptance.
- Independent read-only investigation and candidate review were used for this fix;
  only concrete supported parser/boundary concerns were acted on. Existing strict
  firewall rejection of semicolon/encoded separators remains unchanged.

## Existing/non-vulnerability follow-ups and unexecuted checks

- Low historical QA infrastructure (QA-03): the pre-sync 63960dc run contained one
  forced-fork warning and 524 closed-connection message occurrences. Approved QA-03
  context cleanup was subsequently merged from develop. Both post-sync full logs are
  measured separately in the manifest; absence in these runs is not a universal
  clean-shutdown guarantee. No warning suppression or production deadlock claim.
- Dependency CVE/SCA: NOT RUN; local library versions inventoried, no usable vulnerability
  database/SCA scanner. Semgrep pattern scan is not a replacement. No automatic upgrade.
- Local secret check is bounded: tracked text/known patterns and sensitive filenames
  across available local history; 9 reviewed synthetic test-password candidates, mandatory ENV
  and example placeholders. Not a full-history entropy/Gitleaks scan.
- Actual Master Plan original unnumbered filename/hash was read; `(1).docx` absent.
  No unseen revision is claimed. QĐ should review the recorded source document identity.
- Proposed H2 positive commission limits belong to TD/QĐ business decisions; Manager
  writes currently fail closed under approved ADMIN-06. No financial formula/role policy
  rewrite is made by QA-01.
- Real SMTP timing uniformity, external rate limits, production TLS/proxy/ACLs, browser,
  restart/upload retention/load/multi-instance checks remain QA/UI/REL responsibilities.
  Public outcome claims cover approved body/status/redirect contracts, not constant-time
  network responses or an exhaustive account-enumeration side-channel assessment.

No unresolved confirmed Critical/High QA-01 vulnerability in the tested scope.
PR #66 is now merged at b538dbc after QĐ APPROVED026a29d and HP scoped acceptance. Earlier7aa74a2 approval remains historical. This documentation follow-up performs no merge and does not mark overall QA-01 COMPLETED.


## 2026-10-29 current delta verdict and outstanding verification

Fresh source `6e93d44614b1574ab2d310bae9a54ae57c385893`, synced develop `fa48cb69cef92276f0dddf0d2bd396eee098f2f3`; full 2040 Java +33 combined Node PASS, failures/errors/skips0. The original7aa74a2 approval remains historical; subsequent QĐ approval on026a29d and HP merge are recorded above. QĐ did not rerun Java/PostgreSQL at026a29d.

PR67 changes the owner-scoped cancellation page/read model and browser adapter. Existing server command locks/identity/state/version, safe CSRF token handling, foreign/missing/state/role guards and private WS/inbox contracts were exercised by the fresh suite. Security/Identity/Account/Notification Java package objects remain byte-identical; no new confirmed application vulnerability was identified in this follow-up. The earlier findings describe their original reproduction sources; fixes are preserved, not reimplemented.

The [canonical follow-up gate table](QA-01-security-report.md#deferred-and-operational-follow-up-gates) now records the existing task/gate IDs, owner/reviewer, execution status, HP disposition, required evidence and production/release relation. Reuse QA-01/FR-38, AUTH-03/AUTH-04, UI-02/T50, Q03-G01/G02/R01/R02/R03 and REL-01/REL-02; no duplicate task ID or deadline is created.

SCA remains a verification gap, not a reported CVE or a clean-CVE result. HP's existing [formal deferral](https://github.com/hphuc032/Logistic-UTEExpress/pull/66#issuecomment-6487721811) is now recorded: it permits application-scope integration and does not waive dependency review before production. Other operational checks remain NOT RUN and routed to their existing release gates, not PASS. Actual deployed security, broad browser/QA-03 acceptance and overall QA-01 COMPLETED remain unclaimed. No new risk acceptance, deadline or policy change is made by this close-out.
