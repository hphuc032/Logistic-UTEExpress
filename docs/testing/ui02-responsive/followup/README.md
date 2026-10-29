# UI-02 — supplemental evidence, 2026-10-29

Tested publication source `1124a4908d886b4edfa872839ab90208f38e9ff1` has identical
`src/main`, `src/test`, `pom.xml` and workflow objects to packaged runtime
`1f375fa01882f09755e6a107e0e13c79f427f671`; see [source proof](source-proof.json).
The unchanged JAR digest is recorded there. This packet adds evidence only.
Exact new publication HEAD/CI is recorded on [PR74](https://github.com/hphuc032/Logistic-UTEExpress/pull/74).
Original evidence and its checksum remain unchanged.

## Native confirmation: manual HP + machine corroboration

| CSS viewport | Actual HP action | Verified after dismissal |
|---|---|---|
| 375×812 | Escape | Button focus restored/enabled, current state NEW |
| 768×1024 | Native Cancel/Hủy | Button focus restored/enabled, current state NEW |
| 1440×900 | Tab/Shift+Tab focus visible, then Escape | Button focus restored/enabled, current state NEW |

HP explicitly attested each action in this chat. This is **not QĐ independent
review**, automated physical-key proof, or every key/action at every width.
The browser tool's native key/dialog commands timed out; no automated native
acceptance is inferred. No window.confirm override, fake response, alternate
browser-control stack or weakened browser access was used.

Read-only DOM receipts and actual post-dismissal JPEGs are attached per viewport.
[PostgreSQL/access corroboration](native-db-check.json) proves order2 remains
NEW/version0 with its original updated timestamp and **zero**
`POST /buyer/orders/2/cancel` in this owned environment's unbuffered access log.
Tablet/desktop pre-observation byte offsets are retained; mobile uses the full
run log. No native-dialog-chrome screenshot was captured. Order1's earlier OK
acceptance produced POST200/CANCELLED/version1 and is deliberately not a dismissal
PASS. No Order state was reset with SQL.

## Vendor create happy paths

- [Voucher browser receipt](voucher-browser.json): real filled form, CSRF token
  present (value never exported), POST302, edit GET200, success flash. Exactly
  one SHOP voucher UI02FOLLOWUP, fixed10000, total10/per-user1, active, shop1.
- [Promotion browser receipt](promotion-browser.json): real form POST302, edit
  GET200, success flash; product1,10%, active. A delayed redirect was observed;
  the action was not repeated after timeout.
- [Read-only DB readback](db-readback.json): vendor creator3, correct owner scope,
  UTC timestamps02:00 corresponding to displayed09:00 UTC+7. No SQL was used to
  create these promotion/voucher records.
- Both forms were submitted at375×812. Their successful edit pages were rendered
  at375×812/768×1024/1440×900: **6/6 containment checks PASS**. Filled and success
  screenshots are genuine unmodified JPEGs;11 supplemental images including the
  three native-dismissal aftermath pages. Client widths360/753/1425 exclude the
  scrollbar and are not confused with CSS viewport widths.
- [Companion CSRF controls](csrf-controls.json): authenticated POST without token
  returns403 on both create routes. These are HTTP security controls, not extra
  browser interactions. Update/disable/voucher checkout are not executed here.
- [Console observation](console.json) returned no warn/error entries in this
  follow-up tab. It does not relabel the original240-case run as console-verified.

[Node result](tests.json):40 PASS,0 failures/cancellations/skips. No fresh local
Maven/full PostgreSQL run is claimed for this evidence-only delta. Previous Java
results retain original provenance. The new PR-head CI is reported on PR74.

## QĐ delta review

- [ ] Independently repeat native dismissal/focus; compare HP attestations, DOM,
      access counts and DB state. Extend key/action combinations if required.
- [ ] Inspect real Voucher/Promotion form302→200, success flash and DB fields.
- [ ] Compare the three separate viewport screenshots/geometry for both edit pages.
- [ ] Confirm original source packet is unchanged and final publication adds only
      docs/evidence, with equivalent executable objects and successful new CI.
- [ ] Give an independent verdict; HP alone decides acceptance/merge. Existing
      QA-01 overall, QA-02/QA-03 and release gates remain unchanged.
