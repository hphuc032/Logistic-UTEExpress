# QA-03 checklist — source e886852 (2026-10-28)

PASS means the stated scope only. NOT RUN is missing execution evidence; BLOCKED
means a decision/dependency prevents acceptance. No production failure is hidden
by a PASS from another level. FAIL is retained for the initial harness attempt,
classified separately from the corrected application probes.

| Gate / Master ID | Dataset and expected result | Actual evidence | Status |
|---|---|---|---|
| Assignment / T43 | Distinct Ops/Shipper accounts, CONFIRMED order; stale/locked/foreign/post-pickup denied | ShipmentAssignmentIT 6; fulfillment role/CSRF tests | PASS automated |
| Failure/retry / T44 | Failure 1 -> retry 2 -> failure 2, no retry 3; Order SHIPPING unchanged; atomic evidence | ShipperOrderLifecycleIT 92; fulfillment 16; RTS 17 | PASS automated |
| Review / T36, T37 | Invalid rating/comment/media rejected; only owned DELIVERED items, one review; Return race | Foundation 13; ReviewIntegrationIT 25, including Unicode and both race schedules | PASS automated |
| Engagement / T39 | Same favorite not duplicated; views refresh and retain 30/user | EngagementIT 4 | PASS automated |
| Role scope / T40 | Non-Ops/foreign roles cannot mutate; non-Admin cannot read private financial data | Assignment, fulfillment, AdminRevenueIT, five-role HTTP journeys; see method mapping | PASS tested matrix, not complete security audit |
| Admin protection / T41a | Last active admin, self-lock, stale/unauthorized writes denied; audit rollback | AccountGovernanceIT 3, RoleGovernanceIT 6 | PASS automated |
| Manager commission deny / T41b | Current contract denies all Manager writes | CommissionPolicyIT 3 | PASS current deny policy |
| Manager limited-write workflow / ADMIN-06, T41c | Concrete configured limit and within/over-limit behavior | No approved limit or limited-write implementation found | BLOCKED — HP/QD/TD |
| Category/config / T42 | Invalid/duplicate fee/rate/provider denied; used category cannot hard-delete | CategoryIT 8, ShippingConfigIT 9; restored synthetic DB FK 23503 probe | PASS stated automated/local scope |
| Dashboard / T45 | Known multi-shop facts, no item/payment fanout; correct periods, refund/commission and legacy behavior | OpsDashboardReadIT 28, AdminRevenueIT 26, VendorRevenueIT 46, fulfillment dashboard assertions | PASS automated |
| Socket security / T46 | Missing/invalid JWT, Origin/CSRF, foreign destination denied; revocation/expiry enforced | NotificationSocketIT 29 including malformed claims and lock contention | PASS automated, single-instance |
| Inbox/commit / T47 | Rollback cannot emit success; offline inbox/reconnect; logout/revocation stops push | Inbox 6, Recovery 7, Scheduler 1, Socket 29, Node 12 | PASS automated |
| Clean migration / T48a | Empty PG17.6 app boots, validates 28 migrations, second migrate no-op; FK/unique/CHECK and upgrade evidence | DatabaseBaselineIT + migration suites + real packaged-JAR empty DB smoke | PASS automated/local |
| Complete ordered demo seed / T48b | All agreed SEED_MANIFEST stages on empty DB; rerun preserves progress and totals | Synthetic test fixtures and optional catalog/shipping seed checks are not full manifest execution | NOT RUN — shared owners |
| Missing ENV / T49a | Required ENV cleared -> nonzero startup with missing configuration | Local packaged JAR exit 1, MAIL_HOST first; not an exhaustive per-variable matrix | PASS one local scenario |
| Upload persistence / T49b | Actual HTTP upload stays readable after app process restart | CSRF-authenticated PNG avatar, equal before/after content hash | PASS local process rehearsal |
| Secret/log controls / T49c | Production config/logs/telemetry contain no secrets or sensitive payload | Sanitized evidence only; no production audit/alerting acceptance | NOT RUN for deployed environment |
| DB backup / release follow-up | pg_dump restores into new DB with matching rows | 34 application-table fingerprints match | PASS synthetic local DB only |
| Upload backup + off-host disaster restore | Restore both DB and uploads on target environment, verify ACL/recovery objectives | Not exercised; local DB rehearsal is insufficient | NOT RUN — REL-01 |
| Browser/manual Ops / T50 Ops subset | Actual form/JSON UX, multi-tab notification, review/public media and negative role paths | No current manually signed browser run or screenshots | NOT RUN — QD/HP |
| Responsive / T50, UI-02 | 375/768/1440, visible/usable controls and messages | HTTP HTML checks do not prove layout | NOT RUN — HP/QD |
| Clean-host / REL-01 | Provision from README, ENV, migrate+seed, restart/storage permissions, health | Current Windows developer workstation only | NOT RUN — REL-01 |
| Shared INT-02/QA acceptance | Recorded owner acceptance for all shared criteria on accepted source | Code merges known; full gate sign-off not established by this packet | BLOCKED pending HP decision |
| Whole QA-03 | All in-scope gates have evidence/explicit accepted exceptions and reviewer sign-off | Automated and local checks pass; gaps remain above | BLOCKED |

## Manual Ops execution plan (all steps currently NOT RUN)

Use approved fictional Buyer A/B, Vendor A/B, Ops ADMIN/MANAGER, Shipper A/B and
separate shops. Record build/source SHA, dataset manifest/version, role, expected,
actual, pass/fail, screenshots/request correlation without cookies/tokens, and any
finding ID for each step. Do not fabricate Order status or Payment facts with SQL.

1. From Admin/Manager Order views, assign/reassign using persisted versions
   (`/{admin|manager}/orders/{id}/assign`, `/reassign`); confirm locked Shipper,
   stale token, wrong role and post-pickup paths cannot change assignment.
2. In `/shipper/shipments/view` and `/shipper/dashboard`, use the actual order
   forms for pickup, `/shipper/orders/{id}/delivery-failed` and `/retry`. Verify
   attempt display, evidence error messages, no third retry and successful attempt-2
   delivery with exact COD. Use a separate exhausted failure for the trusted RTS flow.
3. Open `/notifications` in two isolated owner sessions, Vendor-confirm an Order,
   verify Buyer badge/inbox, read/dedup, offline/reconnect and logout behavior.
   Confirm Buyer B never sees Buyer A notification. Do not capture token contents.
4. From owned delivered `/orders/{id}`, follow server-derived item link to
   `/order-items/{id}/review`; try valid and invalid rating/text/media, duplicate
   submission and foreign item. Check `/products/{id}/reviews` and public media.
5. Run the complete group Register -> Checkout -> Delivery -> Review journey.
   Repeat critical Ops pages at 375/768/1440; verify labels, controls, errors and
   navigation remain usable. HTTP/JSON assertions cannot substitute this visual run.
6. Attach results and owner decisions to the report. Re-run the automated evidence
   on any changed accepted implementation as required; do not relabel NOT RUN rows.
