# INT-02 findings and owner decisions

Applies to frozen develop `5b5164dd29ba5465ffefe19d431da7b70e41357a` and INT-02 test-source commit `9d296d7e5a3e4bc26da76c4b20c7a8d003c8698a`.
Severity below describes integration/acceptance impact. No Critical/High security vulnerability was confirmed. Functional gaps and policy deviations are not labelled as proven exploits.

## F01 — Pending P1 dashboards prevent full acceptance
- Severity: Medium (acceptance blocker).
- Component/owner: DASH-01 TD; DASH-02 QĐ; integration/approval HP.
- Reproduction: inspect develop tree and PR #60 / #59 metadata. No RevenueService or RevenueQueryRepository on develop; OpsDashboardService still returns static capability shells. #60 remains OPEN/unmerged; #59 OPEN/unmerged and Phase 1 only.
- Expected: Task Board INT-02 deliverable “Bản đủ P0/P1”; J/S4 includes dashboards.
- Actual: pending branches have reporting contracts/tests, but none may be counted as functionality integrated here.
- Root cause: integration sequencing, not a failing implemented formula in approved develop.
- Suggested fix: owners complete/review pending dashboard slices; HP integrates only after normal approval, then executes a new full INT-02 run.
- Status: OPEN/BLOCKED; no pending branch merged or copied.

## F02 — Shipper success ratio differs from Master Plan
- Severity: Medium (confirmed functional contract mismatch).
- Component/owner: `shipping/dto/ShipperDashboard.java:9`, SHIP-04 QĐ; HP business definition.
- Reproduction: instantiate production `new ShipperDashboard(6,0,0,0,2,1,3,0)` from compiled target/classes. Actual Java probe exits 0 and reports 66.7. Master Plan final-outcome denominator gives 2/(2+3)×100 = 40.0.
- Expected: Plan final Q&A / N T45: DELIVERED / (DELIVERED + RETURNED_TO_SENDER), for completed shipments in the reporting period.
- Actual: delivered/(delivered+current DELIVERY_FAILED), without a period filter; UI/docs intentionally label it current outcomes. A first-attempt failure can enter the denominator, then disappear during retry.
- Root cause: current SHIP-04 follows a documented reduced/current-state metric rather than the final Plan metric. Existing all-status fixture has equal failed/returned counts, so both formulas happen to produce 66.7; its PASS alone cannot reveal the difference.
- Suggested fix: HP/QĐ explicitly approve a different name/scope or implement the Plan's terminal-outcome/period model with a discriminating dataset and required terminal evidence. Do not silently swap a denominator and imply missing period/return support is complete.
- Status: OWNER REVIEW REQUIRED. No Shipping production code changed. Safe probe source/command is in the evidence manifest.

## F03 — Exhausted return-to-sender completion missing
- Severity: Medium (confirmed incomplete required workflow).
- Component/owner: SHIP-03 QĐ and Order/inventory TD; HP scope approval.
- Reproduction: `ShipmentFulfillmentIT.failedDeliveryRetryAndExhaustionPreserveOrderAndImmutableEvidence` exercises fail→retry→fail and rejects further retry. Inspect current shipping production sources: RETURNED_TO_SENDER appears only in enum/count projections; no receipt/resolution writer/endpoint exists. Existing Ops cancellation supports only NEW/CONFIRMED before pickup.
- Expected: Task Board SHIP-03 includes return-to-sender and stock integration; N T44 permits restock only after confirmed goods return.
- Actual: exhausted Shipment stays DELIVERY_FAILED / Order SHIPPING. Preserving stock is safe, but it is not a completed physical-return/resolution flow.
- Root cause: merged SHIP-03 intentionally implements failure/retry slice only. SHIP-04 is a dashboard, not the missing mutation.
- Suggested fix: HP/TD/QĐ approve receipt evidence, lifecycle/stock rules and lock order, then owner implements and verifies a separate scope. INT-02 must not fabricate physical receipt, directly set status or auto-restock.
- Status: OWNER REVIEW REQUIRED / BLOCKED for complete P0/P1 acceptance.

