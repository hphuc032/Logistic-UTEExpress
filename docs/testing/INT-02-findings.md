# INT-02 findings and owner decisions

Current executable source `e6bc22decc20118015b48eae21a60e59abf49646`; integrated develop
`663fc9155bffbc0b35cc5ae0b465a0a74c5ca645`. This replaces the Oct25 OPEN statuses;
historical evidence remains preserved locally and in Git history. No independent TD
approval or exhaustive vulnerability-scan result is claimed.

## F01 — Dashboards absent from old baseline — RESOLVED

- Original symptom: old develop 5b5164d lacked #60/#59; report correctly blocked that snapshot.
- Root cause: integration sequencing; that status became stale after approved merges.
- Authority: INT-02 complete P0/P1, S4 dashboards and I7 financial reporting.
- Fix: normal merge of develop 663fc915 (includes #60 e6d94c5 and complete #59 financial slice).
  No copied financial SQL or changed ledger invariants.
- Final evidence: VendorRevenueIT 46, AdminRevenueIT 26,
  OpsDashboardReadIT 28; RevenueServiceTest and OpsReportFilterTest.
  Shipping-excluded collection/refund periods, original commission, exact successful
  Payment despite failed attempts, no item multiplication, Shop/global parity, unknown
  evidence, Ho_Chi_Minh windows, read-only snapshots, Manager/Admin privacy and audit tested.

## F02 — Shipper rate 66.7 versus 40.0 — RESOLVED

- Original aggregate probe: 2 DELIVERED, 1 current DELIVERY_FAILED, 3 RETURNED_TO_SENDER,
  total 6. Old formula 2/(2+1)=66.7%; terminal definition 2/(2+3)=40.0%.
- Source: Master Plan S-07 and explicit final Q&A: completed shipments in period,
  DELIVERED/(DELIVERED+RETURNED_TO_SENDER). The denominator is uniquely specified;
  no business exception was assumed. Current-failed count is a separate operational card.
- Root cause: SHIP-04 implemented a reduced current-failure metric. Its symmetric existing
  fixture has equal failed/returned counts and cannot discriminate the formulas.
- Fix: minimal existing DTO calculation and clear UI label: terminal success, all recorded
  data for the current assignee; no selectable period or historical assignment model.
- Evidence: ReturnToSenderIT.terminalMetricUsesRealShipmentPathsNotAttemptsOrOpenFailuresAndIsTenantScoped
  runs every operation through actual assignment/fulfillment/receipt services. Existing
  ShipperFulfillmentIT now expects NULL when there are no terminal outcomes.

| Real owner shipment group | Shipments | Started attempts | Completed attempts | Successes | Failed attempts | Terminal denominator |
|---|---:|---:|---:|---:|---:|---:|
| Assigned, not started | 1 | 0 | 0 | 0 | 0 | 0 |
| First-attempt delivery | 1 | 1 | 1 | 1 | 0 | 1 |
| Failure then retry delivery | 1 | 2 | 2 | 1 | 1 | 1 |
| Two failures, not physically returned | 1 | 2 | 2 | 0 | 2 | 0 |
| Three physical returns, two failures each | 3 | 6 | 6 | 0 | 6 | 3 |
| Delivery in progress | 1 | 1 | 0 | 0 | 0 | 0 |
| Owner total | 8 | 12 | 11 | 2 | 9 | 5 |
| Foreign Shipper delivery, excluded | 1 | 1 | 1 | 1 | 0 | excluded |

| Interpretation | Numerator / denominator | Percentage | Contract |
|---|---|---:|---|
| Completed shipments | 2 / (2+3) | 40.0% | Plan; implemented |
| Successful completed attempts | 2 / (2+9) | 18.2% | Different productivity metric |
| Delivered / all assigned | 2 / 8 | 25.0% | Different backlog-sensitive metric |
| Delivered / delivered + open failed | 2 / (2+1) | 66.7% | Old erroneous formula |

The prior six-row aggregate probe contains no reconstructible historical attempts;
none are invented. The new real-path dataset recreates its discriminating terminal
counts and adds pending/in-progress cases. Foreign=100.0%, empty denominator=NULL.

## F03 — Exhausted physical-return resolution absent — RESOLVED

- Original symptom: retry/exhaustion existed; only enum/read support for RETURNED_TO_SENDER.
  Ops cancellation correctly refused post-pickup, leaving stock reserved/Order SHIPPING.
- Authority: Plan I state machine/I5/T44; maximum two attempts, Ops actual receipt before
  cancellation/restock, sole lifecycle writer, stock release once. Delivery failure is
  Shipment state, never a new OrderStatus.
- Fix: dedicated confirmReturnToSender, immutable receipt and existing Ops page form.
  Order -> Shipment -> Payment -> persisted Ops Account -> Products -> Voucher/usage.
  COD UNPAID evidence remains unchanged. Existing NEW/CONFIRMED cancellation guards are
  retained; dedicated domain inventory marker and persisted-CANCELLED voucher boundary
  resolve the initially discovered guard mismatch without weakening pre-delivery guards.
- Replay requires original versions, canonical reference and same currently authorized
  Ops actor, matching final state/markers. SQL uniqueness/append-only trigger and Order
  lock serialize receipts. No automatic physical-return inference, shipping fee refund,
  Payment rewrite or stock restoration on failure.
- Final evidence: ReturnToSenderIT 16 cases, Int02CommerceFlowIT
  RETURN_TO_SENDER variant, ReturnToSenderMigrationIT 2.
  First/second failures, limit, ADMIN/MANAGER success, USER/VENDOR/SHIPPER denial,
  revoked persisted role, no CSRF, unknown fields, stale version, missing/PAID/ONLINE/
  wrong-amount Payment, immutable snapshots, quota and stock once, replay mismatch,
  held transaction concurrency both commit and rollback, injected history failure,
  outer-TX rejection and receipt UPDATE/DELETE/TRUNCATE denial all execute against PG.
- Limitation: authorized Ops physically-received attestation/reference is the evidence;
  this application does not independently verify carrier custody.

## F04 — After-commit inbox violated literal I8 atomicity — RESOLVED, HP AUTHORIZED

- Original mechanism: B, after-commit Order event -> REQUIRES_NEW inbox projection.
  Durable history allowed recovery but an Order could commit while inbox was absent.
- Approval: HP answered **“Cho phép triển khai transaction correction theo thiết kế này”**
  to the concrete MANDATORY writer/pre-commit fact proposal in this chat, before editing
  production behavior. This is authorization of that design, not an independent TD review.
- Fix: OrderTransitionPrepared after lifecycle history flush, trusted synchronous event;
  notification module's MANDATORY writer projects existing persisted history/recipient
  pairs in the same transaction. It does not swallow failure or suspend the transaction.
  OrderStatusChangedEvent remains committed-only; initial NEW notification policy unchanged.

| Criterion | Master Plan I8 | Old mechanism / risk | Current approved mechanism | Evidence |
|---|---|---|---|---|
| Persist inbox | Business TX | Separate after-commit TX; temporary missing inbox | Same MANDATORY TX; insert failure rolls back Order | NotificationRecoveryIT constraint injection/retry |
| Rollback | No successful notification | History rollback already safe | Order/history/inbox rollback; no hint | Recovery outer rollback; OrderDomainCoreTest |
| Socket | After commit only | Already after projection commit | Existing afterCommit hints preserved | Signals empty inside TX/rollback; NotificationSocketIT |
| Offline | Stored inbox recoverable | History recovery needed before row existed | Inbox exists on commit; legacy reconciliation retained | NotificationIT/RecoveryIT/SchedulerIT and socket reconnect |
| Retry/dedup | IDs mitigate duplicates | Unique history/recipient | Same unique keys and private IDs | Concurrent workers/replay/Node tests |
| Privacy/freshness | Recipient and auth enforced | No proven leak | JWT/Origin/CSRF/expiry/logout/lock policies unchanged | NotificationSocketIT, SecurityHardeningIT |

Final NotificationRecoveryIT 7 and NotificationSocketIT
29 pass. Legacy/imported-history late lower-ID recovery remains
tested without pretending new lifecycle writes may commit without their inbox. No new
broker/outbox, dependency cycle or exactly-once socket guarantee is introduced.

## F05 — Test JVM/container shutdown diagnostics — OPEN LOW, QA FOLLOW-UP

Historical baseline/final runs emit Hikari closed-connection and 30-second fork shutdown
diagnostics after successful assertions. Current full log is retained; see manifest for
observed counts. Maven exit 0, summary timeout=false and zero failures/errors/skips/flakes
are reported independently from shutdown cleanliness. Root cause is not established;
no production deadlock is inferred. QA-03 should investigate cached context/container/
scheduler lifecycle without suppressing warnings or weakening assertions. This is not
an unresolved INT-02 business blocker.

## F06 — Test-only fixture defects — RESOLVED

Oct25 BYTEA Map identity equality was corrected to content comparison, not skipped.
Current iterations exposed old pre-delivery guard assumptions (fixed narrowly
as F03), abstract-interface Mockito callRealMethod misuse (spy concrete current-user
provider while preserving real DB locks), a helper-name collision, and an HTTP Vendor
fixture slug with underscore (correct fixture to valid slug). Failure logs remain local;
subsequent focused/full assertions all execute. No CART/security/production schema rule
was weakened to make fixtures pass. The first full post-merge run then found six
assertion failures: two OrderDatabaseIT assertions conflated internal preparation with
committed events, and four ReturnReceiptMigrationIT/RefundLedgerMigrationIT cases had
exact migration inventories ending before the new additive version. The collector now
separates/asserts the internal fact while keeping committed-event rollback/timing
assertions; the exact expected version lists include 20261027100000. Migration,
checksum, historical data and immutable ledger assertions remain. The failed full
attempt and XML are retained separately; only the subsequent clean run counts as
current acceptance evidence.

## Acceptance limits and classification

- INT-02 contract blockers F01–F04: RESOLVED on tested source, pending TD review.
- Browser 375/768/1440: NOT RUN. Installed Firefox launched; Computer Use policy stopped
  inspection because URL-policy enforcement is unsupported for this Windows browser.
  No bypass/screenshot/viewport claim. UI-02 S5 follows INT-02; full T50 manual group
  acceptance remains UI/QA/release work, not secretly relabelled PASS.
- Clean isolated PG migration + real app/HTTP five-role startup: PASS. Full T49
  missing-ENV/upload restart/production ACL/backup/log operations: NOT RUN; QA-03/REL.
- SMTP, browser compatibility, production monitoring/load/multi-instance broker remain
  unverified. UNKNOWN review-media cleanup remains restricted/operator-controlled.
- No owner decision remains silently unresolved. No push/PR/merge/remote comment made.
