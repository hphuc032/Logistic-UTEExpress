# QA-02 test matrix — approved Buyer cancellation correction

Base develop: `798535b32ff81a011f34cfa01bbeb2d2dd0cb981`. Exact tested source HEAD: `b6c832785d87c3af10008a7c357389ef685f2123`.
Branch: `fix/q02-f05-buyer-cancellation`; worktree: `C:/Logistic-UTEExpress/.work/q02-f05-fix`.
Owner/executor: TD. HP retains final acceptance and merge authority.
Q02-F05: **FIXED, LOCAL REGRESSION VERIFIED**. QA-02 remains **IN PROGRESS**, pending
publication/exact-head CI, broader original-plan comparison and HP final acceptance.
No QA-02 COMPLETED, remote CI or merge is claimed.

Required full commands, executed in the fix worktree with Java 21:

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/notifications.test.cjs
git diff --check
```

Fresh full result: Maven exit **0**, Surefire **682** / 72 classes +
PostgreSQL Failsafe **1303** / 62 classes = **1985 Java tests**.
Node exit **0**, **12/12 PASS**. Zero failures/errors/skips/flakes/rerun markers;
Failsafe timeout=false, completed=1303. The success result attribute is `null` per the
installed Surefire API 3.5.6, failureMessage=nil. Focused counts overlap and are not added.
Environment: Windows PowerShell, Temurin Java 21.0.12.1+1 LTS, Maven Wrapper 3.9.11,
Spring Boot 4.1.1, PostgreSQL 17.6 Testcontainers on Docker Desktop Linux engine,
Node 22.11.0; JVM tests UTC, financial periods Asia/Ho_Chi_Minh.
Fresh/upgrade migration suites and Hibernate validate PASS; 27 fresh migrations through
`20261027100000`, populated history/checksum preservation and repeat migrate=0 verified.
No migration, financial formula, SQL projection or forbidden owner-module source changed.


All Java paths below are under `src/test/java/com/uteexpress/`. Counts come from fresh
full XML; focused subsets are never added. This is executed automated coverage, not
human/browser/release acceptance or proof every possible input has been tested.

| ID | Requirement | Executed evidence / discriminating check | Result |
|---|---|---|---|
| O01 | NEW/CONFIRMED/ready/PICKED_UP/SHIPPING/DELIVERED | order/VendorOrderIT; integration/Int02CommerceFlowIT DIRECT/RETRY | PASS |
| O02 | Owned USER NEW cancellation / foreign 404 | order/BuyerCancellationIT real JWT success; foreign/missing indistinguishable; separate Buyer authority | PASS, Q02-F05 fixed locally |
| O03 | Vendor/Admin/Manager/Shipper denied, anonymous/CSRF blocked | BuyerCancellationIT nonUserRoles/anonymousMissingAndInvalidCsrf; unchanged global security | PASS |
| O04 | Required body expectedOrderVersion, stale/status/replay guards | BuyerCancellationIT validation/query override, stale versions, every non-NEW status, replay original/current versions | PASS |
| O05 | Current persisted active USER, principal-derived identity | BuyerCancellationIT stalePrincipal; account/role locked; no client actor | PASS |
| O06 | Return request approve/reject/receipt | order/ReturnManagementIT; integration/Int02CommerceFlowIT | PASS |
| O07 | Invalid transitions and optimistic stale sessions | order/OrderContractTest; OrderDatabaseIT; VendorOrderIT; ReturnManagementIT | PASS |
| O08 | Actor/status/timestamp history, version and rollback | BuyerCancellationIT, OrderDatabaseIT, ReturnManagementIT, ManualCodRefundIT | PASS |
| O09 | Dedicated RTS only after exhausted failures and Ops receipt | integration/ReturnToSenderIT 17; commerce RTS; shipping/ReturnToSenderMigrationIT | PASS |
| C01 | Concurrent Buyer requests, winner commit/rollback | BuyerCancellationIT realHttpContenders: separate transactions/PIDs, Order SQL, pg_blocking_pids, bounded latches; 200/409 and injected500/200 | PASS |
| C02 | Existing Vendor/Ops/return/refund races and stale facts | VendorOrderIT 73, order/service/OpsOrderCancellationIT, ReturnManagementIT, ManualCodRefundIT | PASS |
| I01 | Real checkout decrements once/no oversell/replay | order/PlaceOrderIT; promotion/VoucherCheckoutIT; Buyer fixture real Cart/placement | PASS |
| I02 | Buyer/Vendor/Ops/RTS restore stock and quota once | BuyerCancellationIT multi-item restore/replay/races; VendorOrderIT, OpsOrderCancellationIT, ReturnToSenderIT | PASS |
| I03 | Persisted voucher quota eligibility/history, delivered returns do not release | promotion/VoucherCheckoutIT; ManualCodRefundIT; commerce variants | PASS |
| P01 | COD exact authoritative amount, no duplicate collection | payment/CodPaymentIT; commerce; Buyer payment unchanged | PASS |
| P02 | Valid refund evidence/replay/concurrency/rollback, no fabricated RTS refund | order/ManualCodRefundIT; payment/RefundLedgerMigrationIT; ReturnToSenderIT; commerce | PASS |
| F01 | Six formulas, Vendor/Admin scoped/global/multi-shop/item no fanout | order/VendorRevenueIT 46; governance/AdminRevenueIT 26; shared projections only | PASS |
| F02 | Real JWT Buyer cancellation financial zero and snapshots | integration/Int02CommerceFlowIT BUYER_CANCEL: all six metrics known/complete zero after cancel and replay409; no paid/refund fabrication | PASS |
| F03 | Payment attempts, cross-period refunds, original commission, shipping exclusion | VendorRevenueIT/AdminRevenueIT plus DIRECT/RETRY receipt/refund checkpoints | PASS |
| F04 | Signed negatives, BigDecimal, legacy incomplete/unavailable, Manager denied | RevenueServiceTest; VendorRevenueIT/AdminRevenueIT | PASS |
| N01 | Inbox persistence joins Order transaction and failure rolls everything back | BuyerCancellationIT inbox/history/voucher failure and retry; notification/NotificationRecoveryIT; RTS inbox-failure case | PASS |
| N02 | No pre-commit/rollback socket dispatch, retry dedupe | BuyerCancellationIT publisher/broker observations; NotificationRecoveryIT; NotificationSocketIT; Node tests | PASS |
| D01 | Fresh/populated migration/checksums/immutability/repeat migrate=0/Hibernate validate | DatabaseBaselineIT, OrderDatabaseIT, RefundLedgerMigrationIT, ReturnToSenderMigrationIT, ReturnReceiptMigrationIT, NotificationMigrationIT | PASS |

Net Collected Revenue = Collected Payments - Refunds.
Net Commission = Commission Accrued - Commission Reversed.
Net Revenue After Commission = Net Collected Revenue - Net Commission.
No formula or financial SQL changed.

Original Master Plan/Task Board not in checkout; HP's explicit Q02-F05 requirements and
merged contracts govern this correction. PAY-03/ONLINE refund, manual browser/deployment,
SMTP/gateway/load are outside scope and not run. Exact-head PR CI/HP acceptance pending.
Commands/environment/counts/hashes and teardown limits are in report/manifest/findings.