## F04 — Notification atomicity wording needs explicit reconciliation
- Severity: Informational (architecture/acceptance decision; not confirmed durable loss).
- Component/owner: WS-01/WS-02 QĐ with TD Order and HP acceptance.
- Reproduction: `OrderLifecycleServiceImpl` publishes only in afterCommit; `NotificationProjectionService.projectOrder` starts REQUIRES_NEW. Existing `NotificationRecoveryIT.notificationFailureDoesNotFailCommittedOrderAndReconciliationRecovers` forces a notification insert failure: Order remains CONFIRMED and inbox has zero rows until reconciliation succeeds.
- Expected: Master Plan I8 literally writes inbox in the business transaction; socket sends after commit.
- Actual: immutable committed Order history is durable replay source; asynchronous/retryable inbox projection is eventually created and hints fire after that projection commits. Dedupe/recovery/late lower-history-ID commits are tested. This intentionally avoids turning a committed Order into an apparent failed mutation.
- Root cause: documented history-projection design differs from literal same-transaction inbox timing. Broker remains single-instance; live hints are best-effort.
- Suggested fix: HP explicitly accept the documented durable-replay equivalence and latency limits, or coordinate an owner-reviewed change. Preserve existing recovery rather than build a second notification system.
- Status: DECISION REQUIRED. Current recovery tests PASS; no automatic architecture rewrite.

## F05 — Test JVM/container teardown diagnostics
- Severity: Low (test infrastructure).
- Owner: QA QĐ / integration HP.
- Reproduction: fresh baseline full clean verify emits Hikari closed-connection warnings and “Surefire is going to kill self fork JVM” after 30 seconds following System.exit(0).
- Expected: suites complete and process/contexts terminate cleanly.
- Actual: baseline exit 0 / BUILD SUCCESS, 1,793 tests pass, zero failures/errors/skips/flakes, Failsafe timeout=false; teardown is not clean.
- Root cause: not established here; consistent with existing documented cached-context/container teardown. No production deadlock/timeout is inferred.
- Suggested fix: separate bounded QA investigation of context/container/scheduler shutdown; do not suppress diagnostics or relax test assertions.
- Final run observation: Both baseline and final full runs emitted Hikari closed-connection warnings and the 30-second fork exit-grace diagnostic after tests. Maven/runner exited 0, Failsafe timeout=false and zero test failures/errors/skips/flakes. This is an unresolved test-infrastructure follow-up, not a clean-shutdown claim.
- Status: OPEN follow-up; not hidden as “no errors in logs”.

## F06 — Resolved new test assertion defect
- Severity: Informational (test-only, resolved).
- Component/owner: Int02CommerceFlowIT, HP.
- Reproduction: first focused run used Map equality after refund replay; JDBC materializes BYTEA canonical payload/digest into fresh arrays per query.
- Expected: compare persisted bytes by contents; unchanged refund evidence succeeds.
- Actual: two assertion failures despite successful business flow; no production contract defect established.
- Root cause: array identity comparison in the new test.
- Resolution: recursive/content comparison plus explicit successful refund Payment/Return linkage assertions. Focused rerun: 7 architecture + 2 integrated cases, zero failures/errors/skips; original failure log retained locally.
- Status: RESOLVED, not skipped/mocked.

## Unexecuted acceptance / limitations

T49 full deployment/restart/upload retention and T50 manual 375/768/desktop demo were not run. Real external SMTP, production log ingestion/alerts, backup/ACL/proxy settings, load and multi-instance broker behavior are unverified. These are stated limits, not invented vulnerabilities. Review orphan handling remains a restricted maintenance/drain/recheck procedure; no files were automatically removed. Attested full COD refunds do not prove partial/ONLINE/cancelled-paid gateway flows.

No owner production business rule, migration, CSRF/JWT policy, financial formula or QA CI was changed. Findings remain reviewable proposals; no remote issue/PR comment was posted.
