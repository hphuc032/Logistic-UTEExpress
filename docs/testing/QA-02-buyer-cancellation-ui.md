# Buyer NEW-order cancellation UI — PR validation

Owner: TD (Tender0603). Reviewer/final acceptance: HP (hphuc032).
Status: **LOCAL VALIDATION PASS; PENDING PR REVIEW AND MANUAL BROWSER ACCEPTANCE**.
QA-02 remains **IN PROGRESS**.

Buyer order detail previously offered no cancellation action despite the accepted API.
The existing implementation now exposes Hủy đơn hàng only for an owned eligible NEW
order, asks for confirmation, submits the persisted expected version with masked CSRF,
prevents duplicate submission, handles stale/missing/auth/uncertain results safely, and
reloads committed status. Order-list links lead to this detail action.

## Source and integration

Branch: `fix/qa02-buyer-cancel-ui`.
Latest integrated develop: `72558c7dba1b69e847e5c87f9d580732ab1b185a`.
The branch fast-forwarded from d2f722d without a rebase/integration commit or reset.
All 11 original files were backed up and byte-checked after integration; upstream
QA additions did not overlap. Other worktrees and their dirty files remain unchanged.

Validated executable/test/build input manifest SHA-256:
`c2bc230868c9cde6a86a42f835eba2f5fbe370d4eb5e753d0fdedee4faf7ea1d`.
The final commit SHA is supplied in the PR, then its content is checked against this
manifest. This avoids a self-referential commit hash in the evidence file. Tests ran
on the final branch immediately before committing, and executable inputs stayed
unchanged. [Sanitized evidence](QA-02-buyer-cancellation-ui-evidence.json) records all
input/report hashes, commands, actual counts and historical full-run provenance.

## API and behavior

POST `/buyer/orders/{orderId}/cancel`, JSON `{"expectedOrderVersion":0}` (actual
persisted version, not a constant). Existing authenticated USER authority, active
persisted USER, owned Order lock, NEW/version/marker guards and global CSRF remain.
No new endpoint, lifecycle, status writer or stock/quota operation is introduced.

An HTML-only page DTO supplies detail and version from one read snapshot. The shared
Buyer/Vendor JSON shape and persisted OrderItem IDs remain unchanged. Page eligibility
is advisory; the existing POST rechecks facts under lock.

The browser preserves the entire Long version and ID, uses the Thymeleaf-generated
URL and masked header token, and sends same-origin credentials. Declining confirmation
does nothing. Pending/success/error submissions stay disabled until navigation/reload.
Only 200 with the expected OK body triggers status reload. 404 is generic; 409 asks
for current state; 401/403 guide login/security-session recovery. Network/server/parse
failures never retry automatically because the command may already have committed.
No raw error body is rendered and no client inventory/voucher/Payment manipulation occurs.

Existing layout styles, native button/confirmation, help text, polite live status and
reload link support accessibility. JavaScript is required; noscript explains a disabled
control. Browser appearance, focus and screen-reader behavior remain manual checks.

## Reviewed files

Exactly these 11 intended UI/test/workflow/documentation files form the PR:

- `.github/workflows/ci.yml`
- `docs/testing/QA-02-buyer-cancellation-ui-evidence.json`
- `docs/testing/QA-02-buyer-cancellation-ui.md`
- `src/main/java/com/uteexpress/order/controller/BuyerOrderController.java`
- `src/main/java/com/uteexpress/order/dto/BuyerOrderPageDetail.java`
- `src/main/java/com/uteexpress/order/service/BuyerOrderService.java`
- `src/main/resources/static/js/buyer-cancellation.js`
- `src/main/resources/templates/order/detail.html`
- `src/test/java/com/uteexpress/order/BuyerCancellationIT.java`
- `src/test/java/com/uteexpress/order/service/BuyerOrderServiceTest.java`
- `src/test/js/buyer-cancellation.test.cjs`

No Payment, Shipping, Admin, security production, migration, cancellation controller/
authority/lifecycle or shared BuyerOrderDetail source was changed. Raw logs, build
outputs, secrets/environment files and generated test reports are excluded. The two
documentation/evidence files are intentional sanitized deliverables.

