# ORD-03 Vendor Order Lifecycle completion report

Owner: TD. Reviewer: QD. Priority: P0. Dependencies: ORD-01, ORD-02, PAY-01.
Branch: `feature/ord-03-vendor-workflow`.
Initial HEAD and local origin/develop: `27aab100acd0a64bd54ed0f6cb867467a3eff09e`.
The hard gate passed before edits; the initial worktree was clean. No Git publishing
or index mutation was performed.

## Audit and reconciliation

Audited Order/OrderItem/history/versions/repositories/lifecycle policy, ORD-02 read
DTOs/controller/templates/tests, COD initialization/collection and payment facts,
CHK-02 placement/idempotency/cart cleanup, inventory locks/decrease/restore,
shop ownership/restrictions, principal identity/security, migrations and contracts.
The original Master Plan document is absent; the supplied task and existing
`order-contracts.md` provide the relevant acceptance/guard rules.

- OrderStatus remains NEW, CONFIRMED, PICKED_UP, SHIPPING, DELIVERED, CANCELLED,
  RETURN_REQUESTED, RETURNED, REFUNDED. No READY status exists or is required.
- Ready is explicitly a no-status-change preparation action: persist existing
  ready_at while remaining CONFIRMED. No self-transition or fake history event.
- Existing Order already has ready_at, inventory_released_at and @Version; existing
  Flyway schema supports every added operation. No migration is necessary.
- Order.shopId is a persisted scalar reference to Shop.id. Shop.ownerId references
  the persisted account, with a unique one-shop-per-owner constraint.
- CurrentAccountIdProvider reads the authenticated UteExpressPrincipal user ID;
  active account validation is performed through AccountIdentityService. Production
  JWT restoration additionally reloads persisted roles/account/token version.
- Existing shop query conventions require an APPROVED owned shop. ORD-03 retains
  this policy for reads and mutations, with locked restriction revalidation on writes.
- CHK-02 decreases stock from its checked/locked selected quantities in the placement
  transaction. It locks buyer -> cart -> products ascending -> shop/category evidence
  before creating a new Order/history/COD Payment and cleaning selected cart lines.
  Canonical same-hash replay returns before those mutable operations.
- InventoryService.restore already locks products ascending and rejects overflow;
  ORD-03 supplies persisted item quantities and the locked inventory release guard.
- Payment states remain UNPAID/PAID. There is no cancellation/refund payment state.
  PAY-01 initialization remains checkout-only; collection remains internal and SHIPPING-only.
- Shipping has configuration/quotes/vocabulary, with no Shipment or assignment persistence.
  Assignment resolution during cancellation is deferred to the subsequent Shipping tasks.

## HTTP/read model/UI

| Endpoint | Behavior |
| --- | --- |
| GET /vendor/orders | Paginated owned-shop JSON or HTML list |
| GET /vendor/orders/{id} | Owned-shop JSON or HTML detail, item/address snapshots, persisted timeline/payments |
| POST /vendor/orders/{id}/confirm | Version-checked NEW -> CONFIRMED |
| POST /vendor/orders/{id}/ready | Version-checked preparation timestamp; status remains CONFIRMED |
| POST /vendor/orders/{id}/cancel | Version-checked NEW/CONFIRMED -> CANCELLED with controlled reason |

Mutations accept JSON or application/x-www-form-urlencoded forms. JSON requests receive
an OK acknowledgement; form success redirects to vendor detail. Clients select JSON
errors using Accept: application/json; HTML requests reuse the existing order error view.
Validation and malformed IDs produce controlled errors. Unsupported mutation GETs return
405 and have no effects. Existing security config remains unchanged.

Reads require VENDOR and active persisted identity/owned approved shop. List/detail
queries are scoped by that server-derived shop ID, never client vendorId/shopId. Foreign
and missing orders share RESOURCE_NOT_FOUND/404. Limits: page >= 0, size 1..100, signed
JPA offset range; default page 0/size 20. Order: createdAt DESC then id DESC. Lists use
DB pagination and scoped counts. Detail uses a read-only REPEATABLE_READ transaction
to project coherent persisted status/history/payment facts. Absent facts stay absent.

Vendor DTOs reuse ORD-02 safe facts without calling its buyer ownership path. Vendor
detail adds version/readyAt; checkout keys/hashes, commission, history actor IDs and
payment attempt/provider references are excluded. Buyer and vendor access stay separate.

The navbar links vendors to the new list. Templates reuse layout/classes and ORD-02
presentation, escaping snapshots via th:text. NEW shows confirm/cancel; CONFIRMED shows
ready until ready_at exists, plus cancel; later states show no vendor actions. Server
guards remain authoritative. Thymeleaf forms retain CSRF inputs and version guards.

