# INT-02 evidence manifest

Sanitized source/counts/digests only. Raw logs/XML may contain synthetic credentials,
request cookies/proof references and properties; they remain ignored/local. No secret,
JWT, private media, DB dump, target artifact or environment dump is staged.

## Frozen identities

- Repository: https://github.com/hphuc032/Logistic-UTEExpress
- Branch: `feature/int-02-integration-verification`, local only, no push/PR.
- Old baseline develop `5b5164dd29ba5465ffefe19d431da7b70e41357a`.
- Preserved existing INT-02 commits `9d296d7e5a3e4bc26da76c4b20c7a8d003c8698a`,
  `b4ea3ca8eea3cb250113ae3f2c3129139294eb5d`.
- Latest approved develop `663fc9155bffbc0b35cc5ae0b465a0a74c5ca645`;
  #60 merge e6d94c5 and #59 merge 663fc915 verified ancestors.
- Normal local merge `2c0a3819d0af7df5cf1416c94d4f436f5e5bdbe5`.
- Production/integration remediation `a8056f3180eacb801dfadee8090bc69c49029710`;
  regression-contract assertions `e6bc22decc20118015b48eae21a60e59abf49646`.
- Final executable/test commit `e6bc22decc20118015b48eae21a60e59abf49646`; clean at gate start and evidence collection.
- The subsequent local report commit updates only these four Markdown files. Verify
  `git diff e6bc22decc20118015b48eae21a60e59abf49646..HEAD --name-only`; no later executable run is claimed.
- Master Plan `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256
  `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.
  Requested `(1).docx` not located; unnumbered actual original read, not another assumed revision.
- External QA-03 tooling source `fd48d18495c2128d242085569a85ca5a4b5334b9`, #61 unmerged
  at inspection. Isolated archive only, no QA CI changes or merge. Python runner digest
  is unchanged from previously inspected tooling; own 15 tests rerun independently.
- Remote readback: develop 663fc915, main `4c2dc14f8f9a1df4441283b9dd870a232359d1fb`.

