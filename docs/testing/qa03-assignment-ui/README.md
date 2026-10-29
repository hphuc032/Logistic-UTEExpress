# QA-03 G01: assignment UI fix and browser evidence

Actual browser execution on develop `a34e8cc` found no assignment/reassignment control on the eligible Ops detail page at 375/768/1440. This blocked the full UI journey. The additive UI adapter now calls the existing guarded JSON commands; original versions and masked CSRF are sent, server locks/authorization remain authoritative. No migration, SecurityConfig, mutation service, financial ledger or lifecycle change.

## Exact tested source

`55384056538968f9e54bb2fcf1f525272c60b157`, based on merged PR68 `a34e8cc`. Later evidence publication must not be described as another test execution. Full runner: 705 unit/architecture + 1,354 PostgreSQL = **2,059 Java PASS**, zero failures/errors/skips, exit0, timeoutfalse. Separate Node41 and tooling17 PASS. The full runner's built-in Node12 is a subset of the separate41; do not add them. See byte-preserved `full-run-manifest.json` and `supplementary-results.json`. Raw logs/XML remain local; their hashes were checked before publication. Manifest hashes do not imply raw artifacts were publicly published.

## Real browser execution

Chrome155.0.8059.82, Playwright, headless: **24/24 automated UI cases PASS**, eight groups at375/768/1440. `browser/results.json` records source, dataset/JAR hashes, UTC start/end, actual assertions and original screenshot hashes. Original PNGs are copied without editing. Six representative images were visually inspected by the agent; this is not a complete human visual audit. Human manual execution/signoff is **NOT RUN / NOT CLAIMED**; HP must decide whether this explicitly labeled evidence satisfies the browser gate or requires additional human acceptance.

Groups: assignment/reassignment (stale, locked actor, foreign assignee, post-pickup); failure/retry exhaustion; second-attempt COD; return-to-sender; two-session notification isolation/read/offline/logout; review upload/ownership/public retention; Admin financial scope/Manager denial; full Register→OTP→address→checkout→Vendor readiness→Admin assignment→Shipper COD→public review.

Fixture preparation uses real HTTP commands, except initial synthetic ADMIN role bootstrap. Read-only SQL verifies outcomes. Review-retention prerequisite Return is explicitly HTTP preparation, not manual Return evidence. The disposable environment was closed and its owned container removed (`browser/session-result.json`). G01 is separate from final G02 combined-refund validation; PR70 has not merged.

## Findings and retained attempts

- **Q03-G01-ASSIGNMENT-UI-MISSING / High:** reproduced on develop; fixed and verified on this candidate, pending review/merge/HP acceptance. No weakening of existing assignment authority.
- **Q03-G01-OTP-COPY / Low, HP UI/security owner:** actual registration page says OTP lasts10minutes, while current server policy is5minutes. Screenshot `browser/full-user-journey-375-otp-policy-copy.png`; no OTP code/token is published. No security-policy change made by QD.
- First implementation `2d550f4` failed unchanged ArchitectureTest. DTO mapping moved into governed service at5538405; full rerun passed. Failed raw attempt retained locally.
- Baseline24:12PASS/6FAIL/6BLOCKED. Missing assignment controls were a product defect; wrong Review redirect and nonunique inbox selector were harness defects. Corrected attempt:19PASS/5BLOCKED (mobile navbar logout/checkout selector harness issues). Prior results/triage retained in `prior-attempts`; some baseline diagnostic screenshot names were overwritten, so no claim that every historical screenshot reference remains hash-verifiable. Final screenshots use unique names and all referenced hashes were verified.
- node_repl/CUA initialization failed with kernel-assets os error3 despite one reset. Installed Chrome plus bundled Playwright was verified and used under explicit user authorization. Do not describe the runtime as still blocking all browser execution.

QA03 remains **BLOCKED**: human/manual acceptance decision, candidate review/merge, PR70 merge then final #69 sync/regression/combined seed and HP bootstrap/checkpoint acceptance. Q70-S01 remains **OPEN / Medium**, disposition **ACCEPTED RISK — LIMITED**. No PR approval/merge or QA completion is implied.
