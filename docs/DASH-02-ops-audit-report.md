# DASH-02 Phase 1 — Ops aggregates and Admin audit viewer

Owner QD, reviewer TD; authorization review HP. Base develop bce7ff0 (REV-01 merged). WS-02 #58 is not a dependency and is not bundled into this branch. This is the operational/account/audit portion of DASH-02; A-01 financial revenue reporting remains open pending integration with TD's DASH-01 financial read model. Do not mark all DASH-02 done from this PR.

## Scope and metric definitions

GET /admin/dashboard and /manager/dashboard retain their role-specific landing pages and gain a date/shop filter. JSON metrics use /admin/dashboard/metrics and /manager/dashboard/metrics. Only persisted ACTIVE accounts with the corresponding current role are accepted, in addition to existing HTTP/method security. No client actor or role input is used as authority.

- Orders: count rows by current Order status where orders.created_at is inside the requested period.
- Shipments: count rows by current Shipment status where shipments.created_at is inside the period, with its Order used only for Shop scoping. Order creation need not be in the same period.
- Shops: count rows by current registration/moderation status where shops.created_at is inside the period.
- Successful refunds: count immutable SUCCEEDED ledger rows by refunds.server_timestamp, linked to the scoped Order. This is the Admin recording/completion time, not bank-transfer time. A REFUNDED Order status without ledger evidence is not fabricated into a refund event. No refund amounts, commissions or earnings are computed here.
- Accounts (ADMIN only): global creation-period counts by current account status. These do not inherit a Shop filter; both UI and DTO keep that distinction explicit. Manager receives no account aggregates.

Orders/shipments/shops are creation-cohort counts with current states, not transition-event counts or sales-period metrics. Independent streams are never joined through OrderItems/Payments, avoiding fanout and equal-money deduplication errors. No pricing, commission, Payment, inventory, lifecycle, voucher or Shipping mutation.

## Window, consistency and authorization

Local inclusive from/to dates in Asia/Ho_Chi_Minh are converted once to UTC [start,end). Default last 30 local days; maximum 366 days, ISO years 1..9999. SQL binds UTC OffsetDateTime parameters and never date(column) or session timezone expressions. Shop ID is an optional positive read filter within existing system-wide Ops authority, not an ownership claim. Unknown/duplicate query parameters, invalid ranges/IDs/codes and out-of-bounds pagination return 400.

The read service opens a fresh REPEATABLE_READ read-only transaction. Persisted role/ACTIVE checks and all counts/pages share its snapshot; no Account/Order/Shipment row locks. Concurrent governance after the snapshot does not retroactively change an already-authorized read response. This is a reporting read contract, not a write-authority or long-lived socket contract.

## Audit viewer

Master Plan A-09 and route table reserve GET /admin/audit-logs for ADMIN. /admin/audit-logs/data provides the same minimal JSON projection. Manager and all other roles are denied by route and service guards. Filters: local dates, action, targetType, actorId, targetId and page. Code filters are uppercase ASCII identifiers <=64 characters; IDs positive; fixed 20 rows/page, page 0..500. Ordering created_at DESC,id DESC. Actor may be NULL for system history.

Projection includes ID, actor ID, action, target type/ID, reason code and UTC timestamp. It excludes raw before/after summaries, contact/credential data, refund proofs and receipt references. It is an audit metadata viewer, not an unrestricted log dump/export. HTML uses escaped text. Count and page use a single snapshot, but separate page requests are fresh snapshots rather than a multi-page export guarantee. There is no audit mutation endpoint. HTTP security keeps CSRF enabled; JSON responses are no-store.

## SQL and performance evidence

Admin: five aggregate SQL calls; Manager: four (no global account query), plus current-identity reads. Audit: two projection queries, plus identity reads. Query count is independent of OrderItem count and number of rows. All values are bound; table/column/order fragments are fixed code, never client-supplied SQL.

An isolated PostgreSQL 17.6 fixture compares empty scope and 100 Orders: aggregate count stays fixed. EXPLAIN (ANALYZE,BUFFERS) is captured from the actual NamedParameterJdbcTemplate SQL/parameters. This small synthetic baseline is not production load testing or a performance SLA; some streams have no matching rows. No new index/migration is justified from it. Larger global/cohort/audit datasets should be profiled before reserving any future index migration with QD/TD.

## Validation and review gate

Focused unit/UI/architecture and PostgreSQL tests cover date/year boundary/default/leap range limits, invalid/duplicate filters, role/persisted-eligibility checks, Admin/Manager scope, item fanout, independent creation streams, Shop isolation, no-ledger legacy state, real RECEIVE_RETURN/refund completion timing, unchanged evidence after reporting, stable audit pagination/NULL actor, private-summary exclusion, actual read-only repeatable snapshot under concurrent append and captured SQL plans.

Full Java 21 `-Ppostgres-it clean verify` passed: 631 unit/architecture tests and 1,137 PostgreSQL tests across 54 integration classes (1,768 total), zero failures/errors/skips, zero Failsafe flakes, timeout=false, Maven exit 0. The initial full run exposed a no-DB authentication web fixture needing the new reporting mock; that test-only fixture was updated and the entire clean verification rerun. Existing Testcontainers teardown emits closed-connection/fork shutdown warnings; these are not counted as additional successful checks. Exact-head CI is recorded separately in the PR. Existing append-only refund/receipt/migration protections remain enabled; no applied migration changes. Financial revenue/earnings cards are intentionally not claimed as implemented, and no financial source-of-truth or new formula is introduced by this phase. TD reviews aggregates and future financial reuse; HP reviews Ops/audit authorization and reporting scope before merge.