## Executed application commands and independent tooling

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test --test-reporter=tap src/test/js/notifications.test.cjs
python -B -m unittest discover -s .work/int02-remediation/qa03-tooling/source/scripts/qa/tests -v
git diff --check
```

Maven exit=0, Node exit=0, tooling exit=0. The direct Maven command includes test/package.
Maven and Node use the final executable revision above. The tooling's own 15 tests
were run earlier in this remediation against its pinned external source, independently
of the application revision; their counts are never added to application totals.
QA-03 collect/git_identity/node_counts were invoked only after both successful exit files;
they validate actual fresh reports, exact testcase totals, required suites, failures,
errors/skips/flakes/reruns and Failsafe completion/timeout. This is parser-based supplementary
collection, **not** another full QA runner or CI execution.

| Gate | Classes | Actual tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| Surefire unit/architecture | 72 | 682 | 0 | 0 | 0 |
| Failsafe PostgreSQL | 61 | 1268 | 0 | 0 | 0 |
| Java total | 133 | 1950 | 0 | 0 | 0 |
| Node | separate | 12 | 0 | 0 | 0 |
| QA-03 tooling Python | separate | 15 | 0 | 0 | 0 |

Flaky/rerun XML markers=0; Failsafe timeout=false. Collection UTC `2026-10-27T04:15:48.470713+00:00`.
Final log diagnostic occurrences: fork shutdown warning=1; closed-connection
message=480. Counts are literal log occurrences, not inferred unique faults.
Maven success does not establish clean teardown. F05 remains a Low QA follow-up.

## Evidence inventory (relative to D:/UTEExpress; raw files ignored)

| Artifact | Meaning |
|---|---|
| `.work/int02-remediation/final-source-head.txt`, `final-java.txt` | Frozen revision/actual Java 21 |
| `.work/int02-remediation/final-maven.log`, `final-maven.exit` | Required full direct command, BUILD SUCCESS/0 |
| `.work/int02-remediation/final-node.log`, `final-node.exit` | Actual Node TAP 12/12, 0 |
| `.work/int02-remediation/final-evidence.json` | Per-suite counts/XML hashes, source inputs, exact commands/exit status |
| `target/surefire-reports/TEST-*.xml` | Fresh clean-run unit/architecture reports |
| `target/failsafe-reports/TEST-*.xml`, `failsafe-summary.xml` | Fresh PG case reports and completion |
| `.work/int02-remediation/qa03-tooling/tool-tests.log` | 15 tooling tests, not app count |
| `.work/int02-remediation/merged-dashboard-focused.log` | Initial approved dashboard regression, 37 unit +102 PG |
| `.work/int02-remediation/notification-atomic-focused.log` | Atomicity/socket focused pass, 47 unit +36 PG |
| `.work/int02-remediation/rts-evidence-focused.log` | RTS/metric/migration pass, 47 unit +19 PG at that iteration |
| `.work/int02-remediation/rts-final-focused.log` | Last RTS pass, 7 architecture +16 PG |
| `.work/int02-remediation/contract-regression-focused.log` | Exact event/migration assertion fixes and notification recovery, 7 architecture +21 PG PASS |
| `.work/int02-remediation/http-environment-focused.log` | Corrected actual HTTP startup 5 roles +7 architecture pass |
| `.work/int02-remediation/*initial*.log`, `rts-*focused.log`, `final-focused.log` | Retained failed iterations; see F06, not counted as full acceptance |
| `.work/int02-remediation/prior-final-reports/` | Preserved Oct25 final XML before target clean |
| `.work/int02-remediation/full-attempt1/` | First post-merge full attempt, six stale-contract/inventory assertions failed; retained log/XML, not a PASS |
| `.work/int02-baseline/`, `.work/qa03-tooling/` | Earlier baseline/runner/probe evidence; historical only |

The old `target/qa-03` outputs were preserved before clean; they are not represented as
current final results. No baseline/focused totals are added to the final suite count.

| Current safe artifact reference | SHA-256 |
|---|---|
| `.work/int02-remediation/final-maven.log` | `cbc0ce0063a39836776b76d4b42753e0d0e6fd3a9e80f76af390e9031f6f536d` |
| `.work/int02-remediation/final-node.log` | `e3a5f5b7ce0456d78d4278e12c8736bf75035efdc5ef27f789a277db275bc7af` |
| `.work/int02-remediation/qa03-tooling/tool-tests.log` | `48b08c18a3e83205df79ce526ea0bc3f1989015c9fd872bc0eb8dc83de9c77ed` |
| `.work/int02-remediation/qa03-tooling/source/scripts/qa/verify_operations.py` | `5ce1f478ef4b1dcfd62d73f924482d5203231bfe5b67c11d1a7c9f8fab845d8b` |
| `.work/int02-remediation/final-evidence.json` | `9653036ce7a4500ae9b9fee77ba7ccbad373d4b45747d4f310be5cc843f721a8` |
| `target/failsafe-reports/failsafe-summary.xml` | `2473348f2306f45a1d649b79f289109234276b2e62ca44b9294f148a68c78d1a` |

## Frozen executable Git input objects

| Input | Git object ID |
|---|---|
| `src/main` | `beb90b8e5e2da9acfb654246e1850723e985eed3` |
| `src/test` | `f85f457d2e914dc3e3f846a599e934a9c79887c4` |
| `pom.xml` | `89ba1f2c4fdbd5aafc0e9dfe909034b52990452f` |
| `.github/workflows/ci.yml` | `97dc4d2f0d66997d78aaeb967c62f1c58c4a91b8` |

## Local remediation file inventory

Relative to integrated develop merge 2c0a381 (dashboard imports belong to that merge):

- `docs/PROMO-01-vouchers.md`
- `docs/SHIP-04-dashboard.md`
- `docs/WS-01-notification-store.md`
- `docs/order-contracts.md`
- `docs/testing/INT-02-findings.md`
- `src/main/java/com/uteexpress/notification/service/OrderTransitionNotificationWriter.java`
- `src/main/java/com/uteexpress/order/controller/ReturnToSenderController.java`
- `src/main/java/com/uteexpress/order/dto/OrderTransitionPrepared.java`
- `src/main/java/com/uteexpress/order/dto/ReturnToSenderCommand.java`
- `src/main/java/com/uteexpress/order/dto/ReturnToSenderInput.java`
- `src/main/java/com/uteexpress/order/dto/ReturnToSenderResult.java`
- `src/main/java/com/uteexpress/order/entity/Order.java`
- `src/main/java/com/uteexpress/order/service/OpsOrderAuthority.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleService.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleServiceImpl.java`
- `src/main/java/com/uteexpress/order/service/OrderPlacementService.java`
- `src/main/java/com/uteexpress/payment/service/PaymentReadService.java`
- `src/main/java/com/uteexpress/promotion/repository/VoucherUsageRepository.java`
- `src/main/java/com/uteexpress/promotion/service/VoucherService.java`
- `src/main/java/com/uteexpress/shipping/dto/ReturnToSenderReceipt.java`
- `src/main/java/com/uteexpress/shipping/dto/ShipperDashboard.java`
- `src/main/java/com/uteexpress/shipping/service/ReturnToSenderService.java`
- `src/main/resources/db/migration/V20261027100000__hp_int02_return_to_sender.sql`
- `src/main/resources/templates/governance/orders/detail.html`
- `src/main/resources/templates/shipping/shipper/dashboard.html`
- `src/test/java/com/uteexpress/fulfillment/ShipperFulfillmentIT.java`
- `src/test/java/com/uteexpress/integration/Int02CommerceFlowIT.java`
- `src/test/java/com/uteexpress/integration/Int02HttpEnvironmentIT.java`
- `src/test/java/com/uteexpress/integration/ReturnToSenderIT.java`
- `src/test/java/com/uteexpress/notification/NotificationRecoveryIT.java`
- `src/test/java/com/uteexpress/order/OrderDatabaseIT.java`
- `src/test/java/com/uteexpress/order/ReturnReceiptMigrationIT.java`
- `src/test/java/com/uteexpress/order/entity/OrderDomainCoreTest.java`
- `src/test/java/com/uteexpress/payment/RefundLedgerMigrationIT.java`
- `src/test/java/com/uteexpress/shipping/ReturnToSenderMigrationIT.java`

The final documentation commit also updates all four existing `docs/testing/INT-02-*.md`.
No pom/CI/security exemption/applied migration/financial ledger change. Inspect with
`git show --stat a8056f3180eacb801dfadee8090bc69c49029710`,
`git show --stat e6bc22decc20118015b48eae21a60e59abf49646` and
`git diff 663fc915..HEAD` for the complete review scope.

## Evidence limits

Browser/mobile375/tablet768/desktop1440 NOT RUN: Windows Firefox Computer Use policy
rejected URL enforcement support; no screenshots or browser acceptance were fabricated.
Int02HttpEnvironmentIT is real embedded HTTP + fresh PG, not a browser. Full T49
production deployment/upload-restart/ENV and T50 human three-width acceptance remain
QA/UI/release tasks. No CI run/approval, external SMTP delivery, multi-instance/load,
production monitoring or completed-release claim. Final handoff: technically ready,
pending TD review; no push/PR creation/merge was authorized or performed.
