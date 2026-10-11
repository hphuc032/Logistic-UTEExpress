# ORD-OPS-01 multi-role lock-order characterization

Baseline: `f062d7e0ae8532a29c1dbc2b47842805137f37f1`.
Branch: `feature/ord-ops-01-cancellation`. Owner: TD; cross-owner review: HP/QD.

This work adds tests and this report only. Production authorization, transaction
propagation and lock ordering are unchanged. There is no `cancelForOps` API or
assumed Ops cancellation policy. ADMIN is used only for existing assignment and
role-governance operations. Test users have their multiple roles persisted in
`user_roles`, as well as represented by their authenticated principal.

## Observed SQL and transaction boundaries

PostgreSQL 17.6 / Hibernate's PostgreSQLDialect / Java 21 were used. The integration
run reports READ COMMITTED isolation. The SQL below abbreviates Hibernate's column
projection; lock clauses and predicates are preserved. Runtime evidence comes from
`pg_stat_activity.query` on the actual worker backend, with `wait_event_type='Lock'`
and `pg_blocking_pids` identifying the other transaction.

| Boundary | Actual lock SQL / ordering | Transaction |
| --- | --- | --- |
| `VendorOrderAuthority.lockOwnedOrder` | Active Account, then owned Order. Hibernate emits `FOR NO KEY UPDATE OF ue1_0` for `users`, and `FOR NO KEY UPDATE OF o1_0` for `orders`. | MANDATORY, inside lifecycle `TransactionTemplate` (REQUIRED). |
| `ShipperOrderAuthority.requireTransition` | Order `FOR NO KEY UPDATE OF o1_0`, then Shipping contract. | Inside prepare/complete's MANDATORY transaction. |
| `ShipmentFulfillmentService` | Reacquires Order with `FOR UPDATE OF o`; Shipment `FOR UPDATE`; assigned Account `FOR SHARE`. | MANDATORY, retained through outer completion. |
| `ShipmentAssignmentService.reassign` | Order `FOR UPDATE`; Shipment `FOR UPDATE`; global SHIPPER Role `FOR UPDATE`; target Account `FOR UPDATE`. | REQUIRED; joins the test's outer transaction. |
| `RoleGovernanceService.change` | Selected Role `FOR UPDATE`, then target Account `FOR UPDATE`, then membership/token writes. | REQUIRED; joins the test's outer transaction. |
| `AccountIdentityService.requireActiveAccountForUpdate` | JPA `PESSIMISTIC_WRITE` becomes `FOR NO KEY UPDATE`, not `FOR UPDATE`, in this dialect/run. | REQUIRED; joins Vendor's transaction. |

Live Vendor account query:

```sql
select <user projection>
from uteexpress.users ue1_0 where ue1_0.id=$1
for no key update of ue1_0
```

Live Vendor Order waiter:

```sql
select <order projection>
from uteexpress.orders o1_0
where o1_0.id=$1 and o1_0.shop_id=$2
for no key update of o1_0
```

Live fulfillment account waiter:

```sql
select status from uteexpress.users where id=$1 for share
```

Live assignment/governance account waiter:

```sql
select version, status from uteexpress.users where id = $1 for update
```

Shipping's raw Order/Shipment clauses and governance's preceding Role clause are
explicit SQL in their production services/repository. The tests observe the native
Shipment query at the assignment pause and the Account query at its actual wait.
The preceding Role lock follows from the production call sequence; it is not
misreported as a separately sampled Account-wait query.

Hibernate's subsequent `EntityManager.refresh` read can omit a lock clause after
the initial Order lock. A later nonlocking read does not release that earlier lock:
the competing Vendor request is observed blocked on the same Order holder. The
initial test run exposed and corrected a mistaken assertion about that last refresh
statement; no production change was made to alter the behavior.

## Deterministic characterization cases

All additions live in `src/test/java/com/uteexpress/order/ShipperOrderLifecycleIT.java`.
The existing fixture creates a real Order, Payment and Shipment, then uses real
Vendor confirmation/ready and Shipping assignment. Only fixture role grants use
direct SQL. Mockito spies call real methods and pause at existing boundaries; they
do not fabricate evidence, add business locks, bypass guards or swallow failures.

There are six new test invocations:

