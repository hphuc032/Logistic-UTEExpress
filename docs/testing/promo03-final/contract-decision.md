# PROMO-03 contract decision

Base: `a5620f1971d3b684d9b05d7444f5e90442404656`; isolated integration branch `codex/promo03-platform-integration`.
Master Plan: `D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx`, SHA-256 `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.

| Requirement | Master Plan | Existing source at base | Missing behavior | HP decision |
|---|---|---|---|---|
| Shop/platform vouchers | FR-21, PROMO-01/03 | Voucher supports both scopes; VendorVoucherService only SHOP; VoucherService handles checkout | Admin PLATFORM management | Implement bounded voucher slice |
| Ops promotion and voucher pages/actions | PROMO-03, `/{ops}/promotions`, `/{ops}/vouchers` | Vendor product management in Catalog; no Admin promotion management | Admin product-promotion authority and adapter | HP requested expansion **after TD/QĐ boundary agreement**; affected slice BLOCKED pending that agreement, neither completed nor silently deferred |
| Manager limits | PROMO-03 | No PLATFORM management policy | Explicit role decision | HP explicitly ADMIN only; MANAGER DENY ALL for this slice, a narrow variance from Plan, unrelated to ADMIN-06 |
| Used voucher mutability | I7 says only stop/create new | Voucher.update freezes code/type/value/maxDiscount/minSubtotal, permits window/enabled changes and quota increases; RELEASED history also counts | Clarification | HP explicitly retains existing PROMO-01 rule for PLATFORM; does not alter SHOP or historical facts |
| Pricing | I7, PROMO-02 | Existing product pricing, VoucherService, checkout/order authority | No missing engine | Reuse unchanged |
| Audit/security | Existing SEC/ADMIN contracts | RoleGovernanceService, AuditLogService | Authorized, audited PLATFORM adapter | Account lock → fresh DB role check → PLATFORM Voucher lock/refresh → usage query → flush → mandatory audit, one transaction |

## Roles and server authority

ADMIN requires persisted ACTIVE account and a current DB ADMIN grant on every service operation. MANAGER, VENDOR, USER, SHIPPER and anonymous are denied. HTTP route policy and method security remain intact; neither hidden buttons nor a stale principal grant authorize management. Account lock serializes revocation/account state changes; role grants are read again after the lock. PLATFORM scope, null shop and actor come from the server. Browser binding accepts only voucher definition fields and expected version; unsafe actions require CSRF. A SHOP ID never resolves through the PLATFORM service.

## TD contract and QĐ adapter

TD-owned `PlatformVoucherManagementService`: list/get/requireAdministrator/create/update/disable. Definition validation is extracted unchanged from VendorVoucherForm to VoucherDefinitionForm, preserving Vendor API inheritance. Existing Voucher constructor/update/disable are the business authority; no new pricing or usage engine. Update and disable require persisted expected version. Read projections contain no entities. QĐ-owned controller/templates call only this boundary. Audit is appended by the transactional management boundary, not by a second independently committing UI operation; failure propagates and rolls back. Safe audit metadata uses existing active/version allowlist, never browser text.

Management never acquires Product, Order or Usage row locks after Voucher. Checkout retains Account → Cart → Product → Shop → Category → Voucher ordering; cancellation/refund paths remain untouched. Global canonical code uniqueness remains DB-authoritative. Historical usage, order/payment/refund snapshots and formulas remain unchanged: grandTotal = subtotal − voucherDiscount + shippingFee; commission = Money.round((subtotal − voucherDiscount) × authoritativeRate / 100).

## Owner gates and source provenance

This is an HP-authorized isolated implementation proposal, **not TD or QĐ approval**. TD must review service/locking/history/checkout contracts; QĐ must review adapter/binding/CSRF/audit evidence; HP retains final acceptance. No messages sent on owners' behalf. Scope proposal `docs/QD-SHIP03-SHIP04-PROMO03-scope.md` is not an implemented API. Boundary request was read from `origin/test/qa-03-integrated-seed`, not present on accepted develop; no unmerged implementation is copied. PR #69 and other worktrees remain unchanged. No migration is needed for existing PLATFORM columns/constraints. Product-promotion extension requires a separate exact service/target/locking/audit agreement before wiring Admin UI. PROMO-03 overall is not COMPLETED; QA-02/03 and release gates remain unchanged.

### Fresh actor boundary detail

`RoleGovernanceService.requireCurrentActiveAdministratorForUpdate()` is a narrow MANDATORY service guard. It derives its own current actor and uses existing governance scalar account/role queries under the account row lock. This deliberately avoids authorizing from a previously cached JPA UserEntity in an outer transaction. It acquires no Role row lock after Account. PLATFORM management delegates this guard; no identity repository or UserEntity crosses into Promotion. A PostgreSQL race test preloads ACTIVE in the caller's JPA persistence context, then commits status/role revocation while management waits and proves rejection. QĐ must review this bounded governance addition with the adapter; no existing guards/policies are changed.