## Lifecycle/history

There is one production OrderLifecycleService bean: existing OrderPlacementService.
Its inherited shared orchestration now delegates vendor identity/ownership/guard effects
to VendorOrderAuthority. No second transition graph or independent status writer exists.
OrderAction/OrderTransitionPolicy are unchanged. Default unintegrated guards still deny.

CONFIRM preserves the existing normative payment guard. COD requires exactly one
persisted matching, unexpired UNPAID Payment with no paid_at. ONLINE permits multiple
ONLINE attempts with exactly one matching, unexpired PAID attempt with paid_at; earlier
unpaid/expired attempts do not prevent confirmation. This only validates existing facts;
no online payment creation/gateway is implemented. Missing evidence or mixed payment
methods conflict. Historical orders without Payment stay readable/cancellable but cannot
confirm; no method inference or backfill is performed.

Successful confirm/cancel persists exactly one OrderStatusHistory with authenticated
actor, source/target and the real server transition instant, and publishes the existing
minimal status event only after commit. Failed/rolled-back operations publish nothing.
Ready persists ready_at/updated_at and increments version, retaining CONFIRMED. The
contract explicitly excludes ready from status history/events because status is unchanged.

CONFIRMED -> DELIVERED, vendor pickup/shipping/delivery/returns/refunds/Ops/system
operations, invalid source states and stale/repeated versions are rejected.

## Cancellation/inventory/payment

Only NEW and CONFIRMED can be cancelled, including a prepared CONFIRMED order. Reason
must be OUT_OF_STOCK or UNABLE_TO_FULFILL; the guard maps it to safe server-defined
text, rather than copying unchecked browser text into history/events. Empty/unknown
reason codes are invalid. Client quantities and ownership fields have no authority.

The order lock and expected state/version prevent duplicate transitions. A non-null
inventory_released_at conflicts before restoration. Restore reads authoritative persisted
OrderItems, locks their products in ascending ID order, and restores their quantities
without requiring products to remain purchasable. It records inventory_released_at at
the same instant as cancelled_at/history. Missing/invalid inventory or overflow rolls
back all effects. Repeated cancellation never double-restocks. Cart is untouched.

Payment records are preserved byte-for-byte throughout confirm/ready/cancel. No second
Payment, PAID mutation, collection or refund occurs. Existing PAID records remain PAID
after cancellation; future payment/refund handling must establish its own verified rules.

## Concurrency/lock ordering

Mutations join the shared lifecycle REQUIRED transaction. Vendor guards are MANDATORY.
They acquire active vendor account -> owned Order, then:

- Confirm: lock/refresh Payment records ascending, then lock/refresh owned Shop.
- Cancel: restore locked products ascending, then lock/refresh owned Shop.
- Ready: lock/refresh owned Shop, then persist preparation without inventory/payment effects.

Order and Shop are refreshed under pessimistic write locks to reject stale managed
snapshots. Payment guard refreshes locked records too. Expected status/version and
@Version remain enforced; optimistic conflicts map to CONFLICT, including ready.
All successful writes wait for transaction commit. Shop ownership/restrictions are
rechecked under its lock. No cancellation path locks Shop before products, matching
checkout's inventory ordering. PAY-01 retains Order-before-Payment ordering. Checkout
inserts a new Order only after inventory checks; canonical replay takes no lifecycle lock.

PostgreSQL tests deliberately hold the persisted vendor account lock, require both
competing threads to be blocked in pg_stat_activity, then release it. Threads preload
Order before entering lifecycle to exercise stale persistence-context conflicts. Confirm,
ready, cancel and competing confirm/cancel each yield one winner and one CONFLICT,
with one appropriate history effect and no double-restock or payment mutation.

## Shipping integration boundary

No Shipment, assignment/reassignment, assignedShipperId, pickup, delivery completion,
SHIP-01/SHIP-02, collection HTTP endpoint, VNPAY or refund is implemented. Current
cancellation rejects PICKED_UP and every later state using authoritative Order status.

SHIP-01/SHIP-02 must lock Order before Shipment, verify current assigned shipper and
vendor ready_at, and atomically record actual pickup through the shared lifecycle.
They must add their action-specific lifecycle authorization, not bypass the vendor guard
or write status directly. Concurrent cancellation/pickup must serialize on that Order
lock. When assignment exists, cancellation must atomically resolve it inside the same
transaction. Physical pickup with Order still CONFIRMED cannot be distinguished by
this model and is not a supported integration. Delivery must collect exact COD while
SHIPPING before DELIVERED in the same authorized transaction, per PAY-01.

