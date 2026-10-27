# QA-02 findings — Q02-F05 correction

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


## Findings and decisions

| ID | Severity | State / evidence |
|---|---|---|
| Q02-F01 | Medium | OPEN: original Master Plan/Task Board unavailable. HP's explicit approved route/role/version/invariant contract is sufficient for this bounded fix; whole-plan acceptance comparison remains pending. |
| Q02-F02 | Low | RESOLVED: original QA matcher/exception/dedup fixture corrections; preserved in original checkout history/reports. No weakening in this fix. |
| Q02-F03 | Low | OPEN: fresh full log contains 484 closed-connection lines and 1 30-second fork-shutdown lines despite exit0/timeout=false and zero test errors. Root cause is not established; no production deadlock inferred. QA-03/owner investigation remains; no diagnostic suppression. |
| Q02-F04 | Medium | OPEN handoff gate: no push/PR/exact-head CI or HP final acceptance. User explicitly requires TD confirmation before publication. |
| Q02-F05 | High | FIXED, LOCAL REGRESSION VERIFIED: separate USER-only Buyer boundary, locked persisted ownership/version/eligibility, atomic shared release/history/inbox; 33 Buyer cases plus four real HTTP commerce variants and full regression PASS. Final task acceptance remains HP's authority. |
| Q02-F06 | Low | Historical Node sandbox spawn EPERM resolved in prior QA run. Current required Node command runs outside sandbox and passes; not an application flake. |
| Q02-F07 | Low | RESOLVED: initial fix attempt missed test Import and stopped at testCompile. Corrected import; subsequent focused/full runs execute all tests. No product failure hidden. |

## Q02-F05 root cause and contract correction

Original service entry allowed USER/VENDOR but generic loading/auth always delegated to
VendorOrderAuthority, scoped to seller Shop. The previous reproduction demonstrated USER
AuthorizationDeniedException and VENDOR Buyer foreign-Shop RESOURCE_NOT_FOUND. This was
a missing capability, not evidence it was introduced by INT-02. Original red test and
raw evidence remain in the original QA worktree; no old result substitutes for this run.

HP now explicitly restricts this cancellation route to USER. That supersedes the earlier
broad Buyer USER/VENDOR interpretation for cancellation; reads/return policies remain.
The fix therefore adds a dedicated lifecycle method instead of changing Vendor authority
or making the old generic transition a Buyer writer. The dedicated API and tests follow
the approved narrower contract; original red reproduction is preserved separately.

HTTP contract: `POST /buyer/orders/{orderId}/cancel`, JSON body
`{"expectedOrderVersion":0}`, success HTTP 200 `{"result":"OK"}`. HP approved production
implementation and left version placement open. JSON body was selected from existing
mutation DTO conventions; no header/query/form alternative or extra business fields.
Only authenticated USER authority is accepted, followed by locked persisted active USER
eligibility and Buyer ownership. VENDOR-only/Admin/Manager/Shipper are denied; foreign
and missing Order return 404 before state/version disclosure; stale/non-NEW/replay return
409. Missing/null/negative body version, unknown fields and conflicting query parameters
reject. Existing CSRF is mandatory. SecurityConfig is unchanged.

`OrderLifecycleService.cancelForBuyer` owns a fresh transaction and rejects an existing
outer transaction, including one holding Account. BuyerCancellationAuthority (MANDATORY)
scopes the Order lock to principal-derived `buyer_id`, refreshes under lock, then locks
active Account and rechecks persisted USER. Order state/version and release markers are
checked while locked. Existing Order-internal `OpsOrderAuthority.release` supplies only
inventory/marker/voucher effects; no Ops authorization/audit, Vendor authority or Shipping
operation is invoked. Restoration logic is reused rather than copied. The shared writer
persists Order/history and synchronous inbox in that transaction; committed events and
private broker dispatch occur only after commit. COD Payment evidence and financial/item/
commission/discount snapshots remain unchanged; no refund or new idempotency ledger.
The row lock plus expected version permits one successful cancellation. Replay conflicts
without a second stock/quota/history/inbox effect.


## Concrete production scope and verification

Production paths, all under `src/main/java/com/uteexpress/order/`:

- `controller/BuyerCancellationController.java`: route, method USER guard, validated JSON.
- `dto/BuyerCancellationInput.java`, `dto/BuyerCancellationCommand.java`: version/resource selections.
- `service/BuyerCancellationAuthority.java`: Buyer-scoped Order lock, persisted eligibility and guards.
- `service/OrderLifecycleService.java`, `OrderLifecycleServiceImpl.java`: dedicated transaction/shared writer.
- `service/OrderPlacementService.java`: wire authority and reuse existing release effect only.

VendorOrderAuthority, SecurityConfig, Shipping/Admin/Payment production, financial SQL/
formulas, dependencies and applied migrations are unchanged. New BuyerCancellationIT
proves Vendor authority is not called. Real commit/rollback contention proves one release,
not a sleep race. Broker spy observes actual unchanged NotificationSocketListener sends
only after commit; injected persistence/pre-commit rollback cannot publish them. Existing
real socket security/recovery suites also pass in full.

No unresolved Critical/High blocker was found for this approved fix after full regression.
PR readiness is local READY; broader QA-02 completion/CI/HP final acceptance remain open.
