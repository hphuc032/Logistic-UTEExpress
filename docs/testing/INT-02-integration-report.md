# INT-02 — Integration verification and acceptance

Owner/executor: Hoàng Phúc (HP). Reviewer: Tiến Đạt (TD).
Verdict: **TECHNICALLY READY — PENDING TD REVIEW**. No independent review,
PR approval, completed milestone, production deployment or release acceptance is claimed.

## Source and preserved history

- Latest approved develop integrated: `663fc9155bffbc0b35cc5ae0b465a0a74c5ca645`.
- DASH-01 #60 merge `e6d94c5c437c6935b2cde66fd474568d6d64fdae` and DASH-02 #59
  merge `663fc9155bffbc0b35cc5ae0b465a0a74c5ca645` are ancestors, including Admin financial reporting.
- Normal local integration merge: `2c0a3819d0af7df5cf1416c94d4f436f5e5bdbe5`, no conflicts.
- Remediation commit: `a8056f3180eacb801dfadee8090bc69c49029710`;
  regression-contract test commit: `e6bc22decc20118015b48eae21a60e59abf49646`.
- Branch: `feature/int-02-integration-verification`.
- Final executable/test commit: `e6bc22decc20118015b48eae21a60e59abf49646`. Final documentation follows this commit
  and changes only the existing four INT-02 Markdown reports; it is not another Maven run.
- Preserved original commits: `9d296d7e5a3e4bc26da76c4b20c7a8d003c8698a` and
  `b4ea3ca8eea3cb250113ae3f2c3129139294eb5d`.
- Actual Master Plan: `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256
  `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
  The requested `(1).docx` filename was not found in the repository, D:/JavaWeb,
  Downloads/Documents/Desktop. No different revision is assumed or binary committed.
- Reviewed Task Board INT-02 (S4 P0, HP/TD, full flow + return/refund/review/WS,
  complete P0/P1), I/I5–I8, L/M/N, S4/S5 gates and final Q&A.
  Document workflow is reference data; current user instructions control Git actions.

## Modules and actual remediation

AUTH/account/address/catalog/shop/cart/checkout, pricing/voucher/commission snapshots,
Order lifecycle, assignment/failure/retry, COD Payment, return receipt/refund, review/media,
SEC-02, notification inbox/private STOMP, Vendor/Admin revenue and Ops/audit were reviewed
against owner services and current tests. Existing financial SQL, immutable refund ledger,
JWT/CSRF policies and applied migrations were not rewritten.

1. **F01 resolved:** merged DASH-01/DASH-02 now execute as integrated production code.
   VendorRevenueIT 46, AdminRevenueIT 26 and
   OpsDashboardReadIT 28 pass in the final full run. They verify
   tenant/privacy, exact successful Payment/refund evidence, independent time windows,
   snapshot commission, shipping exclusion, no item fanout, unknown versus known zero,
   Admin global/Shop parity, Manager financial denial, audit filters and repeatable reads.
   Shared RevenueProjectionService remains the source; no duplicated ledger/query.
2. **F02 resolved:** SHIP-04 used DELIVERED/(DELIVERED+current DELIVERY_FAILED).
   The Plan final Q&A uniquely requires terminal shipments:
   DELIVERED/(DELIVERED+RETURNED_TO_SENDER). The UI explicitly says all recorded data
   for the current assignee. Real first/retry success, exhaustion, physical returns,
   unstarted/in-progress and foreign-Shipper paths prove 2/(2+3)=40.0%, not 66.7%.
   No date picker or historical assignment attribution is claimed.
3. **F03 resolved:** dedicated Ops physical-return receipt closes the missing path.
   First failure -> retry -> second failure keeps Order SHIPPING and reserved stock.
   ADMIN/MANAGER attests goods physically received with a receipt reference. The boundary
   checks two immutable failures, original versions, current Ops authority and exactly
   matching UNPAID COD under Order -> Shipment -> Payment locks. In one transaction:
   immutable receipt, Shipment RETURNED_TO_SENDER, lifecycle SHIPPING -> CANCELLED,
   stock marker/restoration once, voucher quota release once, history/audit/inbox.
   Payment and money snapshots remain unchanged. Original actor/reference/versions replay;
   changed/stale/forged requests fail. Both winner-commit and winner-rollback races verify
   actual PostgreSQL blocking. Physical receipt is Ops attestation, not external carrier proof.
4. **F04 resolved with explicit HP authorization:** HP approved the concrete pre-commit
   fact/MANDATORY writer design in this chat. After lifecycle history flush,
   OrderTransitionPrepared joins the existing transaction to persist inbox rows;
   inbox failure rolls back business/history. Existing committed events and socket hints
   stay after commit. Legacy reconciliation/dedupe and every WS security policy remain.
   Durable inbox commit is guaranteed by that transaction; socket delivery is best effort.
5. F05 shutdown diagnostics remain a Low QA follow-up; F06 test-fixture defects are
   resolved with failure logs retained. Details/comparison in the findings document.

