# UI-02 — responsive polish and browser acceptance

Owner HP; independent reviewer QĐ. **READY FOR SCOPED REVIEW**, subject to the
publication HEAD's CI and the explicit open browser gates below. This is not
UI-02 completion, QA-02/QA-03 completion, T50 acceptance or release approval.

## Plan, source and scope

The supplied `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx` was read;
SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
Its actual UI-02 row specifies S5, HP/QĐ, **P1**, dependency **INT-02**, shared
fragments/consistency/375–768–desktop checks and an **UI acceptance checklist**.
The Plan's suggested branch is `fix/ui-02-responsive`; HP's current request allows
the dedicated `feature/ui-02-responsive-polish` branch used here. T50's wider
Register→Order→Delivery→Review/manual acceptance remains a separate group gate.

Initial fetched develop / PR #72 merge was
`fb7dcebd434c6887396881e9b34df1bf12d8250e`; PR #66/#72 were verified merged.
During execution, owner PR #68 merged as
`a34e8cc3f0c0ecca4bbd08fd9f92b1d9c9247fd9`. A normal, conflict-free merge
produced `1f375fa01882f09755e6a107e0e13c79f427f671`. No rebase, force push,
direct develop/main commit, PR approval or PR merge was performed by UI-02.
Final source/build identities, actual counts and publication CI are in
[the evidence manifest](UI-02-evidence-manifest.md).

INT-02's merge `798535b` (PR #62) is an ancestor of the synchronized develop;
its integrated controllers/services and regression suites remain present.

Ops is a **screen grouping**, not an invented role: the actual routes use ADMIN,
MANAGER and SHIPPER. Buyer contracts also allow applicable VENDOR purchasing.
All screens and API-only boundaries were derived from real controllers/templates;
no dashboard, controller, permission or fake business data was added to main.

## Implemented

- Retain the established Bootstrap 5.3.3/CDN, brand, tokens and template strategy.
  Wrap long content; constrain large narrow-screen gutters; keep inline search
  submit buttons readable while inputs shrink.
- Contain wide tables in intentional horizontal scroll regions. Position the
  regions so absolute accessible labels cannot escape and widen the document.
  Preserve every business column/action; add native keyboard access, caption-based
  accessible names and visible focus without replacing table semantics.
- Below 992px use a single-column dashboard and the existing Bootstrap collapse
  behavior for workspace navigation. Desktop navigation remains visible. Header
  actions wrap as complete buttons rather than compressing Vietnamese labels.
- Buyer-only navbar links reuse the existing `buyerFeaturesAvailable` flag.
  Staff usernames remain visible without linking to a buyer-only profile route.
  This changes presentation, never authorization or the principal contract.
- Add seven focused shared-JavaScript regressions; update UI conventions.

No Java production source, security configuration, API/DTO, financial/lifecycle
logic, notification transaction, migration, dependency or workflow change is part
of the UI-02 diff against the synchronized develop. The two binder controller
changes came exclusively from already merged owner PR #68.

## Execution and evidence boundaries

Real packaged Spring application, normal `local` profile, Java 21, PostgreSQL
17.6 in an owned disposable Docker container, production Flyway migrations and
Hibernate `validate`. Real login, signed HttpOnly JWT and enabled CSRF are used.
Fresh random secrets/passwords remain private. SMTP is loopback/non-delivering;
live email acceptance is not claimed. Only synthetic users/addresses/products
exist in this database; this is not the ordered demo seed or Q03-G02 acceptance.

Real browser automation uses the Codex in-app browser through `cua_repl`.
Explicit viewport overrides and DOM `innerWidth`/`innerHeight` verify **375×812,
768×1024, 1440×900 CSS pixels**. Screenshot height may exceed viewport height.
No generated/edited pictures, mocked successful responses or old-source images
are substituted for final browser evidence.

