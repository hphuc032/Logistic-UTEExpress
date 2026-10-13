# RETURN-01 Phase 1 implementation

Buyer requests now create persistent PENDING return evidence and transition a delivered
Order through the existing lifecycle writer. Vendors can approve without an Order
mutation or reject atomically back to DELIVERED while preserving the original delivery
time. Exactly one request is retained per Order, including after rejection.

Branch: `feature/return-01-management`. Base/HEAD:
`fc78b41781df852a71a4a6a7abeca50bb2eb9146`.
Implementation remains uncommitted. No branch switch, commit, push, merge or rebase.

## API and UI

| Route | Representation and input |
| --- | --- |
| GET /orders/{id}/return | Buyer JSON/HTML owned evidence and current eligibility/version |
| POST /orders/{id}/return | JSON/form: expectedVersion, reason |
| GET /vendor/orders/{id}/return | Vendor JSON/HTML shop-owned evidence and versions |
| POST /vendor/orders/{id}/return/approve | JSON/form: expectedVersion, expectedRequestVersion; no reason |
| POST /vendor/orders/{id}/return/reject | JSON/form: expectedVersion, expectedRequestVersion, reason |

Existing Order detail pages link to the return pages. Forms include CSRF through
Thymeleaf, server-provided versions and required bounded reason fields; successful
forms redirect to the return page. Reasons are escaped, historical evidence remains
visible after rejection, and approval never displays receipt/refund action controls.
Return times display in Asia/Ho_Chi_Minh with an explicit Vietnam-time label.

