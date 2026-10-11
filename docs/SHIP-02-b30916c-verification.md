# SHIP-02 b30916c against ORD-OPS-01 d100cab

Verified on 2026-10-11 using Java 21, PostgreSQL 17.6 and READ COMMITTED.

**PASS for both reproduced inversions and the tested eligibility/TOCTOU schedules.**
Vendor cancellation versus same-account pickup and versus ADMIN reassignment
targeting that account serialize on Order in both scheduling directions. No
`40P01`, lock timeout or statement timeout occurred in the final focused run.
This result does not certify arbitrary outer transactions that acquire Account
before joining an existing-Order lifecycle operation.

## Repository and scope

- Initially clean branch: `feature/ord-ops-01-cancellation`.
- Original branch/HEAD, still preserved: `d100cabada99e2002427f15d2f3b60b3b81cce4a`.
- Fix: `b30916c305562f9862247d51fdb2c83a542d9155`.
- Temporary branch: `verify/ship-02-multirole-locks`.
- Temporary HEAD: `1a5ab22fc1cc38c4fb477d62966d2704d9bdc56e`.
- HEAD's parent is exactly b30916c. Cherry-picking d100cab had no conflicts.
- Verification edits remain uncommitted on the temporary branch.
- No production-code changes relative to b30916c; no push, merge, PR or
  `cancelForOps` implementation.

The original characterization document remains historical evidence for d100cab.
Its expected-deadlock conclusions and earlier test totals do not describe this
temporary checkout. This report records the corrected expectations and fresh run.

## All six original characterization invocations

The two former deadlock tests now each exercise both directions, producing eight
invocations from the original six. No case was disabled or removed. Two additional
Vendor Account restriction invocations verify the relocated eligibility boundary.
ShipperOrderLifecycleIT therefore contains 38 existing + 8 adapted + 2 additional
invocations, totaling 48.

| Original case | Verification expectation/result |
| --- | --- |
| Vendor cancellation versus own assigned pickup | Vendor first: cancellation OK, pickup CONFLICT. Pickup first: pickup OK, cancellation CONFLICT. Both PASS. |
| Vendor cancellation versus ADMIN reassignment to that account | Vendor first: cancellation OK, reassignment CONFLICT. Reassignment first: reassignment OK, then cancellation OK. Both PASS. |
| Full-lock Vendor-first pickup serialization | Pickup waits on Order, then conflicts after cancellation commits. PASS. |
| Full-lock pickup-first Vendor serialization | Vendor waits on Order, then conflicts after pickup commits. PASS. |
| ADMIN+SHIPPER reassignment on another Order | Reassignment waits on target Account through pickup completion and outer commit; both commit. PASS. |
| ADMIN role revocation of ADMIN+SHIPPER | Governance waits on Account through pickup completion and outer commit; pickup and revocation commit, SHIPPER remains. PASS. |

The adapted test names are `sameAccountVendorCancellationAndPickupSerializeBeforeEligibility`
and `sameAccountVendorAndAdminAssignmentTargetSerializeBeforeEligibility`.
The latter does not invent Ops cancellation permissions: ADMIN only invokes the
existing reassignment API.

Reassignment does not change Order's CONFIRMED status/version. Cancellation has
no client-supplied expected Shipment version or immutable assignee condition;
after reassignment commits it can legitimately cancel the newly assigned Shipment.
Requiring a conflict in that direction would invent a production contract.

## Exact committed effects

Each early-eligibility schedule checks full Payment preservation, assignment
history preservation/append, inventory release marker, final assignee, stock,
Order/Shipment versions, lifecycle history count and Shipment audit counts/actions
with the persisted actor. Here deltas start after fixture assignment:

| First operation | Order / Shipment | Order version | Shipment version delta | Stock | Lifecycle history delta | Shipment audit delta |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Vendor, versus pickup | CANCELLED / CANCELLED | 3 | +1 | 9 | +1 | +1 CANCELLED |
| Pickup, versus Vendor | PICKED_UP / PICKED_UP | 3 | +1 | 8 | +1 | +1 PICKED_UP |
| Vendor, versus reassignment | CANCELLED / CANCELLED | 3 | +1 | 9 | +1 | +1 CANCELLED |
| Reassignment, then Vendor | CANCELLED / CANCELLED | 3 | +2 | 9 | +1 | +2 REASSIGNED, CANCELLED |

