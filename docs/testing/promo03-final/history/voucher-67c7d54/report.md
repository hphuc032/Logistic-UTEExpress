# PROMO-03 implementation and verification report

**READY FOR REVIEW — PLATFORM voucher slice only. PROMO-03 overall NOT COMPLETED.**

Accepted develop base: `a5620f1971d3b684d9b05d7444f5e90442404656`. Tested commit: `cd3651f59ca22e2805675b5be589e8150784c65e`, tree `9cd3934e6b2c6bfa2ce7a19c3aa4a7182c1425cd`. Branch: `codex/promo03-platform-integration`. Later publication adds documentation only; compare source/test/POM/workflow objects with tested commit, not a relabelled execution on a documentation SHA.

## Plan and decisions

Master Plan PROMO-03 is S3/P1, owner QĐ, reviewer TD, depends PROMO-01/02: Ops promotion/voucher pages/actions, Manager limits, service TD. FR-21 covers SHOP/PLATFORM vouchers and usage; FR-22 product promotions. Full requirement/current/missing/decision matrix and Plan SHA-256 are in [contract-decision.md](contract-decision.md).

HP explicitly authorized Admin PLATFORM voucher CRUD-with-disable, server scope/actor, and DENY ALL for Manager/Vendor/User/Shipper/anonymous. HP explicitly retains existing PROMO-01 used-history mutation rules. HP requested Admin product-promotion expansion **after TD/QĐ boundary agreement**; that required slice remains BLOCKED, not deferred/completed. See [owner-handoff.md](owner-handoff.md).

## Changes

Added PLATFORM-only management service/DTOs/repository queries; Admin controller and three shared-layout templates; Admin dashboard navigation. Extracted existing Vendor form validation unchanged into a common definition superclass. Added narrow current ACTIVE ADMIN guard to RoleGovernanceService with fresh scalar DB reads after Account lock, safe even if outer JPA transaction cached ACTIVE. No existing security policy/guard is broadened.

Lock/transaction: Account → current DB role → PLATFORM Voucher lock/refresh → usage query → save/flush → MANDATORY audit. Stale update/disable version conflicts. Audit failure rolls back business changes. No Product/Order/Usage locks introduced. Existing entity monetary/history validation and VoucherService checkout remain authoritative; no duplicate pricing engine. PLATFORM has null Shop; creator is the principal-derived persisted actor. HTTP fields cannot choose scope/shop/creator/history/order amounts; POST actions require CSRF. Safe HTML errors use existing public messages. Read projections contain no entities.

No migration, SecurityConfig/JWT, lifecycle, checkout/payment/refund/commission formula, cancellation/usage-release, persisted historical snapshot or CI changes. Existing SHOP/Vendor product promotion behavior remains unchanged. No active PR #69 or other worktree edited. Full changed-file list and Git object identities are in source-proof.json.

## Actual verification

- `.\mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-it clean verify`: **PASS / exit 0**, `2026-10-29T18:34:55.2415688Z` → `2026-10-29T18:49:08.9666177Z`. Includes compile, full unit/architecture suite, package/repackage and all configured PostgreSQL ITs, not separate claimed commands.
- Java: **709 unit/architecture + 1476 PostgreSQL = 2185 tests**, 0 failures, 0 errors, 0 skipped. Counts are summed from original clean-run JUnit reports; repeated preliminary runs are not added.
- New PlatformVoucherManagementIT: **55**; VoucherCheckoutIT including two new Admin real-checkout proofs: **59**. Regression includes VendorVoucherIT, ProductPromotionIT, PromotionBrowserFormsIT, OrderPlacement/INT-01, AccountGovernance, refund/ledger, shipping, notification and security suites; per-class exact results/hashes in test-results.json.
- Four existing JavaScript suites: **48 PASS**, exit 0; Node v24.22.1. No Node source changes.
- Node execution is bound to `61b55d4480f6d1b4994eda4d225363a7ebf23cb1`; its JS source/test objects are unchanged on the final Java-tested source. It is not relabelled as a new execution.
- Real PostgreSQL test evidence includes canonical/global code conflict, current role/active checks, stale versions, CSRF rejection, forged fields, used/RELEASED history rules, audit-trigger rollback of create/update/disable, observed Account/Voucher lock waits, preloaded-JPA account revocation, real checkout quota transaction versus Admin update, Admin-created PLATFORM quote/place/disable/cancellation snapshots and real JWT cookie/rendered CSRF forms.
- Existing ManualCodRefundIT protects its SHOP voucher history/financial fixture through refund; it is not relabelled as a new dedicated PLATFORM-refund browser scenario. Refund production objects and the shared release rules are unchanged; TD reviews this compatibility scope.
- `git diff --check`: PASS. Architecture rules unchanged. SQL migration and unrelated module source objects unchanged from accepted develop.
- Cleanup: this run's exact 81 logged Testcontainers identities checked after JVM exit; none running. Testcontainers performed cleanup; no unrelated resources deleted. Receipts in test-results.json. Raw logs/original JUnit reports retained locally, with hashes; not independent owner verification.
- Original full run on `61b55d4`: FAIL (1 PostgreSQL fixture deadlock error, 0 failures). Preserved receipt/log/JUnit reports, then corrected only synthetic buyer/lock order in `cd3651f`. Focused recheck passed; final clean run above is the fresh acceptance regression. Production code did not change between these full runs.

## Limits and acceptance

TD service/locking/financial compatibility review and QĐ adapter/security/audit review are PENDING. HP approved the explicit contract decisions, **not final implementation acceptance**. Product-promotion Admin boundary still needs TD/QĐ/HP agreement before implementation. New real-browser/manual responsive evidence NOT RUN; rendered-page checks are MockMvc only. CI NOT RUN at packet freeze; inspect live PR on publication for current exact-head CI. Standard CI does not run PostgreSQL profile. No claim of full seed/human QA-03, SCA, live gateway/SMTP or release acceptance. QA-02/03, Q70-S01 and existing findings remain unchanged.