The new /orders/*/return route has an explicit USER/VENDOR security matcher; existing
Order/payment route authorization and error negotiation remain unchanged.
Controllers and lifecycle methods retain method authorization. Buyer USER/VENDOR
and vendor VENDOR are rechecked against persisted identity; vendor ownership is
resolved through the approved owned Shop contract. Foreign/missing selections share
safe 404s. Unknown JSON fields and unexpected/duplicate form/query fields reject.
No browser actor, owner, Shop, status, receipt flag or timestamp supplies authority.
Both HTTP and internal commands validate required/nonnegative versions and reason
limits. JSON errors use existing shared advice; HTML errors use order/error.

## Persistence and schema decisions

Migration: `V20261013090000__td_return01_management.sql`; only this new migration
adds `uteexpress.return_requests`. Historical migrations are unchanged.

- BIGINT identity PK and unconditional UNIQUE(order_id).
- requested_by FK to Users and composite (order_id, requested_by) FK to
  Orders(id, buyer_id), preserving database-level requester/Order association.
- decided_by nullable FK to Users; no cascading historical deletion.
- VARCHAR(16) status CHECK: PENDING/APPROVED/REJECTED.
- reason and decision_note VARCHAR(1000); database nonblank checks.
- requested_at, decision_at, created_at and updated_at use TIMESTAMPTZ/Instant.
- PENDING has no decision evidence; decisions require actor/time; APPROVED has no
  decision note; REJECTED has a nonblank note. Creation/decision time checks prevent
  evidence predating the request and require updated_at to match the recorded decision.
- version BIGINT NOT NULL DEFAULT 0 with @Version Long and nonnegative check.
- received_at/restockable are nullable and constrained to NULL in Phase 1.
  Their presence follows E2 and does not claim known receipt or restockability.
  Future receipt/status expansion requires a separate owner-approved migration.

Buyer request and vendor rejection reasons are required and at most 1000 characters
before stripping. Approval accepts no reason. These implement the accepted audit's
proposal using the existing 1000-character validation convention.

**QD review remains required before merge:** migration uniqueness and ordering versus
current develop, the additional ReturnRequest version, text limits, composite FK and
evidence checks. The latest migration in the audited checkout was 20261008090000;
no live remote refresh or external QD approval was performed.

## Lifecycle and transactions

OrderPlacementService remains the sole production lifecycle bean, extending the
shared OrderLifecycleServiceImpl writer. requestReturn/approveReturn/rejectReturn
use ReturnOrderAuthority, never VendorOrderAuthority's cancellation branch.

Every public return write rejects a pre-existing transaction before acquiring resource
locks and uses its own TransactionTemplate. REQUIRES_NEW is not used. Lock order:

`owned Order -> existing ReturnRequest -> active Account -> owned approved Shop (vendor)`

Initial creation serializes on the parent Order because an absent ReturnRequest cannot
be row-locked. Locked Orders/evidence are refreshed; expected Order/request versions,
persisted roles, active account and vendor Shop authority are checked inside the write
transaction. No Account/Shop lock is held while waiting for Order.

The request window compares the actual server UTC instant against
`delivered_at <= now <= delivered_at + 168 hours` before timestamp rounding.
NULL/future delivery, another request, wrong state or stale version conflicts.
Requested timestamps and lifecycle timestamps share PostgreSQL microsecond precision.

| Operation | Return evidence | Order effects |
| --- | --- | --- |
| Request | Insert PENDING, requested actor/time/reason, version 0 | RETURN_REQUESTED; version +1, exactly one history row/event |
| Approve | APPROVED, decision actor/time, request version +1 | No status/version/history/event change |
| Reject | REJECTED, actor/time/note; retain original request facts, version +1 | DELIVERED; version +1, exactly one history row/event; original delivered_at retained |

History/evidence persist within one transaction. Existing after-commit publication
applies; a failure including beforeCommit rolls all writes back and publishes no event.
Events retain the existing delivery guarantees; no outbox/exactly-once delivery claim.
History/events use fixed "Buyer requested return" and "Vendor rejected return" reasons.
Full request/rejection text stays in the owned ReturnRequest projection, preventing
free text containing payment/address details from entering general lifecycle events.

## Interacting lock-path review

- Existing Vendor lifecycle locks Order before Account, then Shipment/effect resources.
  Return operations skip every cancellation effect.
- Shipping locks Order -> Shipment -> assigned Account, then Payment where required.
  Both schedules against return use the same Order serialization point.
- Role governance locks global Role -> Account and does not then acquire Order.
  Returns never acquire a global Role lock after Account.
- Account restrictions acquire Account, and Shop suspension acquires Shop; neither
  acquires an existing Order afterward. Tests cover restrictions before/after return.
- Shop approval locks a PENDING Shop and grants its owner's role; return decisions
  require an APPROVED Shop. A pending Shop fails the initial eligibility selection.
- Checkout locks buyer/cart/products/Shop/Voucher then creates a new Order. Its replay
  query does not acquire an existing Order write lock. Return has no product, cart,
  voucher, Payment or Shipment lock/effect path.
- Return APIs reject outer transactions, preventing coordinators from introducing
  Account/Shop -> existing Order inversions.

The previously stale Account-first vendor paragraph in order-contracts.md is corrected.
The seed manifest now names RETURN-01 instead of ORD-04 for return fixtures.

## Verification

Java 21.0.12.1 and Maven Wrapper 3.9.11; PostgreSQL 17.6/Testcontainers.

| Final gate | Surefire | Failsafe | Total | Failures | Errors | Skips | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Focused | 63 | 87 | 150 | 0 | 0 | 0 | 0 |
| Interacting regressions | 78 | 523 | 601 | 0 | 0 | 0 | 0 |
| Non-superuser migration probe | 7 | 1 | 8 | 0 | 0 | 0 | 0 |
| Full clean verify | 423 | 842 | 1265 | 0 | 0 | 0 | 0 |

Commands (JAVA_HOME points to the installed Java 21 JDK):

```powershell
.\mvnw.cmd -Ppostgres-it -Dtest=ArchitectureTest,OrderContractTest,OrderDomainCoreTest,SecurityFoundationTest -Dit.test=ReturnManagementIT verify
.\mvnw.cmd -Ppostgres-it -Dtest=ArchitectureTest,OrderContractTest,OrderDomainCoreTest,SecurityFoundationTest,AuthenticationWebTest,CsrfCookieWebTest,UiLayoutTest -Dit.test=ReturnManagementIT,BuyerOrderIT,VendorOrderIT,ShipperOrderLifecycleIT,OpsOrderCancellationIT,CodPaymentIT,ShipmentAssignmentIT,ShipmentFulfillmentIT,ShipperFulfillmentIT,RoleGovernanceIT,PlaceOrderIT,VendorVoucherIT,DatabaseBaselineIT,OrderDatabaseIT clean verify
.\mvnw.cmd -Ppostgres-it -Dtest=ArchitectureTest -Dit.test=ProductPromotionIT#nonSuperuserDatabaseOwnerRunsAllMigrationsAndInstallsExtension verify
.\mvnw.cmd -Ppostgres-it clean verify
```

Logs: `.work-return01-focused.log`, `.work-return01-regression.log`,
`.work-return01-full.log` (ignored workspace verification artifacts).
Final full `-Ppostgres-it clean verify`: BUILD SUCCESS, exit 0; completed
2026-10-13 12:04:48 +07:00, elapsed 6:18. Fresh Surefire/Failsafe XML totals agree
with the Maven summaries. ReturnManagementIT ran 87 cases, zero failures/errors/skips.
The harness printed its 30-second fork-shutdown message after System.exit(0);
Failsafe reports timeout=false and zero failures/errors/skips; Maven exited 0.
The first full run had 423 passing Surefire tests and 842 Failsafe tests with 1 failure,
0 errors/0 skips, exit 1: ProductPromotionIT's non-superuser migration probe expected
20 migrations instead of 21. Its expected count was updated for RETURN-01 while retaining
its fresh-database, validation, second-migration and extension/privilege assertions.

Earlier development gates found test compilation errors, a 77-case focused run with
0 failures/28 errors from transaction-proxy stubbing and invalid fixtures, and a
77-case run with 1 failure/0 errors from the concurrent Shop restriction expectation.
Those were corrected. The initial regression gate had 78 Surefire tests passing and
523 Failsafe tests with 6 failures/0 errors/0 skips, exit 1: the broad /orders route rule
changed existing HTML denial/unsupported COD-route responses. Narrowing the matcher
to the new return route restored those contracts; the final gates above passed.

The new PostgreSQL suite uses PostgreSQL 17.6, real lifecycle delivery and COD/Shipment
setup, persisted voucher usage, fixed server Clock, immutable state snapshots and
actual committed database effects. Only NEW fixture creation and deliberately invalid
state/schema fixtures use direct persistence/SQL; those are not production lifecycle
fallbacks. Real Order/Shipping/Payment APIs create the delivered source state.

Coverage includes ownership and foreign-resource isolation, persisted role/account/Shop
eligibility, exact inclusive nanosecond deadline boundaries, NULL delivery, one request
including after rejection, approval/rejection rules, both expected versions, forged
fields, JWT cookies, CSRF, JSON/HTML errors, rendered form round trips and escaping,
database FK/unique/check constraints, Flyway validation/no pending rerun, history failure,
failure after evidence flush and failure at commit after history/event registration.

Concurrency uses independent transactions, bounded latches/futures and backend PIDs.
pg_stat_activity shows a Lock waiter and pg_blocking_pids identifies the holder; worker
SQL must reference the expected Order/Account/Shop. No delay determines race ordering.
Schedules cover duplicate requests, a first request rolling back, approve/reject in
both winning orders, earlier/later Account/role/Shop restrictions, and Shipping evidence
locking in both orders. Deadlocks/timeouts cannot be reported as successful outcomes.

Payment, Shipment, Products, Vouchers, VoucherUsage, OrderItems, Shipment audit and
assignment-history snapshots remain unchanged by successful return operations and
failed return transactions. Existing HTTP test fixtures add only mocks for the new
persistence-dependent services; existing assertions remain unchanged.

## Remaining boundaries

Final self-review: `git diff --check` passes; no staged files; branch/HEAD unchanged.
Tracked historical migrations and existing Shipping/Fulfillment/Payment/Catalog/
Promotion production code, including VendorOrderAuthority, have no diff.
All 48 changed paths match the complete inventory below. No shared DB or Git
publishing operation was performed.

Phase 1 implements no RECEIVE_RETURN or COMPLETE_REFUND and invokes no refund
processing, stock restoration, voucher release, Payment mutation or Shipment transition.
APPROVED is decision evidence only. There is no trusted physical receipt verifier;
that contract remains a prerequisite for a later phase.

Concurrent Shop restriction may produce either ACCESS_DENIED after refreshed eligibility
or CONFLICT from the existing optimistic guard before refresh. Both deny with no return
writes. QD schema approval remains pending; no shared database migration or merge occurred.

## Changed files

48 changed files: 23 production/schema/template files, 21 test files, 4 documentation files.
The 19 existing HTTP fixture changes add only two service mocks each.

- `docs/DATABASE_SCHEMA.md`
- `docs/RETURN-01-phase1-report.md`
- `docs/SEED_MANIFEST.md`
- `docs/order-contracts.md`
- `src/main/java/com/uteexpress/order/controller/BuyerReturnController.java`
- `src/main/java/com/uteexpress/order/controller/ReturnControllerSupport.java`
- `src/main/java/com/uteexpress/order/controller/VendorReturnController.java`
- `src/main/java/com/uteexpress/order/dto/RequestReturnCommand.java`
- `src/main/java/com/uteexpress/order/dto/ReturnDecisionCommand.java`
- `src/main/java/com/uteexpress/order/dto/ReturnDecisionInput.java`
- `src/main/java/com/uteexpress/order/dto/ReturnManagementView.java`
- `src/main/java/com/uteexpress/order/dto/ReturnRequestInput.java`
- `src/main/java/com/uteexpress/order/dto/ReturnRequestStatus.java`
- `src/main/java/com/uteexpress/order/dto/ReturnRequestView.java`
- `src/main/java/com/uteexpress/order/entity/ReturnRequest.java`
- `src/main/java/com/uteexpress/order/repository/OrderRepository.java`
- `src/main/java/com/uteexpress/order/repository/ReturnRequestRepository.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleService.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleServiceImpl.java`
- `src/main/java/com/uteexpress/order/service/OrderPlacementService.java`
- `src/main/java/com/uteexpress/order/service/ReturnOrderAuthority.java`
- `src/main/java/com/uteexpress/order/service/ReturnQueryService.java`
- `src/main/java/com/uteexpress/security/SecurityConfig.java`
- `src/main/resources/db/migration/V20261013090000__td_return01_management.sql`
- `src/main/resources/templates/order/detail.html`
- `src/main/resources/templates/order/return.html`
- `src/main/resources/templates/vendor/orders/detail.html`
- `src/test/java/com/uteexpress/FoundationHttpTest.java`
- `src/test/java/com/uteexpress/account/controller/AddressControllerTest.java`
- `src/test/java/com/uteexpress/account/controller/ProfileControllerTest.java`
- `src/test/java/com/uteexpress/catalog/controller/PublicCatalogControllerTest.java`
- `src/test/java/com/uteexpress/catalog/controller/VendorProductControllerTest.java`
- `src/test/java/com/uteexpress/governance/AccountGovernanceWebTest.java`
- `src/test/java/com/uteexpress/governance/CategoryWebTest.java`
- `src/test/java/com/uteexpress/governance/ModerationWebTest.java`
- `src/test/java/com/uteexpress/governance/OpsDashboardTest.java`
- `src/test/java/com/uteexpress/governance/ShopApprovalWebTest.java`
- `src/test/java/com/uteexpress/identity/controller/EmailVerificationControllerTest.java`
- `src/test/java/com/uteexpress/identity/controller/PasswordResetControllerTest.java`
- `src/test/java/com/uteexpress/identity/controller/RegistrationControllerTest.java`
- `src/test/java/com/uteexpress/order/ReturnManagementIT.java`
- `src/test/java/com/uteexpress/promotion/ProductPromotionIT.java`
- `src/test/java/com/uteexpress/security/AuthenticationWebTest.java`
- `src/test/java/com/uteexpress/security/CsrfCookieWebTest.java`
- `src/test/java/com/uteexpress/security/SecurityFoundationTest.java`
- `src/test/java/com/uteexpress/shipping/ShippingConfigWebTest.java`
- `src/test/java/com/uteexpress/shop/controller/ShopRegistrationControllerTest.java`
- `src/test/java/com/uteexpress/ui/UiLayoutTest.java`
