# INT-02 — Integration verification and acceptance

Owner/executor: Hoàng Phúc (HP). Intended reviewer: Tiến Đạt (TD).
No independent TD/QĐ review or approval is claimed.
Recommendation: **BLOCKED for complete Master Plan acceptance**. Executed merged-scope checks can pass without making the complete P0/P1 milestone pass.

## Source and scope

- Master Plan: `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
- Task Board INT-02: Sprint S4, P0, HP owner / TD reviewer; “Full flow + return/refund/review/WS, resolve data inconsistencies”; direct dependencies ORD-04, PAY-02, REV-01, WS-02; deliverable “Bản đủ P0/P1”.
- Sections A6/J require the complete P0/P1 build; L contains common DoD and WS acceptance; M defines owner boundaries; N supplies T01–T50. P2 VNPAY T33/T34 and conditional video scope are not prerequisites for this verification.
- The Plan suggests `chore/int-02-full-integration`. The current explicit request overrides that with `feature/int-02-integration-verification`. Document workflow text is reference data, not permission to push/merge.
- Repository contracts: `docs/order-contracts.md`, ORD-04 receipt, PAY-02, SHIP-02 global lock order, SHIP-03 integration, SHIP-04, REV-01 eligibility/integration/media runbook and WS-01/WS-02 reports. Production source and current tests resolve stale historical “running/uncommitted” statements in old reports.
- Frozen approved develop: `5b5164dd29ba5465ffefe19d431da7b70e41357a`.
- Final executable/test source verified: `9d296d7e5a3e4bc26da76c4b20c7a8d003c8698a`. The later evidence-documentation commit changes only these four Markdown files; it is not represented as another executed Maven run.

## Actual integration state at audit

| Capability | Verified develop history/status |
|---|---|
| AUTH/account/catalog/cart/checkout/COD/vendor workflow, SEC-02 | Present; INT-01 #35 and SEC-02 #38 ancestors |
| Assignment and shipper delivery, promotion/voucher | Present, including #40/#43/#44/#46/#48 |
| Return request/decision and attested receipt | #49 and #53 merged; actual Order/Return lifecycle boundaries present |
| Shipping failure/retry / Shipper dashboard | #54/#55 merged |
| Manual full COD refund | #56 merged |
| Review vertical slice | #57 merged |
| Private WebSocket notifications | #58 merged; develop HEAD above |
| DASH-01 #60 | OPEN, not merged, head `1e608133708e322e5ab741cf50803381cf63bc49` |
| DASH-02 #59 | OPEN, not merged, head `c958510c3b05ad761400c5941198ad9c5a91ff7b`; Phase 1 only; financial slice remains open |
| QA-03 #61 | OPEN/Draft, head `e3a153e6b7f5efcdda67682cf503992c079d742f`; tooling used separately, never merged |

All four direct INT-02 dependency capabilities exist. This does not satisfy the wider P0/P1 deliverable while dashboards/other noted acceptance gaps remain. Pending PR-specific test claims are not counted as integrated results.

## Environment and commands

Oracle Java 21 (21+35-LTS-2513); Maven Wrapper 3.9.11; Windows 11 amd64; Docker Desktop Linux engine 29.4.3; actual PostgreSQL 17.6 Testcontainers; Node 24.14.0; Python 3.14.2.
CI config uses Temurin 21 / Node 22 and Node tests + Maven test/package; it does not run postgres-it. Node 22 or a new CI run on INT-02 was not executed/claimed.

| Execution | Source | Actual result |
|---|---|---|
| Baseline `mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify` | develop SHA above, clean tree | 652 Surefire / 70 classes + 1,141 PostgreSQL / 54 classes = 1,793 Java; exit 0 |
| Baseline `node --test --test-reporter=tap src/test/js/notifications.test.cjs` | same | 12 PASS; exit 0 |
| New focused `mvnw.cmd -B --no-transfer-progress -Ppostgres-it -Dtest=ArchitectureTest -Dit.test=Int02CommerceFlowIT verify` | final test content | 7 architecture + 2 integration cases PASS; exit 0 |
| Final full Java/PostgreSQL + Node via isolated QA-03 runner | frozen INT-02 source above | PASS: 652 Surefire / 70 classes + 1,143 PostgreSQL / 55 classes = 1,795 Java; 12 Node PASS; Maven/Node/runner exit 0 |
| QA-03 tool's own Python tests | exact external tooling SHA above | 15 PASS; exit 0; not application test counts |
| Whitespace / Git scope | local branch | PASS; clean at runner start/end; staged/unstaged whitespace checked separately for the documentation handoff |

Final failures/errors/skips/flakes: 0 failures, 0 errors, 0 skips, 0 flaky/rerun XML markers. Failsafe timeout: false.
Overlapping baseline, focused and final runs are never added as distinct tests.
Fresh and populated-upgrade migration tests and Hibernate validation use real PostgreSQL; no H2, schema overrides or applied migration edits.
Both baseline and final full runs emitted Hikari closed-connection warnings and the 30-second fork exit-grace diagnostic after tests. Maven/runner exited 0, Failsafe timeout=false and zero test failures/errors/skips/flakes. This is an unresolved test-infrastructure follow-up, not a clean-shutdown claim.

Initial new-test attempt: 2 assertion failures caused by Java Map equality comparing freshly read BYTEA arrays by identity. Corrected only the new snapshot assertion to recursive/content equality; rerun passed. This is a resolved test defect, not a suppressed production failure.

## Added integration proof

`Int02CommerceFlowIT` adds two deterministic variants on one actual Order per invocation: direct delivery and first-attempt failure → retry → delivery. Actors log in through the actual Login controller/BCrypt/JWT filter; no injected mock principals, lifecycle mocks or direct SQL writes to Order/Shipment/Payment/Return/Refund/Review state.

Fixtures seed ACTIVE accounts, owned Shop/Product/category/address, shipping rate, 10% product promotion, 10,000 voucher and 7% commission. HTTP proves catalog → DB cart → selected quote → placement NEW → Vendor confirm/ready → Ops assignment → assigned Shipper delivery/COD → Buyer Review → Return request → Vendor approval/receipt → Admin full COD refund/replay.

Expected evidence: two units at 100,000 less 10% per-unit promotion gives subtotal 180,000; voucher 10,000; shipping 15,000; total/COD/full refund 185,000; commission 11,900. Stock decreases 10→8 once; unselected cart row remains. Wrong COD leaves complete Order/Shipment/Payment/history/notification/refund snapshots unchanged. Foreign Buyer/shipper are denied; refund requires Admin and CSRF. Review attribution is server-derived, duplicate blocked and text escaped. Refund replay/changed payload do not mutate the immutable ledger or other facts. Receipt, Payment PAID, Shipment DELIVERED, voucher REDEEMED, stock and original Order/Item snapshots remain unchanged. Seven non-NEW Order transitions yield 14 owner-scoped notifications for Buyer/Vendor; stranger inbox stays empty.

The new MockMvc requests use the standard valid csrf() test processor; missing-CSRF refund is explicitly denied. Existing authentication/socket suites separately verify actual token bootstrap/masking. This MockMvc flow is not a manual browser demo or real socket delivery proof. Real embedded-Tomcat/JDK WebSocket, PostgreSQL blocking/commit races, bad Origin/CSRF, revocation, rollback/recovery and Node recovery are separately exercised by the existing suites mapped in the matrix.

## Findings, limits and acceptance

See [findings](INT-02-findings.md) and [matrix](INT-02-test-matrix.md). No Critical/High security defect was confirmed in tested integration boundaries. This is focused source/test verification, not an exhaustive repository/dependency security scan or production deployment audit.

Blocking acceptance:
1. DASH-01 / required DASH-02 functionality is not integrated.
2. Shipper success-rate diverges from the Master Plan final-outcome formula; HP/QĐ must resolve the definition.
3. Exhausted failure return-to-sender / physical-return stock resolution remains unimplemented in current Shipping production paths.
4. Master Plan I8 transactional inbox wording differs from history-based eventual projection; HP must explicitly accept equivalence or coordinate an owner change.
5. Manual T50 three-width browser acceptance and T49 clean-machine/restart/deployment checks have not been performed.

No suspected orphan media was removed. Review media UNKNOWN transaction outcomes remain deferred; reconciliation/operational log retention and maintenance controls remain operator responsibilities. Single-instance simple broker only; no multi-instance/load/browser compatibility or SMTP delivery claim. Current refund scope is attested full COD after receipt, not partial/ONLINE/cancelled-paid gateway refund.

## Git and handoff

One local test commit and an evidence-only documentation commit are allowed. No push, PR creation/approval, merge, deployment, production DB access or destructive Docker/Git operation is performed. Existing ignored user logs and unrelated running containers are preserved.

HP should resolve the listed owner decisions, integrate approved pending work through normal review, then rerun against the new develop. Do not carry this result forward as evidence for later commits.
