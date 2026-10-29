# UI-02 findings and execution limits

Source and image identities are in [the manifest](UI-02-evidence-manifest.md).
Measurements use document **clientWidth**, allowing only a one-pixel rounding
tolerance, not `innerWidth` (which can include the scrollbar). Tables extending
inside a clipped horizontal region are intentional, not page-overflow failures.

| ID | Severity / area | Reproduction and actual observation | Resolution / verification |
|---|---|---|---|
| U02-F01 | MEDIUM — Buyer long content | Original product1 at375×812: document1338px; long title/breadcrumb escapes. Interim0ad4 wrapping leaves12px from `row g-5` negative gutters (372 versus360 client pixels). | **RESOLVED (layout)** — wrap content and reduce large narrow-screen gutters. Final product1 passes all three viewports; original/interim images and measurements retained. |
| U02-F02 | MEDIUM — Ops order table | Original `/admin/orders/1`: document818px at375,834px at768; populated table lacks a scroll wrapper. | **RESOLVED (layout)** — responsive wrapper with semantic headers; same business expressions/data remain. Final Admin/Manager detail containment PASS; keyboard scroll PASS at70b80a2, not freshly rerun after sync. |
| U02-F03 | MEDIUM — tablet workspace | Original Manager768 dashboard has256px sidebar and crowded date fields. Mobile navigation also precedes content as a long permanent list. | **RESOLVED (layout)** — single-column below992 with a real Bootstrap workspace toggle. Final geometry PASS; keyboard Enter/Space checked at70b80a2 and not freshly rerun after sync; no link removed from workspace navigation. |
| U02-F04 | LOW — Vendor desktop header | Original1440 Vendor action labels occupy up to86px height, compressed to multiple lines. | **RESOLVED (layout)** — wrap whole action groups/buttons; retain permitted destinations, with F06 correcting buyer-link visibility. Final Vendor desktop screenshot inspected; whole labels remain readable. |
| U02-F05 | MEDIUM — intermediate moderation regression | Probe0ad4 at375: Manager/Admin products531px, shops440px. Absolute visually-hidden labels escape a static scroll container after widening tables. | **RESOLVED (layout)** — positioned scroll regions; hidden labels/form names retained. Final moderation routes PASS at all widths; retain probe failures rather than calling them original-source findings. |
| U02-F06 | LOW — staff buyer links | Original navbar shows protected buyer cart/address/profile links for non-buyer staff; server correctly denies them. Code-audit evidence, not a claimed original opened-menu screenshot. | **RESOLVED (view-only)** — existing buyer capability reused, staff identity text retained. Staff menu observed at70b80a2; final templates match and companion403 controls PASS; no new backend policy. |
| U02-L01 | LOW / OPEN — legacy Ops form styling | Some commission/delivery controls retain native styling instead of the established Bootstrap control classes. No page overflow is inferred from their appearance; actual source-bound observations remain in the matrix. | HP UI follow-up/QĐ consistency review; no new deadline or acceptance waiver. Delivery form actions were exercised separately; no uniform visual-completion claim. |
| U02-B01 | BLOCKED verification — native dialog | At70b80a2 the browser key dispatch timed out; later `getJsDialog()` returned undefined and order2 was CANCELLED. The tool could not establish controlled dismiss/Escape/focus behavior. | QĐ must perform independent native dialog checks on the final source at all three sizes. No production defect or native manual PASS is inferred. Buyer cancellation JS/detail template are preserved; prior PR67 evidence stays historical. |

The F01–F06 layout-resolution evidence is to the extent of the final matrix and
interaction evidence; they are not an HP/QĐ final sign-off. No confirmed backend
or security defect was changed by UI-02. Owner PR #68 was imported after its merge,
not rewritten as an UI-02 fix.

## Harness history retained

- Initial clean/package failed because the running baseline JAR locked `target/`
  on Windows. Corrected by an isolated runtime JAR; later clean/test/package pass.
  Failed logs remain private and are hashed; no source/test/security bypass.
- DOM evaluation does not expose `performance`; those unsupported observations
  were removed. No fabricated CDP network trace is claimed. Console observations
  and sanitized Tomcat access logs/companion HTTP are distinct evidence.
- Early locator attempts used a required-label exact match or the wrong register
  button name; current DOM inspection corrected the harness. No application fix
  is attributed to these setup errors.
- One dashboard filter URL was sampled before navigation completion. Retained
  FAIL attempt was followed by actual keyboard and settled mouse submission with
  explicit navigation wait PASS. No discarded result is counted as a clean run.
- Native-dialog failure remains blocked; an occurrence of NEW in the order's
  timeline is explicitly not evidence that its current status remains NEW.

No failed attempt is deleted because a later attempt passes. No old-source
result is silently assigned a new SHA. See the report for QA/release boundaries.


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

## Review disposition — 2026-10-29

QĐ's official review atb526fca is COMMENTED / SCOPED EVIDENCE READ-BACK PASS,
not APPROVED. HP explicitly accepted the disclosed manual-native-evidence limit
for UI-02 only; independent re-execution is not retroactively claimed. The Node
coverage gap is addressed by adding app.test.cjs to the existing CI command;
local40 PASS, fresh CI execution pending publication. Historical findings and
packet hashes stay unchanged. See [the scoped decision](UI-02-responsive-report.md#qđ-read-back-hp-disposition-and-ci-coverage-correction--2026-10-29)
and PR74 for the final new-HEAD CI result and outstanding QĐ verdict.