The first three preserve assignment history exactly; the last appends one entry
and preserves its existing prefix. No losing pickup/cancellation adds effects.
The retained-lock cases also assert exact Order/Shipment versions, histories,
audit deltas, unchanged stock/Payments and the intended Account/role effects.

## Live SQL and pg_blocking_pids evidence

Final run, `.work-ship02-verification.log` lines 326-339. PIDs are scoped to each
test/container; repeated numeric PIDs are not a claim of shared test transactions.
Every listed wait reports `state=active`, `wait_event_type=Lock` and the indicated
`pg_blocking_pids(waiter)` result.

| Schedule | Waiter -> holder | Blocking SQL, projection abbreviated |
| --- | --- | --- |
| Vendor first / reassignment | 71 -> 70, blockers=[70] | `SELECT status, version, ready_at, shipping_provider_id, shipping_service_code, shipping_fee FROM uteexpress.orders WHERE id=$1 FOR UPDATE` |
| Reassignment first / Vendor | 71 -> 70, blockers=[70] | `SELECT ... FROM uteexpress.orders o1_0 WHERE o1_0.id=$1 AND o1_0.shop_id=$2 FOR NO KEY UPDATE OF o1_0` |
| Vendor first / pickup | 71 -> 70, blockers=[70] | `SELECT ... FROM uteexpress.orders o1_0 WHERE o1_0.id=$1 FOR NO KEY UPDATE OF o1_0` |
| Pickup first / Vendor | 71 -> 70, blockers=[70] | Owned Order query above, `FOR NO KEY UPDATE OF o1_0` |
| Vendor eligibility first / restriction | 71 -> 70, blockers=[70] | `SELECT ... FROM uteexpress.users ue1_0 WHERE ue1_0.id=$1 FOR NO KEY UPDATE OF ue1_0` |
| ADMIN role revocation / pickup holder | 70 -> 72, blockers=[72] | `SELECT version, status FROM uteexpress.users WHERE id=$1 FOR UPDATE` |
| Other-Order reassignment / pickup holder | 70 -> 72, blockers=[72] | Same Account query, `FOR UPDATE` |

The two full-lock Vendor/pickup directions additionally observe 71 -> 70 on Order
`FOR NO KEY UPDATE`. Thus pickup-first Vendor now waits on Order rather than on
Account as in the original characterization.

The four early-eligibility schedules pause the first operation after its real
Order lock and before Account acquisition. They require the other operation to
wait on Order without reaching its Account/eligibility boundary. The holder has
no blockers at that point. Releasing the holder must produce the exact outcomes
above, within bounded Futures. This would fail with Account-first Vendor ordering.

Separate real backend transactions, latches, ten-second observation/latch bounds,
25-second Future bounds, 60-second test bounds and bounded executor cleanup remain.
No sleep chooses transaction order. Local test-container settings remain
deadlock_timeout=5s, lock_timeout=20s and statement_timeout=25s. The shared worker
helper now fails on every SQL exception, including `40P01`, `55P03` and `57014`;
these cannot satisfy a safe-serialization expectation.

## Eligibility, retained locks and TOCTOU

Shipping still locks Order then Shipment, validates live assignment, locks the
assigned Account using `SELECT status ... FOR SHARE`, and queries persisted
ACTIVE/SHIPPER membership. Subsequent fulfillment/evidence calls repeat those
checks. There is no KEY SHARE substitution, Account lock upgrade or global Role
lock added to fulfillment.

All four existing governance-first/fulfillment-first delivery invocations passed
for both Account restriction and SHIPPER revocation:

- Governance first: the stale authenticated SHIPPER principal does not suffice.
  Delivery waits, then returns ACCESS_DENIED after restriction commits, preserving
  Order, Shipment, Payment, stock, voucher, history and audit state.
- Fulfillment first: governance cannot commit until the outer transaction commits,
  even after all fulfillment boundaries return. Delivery commits once; Account
  restriction/role revocation and token rotation follow. After-commit events and
  exact history/audit/Payment facts remain checked.

The two added `vendorEligibilityAfterOrderSerializesWithAccountRestriction`
invocations passed. A real ADMIN restriction that commits after owned Order
locking but before Vendor Account eligibility causes ACCESS_DENIED with the full
Order-related state unchanged. If Vendor already holds Account, restriction is
observed waiting on users until cancellation commits, then commits its Account
status, token rotation and one ACCOUNT_LOCKED audit. Vendor's ACTIVE lock was
moved, not removed.

