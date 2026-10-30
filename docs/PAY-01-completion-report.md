# PAY-01 COD completion report

Owner: TD. Reviewer: QD. Sprint S2, P0. Branch: `feature/pay-01-cod`.
Initial HEAD and local origin/develop: `000cdfcf6e2aefd2c441c5f2ed6316732e30e0c0`.
Initial branch, HEAD, remote-tracking ref, clean worktree and diff check all passed.

## Master Plan alignment and audit

The authoritative Master Plan excerpt supplied for this task specifies CHK-02 as
PAY-01's only dependency. ORD-03 depends on ORD-01/ORD-02/PAY-01; SHIP-01 follows
ORD-03/SHIP-00/ADMIN-02; SHIP-02 follows SHIP-01/PAY-01. Integration order is Checkout,
Order and COD, then vendor lifecycle, then assignment/delivery. No separate Master
Plan file was found in this checkout; the supplied excerpt is the sequencing source.
The earlier decision to wait for Shipment persistence is superseded.

Audited Payment, repository/read view, ORD-01 schema/domain tests, Order totals and
lifecycle/history, CHK-02 placement, ORD-02 reads/tests, shipping rates/quotes/tests,
security principal/roles/errors, Flyway constraints and locking conventions.

- Existing Payment already stores COD/ONLINE, UNPAID/PAID, NUMERIC(19,2) amount,
  attempt/provider keys and timestamps. There is no collection actor column.
- CHK-02 supported COD selection but created no payment. Order has no payment method.
  Missing historical attempts cannot establish COD. ORD-02 truthfully reads persisted
  attempts after ownership checks and displays an absence message for missing records.
- Shipping implements provider/rate configuration and quotes only. ShipmentStatus is
  vocabulary; no Shipment, assignment persistence or delivery operation exists.
- Lifecycle transition authorization remains fail-closed. PAY-01 adds no order transitions.
- Whole VND Money/OrderTotals use BigDecimal; grand_total is subtotal minus order
  discount plus shipping. Commission is not an additional buyer charge.
- REQUIRED/MANDATORY transactions, pessimistic locks, order versions, and the unique
  partial PAID-per-order index are existing conventions. Payment has no version field.

## Implementation and payment initialization

Reuse PaymentRecordView and PaymentReadService; no duplicate PaymentView or ORD-02
display rewrite. Add PaymentService, CodPaymentService, CodCollectionCommand and a
read-only PaymentController. Extend Payment with a validation-before-mutation COD
transition and PaymentRepository with locked scalar order facts, locked attempts,
and ownership lookup. Scalar SQL projections preserve the existing acyclic
Order -> Payment module direction and do not expose Order entities across modules.

After explicit COD selection and flushed NEW order/items/history creation,
OrderPlacementService calls initializeCodForNewOrder(orderId) in its existing transaction.
It locks the persisted order, reconstructs/validates persisted totals, and creates one
UNPAID COD attempt with authoritative grand_total and a deterministic server attempt
key. Cart cleanup follows. Failure rolls back stock/order/history/payment/cart together.
Initialization requires NEW and no existing attempts; duplicate initialization conflicts.

Checkout replay returns before initialization and preserves every existing payment
fact. Historical replay with no payment stays absent. Reads and collection never
backfill or infer COD. Historical remediation requires separately verified payment
method evidence; no mass migration or inferred backfill is performed.

No migration is required: existing Payment.method represents COD versus ONLINE,
existing timestamps record payment success, and existing uniqueness/FKs remain intact.
ONLINE stays rejected by placement; no VNPAY, refund or additional payment states.

## Collection, money and concurrency

Internal boundary: PaymentService.collectCod(CodCollectionCommand(orderId, collectedAmount)).
The command carries only order selection and the claimed collection, never a status,
authoritative total, buyer/vendor/shipper identity or assignment boolean.

Both internal mutations require an existing transaction (MANDATORY). Collection locks
the stable order row first, then attempts by ascending ID. Native locked order facts
are read from persisted orders; no caller-supplied totals are accepted. The payment
entity is explicitly refreshed under its lock so a caller's old managed UNPAID snapshot
cannot report another successful collection after a concurrent commit.

