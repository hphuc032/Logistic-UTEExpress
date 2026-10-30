# REV-01 foundation — QD handoff

Baseline develop 1b3203d (PAY-02 #56 merged; SHIP-04 #55 merged). Implements only independent QD Review foundation. This is not a completed Review vertical slice and must remain Draft until real Order-owned eligibility and end-to-end integration exist.

## Delivered foundation

New migration V20261023170000__qd_rev01_review_foundation.sql, after PAY-02 V20261022090000. No applied migration edited. Review stores item, Order, authenticated user and Product; same-Order/item/product composite FK and existing Order/buyer composite FK prevent forged attribution; UNIQUE(order_item_id) prevents duplicates including concurrent insertion. Adds matching OrderItem composite unique target in NEW migration (requires TD schema review). Rating 1–5, comment 50–2000 chars and nonblank, visibility PUBLIC/HIDDEN, server default created_at. Product/visibility/time index prepares bounded public display. No cascade financial/history deletion.

Review media accepts image/jpeg/png/webp only, UUID reviews namespace server keys, unique storage_key and three unique positions 0–2 per review. DB constraints do not prove file content: future upload flow must call existing FileStorageService.storeImage with ReviewImagePolicy (5 MiB, 4096x4096, max 3 images), never trust extension/MIME alone. No video, SVG, HTML or client-supplied storage key. ReviewContent strips before rating/length validation, rejects NUL; comments remain plain text and MUST render with th:text. DTOs confer no eligibility/authorization.

Migration compatibility changes only append this version to exact expected pending lists in Receipt/Refund upgrade tests. Prior constraints, immutable ledger and old checksum tests remain intact. This reservation is specific to Review; it does not alter PAY-02/SHIP-04 history.

## Missing TD dependency — do not bypass

OrderEligibilityQueryService.isCurrentBuyerEligibleToReview currently has no bean/implementation. Existing order-contracts.md requires current buyer ownership and Order DELIVERED; RETURNED/REFUNDED is not eligible. QD adds no direct Order query or fake successful guard.

Proposed TD handoff to agree: current-user Order/item eligibility authority in the write transaction, with Order lock held through Review commit so Return cannot race creation. Persisted facts (Order ID, Item ID, Product ID, buyer identity, original Order version/state) must come from TD service/DTO, not browser owner/product IDs or foreign repositories. The existing boolean-only API cannot itself supply attribution facts; TD should propose a compatible extension or dedicated trusted facts operation. Read-only eligibility display is not a reservation. Agree Order -> Account -> Review lock order and role/ACTIVE freshness with HP/TD, reject Account-first outer transactions; no unreviewed interface edits by QD.

After TD branch/SHA and protocol approval, QD adds actual create/list/media service and HTTP/form/public display. No successful endpoint, fake Order authority or completion claim is included in this foundation.

## Verification scope

ReviewFoundationTest covers rating/text trim/bounds and image count/policy. ReviewFoundationMigrationIT covers fresh and deployed Shipping/Refund upgrades, checksum and historical Order preservation; foreign buyer/product, rating/content checks, two concurrent inserts with only one success, max 3 image positions, duplicates, separate path/type rejection. Receipt/Refund/Shipping upgrade regressions are run with this migration; see PR for final command/results. No DELIVERED ownership/lifecycle/CSRF/upload acceptance is claimed until actual TD boundary integration.

Outstanding: trusted Order facts/write guard; Account/Return races; image upload compensation on DB rollback and content decoding; HTTP CSRF/USER-VENDOR roles and strict DTO allowlist; public listing visibility/pagination and safe media serving; historical visibility after return requires engagement policy review. No Order/Payment lifecycle writes, refund or inventory effects. No merge.