The matrix's PASS means **that actor's real page rendered and document overflow
was contained at that viewport**. It does not mean every CRUD action on that page
was executed or that physical mobile hardware, a screen reader or native browser
dialog chrome was verified. Separate interaction results cover actual menu keys,
horizontal table scrolling/focus, validation, filtering and purchase/fulfillment
actions. Companion HTTP observations establish statuses/RBAC/ownership/CSRF;
they are labelled separately from browser observations.

Prior full verification at the first UI runtime `0ad4d64` passed **694 unit +
1346 PostgreSQL = 2040 Java**. Later CSS/navbar refinement was packaged/tested
and browser-tested at `70b80a2`. PR #68 subsequently changes two binder controllers
and adds tests; final post-sync verification is explicitly separate. Neither
2040 nor owner PR #68's independent results are relabelled as a new full run on
the final merge. The final focused PostgreSQL regression targets the imported
binding delta; actual results and hashes are recorded in the manifest.

Final source `1f375fa` has **240/240** saved render/containment checks:219 for
the five actor groups plus21 public/auth checks. Fresh post-sync execution passed
**705 unit/architecture**, **6 focused PostgreSQL binding cases** and **40 Node**.
There is no claim of a fresh full PostgreSQL run on that final source. Companion
HTTP independently returned200 for73 authenticated route rows and passed9
RBAC/ownership/CSRF controls; those are not browser test counts.

## Demonstrated journeys and remaining scope

Real browser forms/links exercised catalog→product→DB cart, quantity update,
owned-address/shipping quote, COD order NEW/receipt/detail; Vendor confirmation
and ready; Shipper pickup→shipping→delivery/COD. Assignment fixture preparation
uses the existing owner REST API because no assignment form exists in the Ops
page. It is **not browser assignment acceptance**. Return/review page eligibility
and applicable extra states are identified in the matrix; unexecuted write states
remain explicit. No direct SQL Order/Payment/Shipment state edits were used.

Native cancellation confirmation remains **BLOCKED/NOT VERIFIED**: at70b80a2
Enter timed out in `Input.dispatchKeyEvent`; the subsequent dialog API returned
undefined and the document showed CANCELLED. Do not interpret this as a proved
dismissal, physical Escape/Tab behavior, focus return or visual dialog PASS.
The earlier word-NEW observation also matched timeline history; it is not a
current-state assertion. The unchanged raw attempt and this correction are kept.
QĐ must independently check the real native dialog at the final source; historical
PR67 manual attestations are not reused as new-source acceptance.

After the final240 route checks and actual invalid voucher POST/three validation
screenshots, the browser session was interrupted; its remaining debugger tab
could not synchronize. A documented fresh-tab recovery succeeded, then local
browser use became **disabled in Work settings**. No alternate browser-control
mechanism was used to bypass that setting. Remaining post-sync menu/physical
dialog/console and successful voucher/promotion write interactions are
**BLOCKED/NOT RUN**. The pre-sync same-UI-source interaction receipts remain
clearly labelled; they do not establish fresh final-source execution.

Other explicit limits: physical touch/IME and mobile keyboard occlusion, audible
screen reader output, exhaustive CRUD/refund/return exception states, production
TLS/SMTP/load/backup and complete ordered seed are not accepted here. Existing
legacy native controls on some Ops forms remain a low-priority consistency item;
their observed layouts and exercised delivery actions are not hidden.

## Independent QĐ review checklist

For reproduction use a **separate disposable PostgreSQL database**, normal local
profile and approved fictional USER/VENDOR/MANAGER/ADMIN/SHIPPER fixtures. HP's
private `.work/ui02/` fixture helpers/run logs are available for owner handoff;
they are not a production/demo seed and are not committed. Use real login/form
submissions; do not paste cookies or disabled-CSRF helpers. IDs in the matrix
refer to this run's fixtures and must be resolved in a new database.