## Verification and scope review (before final review)

STATUS: READY FOR REVIEW.

Java: installed Adoptium 21.0.12.1; Maven wrapper pinned to 3.9.11. PostgreSQL: 17.6.
Focused initial verification: 46 Surefire and 63 PostgreSQL Failsafe tests passed,
zero failures/errors/skips. Three additional HTTP/read cases bring ORD-03 to 66 cases.
Relevant regressions passed: all 339 Surefire tests plus 272 selected PostgreSQL tests
(ORD-03, ORD-02, PAY-01, CHK-02, Order database/lifecycle, vendor products, shop
registration, checkout quote and Shipping configuration). Security/CSRF/UI/architecture
checks are included in Surefire. The final UI tests also assert intact Vietnamese labels.

Final command, with JAVA_HOME selecting Java 21:

```powershell
.\mvnw.cmd -Ppostgres-it clean verify
```

Final clean verification finished 2026-10-04 22:20:58 Asia/Saigon (03:41 elapsed):
339 Surefire + 411 Failsafe = 750 tests, zero failures, errors or skips. BUILD SUCCESS,
exit 0. All 66 ORD-03 PostgreSQL cases passed. Exact XML report totals were also checked.
Maven emitted a fork-JVM shutdown timeout diagnostic after passing tests/System.exit(0);
it forcibly terminated that fork and still returned BUILD SUCCESS/exit 0. This is a
test-process shutdown limitation, not a failed or skipped test; no ORD-03 product fix
or unrelated harness refactor is claimed.

Docker initially was stopped; it was started in the background. The earlier unavailable
Docker attempt is not counted as a passing test run. Final evidence uses real PostgreSQL.
Verification logs are `ord03-focused.log`, `ord03-regressions.log` and
`ord03-full-clean-verify.log` in the OS temporary directory; XML reports are under
ignored target/surefire-reports and target/failsafe-reports.

Self-review: no remaining High or Medium defects. The initial ready optimistic conflict
mapping and damaged new-label encoding were corrected and reverified. Low: the reported
test-fork shutdown diagnostic; future Shipping/assignment and refund integration are
documented scope boundaries, not implemented features. git diff --check passed.
Reviewed all 28 tracked modifications and 11 expected untracked files; no unexpected
files or staged changes. The 19 pre-existing test files change only by adding two
vendor-service mocks each; their existing assertions remain intact.

Existing non-database application tests mock both new persistent vendor services,
matching their established mocked-persistence test profile. Their assertions are unchanged.
Only ORD-03 production code/UI/contracts and these test-context declarations change.
No enum/schema/dependency/security configuration, Shipping workflow, payment mutation,
admin/governance implementation, generated artifact, IDE file, log or secret is added.
Build artifacts remain ignored; verification logs are in the OS temporary directory.

No branch switch, staging, commit, push, merge, rebase or PR action was performed.

## Final pre-commit review

STATUS: READY TO COMMIT.

Reviewed the actual contents of all 28 modified tracked files and 11 untracked files,
including the pre-ORD-03 contracts and migration history. One Medium correctness
defect was found: the confirmation guard incorrectly rejected valid ONLINE retries
because it required exactly one Payment row for every method. The existing normative
contract and partial unique PAID-per-order index permit multiple ONLINE attempts.
The minimum production fix is in PaymentReadService: retain the exact single-record
COD guard, while allowing all-ONLINE attempts with one valid matching paid winner.
Missing evidence, mixed methods and invalid paid evidence still conflict. Six new
PostgreSQL cases prove successful pending/expired retries and rejection of mismatched,
expired, missing-paid-at and mixed-method evidence, without changing Payment or stock.
Both valid retry cases reproduced CONFLICT before the fix and pass afterward.

No new persisted field or migration is needed: ready_at and inventory_released_at
already exist in both the HEAD Order mapping and the original ORD-01 migration,
V20260923090000__td_ord01_domain_core.sql, lines 23 and 26. Both are nullable TIMESTAMPTZ
columns with no default; historical nulls are compatible. PostgreSQL tests run the same
17 Flyway migrations, and production/default Hibernate ddl-auto remains validate.
The HEAD contract explicitly defines ready as a no-status-change action. Only
CONFIRMED with absent ready_at and the current version may prepare. Server Clock
supplies the timestamp; no history/event is synthesized. Prepared CONFIRMED
cancellation remains permitted until actual pickup is atomically represented by
PICKED_UP. Future Shipping must select CONFIRMED AND ready_at IS NOT NULL, verify
assignment under its lock, and use the lifecycle authority for pickup.

