# PROMO-03 complete implementation / review packet

**IMPLEMENTATION AND LOCAL VERIFICATION PASS — PENDING FINAL-SOURCE OWNER REVIEW / MERGE.**

Develop base `a5620f1971d3b684d9b05d7444f5e90442404656`. Tested source `15fb1dda4656892cbb477b4c0e47753bed6a66fc`, tree `379b6beeb4586176e9a9e873680014359b70f859`. Branch `codex/promo03-platform-integration`, PR #78. Later evidence publication is documentation only; input object comparison is required before using this run for a later HEAD.

## Scope and authority

Master Plan PROMO-03: S3/P1, QĐ owner, TD reviewer, PROMO-01/02 dependencies; Ops pages/actions for promotions and vouchers, calling TD authority. Master Plan hash and original source-backed matrix remain in contract-decision.md. HP explicitly selected ADMIN-only (Manager DENY), PROMO-01 used-voucher mutability, then directly instructed completion and merge. This authorizes the concrete Product-scoped Admin contract recorded in the final section of contract-decision.md; earlier BLOCKED labels are historical and superseded for implementation scope, **not fabricated TD/QĐ agreement or formal approval**.

PLATFORM voucher management and Admin product-promotion management are now implemented. Product promotions reuse existing one-Product percentage schedules and pricing authority, not a new global stacking engine. Create/update require Product ACTIVE/Shop APPROVED; history/read/disable remain accessible on unavailable targets. Vendor retains its existing Product ownership rights to schedules; creator identity is immutable. Shared definition validation and private writer primitives preserve Vendor behavior. Catalog holds the Product sentinel; dependencies remain Catalog → Promotion, never Promotion → Catalog.

## Security, transactions and UX

Current ACTIVE/ADMIN is checked from persisted scalar reads under Account lock. All non-Admin roles and anonymous are denied. Browser supplies definition fields/expected version only; Product path, scope, Shop and actor resolve server-side. Binding excludes transport headers and rejects forged URI IDs. CSRF remains enabled and all writes are POST. Existing error messages/escaped templates/PRG are reused.

Voucher lock order remains Account → Voucher/refresh → history/version → audit. Product-promotion facade uses Account → Product/refresh → Shop/refresh → Promotion/refresh → audit. Admin internal writer entries retain separate ADMIN guards and MANDATORY transactions; existing VENDOR class policy is unchanged. Supported production entry is the Catalog facade, not direct external writer calls. Audit and changes share one transaction; failures roll back create/update/disable. Price SQL, overlap exclusion, money/commission formulas, Order/Payment/Refund/lifecycle and historical snapshot logic are unchanged. No migration or SecurityConfig exemption.

Both Admin disable actions now use a safe GET confirmation page; Cancel is a link that performs no POST. Final POST requires CSRF and current version. No native-dialog timing dependency or new JavaScript is introduced. Promotion form invalid fields have visible feedback, aria-invalid and initial focus via existing app.js.

## Actual execution evidence

- Java21 `.\mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-it clean verify`: **PASS**, exit0, 2026-10-29T19:36:24.2761213Z → 2026-10-29T19:50:53.1551182Z. Includes full unit/architecture, package/repackage and all configured PostgreSQL suites.
- **709 unit/architecture + 1517 PostgreSQL = 2226 Java tests**, 0failures/errors/skips. All 69 configured IT classes ran; per-class counts and original XML hashes in test-results.json.
- Four existing Node suites: **48 PASS**, exit0 on same source. No repeated counts are added.
- **33/33 real automated Chrome cases PASS**: 11scenarios ×375×812,768×1024,1440×900. Navigation/mobile workspace menu, validation/focus, create/update, cancel/noPOST/noDBchange and disable for promotions/vouchers; containment, diagnostics, POST302/GET200/flash and DB actor/version/audit checks. Screenshots with route/viewport/source/JAR hashes in browser/ and source-proof.json. These are not human or QĐ independent runtime signatures.
- Browser DB: 3PLATFORM/no-Shop vouchers +3Product promotions, all disabled atversion2;18audit records;0Orders;stock10 unchanged. Dedicated disposable environment,28unchanged SQL migrations/Hibernate validate, application boot PASS.
- Focused PostgreSQL tests cover forged input, cross-role/service denial, stale versions/roles/status, used history, audit failure rollback, Product/Shop/Account freshness, Admin/Vendor overlapping schedule serialization, real checkout commit/rollback visibility, Voucher quota races and immutable snapshots. Full regression includes existing cancellation/refund/ledger/revenue/notification/JWT/CSRF and Vendor suites.
- `git diff --check` PASS. Production entity/pricing, lifecycle/checkout/payment/refund, security policy, migrations, POM and CI objects unchanged except explicitly listed management code. Source-proof lists actual changes.
- Cleanup: 82 exact full-run container identities checked absent/stopped after JVM exit. All browser attempt-owned containers removed, app processes terminated. No unrelated Docker/worktree/PR69 resources changed.

## Failed attempts retained

Original voucher packet is preserved byte-for-byte under history/voucher-67c7d54, including its failed fixture attempt and later2185PASS regression. New initial compile used the wrong test package; next focused setup omitted mandatory moderation reasons. Both were corrected in test fixtures and logs retained. Browser attempts1/2 failed harness setup (collapsed menu and ambiguous CSRF selector matching logout); original raw results/hashes/cleanup receipts retained, sanitized summaries published. Attempt3 passed33cases; final attempt adds6screenshots on confirmation pages and repeats33cases on the same source, not66unique coverage. No application security guard/schema was relaxed to pass tests.

## Acceptance / remaining gates

HP instructed implementation and merge and accepted the earlier voucher evidence limits. TĐ/QĐ scoped reviews supplied in chat were for67c7d54; they are not final-source approvals or independent execution of this new source. **Final-source owner delta review/formal GitHub approval and applicable branch protection remain gates.** Hosted CI is checked separately after publication; standard CI does not execute postgres-it. No approval/merge or PROMO-03 COMPLETED claim is fabricated. Browser evidence is automated desktop Chrome, not real devices/screen reader or human signature. Raw evidence independent review still pending. QA-02/03, Q70-S01 and release/SCA/SMTP/deployment gates unchanged. During execution the primary D:/UTEExpress checkout independently advanced to branch deploy/render-demo atdb782c4; this task did not switch/reset/stage that checkout. All production changes remained isolated here; D:/.work only contains ignored execution tools/evidence.