At each exact viewport, visit the inventoried route, compare the screenshot,
record `innerWidth/innerHeight` and document client/scroll widths, operate the
header/workspace toggle and keyboard-focus the named table region. Save the new
source/JAR identity, timestamp, sanitized result and image. Repeat blocked native
confirmation with a fresh owned NEW order; first dismiss/Escape and prove no
mutation, then use a separate fresh order for acceptance and verify the persisted
current state. Do not infer current state from a word in its history.

- [ ] Compare scope, original Plan row/dependency and actual route inventory.
- [ ] Review Buyer evidence and actual cart/quote/receipt/order states.
- [ ] Review Vendor products/orders/promotion/voucher evidence.
- [ ] Review Manager dashboard, read/write boundaries and moderation pages.
- [ ] Review Admin governance/report pages and role restrictions.
- [ ] Review Ops order/shipping and actual SHIPPER pages.
- [ ] Independently inspect 375×812, 768×1024 and 1440×900 results separately.
- [ ] Check shared-fragment changes across roles, including a unique collapse ID.
- [ ] Test header/workspace opening/closing, table scroll and form/pagination actions.
- [ ] Check keyboard focus and validation; physically test native confirm
      Tab/Shift+Tab/Escape/dismiss/accept/focus return at all three widths.
- [ ] Check business/security/CSRF/ownership/API contracts remain unchanged by UI-02.
- [ ] Review all findings, prior harness failures and residual limitations.
- [ ] Match screenshots, JAR/input hashes, actual tested SHA and publication delta.
- [ ] Confirm exact PR HEAD-associated CI is SUCCESS and inspect synthetic checkout
      equivalence where applicable; CI is not a PostgreSQL/browser run.

HP retains acceptance/merge decisions. QA-01 overall remains incomplete with its
separate gates; QA-02 remains IN PROGRESS, QA-03 BLOCKED. No REL-01/REL-02 gate
is closed and no independent approval is represented by this author checklist.


## Supplemental browser/manual verification — 2026-10-29

The original execution history above remains intact. A new
[follow-up packet](ui02-responsive/followup/README.md) on source
`1124a4908d886b4edfa872839ab90208f38e9ff1` supersedes the historical NOT RUN
status **only** for the cases below; executable objects match runtime1f375fa.

| Gate | New evidence and precise scope |
|---|---|
| Native dismissal | HP manual Escape375, Cancel768, Tab/Shift+Tab then Escape1440. Each returns focus to enabled cancel button. PostgreSQL order2 remains NEW/version0 with original timestamp; zero cancellation POSTs. QĐ independent verification is pending; no native-dialog-chrome screenshot or every action at every width is claimed. |
| Voucher creation | Real Vendor form with CSRF: POST302→edit GET200, success flash, owned SHOP voucher persisted. Submit375; successful edit page containment/screenshots375/768/1440. Update/disable remain NOT RUN. |
| Promotion creation | Real Vendor form with CSRF: POST302→edit GET200, success flash, product1/10%/active persisted. Submit375; successful edit page containment/screenshots375/768/1440. Update/disable remain NOT RUN. |
| Regression/scope |40 Node PASS freshly executed. No production changes; no fresh local Maven/full PostgreSQL run claimed. New publication HEAD/CI is recorded on PR74. |

Original native timeout/changed order and browser-disabled limitations are
historical, not deleted. This run used the Brave extension after browser access
was restored; native tool timeouts were completed by HP manual actions and are
labelled as such. The earlier OK action is separately documented as
CANCELLED/version1, never dismissal evidence. The original151 images/161 hashes
and240 cases remain unchanged. The supplemental packet has its own manifest.
Independent QĐ review and HP acceptance/merge are still required; UI-02 and the
QA/release tasks are not marked COMPLETED. See the packet checklist and remaining
limitations rather than interpreting old NOT RUN entries as current create-case
status or interpreting new results as exhaustive CRUD/browser acceptance.