| Test | Schedule and required result |
| --- | --- |
| `sameAccountVendorCancellationAndPickupReproduceAccountOrderDeadlock` | Assign the Vendor+SHIPPER account to its own shop's order using the existing reassignment API. Pause Vendor after its real Account lock; pause pickup after its real Order lock. Vendor waits on Order; pickup then waits on Account. Observe both edges, then require one `40P01` and one successful transaction. |
| `sameAccountVendorAndAdminAssignmentTargetReproduceAccountOrderDeadlock` | Persist VENDOR+ADMIN+SHIPPER on the Vendor account. Pause Vendor after Account lock; pause existing ADMIN reassignment to that account after Order/Shipment locks. Vendor waits on Order; assignment takes Role then waits on target Account. Require reciprocal waits and one `40P01` plus one success. |
| `sameAccountVendorAndPickupSerializeWhenOneOwnsAllRequiredLocks(false)` | Vendor already holds Account/Order/Shipment and provisional cancellation effects. Pickup waits on Order. Vendor commits; pickup conflicts with no extra lifecycle effects. |
| `sameAccountVendorAndPickupSerializeWhenOneOwnsAllRequiredLocks(true)` | Pickup already holds Order/Shipment/Account share. Vendor waits on its first Account lock. Pickup commits; Vendor conflicts with no extra cancellation effects. |
| `adminShipperAssignmentOnDifferentOrderWaitsForSharedEligibilityWithoutRoleCycle` | ADMIN+SHIPPER account holds fulfillment eligibility on one order. Its existing ADMIN operation reassigns another order to itself, holds SHIPPER Role and waits on target Account. Pickup and Order completion still finish, while assignment remains blocked until outer commit; then reassignment succeeds. |
| `adminRoleRevocationOfAdminShipperWaitsForOuterFulfillmentCommit` | Another ADMIN revokes the ADMIN role of an ADMIN+SHIPPER actor while pickup holds shared eligibility. Governance waits on Account through Order completion, then succeeds after outer commit. SHIPPER remains, and pickup commits. This does not authorize an Ops cancellation operation. |

### Reproduced failures

Both deadlock schedules reproduced real PostgreSQL SQLState **40P01**. In the
passing focused run:

- Vendor backend 70 waited for Shipping backend 71 on the Order query.
- Shipping backend 71 waited for Vendor backend 70 on Account: `FOR SHARE` for
  pickup; `FOR UPDATE` for reassignment.
- Both `pg_blocking_pids` edges were sampled while `wait_event_type='Lock'`.
- PostgreSQL's deadlock detector selected Vendor as victim in both observations;
  Shipping succeeded. Tests accept either victim, because victim selection is
  not a business contract.

Resulting states in that run were PICKED_UP/PICKED_UP for the pickup winner, and
CONFIRMED/ASSIGNED with the new assignee for the reassignment winner. Payment rows
were unchanged. Tests verify exact Order/Shipment version, stock, history and audit
deltas, plus preservation of prior assignment history. Only the surviving
transaction's effects remain. A timeout is not accepted as a deadlock result.

These are current defects characterized by passing tests, not fixed defects.
The expected `40P01` assertions must be replaced with safe-serialization assertions
when an approved correction lands.

The assignment inversion requires the target Account to alias the Account already
locked by Vendor. Merely holding ADMIN alongside VENDOR does not establish that
wait cycle: assignment resolves its actor but locks the selected target account.

### Observed safe waits

Both same-account Vendor/pickup serial schedules returned one success and one
domain CONFLICT. In the Vendor-first schedule pickup waited on Order's
`FOR NO KEY UPDATE`; in the pickup-first schedule Vendor waited on Account's
`FOR NO KEY UPDATE`.

The ADMIN+SHIPPER assignment and ADMIN-role revocation cases waited on Account
`FOR UPDATE` while fulfillment held `FOR SHARE`. Fulfillment continued rechecking
assignment/SHIPPER membership and completing Order without a global Role lock
cycle or an Account lock upgrade. The waiter could not finish until the outer
transaction committed, even after all fulfillment boundary calls returned.

This proves the specified schedules only. It does not prove every multi-role
interaction is safe, establish new Ops permissions, or cover an absent
`cancelForOps` implementation. It does not introduce a retry or HTTP error policy
for deadlocks.

## Bounded execution and evidence

- Independent transactions and distinct PostgreSQL backend PIDs for contenders.
- Latches pause after real locks, then release a contender only after its first
  actual wait is observed. No sleep selects the winner.
- Ten-second latch/observation bounds; 25-second Future retrieval bounds.
- Per-transaction `SET LOCAL deadlock_timeout='5s'`, `lock_timeout='20s'`,
  `statement_timeout='25s'` in the isolated test container. The longer detector
  interval permits sampling both edges; it does not create the inversion.
- Each new test has JUnit `@Timeout(60)`; finally blocks release gates and shut
  down executors with a bounded termination wait.
