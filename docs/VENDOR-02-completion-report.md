# VENDOR-02 Completion Report

## Scope

VENDOR-02 adds Vendor-owned Product management, image management, soft delete, and the database-backed `InventoryService` needed by checkout flows. It reuses the Product and ProductImage schema from PROD-00 and the shared image storage pipeline from USER-01. No Flyway migration is required.

## Product management

- `ROLE_VENDOR` is required at the service boundary.
- The current account is resolved with `CurrentAccountIdProvider`; browser-supplied shop or owner identifiers are never trusted.
- `VendorShopQueryService` exposes only the approved owned Shop identifier and name to catalog code. No Shop entity or repository crosses the module boundary.
- `CategoryQueryService` exposes active Category DTOs. Create and update reject missing or disabled categories.
- Create, update, list, and soft delete use DTOs rather than binding Product entities.
- New Products are `ACTIVE`. Vendor delete changes the status to `HIDDEN`; Product rows and ProductImage rows remain present.
- Name, description, positive whole-VND price, nonnegative stock, and active Category are validated in the form/service and remain protected by database constraints.
- Update and hide require the submitted `@Version` value to match the persisted Product, returning a safe conflict for stale forms.

## Product images

- `FileStorageService` and `LocalFileStorageService` are reused with the `products` namespace and server-generated UUID keys.
- Client filenames never become storage paths.
- JPEG, PNG, and WebP are accepted only after content decoding and MIME validation. SVG and spoofed, malformed, oversized, or excessive-dimension images are rejected.
- Limit: 5 images per Product, 5 MiB per image, maximum 4096 x 4096 pixels.
- Product and image ownership are checked before upload, read, or delete. The preview route reads the server-owned storage key and sends `Cache-Control: no-store` plus `X-Content-Type-Options: nosniff`.
- A newly stored file is deleted after transaction rollback. Image storage is deleted only after its database delete commits.

## Inventory contract

- `DatabaseInventoryService` implements the existing `InventoryService`; no interface change was required.
- `lockAndCheck`, `decrease`, and `restore` use `Propagation.MANDATORY`, so the checkout caller owns the transaction.
- Product rows are locked with PostgreSQL `PESSIMISTIC_WRITE` in ascending Product-ID order.
- Duplicate Product IDs and invalid quantities are rejected before locking.
- `lockAndCheck` validates that every Product exists, is purchasable, and has sufficient stock. The locked batch is bound to the current transaction.
- `decrease` accepts only the exact batch locked in the same transaction and can execute once. Caller rollback restores all changes.
- `restore` locks the same rows before incrementing stock and rejects integer overflow.
- No `REQUIRES_NEW` transaction is used and stock cannot become negative.

## Routes and UI

- `GET /vendor/products`
- `GET /vendor/products/new`
- `GET /vendor/products/{id}/edit`
- `POST /vendor/products`
- `POST /vendor/products/{id}/update`
- `POST /vendor/products/{id}/delete`
- `POST /vendor/products/{id}/images`
- `POST /vendor/products/{id}/images/{imageId}/delete`
- `GET /vendor/products/{id}/images/{imageId}/content`

The Thymeleaf pages reuse the shared Bootstrap layout, escaped output, responsive tables/forms, flash alerts, and CSRF-enabled `th:action` forms. The navbar shows Product management only to authenticated Vendors.

## Verification

- T14: cross-Shop Product CRUD, image upload/delete, and image reads are denied; same-Shop cross-Product image deletion is also denied.
- T15: blank name, nonpositive/fractional price, negative stock, missing Category, and disabled Category are rejected.
- T16: soft delete keeps the Product row and preserves Order/OrderItem identifiers and name/price snapshots.
- T23: concurrent checkout-style transactions competing for the final item yield at most one success and final stock zero.
- Image tests cover JPEG/PNG/WebP, spoofing, truncation, dimensions, size, SVG, traversal-style filenames, rollback compensation, and post-commit deletion.
- Inventory tests cover the required caller transaction, exact batches, insufficient stock, rollback, restore rollback, overflow, and concurrent locking.
- Real JWT role enforcement and CSRF behavior are verified against the MVC routes.
- `mvnw.cmd clean test`: PASS, 261 tests.
- `mvnw.cmd package`: PASS, 261 tests.
- `mvnw.cmd -Ppostgres-it clean verify`: PASS, 261 unit/MVC plus 97 PostgreSQL integration tests.
- Total distinct tests in the full PostgreSQL gate: 358; failures: 0; errors: 0; skipped: 0.
- PostgreSQL 17.6, Flyway validation, Hibernate `ddl-auto=validate`, Catalog, Cart, Order, and Architecture regressions pass.

## Migration decision

VENDOR-02 migration: **NONE**. PROD-00 already provides every required Product/ProductImage column, check constraint, foreign key, unique position constraint, and optimistic version column. Existing merged migrations were not modified.

## Deferred

- Public catalog pages and discovery: PROD-01 / PROD-02.
- Cart integration changes: CART-02.
- Checkout orchestration: CHK-01 / CHK-02.
- Product moderation: ADMIN-05.
- Product variants, warehouses, and remote object storage remain out of scope.
- The existing cross-owner Order Product/Shop foreign-key coordination remains with the Order owner; VENDOR-02 preserves history without changing that schema contract.
