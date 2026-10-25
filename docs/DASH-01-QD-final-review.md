# DASH-01 PR #60 — QD independent scoped review

Reviewed immutable SHA: `1e608133708e322e5ab741cf50803381cf63bc49`. Scope: aggregate grain, payment ambiguity, exact refund matching, reporting snapshot and reuse architecture. Result: SCOPED PASS, no Critical/High finding in this scope. Not a full-suite independent sign-off or merge approval; HP final acceptance and branch protection remain required.

## Independent verification

Java 21, PostgreSQL 17.6 Testcontainers, clean checkout of the reviewed SHA:

```powershell
.\mvnw.cmd -q -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT,ManualCodRefundIT,RefundLedgerMigrationIT' clean verify
```

20 unit/architecture tests + 108 PostgreSQL tests = 128 PASS, failures/errors/skips=0, Failsafe flakes=0, timeout=false, Maven exit=0. VendorRevenueIT contributes 43 PostgreSQL cases; the remaining 65 cover refund lifecycle/ledger migration. QD did not rerun TD's reported 1,789-test full suite. Local log: qd-dash01-independent.log (ignored). No TD source, applied migration, FK, immutability trigger or fixture was changed.

## SQL conclusions

- payment_counts counts ALL PAID records per scoped Order before validating them. Only paid_count=1 joins a PAID row; payment_id/amount/paid_at are from that same row, not independent MIN aggregates. Extra UNPAID attempts do not poison a successful record.
- payment_ok requires that unique record to be COD, not expired, dated and equal to persisted grand_total. Invalid/missing evidence propagates completeness diagnostics rather than a fabricated complete zero. Existing partial unique PAID index also rejects multiple successes; this run did not disable it to manufacture invalid data.
- Money streams remain Order/refund grain; items join only the quantity stream. Equal-valued Orders and unsuccessful attempts do not multiply money. Refund compares exact successful payment_id, receipt ID, paid_at/received_at and original grand_total; subtracts stored shipping for merchandise reporting. Unsuccessful same-Order payment cannot qualify; cross-Order payment fails composite FK.
- Delivery, collection, receipt and refund retain independent reporting periods. Net commission uses original snapshots and refund-period reversal, not current policy. Zero commission and NULL policy ID with valid snapshots remain valid; missing/ambiguous facts remain unavailable.
- Scope derives approved Shop ownership from persisted ACTIVE VENDOR authority. Read-only REPEATABLE_READ covers authorization and projections. HCM windows are half-open. Snapshot authorization is a reporting contract, not a lock-held write authorization guarantee.
- Actual production SQL is exercised and EXPLAINed by VendorRevenueIT with sparse/dense fixtures. This is local measured evidence, not a production SLA or proof that no future index is needed. Undated diagnostics may scan historical Shop data.

## DASH-02 reuse proposal — requires TD/HP interface agreement

Do not import Order RevenueService into governance: Order already depends on governance. Do not copy financial SQL into Ops or independently redefine amounts/completeness.

Preferred follow-up: TD extracts the already-reviewed financial projections and immutable reporting DTOs into a neutral `reporting` module with no dependency on Order/governance services/entities. Order keeps Vendor authentication/approved-owned-Shop resolution; governance keeps persisted ACTIVE ADMIN authorization. Both call a reporting public service through an explicit trusted server scope. Dependency direction: Order -> reporting and governance -> reporting; reporting must not call back into either module. ArchUnit must verify acyclic modules.

A global/Admin scope must be a dedicated server-issued scope, never a missing/NULL client Shop ID that silently broadens Vendor access. Shop-specific and all-Shop aggregates need separate authorized entry points. Preserve one-Order grain, exact successful payment matching, merchandise/shipping split, refund timestamp and completeness propagation across Shops. Adding Shop totals must not multiply Order/item/payment rows or hide unknown amounts. All component queries and authorization must share the wrapper's read snapshot; avoid an inner transaction opening a different snapshot.

Before extraction, TD/HP should approve the service/DTO/scope contract and ownership of the neutral module. Then add cross-Shop, mixed complete/unknown, identical-valued Orders, multi-attempt payments, exact refund reference, day/month UTC boundaries and fixed-query-count PostgreSQL regressions. Until then PR #59 stays Ops/account/audit only; no financial implementation or new reporting index/migration is introduced by this document.
