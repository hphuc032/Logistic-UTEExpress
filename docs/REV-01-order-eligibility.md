# REV-01 Order eligibility boundary

Owner: TD. Consumer: QD Review. Base: origin/develop
`1b3203de84c42326e93c01300a23bc17d9df9ddc`.

`OrderEligibilityQueryService.isCurrentBuyerEligibleToReview(Long orderItemId)` retains
the existing boolean signature. `OrderEligibilityService` implements it using
`CurrentAccountIdProvider` (SEC-01's authenticated `UteExpressPrincipal.userId` mapping),
persisted active account and USER/VENDOR roles, and a buyer-scoped item/Order query.
Display username/subject is never parsed. No buyer ID, Order ID, role or eligibility
boolean is accepted from HTTP. Invalid, missing and foreign item IDs all return false.
An owned item is eligible exactly when its current persisted Order is DELIVERED.
Unauthenticated/untrusted principals raise UNAUTHENTICATED; missing/inactive accounts
or persisted non-buyer roles raise ACCESS_DENIED. The read has no mutation, row lock,
reservation or promise about a later transaction.
Persistence dependencies use the existing ObjectProvider convention so HTTP/security
test contexts can omit the database. Invocation without persistence fails closed;
there is no in-memory eligibility or write fallback.

## QD write integration

Inject `OrderReviewWriteBoundary`. Invoke from a nontransactional coordinator, before
any Account/Review lock. The Order-owned coordinator rejects any existing transaction
with INVALID_REQUEST; it neither joins nor suspends an Account-held caller. It starts
one READ_COMMITTED transaction with `TransactionTemplate` and executes:

1. Resolve authenticated buyer; select owned parent Order ID without row locks.
2. Lock buyer-scoped Order using `OrderRepository.findByIdAndBuyerIdForUpdate`
   (`PESSIMISTIC_WRITE`) and refresh under the same lock.
3. Recheck ownership and item membership. Items are immutable placement snapshots;
   there is no production item reparent/update/delete workflow.
4. Lock active Account via HP's existing `AccountIdentityService` and reread persisted
   role eligibility. Account/role governance writers serialize through that Account.
5. Require DELIVERED under the Order lock; pass server-derived `ReviewableOrderItem`
   (item, Order, buyer, product IDs) to the synchronous callback.
6. QD validates/writes Review and Review media in this transaction. Commit only after
   the callback returns. Order and Account locks survive through transaction commit.

Lock acquisition order is **Order -> Account -> Review**. No global Role lock is
acquired after Account. SQL lock mode is verified against Hibernate/PostgreSQL in the
integration tests; PostgreSQL renders the JPA write lock as `FOR NO KEY UPDATE`.
This conflicts with Return's write lock on the same Order.

Example QD integration (illustrative; no QD implementation is supplied here):

```java
// Nontransactional entry point: do not annotate this coordinator @Transactional.
return orderReviewWriteBoundary.withEligibleCurrentBuyerItem(command.orderItemId(),
        facts -> reviewWriter.createInCurrentTransaction(facts, command));

// QD writer is a separate proxied bean, preferably @Transactional(MANDATORY).
// It derives buyer/product/orderItem from facts, locks Review after Account,
// validates the QD payload, persists, and lets all failures escape for rollback.
```

The callback must not use REQUIRES_NEW, NOT_SUPPORTED, another transaction manager,
async/background persistence, another Order lifecycle, or acquire another Order after
Account. Do not swallow persistence/validation errors. SQL exceptions are left to the
QD coordinator's error mapping after rollback. The boundary returns only after commit.
Successful callback results/facts returned outside it grant no authority for later writes.
The existing application disables OpenEntityManagerInView; the boundary also refreshes
Order before deciding eligibility.

Write errors: invalid/missing/foreign item -> RESOURCE_NOT_FOUND; owned Order with any
other status -> CONFLICT; unauthenticated -> UNAUTHENTICATED; invalid account/role ->
ACCESS_DENIED; existing transaction or null callback -> INVALID_REQUEST.
QD owns HTTP payload validation, uniqueness of review/order_item (including concurrent
duplicates), Review locks/schema, rating/text/media validation, API mapping and review
visibility after returns. None of those are implemented or changed by TD.

## Return synchronization and limits

The real Return lifecycle already rejects outer transactions and locks
Order -> existing ReturnRequest -> Account -> Shop until commit. No Return change is
needed: its leading Order lock serializes with the Review boundary. If Return commits
first, the waiting boundary observes RETURN_REQUESTED and rejects before calling QD.
If the boundary commits first, its callback finishes while Order is still DELIVERED;
Return can then commit. This does not prohibit a later Return or remove an earlier Review.
If Return rolls back, a waiting boundary can remain eligible. A rejected Return restores
DELIVERED and therefore restores review eligibility under the requested status rule.

Tests use PostgreSQL 17.6, separate transactions, latches, bounded waits/backend lock
timeouts, backend PIDs, `pg_blocking_pids` and worker SQL. Both ordering schedules use
the real Return request service. The test callback uses an existing fixture account's
profile field as a persistence probe, with a MANDATORY writer, to prove joined writes,
commit visibility and rollback; it does not create a Review table or simulate QD schema.
An additional blocked-Account test proves Order is already locked before Account and
that the new account status is checked after waiting.

This verifies the Order-owned serialization contract. Full Review-vs-Return creation,
duplicate Review constraints and rollback of actual Review/media rows remain pending
QD's writer/schema integration. QD must confirm callback propagation/lock discipline
and add those end-to-end PostgreSQL tests before claiming the complete Review flow safe.

## TD changed-file manifest

- `src/main/java/com/uteexpress/order/service/OrderEligibilityQueryService.java`
- `src/main/java/com/uteexpress/order/service/OrderEligibilityService.java`
- `src/main/java/com/uteexpress/order/service/OrderReviewWriteBoundary.java`
- `src/main/java/com/uteexpress/order/dto/ReviewableOrderItem.java`
- `src/main/java/com/uteexpress/order/repository/OrderRepository.java`
- `src/main/java/com/uteexpress/order/repository/OrderItemRepository.java`
- `src/test/java/com/uteexpress/order/service/OrderEligibilityServiceTest.java`
- `src/test/java/com/uteexpress/order/OrderEligibilityIT.java`
- `docs/order-contracts.md`
- `docs/REV-01-order-eligibility.md`

No QD Review schema, validation, service or migration files are changed. The preexisting
untracked `tatus --short` is preserved and excluded from staging.

## Verification

Java 21.0.12.1, Maven wrapper 3.9.11, PostgreSQL 17.6/Testcontainers.

Scoped command:

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=OrderEligibilityServiceTest,ArchitectureTest' '-Dit.test=OrderEligibilityIT,ReturnManagementIT' verify
```

Result: exit 0; Surefire 21 PASS (14 eligibility + 7 architecture); Failsafe 175 PASS
(26 eligibility + 149 Return); 0 failures, 0 errors, 0 skipped. Local raw evidence:
`.work-rev01-focused.log`, including the observed PostgreSQL waiter/holder PIDs and SQL.

Full regression command:

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
```

Result: BUILD SUCCESS, exit 0; Surefire 611 PASS (66 report files); Failsafe 1080 PASS
(51 report files); total 1691 PASS, 0 failures, 0 errors, 0 skipped. The Failsafe summary
records 1080 completed and timeout=false. Java/test compilation and packaging passed.
Local raw evidence: `.work-rev01-full.log` and XML under `target/*-reports/`.
`git diff --cached --check` passes for the complete ten-file change.

Remaining diagnostics: Mockito dynamic-agent warnings, Spring AuthenticationProvider
configuration warning, and Hikari closed-connection/connection-refused messages from
stopped Testcontainers databases. The full run also emitted
`Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).`
before Maven completed successfully. This teardown diagnostic is retained, not treated
as a test failure or a clean-shutdown claim; its root cause is not established here.
No unrelated test infrastructure or QD files were changed to suppress it.
