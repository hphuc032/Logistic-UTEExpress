# QA-03-FORM-01 — legitimate voucher/promotion forms rejected

Severity: **Medium functional blocker**, not a demonstrated authentication bypass.
Owners: TD (VendorVoucherController), HP (VendorProductPromotionController); QD
integration regression and evidence. Master Plan: T26/T27 and Q03-G02 integrated
seed; follow H5/H8 form validation/CSRF/ownership requirements.

## Trigger and before/after

On develop `72558c7`, authenticated, valid-CSRF, ordinary HTTP form POSTs with
Content-Type/User-Agent headers returned HTTP 200 form errors instead of creating
voucher/promotion rows. The integrated seed could not build its Commerce fixtures.
The error says unexpected fields; the body contains only declared form fields.

Spring MVC's ExtendedServletRequestDataBinder supplies HTTP headers as binding
values. Strict business allowlists suppress `userAgent`/`contentType`; the two
controllers interpret every suppressed field as malicious form input. Existing
param-only/mock-principal tests did not reproduce the normal transport headers.
A direct framework probe confirmed `userAgent, contentType` in suppressed fields.

The fix disables header participation **only for these two form binders**, using
the supported non-deprecated `org.springframework.web.servlet.support` class.
Request parameters and existing URI-variable handling retain their existing
allowlists. No unknown business field, actor/shop/product identity, CSRF exemption,
permission, pricing/discount formula, quota or lifecycle operation is added.
Headers cannot fill missing business fields either.

## Regression evidence

- Red source `c7e0d70f26c597ca9cf19635973700fea3a76249`: five real-HTTP tests,
  exactly two assertion failures (normal voucher/promotion create/update), zero
  errors/skips; Maven exit 1. Three negative cases pass. Red XML/log hashes in
  [red-evidence.json](red-evidence.json); raw artifacts remain ignored locally.
- Green implementation `a1da10269e362134cc7b8a862691d14f21978ac2`: test source is
  byte-for-byte unchanged from red. Focused clean verify: 686 unit +137 PostgreSQL
  PASS (existing Voucher73/Promotion59 and new HTTP5); exit 0.
- Fresh full approved-runner execution on that same implementation: **686 unit +
  1,308 PostgreSQL =1,994 PASS**, 12 Node PASS, zero failures/errors/skips, timeout
  false, exit codes 0. Actual runner and tested source SHA are recorded together in
  [full-run-manifest.json](full-run-manifest.json). Log and per-suite XML hashes
  were independently recomputed before copying. Focused metadata is historical;
  current full XML/logs remain local under target/qa-03 and reports.

Real HTTP tests use a random port, real cookie login/PasswordEncoder/JWT and rendered
masked CSRF fields. They assert legitimate create/update, missing CSRF and wrong
roles, foreign ownership, forged form identifiers and header-only missing-field
injection. No mock authenticated principal or csrf() helper is used for the new
HTTP requests. Test-only PostgreSQL fixture setup is not a production seed.

## Acceptance limits

The current develop failure is reproduced and the candidate fix passes automated
verification; merge still requires HP/TD owner review. This is HTTP regression,
**not browser/responsive acceptance**. Q03-G01 stays NOT RUN/BLOCKED by unavailable
Computer Use runtime; no screenshot/viewport claim is made. The seed candidate uses
this fix; its evidence must be reviewed independently and rerun on the accepted
source as required. QA-03 overall is not marked COMPLETED.

Subsequent commits only add this report/evidence. No applied migration or external
dependency was changed; final exact-head CI is a separate PR check.
