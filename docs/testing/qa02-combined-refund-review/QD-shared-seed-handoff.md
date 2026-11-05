> Historical combined-verification record. TD subsequently authorized this sanitized Draft review publication only. Publication HEAD/CI is separate from the tested combined tree; no approval, merge or HP final acceptance is claimed.

# QA-02 combined refund handoff — proposal, not owner approval

TD execution; QD owns shared-seed orchestration and schema compatibility verdict; HP final acceptance.
All three PRs remain unmerged at inspection. No QD branch, PR description, review or data was modified.

## Exact reviewed sources

| Source | SHA | State |
|---|---|---|
| develop | fa48cb69cef92276f0dddf0d2bd396eee098f2f3 | Includes merged #67 |
| #68 | e79b760d1e48f0532e6cb132e76ffa44e53dbf78 | Open, non-Draft; no submitted review |
| #69 | 69addc0f9d96105fcc4aab067bd968812515c3b4 | Open, Draft; no submitted review |
| #70 | 688884cd33f4b2f4dc62f5d33a4c4002e9146660 | Open; HP COMMENTED conditional business pass, not APPROVED |

The latest #68 HEAD is not an ancestor of #69 because of later evidence commits.
Their `src/`, POM, Maven and workflow objects are identical. The export takes #69,
overlays the latest #68 evidence and identical application/test files, then disjoint #70 paths.
No commits or Git merges were created. Candidate tree is
`bd0644ba373c5273a09e8af4898300dc4bdd8680`, including the proposed local adapter.

## Changes required in #69

1. Replace the blanket `o.status <> 'REFUNDED'` predicate in `refund_bound_to_paid_order`.
   Keep common invariants: persisted PAID Payment belongs to the same Order, no expired
   collection, Refund=SUCCEEDED, positive whole-VND amount equal to both Payment and grand_total,
   immutable nonblank proof, paid timestamp snapshot matches Payment.
2. Branch positive eligibility by `refunds.operation`, and reject unknown/NULL evidence with
   `NOT COALESCE(valid_operation_evidence,false)` rather than allowing SQL NULL to pass silently.
3. For `PAY02_MANUAL_COD_REFUND_V1`, require Order=REFUNDED, Payment method COD, same-Order
   approved received ReturnRequest, receipt timestamp match, cancellation snapshot NULL and
   result Order version = expected version + 1.
4. For `PAY02_CANCELLED_PAID_REFUND_V1`, require Order=CANCELLED, no delivery marker/history
   or ReturnRequest, release marker equals cancellation, valid PAID Payment created/collected
   before cancellation, matching cancellation snapshot, NULL return-only fields and unchanged
   result Order version. ONLINE evidence must have a nonblank persisted provider reference.
5. Add one paid-cancelled-refunded scenario and state `CANCELLED`, increasing the proposal's
   Order/Payment/item/usage manifest from 9 to 10. Keep all nine original scenarios and the
   original returned-COD refund unchanged. Use stable natural/checkout/idempotency keys.
6. The proposal creates an unpromoted two-item 200000 subtotal Order with voucher 10000 and
   shipping 15000: grand_total/PAID/refund 205000, merchandise 190000. It uses real checkout and
   Buyer cancellation. The single fresh ONLINE/PAID prerequisite is explicitly synthetic in
   the owned disposable database; no gateway/callback or production PAID writer is claimed.
   HP must review this fixture policy along with #69's existing initial ADMIN bootstrap.
7. Replace the global one-refund assertion with total 2 and exactly one of each operation.
   Preserve the old returned ledger row byte/value equivalence and require one new ledger/audit.
8. For this exact dataset, change collectedPayments 340000 -> 530000 and refunds 170000 ->
   360000. Keep deliveredSales=340000, netCollectedRevenue=170000, netCommission=11900,
   netRevenueAfterCommission=158100. Check Vendor A and Admin global with no unknown events.
   Shipping remains excluded; the never-delivered cancellation earns/reverses no commission.
9. Verify new product stock returns to 50 once and voucher usage is RELEASED before refund,
   then unchanged by refund/replay. Keep original promoted product stock=38 and returned-COD
   redeemed usage. Restore the original selected/unselected cart manifest after the fixture.
10. Exercise real-cookie/CSRF roles, proof/amount override/stale/duplicate denials, simultaneous
    same-key requests, and exact replay. Preserve the existing checkpoint-backed rerun after
    catalog edit: all 34 table fingerprints equal and zero second-pass POSTs. This does not
    establish cross-process resumption or recovery from a lost checkpoint.

The original blanket SQL returned one invalid row for the valid new fixture: a controlled
FAIL demonstrating the mismatch. The operation-specific proposal returned zero invalid rows.
Real simultaneous HTTP requests returned SUCCEEDED/replay=false and SUCCEEDED/replay=true,
amount 205000 each, with one ledger row. Separate fresh PostgreSQL integration tests establish
actual lock blocker/race/rollback behavior; HTTP timing alone is not asserted as blocker proof.

## Migration V20261028081110 review notes for QD

- TD adds one new migration after maximum 20261027100000; #68/#69 introduce no migration.
- No table/column/data deletion, data rewrite or old migration edit. Two old CHECKs are replaced
  and three return-only NOT NULL constraints move into the old operation's conditional CHECK.
- Existing returned-COD rows retain required ReturnRequest/version/receipt and +1 result version.
  The new operation requires NULL return-only fields, cancellation evidence and unchanged version.
- `refunds_check` is PostgreSQL's actual name for the original cross-column result-version CHECK;
  the fresh/populated upgrade tests verify that dependency. Schema drift is not repaired silently.
- New nullability intentionally skips the composite ReturnRequest FK for cancelled-paid rows.
  Unique Order/Payment/key, composite Payment/Order FK, persisted processor FK, positive finite
  whole-VND amount, timestamp checks and append-only UPDATE/DELETE/TRUNCATE triggers remain.
- Prior rows/checksums and repeat migrate=0 are checked by fresh combined-source migration tests.
  QD must issue the scoped compatibility verdict; these observations are not an approval.
- Existing manual completion stores immutable SUCCEEDED only; no gateway refund/PENDING writer,
  late callback flow or VNPAY Sandbox acceptance is added. PAY-03 remains DEFERRED.

## Local review artifacts and next authorization

Proposed adapter: `candidate/scripts/qa/rehearse_combined_refunds.py`; original
`candidate/scripts/qa/rehearse_integrated_seed.py` is unchanged from #69.
Results: `evidence/seed-attempt1/result.json`, frozen source identities and fresh regression logs.
The adapter explicitly replaces a Git-clean checkout guard with complete exported-source hash
validation because creating a combined commit was prohibited; no application security guard changes.
When QD ports the business fixture/assertion logic into its real branch, retain its normal Git
HEAD/clean-checkout guard. The export identity override is only for this disposable, commit-free
experiment and is not a proposed replacement for the shared runner's repository provenance checks.

Review publication is authorized. Required next steps: QD adopts/reviews the
operation-specific changes in its own branch, HP accepts fixture/bootstrap/rerun scope, then
owners integrate in the reviewed order and run fresh develop regression/CI before HP acceptance.
