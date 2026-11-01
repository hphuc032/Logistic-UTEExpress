# PROMO-03 owner handoff

Implementation source: `cd3651f59ca22e2805675b5be589e8150784c65e`, based on develop `a5620f1971d3b684d9b05d7444f5e90442404656`. This packet prepares review; it is not owner approval or final HP acceptance. No owner messages or approvals are fabricated.

## TD — voucher business authority review required

- Review `promotion.service.PlatformVoucherManagementService` and PLATFORM-only repository queries. Definition DTOs inherit unchanged validation extracted into `VoucherDefinitionForm`; `VendorVoucherForm` retains its public API. No entity binding or second pricing engine.
- Contract: `list(): List<PlatformVoucherView>`, `get(Long): PlatformVoucherView`, `requireAdministrator(): void`, `create(@Valid PlatformVoucherForm): Long`, `update(Long,@Valid PlatformVoucherUpdateForm): void`, `disable(Long,@Valid VoucherVersionForm): void`.
- Forms contain only code/type/value/maxDiscount/minSubtotal/startsAt/endsAt/totalLimit/perUserLimit/active, plus expected version for update; disable contains only version. No scope/shop/creator/usage/snapshot input.
- PLATFORM/no shop/actor are server-derived. Unique code and monetary rules remain entity/DB-authoritative. Expected version checked after Voucher refresh under write lock. Both REDEEMED and RELEASED history trigger Voucher.update restrictions.
- HP explicitly retains PROMO-01 historical rules for PLATFORM despite stricter original Plan I7 wording: freeze code/type/value/maxDiscount/minSubtotal; quota may only increase; window/enabled editable. Review this recorded variance; do not rewrite SHOP behavior.
- Review Account → Voucher lock order against existing checkout/cancellation/refund locks and real PostgreSQL wait/usage proof. No new Order/Product/Usage locks. No migration or lifecycle, financial, usage-release or refund changes.

## QĐ — adapter, freshness guard and audit review required

- `PlatformVoucherController` owns `/admin/vouchers`, `/new`, `/{id}` and `/{id}/edit`, POST create/update/disable. Its repository/entity dependencies are absent. New templates use current shared layout, safe escaped output, PRG, CSRF and version fields. Dashboard link is Admin-only; unchanged SecurityConfig protects `/admin/**`.
- Review strict allowlists: scope/shop/creator/usage/financial injection produces a safe error, never authority; transport headers are excluded; HTTP id parameter is rejected, URI id remains path-derived with no writable DTO field.
- `RoleGovernanceService.requireCurrentActiveAdministratorForUpdate()` is a narrow MANDATORY current-actor guard using existing fresh scalar account/role queries, preventing stale cached UserEntity authorization. Account lock precedes Voucher; no Role row lock follows it. Existing policies/guards are unchanged.
- Audit append is MANDATORY and participates in management transaction. Only server codes, actor, target, active and version metadata are used. Audit failure rolls back create/update/disable. Repeated already-disabled current-version request is a no-op, without duplicate audit. Stale versions still conflict.
- Review `PlatformVoucherManagementIT` (including real JWT cookie + rendered CSRF and rollback/revocation tests) and two new `VoucherCheckoutIT` methods. Verify render evidence is MockMvc, **not independent browser/manual evidence**.

## Product-promotion administration — boundary agreement required, NOT IMPLEMENTED

HP chose “Mở thêm Admin product-promotion management sau khi chốt boundary TD/QĐ”. Plan explicitly includes promotions and vouchers; this is an unresolved required slice, not a silent deferral or a completed capability.

Proposed discussion boundary (NOT an approved API):

| Decision | Proposed direction for TD/QĐ/HP review | Status |
|---|---|---|
| Target semantics | Existing one-Product Promotion records, not an invented global stacking engine. TD must specify allowed Product/Shop states and whether Admin may select any product or only eligible published products | OPEN |
| Authority placement | Catalog Admin facade resolves Product/Shop and locks Product sentinel before Shop/Promotion; Promotion keeps one-way dependency and existing scheduling/pricing authority | OPEN |
| Authorization | Separate ADMIN entry, persisted current actor check; do not simply broaden Vendor writer's class-level guard or reuse a Vendor principal | OPEN |
| Internal writer API | TD defines exact narrow Admin create/update/disable DTO/transaction contract, preserving overlap exclusion, expected version, UTC windows and existing checkout pricing | OPEN |
| Adapter | QĐ defines `/admin/promotions` selection/list/detail/forms through the approved service boundary; strict binding, CSRF, audit rollback; no repository calls from controller | OPEN |
| Audit/lock evidence | Account → Product → Shop → Promotion; audit in same transaction; real PostgreSQL sentinel/overlap/checkout race tests and Vendor regression | OPEN |

Do not wire that UI until exact contract and owner agreement are recorded. No active TD/QĐ branch or PR #69 is changed. Owner acknowledgement, independent rerun/review and HP final acceptance remain outstanding. QA-02/03, Q70-S01 and release gates remain unchanged.