Reassignment still uses Order -> Shipment -> global SHIPPER Role -> target Account
FOR UPDATE, then checks persisted ACTIVE and SHIPPER membership. The focused
assignment/governance suites passed. Vendor retains trusted principal VENDOR
authorization and approved owned Shop scoping/final locked validation. Vendor
does not add a new under-lock persisted VENDOR-membership check; ManagedRole
excludes VENDOR because that role belongs to shop approval. This run makes no new
VENDOR revocation-policy claim.

## Joined transactions: source findings and limits

Vendor authority is MANDATORY; lifecycle TransactionTemplate defaults to REQUIRED.
The fulfillment coordinator is REQUIRED and its Order/Shipping boundaries are
MANDATORY. Earlier outer-transaction locks are therefore retained, not reordered
or released by this fix.

An outer caller that locks this Account first and then invokes Vendor cancellation
can still recreate Account -> Order versus Shipping/assignment's Order -> Account
cycle. This is a source-derived residual risk, not a newly reproduced deadlock in
this verification. The safe tests start without an inherited Account lock.

Inspection found no current production existing-Order lifecycle caller with that
sequence: VendorOrderController has no transaction/Account-first call; the current
fulfillment coordinator prepares Order before Shipping eligibility. Account-first
APIs still exist in AddressService, VendorVoucherService, product promotion
management and checkout; their inspected normal paths do not subsequently invoke
existing-Order mutation. Checkout's new-Order creation is a distinct workflow.

AccountIdentityService also checks the UserEntity returned by a pessimistic JPA
query without an explicit refresh. A joined caller with an earlier managed Account
snapshot warrants separate freshness verification. Unlike Vendor's explicit Order
refresh, this source alone does not prove Account snapshot freshness. That scenario
was not reproduced/tested here. Current HTTP flow has open-in-view=false; JWT
authentication derives persisted roles/status before the mutation transaction.

HP/TD should document the supported outer-transaction lock preconditions and
review Account snapshot freshness before approving broader joined compositions.
QD should retain the shared eligibility lock and persisted assignment/role checks,
keep reassignment after Order/Shipment locking, and retain both-direction tests.
Future Ops callers need an agreed hierarchy before implementation. Neither retries
nor JWT invalidation replace these lock and persisted eligibility guarantees.

## Fresh validation

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Ppostgres-it -Dtest=ArchitectureTest '-Dit.test=ShipperOrderLifecycleIT,ShipperFulfillmentIT,ShipmentFulfillmentIT,ShipmentAssignmentIT,VendorOrderIT,RoleGovernanceIT' clean verify
```

Final build completed 2026-10-11 23:20:07 Asia/Saigon, BUILD SUCCESS (1:11).
Fresh XML reports were read after this clean run:

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| ArchitectureTest (Surefire) | 7 | 0 | 0 | 0 |
| ShipperOrderLifecycleIT | 48 | 0 | 0 | 0 |
| ShipperFulfillmentIT | 6 | 0 | 0 | 0 |
| VendorOrderIT | 73 | 0 | 0 | 0 |
| ShipmentAssignmentIT | 6 | 0 | 0 | 0 |
| ShipmentFulfillmentIT | 6 | 0 | 0 | 0 |
| RoleGovernanceIT | 6 | 0 | 0 | 0 |
| Total | 152 | 0 | 0 | 0 |

Totals are 7 Surefire + 145 Failsafe. No full-repository suite claim is made.
Final log contains no 40P01/55P03/57014. Existing deliberate constraint-failure
rollback tests emit other SQL errors while passing their assertions.

The first sandboxed run could not access Docker, so its container startup errors
were not product failures. An intermediate added observer assertion erroneously
sampled its own query on the worker connection; it was removed and verification
rerun. That diagnostic log is `.work-ship02-observer.log`. Final evidence is
`.work-ship02-verification.log` and target Surefire/Failsafe XML/text reports.

Final working changes: `src/test/java/com/uteexpress/order/ShipperOrderLifecycleIT.java`
and this report only. `git diff --check` and a whitespace check of this initially
untracked report passed. Production tree comparison against b30916c is empty.