Collection eligibility is SHIPPING only, exactly one persisted COD attempt,
UNPAID, no paid_at or expired_at, valid persisted order totals, and payment amount equal
to grand_total. Missing/ambiguous/ONLINE/expired/previously PAID records reject safely.
Both collected and stored payment amounts must exactly equal authoritative grand_total.
Money.requireAmount rejects negative/fractional/overflow/null evidence; insignificant
BigDecimal scale is ignored. Wrong valid amounts return CONFLICT and invalid amounts
return VALIDATION_FAILED. All domain validations precede mutation.

Success stores PAID, paid_at and updated_at from the server Clock at PostgreSQL
microsecond precision. Existing amount/key/provider facts are preserved. No collector
or provider audit information is invented. Repeat collection returns CONFLICT; no
timestamp/amount drift or duplicate successful effect. Locks serialize concurrent
collection across processes, with the existing PAID-per-order unique index retained
as the database invariant. The result is provisional until the caller commits.

## Authorization, HTTP and UI

No public collection or initialization endpoint exists. ROLE_SHIPPER is never treated
as assignment. The internal operation is trusted application integration, not a
complete end-user authorization boundary. SHIP-02 must verify assignment first.

PaymentController exposes GET /orders/{orderId}/payments only. JSON returns the
existing safe PaymentRecordView list; HTML authorizes then redirects to the existing
ORD-02 payment section. Ownership comes from CurrentAccountIdProvider's persisted
principal, with USER/VENDOR method authorization matching ORD-02's buyer policy.
Foreign and missing orders share RESOURCE_NOT_FOUND; nonbuyer roles are denied even
for an owned order. Provider references and attempt keys remain private. POST/PUT/PATCH
are unsupported, and query fields cannot force status, amount or identity. Existing
shared JSON errors and HTML order error view are reused; no security configuration changes.

ORD-02 continues to show only persisted payment facts. New checkout therefore displays
COD/UNPAID, and successful internal collection displays PAID. Historical absent payments
retain the truthful absence message. No new payment form or unrelated UI is added.

## SHIP-02 integration contract and limits

There is no production delivery-completion operation to guard. PAY-01 proves that wrong
collection cannot produce PAID and that it participates in an enclosing write transaction.
Test-only SQL fulfillment effects demonstrate transaction participation, not a production
delivery operation or assignment implementation.

Future SHIP-02 must:

1. Start one outer write transaction, lock Order, then lock/revalidate the current Shipment assignment
   against authenticated persisted shipper identity, including all fulfillment guards.
2. Coordinate Order -> Shipment -> Payment lock ordering and perform fulfillment/order
   changes through their existing owner boundaries. Audit any other lock-taking paths
   for compatible ordering when those paths are implemented.
3. Invoke PaymentService.collectCod(new CodCollectionCommand(orderId, collectedAmount))
   while Order is SHIPPING, before its SHIPPING -> DELIVERED change in that same
   transaction. A persisted DELIVERED order cannot collect unpaid COD after the fact.
4. Let wrong amount/payment state/authorization failures roll back all effects. Do not
   swallow payment errors, commit delivery separately, or retry a provisional success
   as though its outer transaction were already committed.

Assignment verification, pickup/shipping/delivery endpoints and end-to-end assigned
shipper enforcement are explicitly deferred to SHIP-01/SHIP-02. ORD-03, admin order
management, VNPAY and refunds are outside this change.

## Pre-review verification (historical)

All successful commands used the Maven wrapper and Java 21 from
`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` (Temurin 21.0.12.1).

| Gate | Surefire | Failsafe | Failures | Errors | Skipped | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Initial approved focused run | 43 | 131 | 0 | 0 | 0 | 0 |
| Updated relevant regressions | 129 | 236 | 0 | 0 | 0 | 0 |
| Full `-Ppostgres-it clean verify` | 339 | 344 | 0 | 0 | 0 | 0 |

Pre-review PAY-01-specific coverage is 12 CodPaymentTest plus 46 CodPaymentIT cases (58),
verified in both regression/full gates. The first focused run preceded the additional
cross-order collection case and strengthened checkout field tests, so its totals are
historical successful evidence, not the final suite size. Gate totals overlap and must
not be added together.