- SQLState extraction accepts only `40P01`; unexpected SQL errors, lock/statement
  timeouts and worker failures fail the characterization.
- Runtime observations are printed with the `ORD-OPS-01` prefix in Failsafe output.

## Minimal correction options for HP review

1. **Preferred candidate: move Vendor mutation eligibility locking after the
   owned Order lock.** TD would retain a shop-scoped Order lookup and refresh,
   then acquire/recheck the active persisted Account before authorization/effects.
   This removes the Account-before-Order edge demonstrated by both deadlocks.
   HP must review trusted identity, account refresh after waits, persisted role
   validation and governance races; QD must verify assignment/fulfillment
   interoperability. Keep Product-before-Shop and Voucher-before-Usage ordering.
   Do not globally change the Identity Account API: checkout's Account-first
   serialization has a separate purpose. Joined transactions that already hold
   Account locks must also be considered; reordering this method alone does not
   erase locks acquired by an outer caller.
2. **Broader alternative: coordinated Account-first ordering across Shipping
   and assignment.** This requires QD changes, authoritative assignment rechecks,
   ordering for multiple actor/target accounts, and coordination with Role-first
   governance. It is not an ORD-only patch and needs wider PostgreSQL coverage.
3. **Shared eligibility is a separate lock-strength decision.** Account-first
   shared Vendor eligibility would be compatible with fulfillment's shared lock,
   but still conflicts with assignment's target `FOR UPDATE`; it does not fix
   both reproduced inversions without an ordering change. Review any proposed
   shared-lock path for upgrades and account/membership mutation semantics.

Do not remove Shipping's eligibility lock, weaken it to KEY SHARE, rely on JWT
invalidation, or treat audit foreign keys as protection against account restriction.
The existing governance-first/fulfillment-first eligibility regressions must
remain valid. Request retry/error mapping can be reviewed separately and must
not disguise the demonstrated deadlock as safe serialization.

## Decisions still required

- HP/QD/TD: Ops cancellation roles and scope remain undefined; MANAGER was not
  granted new Order authority by these tests.
- HP/QD/TD: same-account Vendor+SHIPPER and self-assignment business policy. The
  current services allow the tested combination; no exclusion is invented here.
- HP/TD: fresh persisted Account/role checks and lock semantics after contention,
  including supported joins into existing transactions.
- HP/QD/TD: approved cross-owner lock hierarchy and any compatibility changes to
  Vendor, assignment or future Ops boundaries.
- TD/QD: future Ops Shipping hook, reassignment/version policy, cancellation
  reason codes, audit and failed-delivery/shop-receipt evidence.
- HP/TD: controlled deadlock response/retry policy, distinct from business CONFLICT.

## Verification

The initial six-case run had one test-observation assertion failure concerning
Hibernate's refresh statement. After correction, the focused command is:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Ppostgres-it -Dtest=ArchitectureTest '-Dit.test=ShipperOrderLifecycleIT,ShipmentFulfillmentIT,ShipmentAssignmentIT,VendorOrderIT,RoleGovernanceIT' verify
```

Passing focused run, completed 2026-10-11 13:03:55 Asia/Saigon:

| Suite | Tests | Failures / errors / skips |
| --- | ---: | --- |
| Surefire `ArchitectureTest` | 7 | 0 / 0 / 0 |
| Failsafe `ShipperOrderLifecycleIT` | 44 | 0 / 0 / 0 |
| Failsafe `VendorOrderIT` | 73 | 0 / 0 / 0 |
| Failsafe `ShipmentAssignmentIT` | 5 | 0 / 0 / 0 |
| Failsafe `ShipmentFulfillmentIT` | 6 | 0 / 0 / 0 |
| Failsafe `RoleGovernanceIT` | 6 | 0 / 0 / 0 |

Total: **7 Surefire + 134 Failsafe = 141 passing tests**, `BUILD SUCCESS`.
ShipperOrderLifecycleIT includes all 38 existing cases and six new invocations.
The two database deadlock errors in the log are expected characterization evidence,
not test errors. No full `clean verify` result is claimed by this focused verification.

Evidence is in `.work-ordops-focused.log` (ignored local artifact) and the corresponding
`target/surefire-reports` / `target/failsafe-reports` XML/text files. Runtime SQL and
PIDs are also embedded in the ShipperOrderLifecycleIT XML's `system-out`.

`git diff --check` passes, including a separate whitespace check for this initially
untracked report. `git status -sb` shows only the modified Order integration test
and this new report on the requested branch. HEAD remains the baseline above.
No commit, push, PR or merge was performed.