## Current final-branch validation

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=BuyerOrderServiceTest,ArchitectureTest' '-Dit.test=BuyerCancellationIT,BuyerOrderIT,VendorOrderIT' clean verify
node --test --test-reporter=tap src/test/js/buyer-cancellation.test.cjs src/test/js/notifications.test.cjs
git diff --check
```

| Suite/check | Tests PASS | Failures/errors/skips | Exit |
|---|---:|---|---:|
| BuyerOrderServiceTest + ArchitectureTest | 8 + 7 = 15 | 0/0/0 | 0 |
| BuyerCancellationIT | 50 (33 original + 17 added) | 0/0/0 | 0 |
| BuyerOrderIT | 35 | 0/0/0 | 0 |
| VendorOrderIT | 73 | 0/0/0 | 0 |
| Cancellation frontend + notification client | 21 + 12 = 33 | 0 failures/cancelled/skips/todo | 0 |
| git diff --check | N/A | PASS | 0 |

Surefire total 15; Failsafe total 158; timeout=false, flakes/reruns=0. No overlapping
counts are summed. Fresh XML counts/timestamps and input hashes were checked.
The genuine GET-issued CSRF cookie/rendered masked-header test remains intact, isolated
with BEFORE_METHOD DirtiesContext to avoid csrf() helper repository replacement.
All original replay, stock/voucher, history/inbox, commit/rollback/PID contention guards
remain, alongside page visibility, persisted version 7, stale conflict and JSON compatibility.

Frontend tests cover confirmation decline, correct URL/version/CSRF, double-click,
401/403/404/409, uncertain responses, Long precision, disabled invalid configuration,
cross-origin/query rejection and absent controls. They are VM tests, not browser screenshots.

The original full **2,006 Java/PostgreSQL + 33 Node PASS** on d2f722d is retained as
`historical_full_run` in JSON. Its input identity, failed development attempts and
diagnostics remain historical. Original target XML was archived before the fresh clean
focused run. This publication run is focused validation, not a newly claimed full run.
Mockito/JDK agent/CDS, authentication-provider and deliberate fault-injection diagnostics
are retained in raw local logs; no test or warning was weakened/suppressed.

## Manual browser checklist at PR publication — historical

This list records the publication-time state. The real-browser execution update below
supersedes overlapping NOT RUN entries; it does not change the historical test results.

Use fictional owner/foreign/role fixtures and capture source SHA, expected/actual,
dataset and redacted screenshots at **375/768/1440** widths.

1. Orders -> owned NEW detail: visible control/help, no clipping; Tab/Enter reaches
   native confirmation; decline sends nothing. Verify focus and screen-reader status.
2. Confirm and double-click: one POST, disabled/progress state, reload to CANCELLED
   and no action; original COD/snapshots and one stock/quota restoration.
3. Two tabs: stale submission produces safe 409/reload; no second release. Foreign/
   missing order gives generic 404; non-NEW/VENDOR-only buyer shows no action.
4. Missing/invalid CSRF and expired/revoked login produce safe 401/403 without mutation;
   exercise actual browser cookies with no token contents in screenshots.
5. Offline/lost response/server error requires GET reload before another attempt;
   there is no automatic POST retry. Verify the recovery link and live announcement.
6. JavaScript disabled leaves a disabled action plus noscript instruction; no fallback
   form/GET mutation exists.

Manual and HP acceptance are not claimed. This PR addresses Q02-A01's missing UI;
Q02-F05's accepted backend fix remains resolved. Other QA-02 scope/evidence gates are
outside this UI change. No PR merge or QA-02 COMPLETED declaration is authorized.

## PR #67 real browser execution — 2026-10-28

Exact tested source: `c5901c326ff92fee0fde0f8a4c66633a003cfc81`.
See the [browser acceptance report](QA-02-browser-acceptance-PR67.md) and
[source-bound evidence manifest](QA-02-browser-acceptance-PR67-evidence.json).

Real Chromium 140.0.7339.16 / Playwright 1.55.0 ran against the packaged application,
normal local security configuration and disposable PostgreSQL 17.6 synthetic fixtures.
Final main result: **22 PASS, 0 FAIL, 3 NOT RUN**; exit 0, no final timeout. A focused
keyboard recheck passed and overlaps the main count. Thirty-six screenshots, actual
HTTP/transport observations, database effects and sanitized server events are retained.
This is distinct from the VM frontend tests and prior Java/PostgreSQL regression.

Responsive page/action/pending/error layouts, genuine CSRF and expired/revoked-session
rejection/recovery, ownership and role checks, actual Vendor confirmation, replay/two-tab
conflicts, double-click and offline/in-flight/lost-response reconciliation were executed.
Inventory/voucher release, history/inbox and immutable COD/financial snapshots were
verified in PostgreSQL. Earlier harness failures and their corrections remain recorded.

Native dialog visuals at all three widths, physical keyboard traversal inside the native
dialog and audible screen-reader announcements are **NOT RUN**. Page focus returns to
the control on dismissal; successful full reload resets focus to BODY, then Tab reaches
the skip-to-main link. Native/assistive-technology acceptance of that behavior remains
for TD/HP. The older JavaScript-disabled and generic server-error checklist items were
not newly executed by this run; they are not inferred from other tests.

**Initial execution decision: BLOCKED** pending desktop observations; superseded by TD’s final confirmation below. QA-02 remains IN PROGRESS;
HP acceptance is not claimed. Only documentation/evidence changed; nothing was committed,
pushed or merged during this browser task.

## TD final manual acceptance and documentation publication — 2026-10-28

See the [final browser report](QA-02-browser-acceptance-PR67.md),
[manual observations/history](qa02-browser-pr67/manual-observations.json) and
[source-bound manifest](QA-02-browser-acceptance-PR67-evidence.json).

TD’s final Windows Chrome observations substantiate the two previously NOT VERIFIED
cases. Current mandatory PR acceptance: **25 PASS / 0 FAIL**, comprising **22 automated + 3 TD-attested manual** cases. Original automation remains **22 PASS / 0 FAIL /
3 NOT RUN** in unchanged raw records; all **36 automated screenshots** are preserved.
The focused keyboard recheck overlaps existing coverage and is not counted twice.

TD confirms native visuals at 375/768/1440, Tab/visible focus, Enter opening the dialog,
Esc dismissing without cancellation, Tab/Shift+Tab/focus behavior and no keyboard trap.
Narrator reads the Cancel Order button, Vietnamese prompt/OK/Cancel, and order status
when navigating. These are explicit TD attestations, not new agent-run browser tests.
Versions/exact time, Space behavior, exact post-success focused element and automatic
announcement timing remain unrecorded; no native screenshot files were supplied or invented.

The executable tested source remains `c5901c326ff92fee0fde0f8a4c66633a003cfc81`.
Only documentation/evidence is being published; publication HEAD and CI must be verified
after push, with the standard synthetic merge checkout/source-tree qualification retained.
**Ready for HP review only after publication-head CI succeeds.** No merge is authorized;
QA-02 remains IN PROGRESS. The separate local manual application is left running.