The updated regression unit suites are CodPaymentTest, ArchitectureTest,
OrderDomainCoreTest, OrderContractTest, MoneyContractTest, BuyerOrderServiceTest,
CheckoutRequestHashTest, PlaceOrderPageControllerTest, CheckoutQuoteServiceTest,
ShippingConfigWebTest, ShippingDemoActivationTest, SecurityFoundationTest,
JwtSecurityConfigurationTest, AuthenticationWebTest, CsrfCookieWebTest and
FoundationHttpTest. Executed PostgreSQL suites are CodPaymentIT (46), PlaceOrderIT
(55), BuyerOrderIT (34), OrderDatabaseIT (6), CheckoutQuoteIT (43), ShippingConfigIT
(9), CartActionsIT (33) and CartDatabaseIT (10). The full gate additionally executes
all existing suites, including Auth02AuthenticationIT and the remaining security,
identity, catalog, governance and account tests.

Full command: `.\mvnw.cmd -Ppostgres-it clean verify`. BUILD SUCCESS, exit 0,
03:48 min, finished 2026-10-03 20:11:58 +07:00. Independently parsed all 84 XML reports:
56 Surefire suites/339 cases and 28 Failsafe suites/344 cases, matching suite totals;
683 cases overall. Failsafe summary has zero failures/errors/skips/flakes and no
timeout. Flyway validated all 17 existing migrations and Hibernate schema validation
passed. The executable JAR was built. PostgreSQL 17.6 containers actually ran.

The fork emitted its existing Hikari/container shutdown warnings and a 30-second JVM
exit warning after tests; Maven still completed successfully with exit 0. These are
not counted as test failures. No live/deployed delivery or browser run is claimed.

Coverage includes initialization, exact/under/over/fractional/negative/overflow/null
amounts, persisted order versus payment drift, wrong-amount rollback, repeated and
concurrent collection, old managed payment snapshots, cross-order isolation, parent
FK and PAID uniqueness, caller transaction requirement and outer commit/rollback.
Concurrency deliberately holds the PostgreSQL order lock until both collectors are
observed waiting in pg_stat_activity, then proves one success and one CONFLICT.

HTTP cases cover server-owned buyer identity, USER/VENDOR ownership, denial of
SHIPPER/ADMIN/MANAGER reads, identical foreign/missing behavior for JSON and HTML,
guests, malformed IDs, no writable status/collection endpoint, ignored forged read
fields, persisted-only ORD-02 status and GET nonmutation. CHK-02 checks prove JSON
server-field rejection, HTML field whitelisting, ONLINE rejection, replay/concurrent
single-payment creation and rollback after payment flush plus cart cleanup.

Focused/regression logs reside in the system temporary directory (`pay01-focused.log`,
`pay01-regression.log`); the full gate ran directly in the terminal. Test/build reports
are under ignored target/, never staged or included as source changes.

The first sandboxed focused run compiled and passed 43 unit/architecture tests but
could not access Docker (three container-start errors). It is not a passing integration
gate. The focused run was restarted with approved Docker access; no test was weakened.

## Files and review boundary

Production: OrderPlacementService, Payment, PaymentRepository; new PaymentService,
CodPaymentService, CodCollectionCommand and PaymentController. Existing PaymentReadService,
PaymentRecordView, ORD-02 templates, migrations and security configuration are unchanged.

Tests: new CodPaymentTest/CodPaymentIT, updated PlaceOrderIT/BuyerOrderIT expectations,
and one PaymentService mock in each existing no-database HTTP fixture. Those fixture
mocks replace the new persistence service only; existing regression assertions remain.

Documentation: this report and the active order-contracts.md integration contract.
Prior completion report test totals are historical and are not reused as current proof.

No stage/commit/push/merge/rebase/branch switch/PR is authorized or performed.

## Implementation self-review and file audit (before final review)

All changed and untracked files were inspected. No remaining High/Medium/Low findings.
The payment refresh under row lock addresses the stale managed-entity race and is
verified by a real two-transaction test. No production delivery/assignment guard is
claimed; the future integration contract and legacy absence behavior are explicit.

The 19 existing no-database fixtures each add only one PaymentService mock:

- FoundationHttpTest
- account/controller/AddressControllerTest and ProfileControllerTest
- catalog/controller/PublicCatalogControllerTest and VendorProductControllerTest
- governance/AccountGovernanceWebTest, CategoryWebTest, ModerationWebTest,
  OpsDashboardTest and ShopApprovalWebTest
