# QD independent PR68 post-sync verification — 2026-10-29

**SCOPED TECHNICAL DELTA REVIEW + INDEPENDENT FULL DATABASE REGRESSION: PASS** on exact source `8705e8a865736387c96007aee7eb813a58dd1911`. No Critical/High finding identified within reviewed/executed scope. No merge or overall QA completion is implied. Master Plan original SHA256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`; QA02/QA03 and relevant security/Promotion criteria read. Latest repository Task Board overlay was also read: QA01 application-security merged/accepted with deferred gates, not overall COMPLETED.

## Exact source and independent environment

Merge parents are `c593352492184da2a9ca46ebea4a55b802ac6416` and `b538dbc3af17ff80802fa33eea187705c40059fe`. QD created a clean detached checkout, ran Java21 and approved `python -B scripts/qa/verify_operations.py --checkout .` with accessible Docker29.4.1/PostgreSQL17.6 Testcontainers. No filter/assertion/production code changed or external test adapter executed. Source and runner remained8705e8a until collection completed; publication branch was created only afterwards. `environment.json` records exact Git input objects and tool versions; no secret ENV dump.

| Independent QD execution | Result |
|---|---|
| Unit/architecture | 705 PASS,76 suites |
| PostgreSQL | 1352 PASS,65 suites |
| Java total | **2057 PASS**,141 suites; Maven/wrapper exit0, no failure/error/skip; Failsafe timeout=false |
| Complete Notifications + Buyer-cancellation Node | **33 PASS**,exit0; includes runner Notifications12, do not add |
| QA runner tooling | **17 PASS**,exit0; separate from application tests |
| Source clean, XML/summary/log digest consistency | PASS; all141 XML and Maven/Node/summary hashes independently recomputed |

Original generated `operations-evidence.json` bytes preserved. Runner SHA/source SHA and created_at_utc remain from actual execution; log hashes reference private ignored logs. Supplementary log digests and actual exits in supplemental-results.json. Literal diagnostic counts for closed-connection/Connection refused/forced-fork markers are0; no claim that all expected fault-injection logs are absent. This is local developer/disposable database verification, not clean-host or production assurance.

## Static delta and focus evidence

Promotion/Voucher controllers/service trees match c593352 through sync. Verified both promotionForm and disableForm header predicates retain parameter-only business input; version-only disable allowlist, path-derived owned identity and strict submitted-ID rejection remain. Voucher header predicate/allowed fields and approved Shop ownership service paths unchanged. No pricing engine, quota/usage or Order snapshot mutation introduced by the merge.

Security filter order is CSRF -> CsrfCookieFilter -> OTP admission. OTP filter is registered only in Security chain and matcher covers the five POST OTP/register/reset routes with MVC-compatible parsed/context/encoded semantics. Unrelated Vendor form routes are not budgeted; no CSRF exemption or OTP bypass added. Policy lifetime <=5minutes, attempts<=5 and resend>=60s retained.

Focused suites within full counts: VendorProductPromotionBindingTest11; real PromotionBrowserFormsIT6 (normal create/update/disable, transport headers, forged fields/header-only version, CSRF/role/ownership/stale); ProductPromotionIT59; VendorVoucherIT73; VoucherCheckoutIT57; Qa01CookieCsrfIT15; Qa01OtpSecurityIT11; OTP matcher2/rate-limiter5. These are overlapping full-run suites, not additional totals. Full report/rollback/concurrency/migration suite inventory remains in generated manifest.

## Preserve earlier outcomes and integration boundaries

TD's published b0cecbfb packet was independently checked for8 file digests. It records Docker-denied Maven exit1 with73 errors and zero verified PG business assertions. That failed environment attempt remains unchanged; this QD successful run is a separate execution on the same8705e8a source. Historical2011 on8bab8dd and2023 on c593352 remain separate; no counts or raw evidence overwritten.

At current target inspection, develop advanced from b538dbc to fb7dceb by2 documentation commits (README, MASTER_TASK_BOARD and QA01 report classification). #68 consequently is **behind2**, not behind0. These new paths do not change tested executable inputs, but latest-develop sync/exact-head CI/HP acceptance remain separate gates. This packet is not a new run on that later develop, nor blanket signoff on any future merge resolution.

#70 cancelled-paid commit688884c is not an ancestor of tested8705e8a. This run does not claim #70/combined-seed verification, QA03 manual/browser evidence or production/SCA acceptance. Q70-S01 stays HP ACCEPTED RISK — LIMITED; QA02 IN PROGRESS, QA03 BLOCKED, PAY03 DEFERRED. Synthetic ADMIN/PAID bootstrap and checkpoint acceptance of #69 remain HP decisions.

## Handoff

QD has supplied the missing full PostgreSQL evidence on the requested exact source and scoped delta review. TD/HP should coordinate the remaining documentation-only latest-develop sync, verify executable input equality and exact new-head CI; if executable inputs or conflict resolutions change, rerun affected/full requirements per HP. HP decides final #68 approval/merge. QD is PR author and does not self-approve. After actual #68 merge, follow #70 compatibility/migration/old-ledger gate, then #69 sync/final combined verification. No merge performed.
