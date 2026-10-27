# INT-02 requirement-to-evidence matrix

Plan Task Board INT-02, I/I5–I8/L/M/N and final Q&A; actual file/hash in the report.
Frozen executable source `e6bc22decc20118015b48eae21a60e59abf49646`; latest approved develop 663fc915 integrated.
PASS means executed automation, not TD approval, release or manual browser acceptance.
Test IDs below retain the Plan's exact meanings; T48 is migration, not a generic E2E label.

| Plan ID / requirement | Actual suites / scenario | Result and boundary |
|---|---|---|
| T01–07 auth/activation/reset | AuthRegistrationIT, Auth03EmailVerificationIT, Auth02AuthenticationIT, Auth04PasswordResetIT; JWT tests | PASS; test mail, no real SMTP claim |
| T08–11 ownership/account | User01ProfileIT, User02AddressIT, SecurityHardeningIT, SecurityFoundationTest | PASS; principal/IDOR/CSRF |
| T12–19 Shop/catalog/cart | ShopApprovalIT, VendorProductIT, PublicCatalogIT, ProductDiscoveryIT, CartActionsIT, CartDatabaseIT | PASS; visibility and persisted owner cart |
| T20 foreign address/cart | CheckoutQuoteIT, PlaceOrderIT, CheckoutFlowIT | PASS; server buyer scope |
| T21 multiple Shops/invalid shipping | CheckoutQuoteIT, PlaceOrderIT | PASS; no silent split or mutation |
| T22 browser money tampering | CheckoutQuoteIT, PlaceOrderIT, Int02CommerceFlowIT | PASS; server snapshots/commission |
| T23 last stock concurrency | PlaceOrderIT.concurrentCompetingBuyersCannotOversell | PASS; real PG locks, no negative stock |
| T24 placement rollback | PlaceOrderIT, VoucherCheckoutIT | PASS; stock/cart/voucher/Order/Payment/history atomic |
| T25 checkout replay | PlaceOrderIT, Int02CommerceFlowIT | PASS; one Order, changed payload conflicts |
| T26–27 voucher/promo | VoucherCheckoutIT, ProductPromotionIT | PASS; quota and overlap concurrency |
| T28–29 Buyer/Vendor Order ownership | BuyerOrderIT, VendorOrderIT, Int02CommerceFlowIT | PASS; foreign read/mutation denied |
| T30 illegal states | OrderContractTest, ShipperOrderLifecycleIT, ShipperFulfillmentIT | PASS; lifecycle sole authority |
| T31 valid cancel releases once | OpsOrderCancellationIT, OperationsCancellationIT, ReturnToSenderIT | PASS; normal post-pickup cancel still denied, dedicated T44 receipt required |
| T32 exact COD atomicity | CodPaymentIT, ShipperFulfillmentIT, Int02CommerceFlowIT | PASS; wrong amount rolls back, Payment/Shipment/Order commit together |
| T33–34 VNPAY P2 | Outside required current scope | DEFERRED; no gateway proof invented |
| T35 return eligibility/receipt | ReturnManagementIT, ReturnReceiptMigrationIT, Int02CommerceFlowIT | PASS; own delivered, 7-day window, one request |
| T36 review input/media | ReviewIntegrationIT, ReviewFoundationTest, LocalFileStorageServiceTest | PASS; escaped text, validation, restricted cleanup |
| T37 purchased item eligibility | ReviewIntegrationIT, OrderEligibilityIT | PASS; duplicate/foreign/not-delivered denied; Review/Return race |
| T38 full COD refund authorization/DB | ManualCodRefundIT, RefundLedgerMigrationIT, Int02CommerceFlowIT | PASS; Manager denied, receipt, amount, immutable replay and rollback |
| T39 favorites/recent | EngagementIT | PASS; dedup and bounded history |
| T40–41 roles/governance | AccountGovernanceIT, RoleGovernanceIT, SecurityHardeningIT, CommissionPolicyIT | PASS; role/owner/last-Admin/commission/audit |
| T42 category/shipping configuration | CategoryIT, ShippingConfigIT | PASS; used-category/rate/provider guards |
| T43 assignment/ownership | ShipmentAssignmentIT, ShipperOrderLifecycleIT | PASS; current account/assignee and pickup restrictions |
| T44 failure/retry/physical return | ShipmentFulfillmentIT, ReturnToSenderIT (16), Int02CommerceFlowIT RETURN_TO_SENDER | PASS; Order remains SHIPPING until Ops actual receipt; stock/quota once, replay, concurrency commit/rollback |
| T45 Vendor financial | VendorRevenueIT (46), RevenueServiceTest | PASS; payment/refund periods, original commission, unknown evidence, current Shop |
| T45 Admin financial | AdminRevenueIT (26) | PASS; global/Shop parity, no item fanout, exact successful Payment and Admin-only |
| T45 Ops/audit | OpsDashboardReadIT (28), OpsReportFilterTest | PASS; local dates, snapshot, filters/page/privacy, immutable reads |
| T45 Shipper terminal success | ReturnToSenderIT real metric paths; ShipperFulfillmentIT | PASS; 2/(2+3)=40.0, retry counts one, empty NULL, foreign excluded |
| T46 private STOMP authorization | NotificationSocketIT (29) | PASS; real socket, Origin/CSRF/destination/JWT controls |
| T47 transaction/offline/logout | NotificationRecoveryIT (7), NotificationIT, NotificationSchedulerIT, NotificationSocketIT, Node tests | PASS; same-TX inbox, no rollback hint, recovery/dedup, expiry/logout/lock/revocation |
| T48 fresh DB/migrations/constraints | DatabaseBaselineIT, ShipmentAttemptMigrationIT, ReturnReceiptMigrationIT, RefundLedgerMigrationIT, ReviewFoundationMigrationIT, NotificationMigrationIT, ReturnToSenderMigrationIT (2) | PASS; PG17.6/Hibernate, populated upgrade, old checksums/data, append-only receipt and repeat migrate 0 |
| T49 operational deployment | Int02HttpEnvironmentIT (5) plus existing config tests | PARTIAL; real isolated startup/HTTP PASS; full missing-ENV/upload restart/production deployment NOT RUN, QA-03/REL |
| T50 whole human E2E/three widths | Int02CommerceFlowIT (3), Int02HttpEnvironmentIT; auth suites | Automated paths PASS; manual browser 375/768/1440 NOT RUN due Computer Use policy; UI-02 S5/QA/release follow-up |
| INT-02 technical review gate | All current mandatory suites and F01–F04 resolutions | TECHNICALLY READY — PENDING TD REVIEW; not completed/released |

Final clean gate: 682 unit/architecture + 1268 PostgreSQL = 1950 Java;
12 Node separately; 15 external QA tooling tests separately. Zero failures/errors/skips/
flaky-rerun markers; Failsafe timeout=false. No overlapping baseline/focused runs summed.
Concurrency instrumentation invokes real production writers, records actual backend PIDs
and checks pg_blocking_pids; it does not choose a winner using sleeps.