- identity/controller/EmailVerificationControllerTest, PasswordResetControllerTest
  and RegistrationControllerTest
- security/AuthenticationWebTest, CsrfCookieWebTest and SecurityFoundationTest
- shipping/ShippingConfigWebTest
- shop/controller/ShopRegistrationControllerTest
- ui/UiLayoutTest

Final scope: 25 tracked modifications and seven untracked files. The index is empty.
Final tracked diff: 25 files changed, 159 insertions, 13 deletions. Git diff stats omit these
seven inspected untracked files:

- docs/PAY-01-completion-report.md
- src/main/java/com/uteexpress/payment/controller/PaymentController.java
- src/main/java/com/uteexpress/payment/dto/CodCollectionCommand.java
- src/main/java/com/uteexpress/payment/service/CodPaymentService.java
- src/main/java/com/uteexpress/payment/service/PaymentService.java
- src/test/java/com/uteexpress/payment/CodPaymentIT.java
- src/test/java/com/uteexpress/payment/CodPaymentTest.java

git diff --check passes; all untracked files also pass trailing-whitespace inspection.
No Shipment/assignment persistence, SHIP-01/SHIP-02 implementation, ORD-03 transitions,
VNPAY, refunds, migrations, dependencies, unrelated admin/security production edits,
generated files, IDE files, logs or secrets are included in the diff or untracked set.
Required build output remains ignored under target/. No unexpected files were introduced.

## Final pre-commit review

One Medium correctness defect was found and fixed: accepting DELIVERED permitted
COD payment after delivery had already been persisted without full collection. COD
collection now requires SHIPPING. Future SHIP-02 can lock Order -> Shipment -> Payment,
verify assignment, collect COD, then transition delivery through lifecycle authority,
all in the same outer transaction. MANDATORY remains correct and unchanged.

The minimum fix changes CodPaymentService's status guard, PaymentService's integration
documentation, the matching order contract and this report. CodPaymentIT adds DELIVERED
to rejected states and coordinates successful collection before delivery in its outer
commit/rollback test. The failed-collection test retains SHIPPING while mutating an
outer transaction effect, so its failure still proves amount validation and rollback.
No Shipment/assignment/fulfillment implementation was added.

| Final gate | Surefire | Failsafe | Failures | Errors | Skipped | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Focused CodPaymentTest / CodPaymentIT | 12 | 47 | 0 | 0 | 0 | 0 |
| Relevant regressions, including all 19 changed HTTP fixtures | 213 | 237 | 0 | 0 | 0 | 0 |
| Full Java 21 `-Ppostgres-it clean verify` | 339 | 345 | 0 | 0 | 0 | 0 |

Full clean verification finished 2026-10-03 20:29:20 +07:00, BUILD SUCCESS, exit 0,
03:33 min. Independently parsed 56 Surefire and 28 Failsafe XML reports: 684 cases,
zero failures/errors/skips/flakes. Failsafe summary confirms no timeout. PostgreSQL
17.6 containers ran; all 17 migrations and Hibernate schema validation passed.
The increase from 683 is the additional DELIVERED rejection case. Earlier table
counts remain historical pre-review evidence. Final focused/regression logs are in
the system temporary directory as pay01-final-focused.log / pay01-final-regression.log.
The first sandboxed focused attempt could not access Docker; its approved rerun passed.

Each of the 19 fixture files was read and compared to HEAD after removing exactly
`    @MockitoBean com.uteexpress.payment.service.PaymentService paymentService;`.
Every remaining line equals HEAD: no assertion, security configuration or tested
behavior was removed or weakened. The no-database test profile excludes persistence;
this mock supplies the newly discovered PaymentController dependency. Real PAY-01
behavior is exercised by the unmocked PostgreSQL integration suites.

Final re-review found no remaining High/Medium defect. git diff --check and untracked
whitespace checks pass. Branch feature/pay-01-cod and HEAD
000cdfcf6e2aefd2c441c5f2ed6316732e30e0c0 remain unchanged: 25 modified tracked files,
seven untracked files, zero staged. No Git publishing or branch operation was performed.

STATUS: READY TO COMMIT.