New migration `V20261027100000__hp_int02_return_to_sender.sql` follows previous maximum
`V20261023170000__qd_rev01_review_foundation.sql` (version 20261023170000). It adds only immutable
receipt evidence, named constraints and parent uniqueness; no historical data rewrite.
Fresh and populated-upgrade PostgreSQL tests validate Flyway, Hibernate, old checksums,
unchanged historical rows, append-only evidence and second migrate=0.

## Integrated flow and invariant evidence

Int02CommerceFlowIT has three actual-JWT/HTTP variants: DIRECT, RETRY and RETURN_TO_SENDER.
All traverse catalog -> DB cart -> own address/valid shipping -> server quote -> NEW Order
-> Vendor confirm/ready -> Ops assignment -> assigned Shipper. Direct/retry delivery
collect COD -> Buyer Review -> Return request -> Vendor approval/receipt -> Admin full
COD refund/replay. The exhausted branch instead performs two failures -> Ops physical
receipt -> CANCELLED/replay, with no refund and unchanged COD UNPAID.

Two units at 100,000 less 10% promotion produce subtotal 180,000; voucher 10,000;
shipping 15,000; total 185,000; commission 11,900. Stock 10->8 happens once; unselected
cart row remains. Physical return restores 8->10 once and releases voucher quota;
delivered buyer return/refund leaves the original inventory/voucher facts intact.
Ownership, wrong COD, stale versions, unsafe CSRF denial, immutable snapshots, idempotent
checkout/refund, recipient isolation and escaped review text remain asserted.
Existing real WebSocket and PostgreSQL race suites complement MockMvc; no mocked lifecycle
or client money/actor substitution is used to make the cross-module paths succeed.

## Final execution on frozen executable source

Java Oracle 21 (+35), Maven Wrapper 3.9.11, Docker Desktop Linux 29.4.3,
PostgreSQL 17.6 Testcontainers, Node 24.14.0 and Python 3.14.2 on Windows.

| Actual command | Result |
|---|---|
| `.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify` | PASS exit 0; 682 Surefire/72 classes + 1268 PostgreSQL/61 classes = 1950 Java |
| `node --test --test-reporter=tap src/test/js/notifications.test.cjs` | PASS exit 0; 12 tests |
| isolated QA-03 Python unittest discovery | PASS exit 0; 15 tooling tests, separately |
| `git diff --check` | PASS |

Failures/errors/skips/flaky or rerun XML markers: 0. Failsafe timeout=false.
Clean verify includes test/package; separate redundant test/package commands were not
run for this final gate. QA-03 parser validates fresh XML/case counts and Node TAP after
successful direct commands; the full external runner was not invoked again.
The tooling's own 15 tests execute independently against its pinned external source,
not against an application revision; they were run earlier in this remediation.
Historical 652+1,143/12/15 counts from Oct25 and focused counts are never summed here.
Test JVM/Hikari teardown diagnostics are disclosed in F05 rather than called clean shutdown.
Fault-injection/rejection tests intentionally emit some error-level logs; zero test
failures is not represented as a log with no errors.

## Browser, environment and later acceptance

**Browser/responsive 375/768/1440: NOT RUN.** Installed Firefox was discovered and
launched, but Computer Use refused further interaction because Windows browser URL-policy
enforcement is unsupported. No navigation, credential entry, screenshot or substitute
automation was used to bypass that tool restriction. HTTP checks do not prove layout.

**Isolated application startup: PASS.** Int02HttpEnvironmentIT 5
real embedded-server cases use fresh PostgreSQL/Flyway/Hibernate, real BCrypt login,
rendered masked CSRF, HttpOnly JWT cookies, USER/VENDOR/ADMIN/MANAGER/SHIPPER pages,
static CSS, health UP, Manager financial/audit denial and logout/tokenVersion invalidation.
Test-only nonsecure local cookie and disabled mail health do not alter production config.
No existing user database or unrelated container was deleted.

T48 is automated PASS. T49 full ENV-failure/restart/upload/deployment acceptance and
T50 whole-team three-width human acceptance remain partial/NOT RUN. UI-02 explicitly
belongs to S5 after INT-02; QA-03/REL-01 retain deployment/rehearsal and final group
acceptance. These are disclosed follow-ups, not unresolved INT-02 business-contract
decisions and not a claim of release readiness. No actual external SMTP, multi-instance
broker, load/production monitoring or cross-browser compatibility test is claimed.

## Review handoff

No remaining confirmed Critical/High defect or unresolved INT-02 contract blocker in
the tested scope. TD review remains mandatory. P2 VNPAY, partial/ONLINE refunds,
MFA/refresh tokens and unrelated business features are deferred. Review UNKNOWN media
cleanup remains operator-controlled; no files were purged.

Only local merge/remediation/documentation commits were made. No push, PR creation,
approval, develop/main commit or merge, deployment or shared-history rewrite occurred.
Exact evidence, per-suite counts and file inventory are in the companion manifest/matrix.
