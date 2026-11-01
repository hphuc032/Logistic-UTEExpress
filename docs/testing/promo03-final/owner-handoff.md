# PROMO-03 final-source owner handoff

Source `15fb1dda4656892cbb477b4c0e47753bed6a66fc`; developbasea5620f1; PR78. HP directly instructed full completion and merge. This is merge authorization, not a forged independent approval. TĐ/QĐ earlier scoped opinions remain limited to67c7d54. Current implementation is ready for delta review with actual regression/browser evidence; protected merge needs actual owner approval.

## TD — exact business delta to review

- `catalog.service.AdminProductPromotionService` owns Admin Product resolution,20-row search/pagination, server Product/Shop identity, create/update eligibility (ACTIVE +APPROVED), read/disable unavailable schedules, and Account→Product→Shop sentinel locks.
- `promotion.service.PromotionManagementService` retains VENDOR class guard and unchanged public Vendor signatures; narrow ADMIN/MANDATORY methods reuse shared private create/update/disable primitives. Creator comes from persisted current Admin, immutable on update. No second pricing engine, global schedule subtype or stacking behavior.
- Promotion schedules stay Product-owned: Vendor owner keeps current management rights even for Admin-created rows. Existing time/percentage validation, expected versions, overlap/exclusion and SQL pricing authority remain unchanged. Disabled history retained; no Order/Payment/Refund snapshots rewritten.
- Shared `PromotionDefinitionForm` is extracted unchanged, with separate Admin/Vendor subclasses. ShopAvailabilityService exposes fresh Shop lock after Product, permits read/disable unavailable targets, rejects create/update if not APPROVED.
- Review 41AdminProductPromotionIT tests: overlap/version/forgedinput/role/status, auditrollback, stale cached Shop suspension, Admin-versus-Vendor sentinel serialization and checkout commit/rollback/wait/snapshot proofs. Existing ProductPromotion/VendorVoucher/VoucherCheckout/refund regressions included in full run.

## QĐ — exact adapter/security delta to review

- AdminProductPromotionController: GET/admin/promotions product selection; GET/POST/admin/promotions/products/{productId}; /new; /{id}/edit; POST/{id}/update; GETconfirmation +POST/{id}/disable. Strict allowlists and URI-ID tamper rejection, no repository/entity calls.
- Existing PLATFORM voucher service unchanged; controller adds safe GET disable confirmation. Both confirmation actions require current persisted Admin at read/write; final POST requires CSRF/version. Cancel link cannot mutate.
- Fresh persisted actor uses existing guarded Governance boundary. Product writer's ADMIN methods do not widen Vendor methods. Audit stays inside Catalog mutation transaction; failure rolls back all effects.
- Templates retain shared UI/XSS conventions. Invalid promotion fields use aria-invalid and visible focus; Admin dashboard navigation includes both management sections. No SecurityConfig, JWT, pricing, migrations, POM or CI changes.
- Browser packet:33actual automated Chrome cases,375/768/1440, source/JAR/route provenance, safe network summaries, DB version/actor/audit/noPOST checks and screenshots. Not a human or independent QĐ execution. Raw logs/XML local with hashes for review, no credentials published.

## HP / merge gate

HP contract: ADMIN only; current PROMO-01 used-voucher mutability; Product-scoped promotion contract finalized by direct completion instruction. HP authorized normal merge after tests/review/protection. No admin bypass, self-approval, forcepush or policy weakening. Review exact final publication HEAD; docs-only delta may reuse input-identical source evidence, never relabel executions. Independent reviewers can inspect packet before formal GitHub approval. QA/release gates stay unchanged.