The cross-module lock audit found no new reverse-order cycle with current checkout
or COD collection: products precede Shop in both checkout and cancellation; checkout
creates a distinct new Order, and replay does not lock an existing Order. COD keeps
Order before Payment. Approval's Shop -> owner update applies only to PENDING shops,
which vendor operations reject. A same-actor moderation/audit concern was tested in
two real concurrent PostgreSQL transactions. The installed Hibernate 7.4.5 PostgreSQL
dialect emits FOR NO KEY UPDATE for ordinary PESSIMISTIC_WRITE locks, allowing audit
foreign-key KEY SHARE checks while serializing vendor/account updates. The added
test holds Shop, observes cancellation waiting in pg_stat_activity, then executes the
real moderation/audit service. Moderation commits; cancellation rejects through stale
version or refreshed restriction checks, rolling back stock/status/history together.
No production lock or governance code was changed for this check. Its initial assertion
expected only ACCESS_DENIED; it now also accepts the legitimate stale-version CONFLICT,
while still failing on any database deadlock or unexpected exception.

The four original concurrency cases use independent worker transactions and observe
both blocked in PostgreSQL before releasing the account lock. Preloaded Order entities
exercise stale persistence contexts. Confirm/ready/cancel and confirm-versus-cancel
produce one success and one conflict, with exact version/history/stock effects.
Cancellation failure and outer rollback tests inspect committed database state.

Every existing HTTP fixture adds exactly these two declarations:

```java
    @MockitoBean com.uteexpress.order.service.VendorOrderService vendorOrderService;
    @MockitoBean com.uteexpress.order.service.VendorOrderAuthority vendorOrderAuthority;
```

The no-database test profile excludes DataSource/JPA/Flyway; these declarations supply
the newly discovered persistence services, matching the already mocked placement/buyer
services. For each of the 19 files, removing only those two lines reproduces its entire
HEAD contents exactly. No existing assertion, security behavior or lifecycle test is
weakened. The PostgreSQL ORD-03/CHK-02/ORD-02/PAY-01 suites use real production services.
Verified contexts: FoundationHttpTest; AddressControllerTest; ProfileControllerTest;
PublicCatalogControllerTest; VendorProductControllerTest; AccountGovernanceWebTest;
CategoryWebTest; ModerationWebTest; OpsDashboardTest; ShopApprovalWebTest;
EmailVerificationControllerTest; PasswordResetControllerTest; RegistrationControllerTest;
AuthenticationWebTest; CsrfCookieWebTest; SecurityFoundationTest; ShippingConfigWebTest;
ShopRegistrationControllerTest; UiLayoutTest.

| Final review gate | Surefire | Failsafe | Failures / errors / skips | Exit |
| --- | ---: | ---: | --- | ---: |
| Focused lifecycle/payment/architecture + VendorOrderIT | 46 | 73 | 0 / 0 / 0 | 0 |
| Relevant regressions, all unit suites + nine PostgreSQL suites | 339 | 279 | 0 / 0 / 0 | 0 |
| Java 21 -Ppostgres-it clean verify | 339 | 418 | 0 / 0 / 0 | 0 |

Full clean verify finished 2026-10-04 23:02:01 Asia/Saigon, 03:37 elapsed, BUILD SUCCESS.
Independently parsed 56 Surefire and 29 Failsafe XML reports: 757 tests overall,
zero failures/errors/skips. VendorOrderIT contains 73 cases. The existing fork shutdown
timeout diagnostic appeared after successful tests; Maven returned exit 0 and Failsafe
reports no timeout or flakes. Logs remain in the OS temporary directory as
ord03-review-focused.log, ord03-review-regressions.log and
ord03-review-full-clean-verify.log. The earlier reproduction logs are diagnostic
evidence, not passing gates. Git diff --check and untracked UTF-8/whitespace checks pass.

Final findings: no remaining High or Medium defect. Low coverage gaps: no dedicated
CONFIRMED-before-ready cancellation case (the current CONFIRMED case prepares first),
and no dedicated cancellation case preserving an already-PAID payment. The shared
cancellation path allows both and does not call Payment. The fork shutdown diagnostic
remains a Low test-harness limitation. No low-value refactor was performed.

Final scope remains 28 modified tracked files and 11 expected untracked files, zero
staged. No enum/schema/configuration/security change, Shipment, assignment, pickup,
shipping/delivery workflow, COD collection mutation, VNPAY, refund or unrelated
governance implementation is included. No generated/IDE files, logs or secrets are
added. Branch, HEAD and origin/develop remain at the expected review base. No Git
publishing, index mutation or branch operation was performed.
