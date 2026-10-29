# UI-02 evidence manifest

The canonical machine-readable [manifest](ui02-responsive/manifest.json) records
source/build identity, exact viewport dimensions, artifact SHA-256 digests and
actual execution receipts. JSON/JPEG bytes are preserved by the folder's narrow
attributes. This Markdown file is explanatory and is not self-hashed.

## Source chain

| Stage | Identity / use |
|---|---|
| Initial develop and PR72 merge | `fb7dcebd434c6887396881e9b34df1bf12d8250e` — original baseline |
| First UI runtime | `0ad4d6436769bcc8679dd0892c18b7ca19f68617` — full694+1346 Java verification and intermediate probe |
| Narrow-screen/navbar refinement | `70b80a2df8fb64920997a00eff17c4351f6f5904` — package694, Node40, pre-sync browser evidence |
| Owner PR68 develop merge | `a34e8cc3f0c0ecca4bbd08fd9f92b1d9c9247fd9` — two upstream binders/test additions |
| Integrated UI-02 runtime | `1f375fa01882f09755e6a107e0e13c79f427f671` — fresh post-sync verification/browser evidence |

The first full PostgreSQL run copied/compiled runtime inputs before later
CSS/navbar refinements; its timestamps/results remain attached to that runtime.
Final post-sync focused verification is a new execution, not a relabelled full
suite. Reports retained under `.work/ui02/pre-sync/` prevent stale XML from being
counted as freshly executed tests. Existing owner independent runs are reference
evidence, never added to HP totals.

Publication commits add documentation/evidence and preserve executable UI inputs.
The final PR HEAD and CI are identified in the PR and supplementary CI receipt;
they are not conflated with the tested runtime merge or GitHub synthetic merge.

## Evidence and limitations

- Screenshots are actual browser JPEGs; each has route, actor, viewport and
  source/JAR provenance. Baseline/intermediate screenshots retain their own source.
- Raw cases contain DOM geometry and selected table metadata; they contain no
  cookie, JWT, CSRF token, password, request body or real personal data.
- Sanitized HTTP/access observations record paths/statuses, never query tokens or
  headers. They do not turn unsupported browser-performance APIs into a trace.
- Raw Maven logs/XML and private fixture credentials stay ignored under `.work/`.
  Public summaries/digests are verifiable records, not a claim that QĐ has rehashed
  HP's private files or independently rerun this suite.
- The native-dialog attempt is blocked/limited and preserved as such. No current
  physical dialog screenshot, audible screen-reader evidence, real touch/IME,
  deployed acceptance or complete demo-seed result is invented.
- QĐ review and HP final decisions remain pending. QA-01 overall, QA-02, QA-03 and
  REL gates retain their existing states.

<!-- GENERATED EXECUTION SUMMARY -->

## Actual results

| Execution | Source | Result |
|---|---|---|
| Baseline package | fb7dceb | PASS,694 unit; no PostgreSQL profile |
| Initial clean/package | initial UI runtime | FAIL: Windows running-JAR file lock; retained attempt logs |
| Corrected clean test and package | initial UI runtime | PASS,694 unit per overlapping execution |
| Full `-Ppostgres-it verify` | 0ad4d64 runtime | PASS,694 unit +1346 PostgreSQL =2040;0 failures/errors/skips |
| Refined package / Node | 70b80a2 | PASS,694 unit;40 Node; no new full PostgreSQL run |
| Post-sync `-Ppostgres-it -Dit.test=PromotionBrowserFormsIT clean verify` | 1f375fa | PASS,705 unit +6 focused PostgreSQL;0 failures/errors/skips; package included |
| Post-sync Node | 1f375fa | 40/40 PASS;0 failures/skips |
| Final real browser | 1f375fa | 240/240 route/viewport render/containment PASS;219 actor-group +21 anonymous |
| Companion HTTP | 1f375fa | 73/73 authenticated routes200;9/9 RBAC/ownership/CSRF controls PASS |

**No full2057 Java execution is claimed on1f375fa.** The complete pre-sync result
remains attached to its original runtime; the fresh targeted delta covers the two
new owner binders. CI also does not run browser, full PostgreSQL or the seven new
app.js tests (these40 Node cases ran locally; existing CI lists the original33).

Archived screenshots: **151**, each mapped in [screenshots.json](ui02-responsive/screenshots.json).
Manifest SHA-256: `85883646c1cdeb53e88f94dbf45e47e1182128932cf9654e8d1759e3a32b111a`.
The manifest hashes evidence files, not itself; later CI receipts are supplementary
and must have separately verified digests. No checksum is changed to hide a mismatch.


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
