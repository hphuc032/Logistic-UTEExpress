# PROD-00 — Product catalog foundation

Owner: Hoàng Phúc. Reviewer: Tiến Đạt. Base: develop `d0df02e`.

## Persistence contract

Migration `V20260924005244__hp_prod00_catalog_foundation.sql` creates
`uteexpress.products` and `uteexpress.product_images` after the merged Shop and
Category migrations. Products hold scalar `shop_id` and `category_id` values;
the Java model has no cross-module entity association. Both foreign keys and the
ProductImage-to-Product foreign key reject missing parents and use no delete
cascade.

Product prices use `BigDecimal` / `NUMERIC(19,2)`. The database rejects zero,
negative, `NaN`, and fractional-VND prices. Stock is an integer constrained to
zero or greater. `ProductStatus` contains only `ACTIVE` and `HIDDEN`, and Product
uses `@Version Long` with initial database value zero. Required indexes cover
`(category_id, status)` and `(shop_id, status)`. ProductImage persists only a
storage key, nonnegative display position, and optional alt text; positions are
unique within a Product. No upload or storage implementation exists in this task.

Repositories extend Spring Data's narrow `Repository` marker and declare no
delete method. Product mutations and status transitions remain outside PROD-00.

## Catalog query boundary

`DatabaseCatalogQueryService` is the production implementation of the existing
ORD-00 `CatalogQueryService`. Its catalog-owned JDBC read repository joins the
three tables directly and returns only `ProductSnapshot`; it does not import Shop
or Governance entities or repositories.

A requested Product resolves only when Product is `ACTIVE`, its Shop is
`APPROVED`, and its Category is active. Stock is deliberately not part of this
decision because `InventoryService` owns stock availability. A missing or
unavailable member rejects the complete batch with `RESOURCE_NOT_FOUND`, without
revealing which rule failed. Null/nonpositive IDs are invalid, an empty set
returns an empty immutable list, and successful snapshots are ordered by Product
ID. Names, Shop IDs, prices, and versions come from PostgreSQL.

The existing `InventoryService` remains contract-only. PROD-00 adds no fake bean,
pessimistic locking, decrement, restore, checkout transaction, or inventory
release behavior. VENDOR-02 owns that implementation.

## Demo boundary

`ProductDemoSeeder` is active only under the `demo` profile, outside `prod`, and
with `uteexpress.demo.catalog.enabled=true`. It resolves prerequisites using the
configurable `uteexpress.demo.catalog.shop-slug` and
`uteexpress.demo.catalog.category-slug`; the Shop must be approved and the
Category active. It never creates or changes either prerequisite.

The seed inserts three fictional products: active/in-stock, active/out-of-stock,
and hidden/in-stock. Stable demo names scoped to the configured Shop are the
narrow fixture identity because the approved Product schema has no SKU or slug.
An advisory transaction lock serializes concurrent seeders. Reruns insert only
missing fixture names and never reset an existing row. There is no automatic
production seed and no synthetic image file. PROD-01 still owns the larger public
demo catalog.

## Integration boundaries

CART-01 can inject `CatalogQueryService` and use PostgreSQL-backed
`ProductSnapshot` values without accessing Product persistence internals.
`InventoryService` remains unavailable until VENDOR-02.

The Product parent now exists for ORD-01's deferred
`fk_order_items_product_id`, but the merged TD-owned migration and Order code were
not modified. A new follow-up migration must be coordinated with Tiến Đạt. The
already-unblocked `orders.shop_id` foreign key needs the same owner coordination.

Vendor Product CRUD/UI, image upload/management, inventory mutation, public
catalog pages, search/filter/ranking, and moderation workflows remain deferred.
JWT, OTP, CSRF, roles, SecurityConfig, and all public routes are unchanged.

## Verification

- `mvnw.cmd clean test`: PASS — 191 tests.
- `mvnw.cmd package`: PASS — 191 tests and executable jar packaging.
- `mvnw.cmd -Ppostgres-it verify`: PASS — 191 unit/MVC/architecture tests and
  55 PostgreSQL 17.6 integration tests. `ProductCatalogIT` contributed 6 tests.
- Flyway validation, repeat migration, Hibernate `ddl-auto=validate`, named
  constraints/indexes, server-derived snapshots, unavailable-state rejection,
  deterministic batches, and demo idempotency are covered by Testcontainers.

The ORD-01 integration assertion that historically required the Product parent
table to be absent now expects both Shop and Product parents to exist while still
requiring exactly the original five Order foreign keys and an absent
`commission_policies` parent. No Order migration, entity, service, or deferred
cross-owner foreign key was changed; Tiến Đạt should review this narrow test-only
expectation update.
