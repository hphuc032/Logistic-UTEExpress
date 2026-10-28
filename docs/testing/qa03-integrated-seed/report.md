# QA-03 integrated demo seed and remaining manual gate

Master Plan source: original `UTEExpress_Master_Project_Plan.docx`, SHA-256
`18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
Scope: QA-03/T48b and Q03-G02 execution candidate, Q03-G01 manual handoff.
This packet supplements the accepted automated INT-02 baseline; it does not
replace the previous source-bound QA-03 acceptance packet or declare QA-03 complete.

## Rehearsal design and limits

`scripts/qa/rehearse_integrated_seed.py --checkout . --disposable-demo` creates its own UUID-named,
labelled PostgreSQL 17.6 container on loopback, requires explicit --disposable-demo opt-in, activates local,demo profiles, and starts the matching packaged JAR
with a sealed configuration and local SMTP fixture, and removes its container and
app process after the run. It accepts no external database target. Set a fictional
password of at least 12 characters in `QA03_DEMO_PASSWORD` via process ENV; do not
commit or print it. Require Java 21, Docker, Python and a clean checkout. Build
with a successful Java 21/PostgreSQL clean verify before running; never run while
Maven is repackaging the JAR. Compare application/test/POM Git objects and artifact
SHA when using a separate runner checkout. Do not use a stale branch-switch build.

Ten fictional accounts are registered and OTP-verified through real HTTP, cookie
login and CSRF. The only direct business bootstrap is the first ADMIN role link
on that newly verified account in the owned empty database. Other roles, Shop
approvals and all Commerce/Shipping/Review/Return/refund writes use existing real
HTTP/domain boundaries. HP/TD must explicitly review this bootstrap exception.
No direct SQL fabrication of Order/Payment/Shipment/Return states is performed.

Fixture coverage: 4 shops (2 approved, pending, rejected), 14 products/images,
public/hidden/moderated/out-of-stock cases, categories, shipping config, 2 vouchers,
1 promotion, cart/favorites/views, 9 Orders spanning NEW/assigned/shipping/delivered,
failed-attempt/RTS/buyer-cancel/refunded paths, reviews/media and durable inbox.
Full-return receipt and manual COD refund evidence are created via their own
endpoints. Order-only status is not confused with Shipment failure status.

The second `seed_once` invocation uses the completed in-memory checkpoint and
validates/preserves existing fixtures; it does not reissue all creation requests.
A legitimate product edit is made between invocations. A canonical fingerprint of
all 34 tables must remain identical, with zero second-invocation POSTs, unchanged
stock/quota/history/evidence and the edit preserved. Missing/incomplete fixtures
fail closed. This is a manifest-backed validate-and-preserve rerun, not general
lost-checkpoint recovery, a cross-process resumable installer, or production seed.
TD/HP must accept this stated idempotency scope before closing Q03-G02.

Money is asserted through both Vendor A and Admin global read models on the same
synthetic period: delivered/collected goods 340000, merchandise refund 170000,
net collected 170000, net commission 11900, net after commission 158100. Shipping
is excluded. These checks supplement, not replace, cross-period/ambiguity/concurrency
coverage in the full integration suite.

## Evidence integrity

Published JSON is sanitized: source/runner identities, fixture IDs, HTTP method,
route/status, row fingerprints/counts and hashes. No passwords, OTPs, cookies,
Authorization headers or private response bodies are published. Raw app/HTTP
failure logs stay ignored under `.work`; an app-log hash is evidence identity,
not proof that production log/alert controls were deployed. A computed pg_dump
hash does not mean a backup artifact was retained or restore-tested in this run.

Earlier attempts are retained locally: startup failed when a JAR was being
repackaged; strict form header binding prevented voucher/promotion creation;
a rehearsal adapter initially used refund-version field names on Return routes.
The latter was fixed in the adapter, not by relaxing server validation. No failed
attempt is counted as PASS. The header-binding production fix is a separate PR.

## Remaining acceptance

- Q03-G02: candidate execution evidence only; requires TD business/technical review,
  HP acceptance, the form fix accepted into develop and any requested integrated rerun.
- Q03-G01: **NOT RUN / BLOCKED**. Both supported Computer Use entry points fail
  kernel initialization (`failed to write kernel assets`, Windows os error 3).
  `manual-matrix.json` is an unexecuted handoff, with null source/executor/screenshots.
  Do not use API checks, earlier Buyer-only screenshots or integration tests to
  claim current Ops responsiveness at 375/768/1440.
- ADMIN-06/T41c: **DEFERRED / ACCEPTED SCOPE EXCEPTION**, Manager writes DENY ALL;
  no limited-write PASS is implied.
- Clean-host, off-host DB+upload restore and deployed observability: **NOT RUN**,
  separately tracked release follow-ups. This local rehearsal is not those gates.
- QA-03 overall: **BLOCKED**, not READY FOR HP final acceptance until required
  gate evidence and owner sign-off are present.

## Current execution candidate

Runner source `dbd1ebbb8d271c3dc009c3f186e81eaea82ec65c`, application built from `8bab8dd9358a9f6fef022ec2c4e489e2f0c26fb5`, integrated develop `fa48cb69cef92276f0dddf0d2bd396eee098f2f3`. Application/test/POM Git objects are identical between runner and built source. Process exit 0, no timeout, owned container removed. See exact-byte `rehearsal-result.json` and `execution-manifest.json`; evidence files are subsequent documentation only. Full regression on the built source is tracked separately in PR #68.

## Completed application regression

The same built application source `8bab8dd9358a9f6fef022ec2c4e489e2f0c26fb5` completed full Java 21/PostgreSQL clean verify: 686 unit +1,325 PostgreSQL =**2,011 PASS**, 0 failures/errors/skips, no timeout, exit 0. The final JAR still matches the seed artifact checksum. Every 136 suite XML hashes and the Failsafe summary hash were independently rechecked. Runner Notifications Node12 PASS; the supplemental complete Node run is33 PASS including those12, not45. Seventeen runner unit tests also PASS. This full run belongs to the built source, not a new run on later documentation commits.
