# PROD-02 Completion Report

## Scope

PROD-02 extends the public `/products` catalog with server-rendered discovery. It supports a case-insensitive product-name search, approved Shop and active Category filters, an inclusive whole-VND price range, bounded zero-based pagination, and a server-owned sort allowlist.

The request DTO accepts only `q`, `shop`, `category`, `minPrice`, `maxPrice`, `sort`, `page`, and `size`. Blank text is normalized to absent values. Search text is limited to 120 characters, prices must be non-negative whole VND values, `minPrice` cannot exceed `maxPrice`, the default page size is 12, and the maximum is 48.

## Query and security contract

All public discovery queries retain the authoritative visibility predicate:

```text
product.status = ACTIVE
shop.status = APPROVED
category.active = true
```

Search and filter values use named JDBC parameters. `%`, `_`, and `\` in a search term are escaped before the `ILIKE` parameter is bound, so wildcard and SQL-injection payloads remain literal search text. The client cannot supply a column or raw `ORDER BY` expression. An unknown sort value falls back to `newest`.

The implemented sort values are:

- `newest`: product creation time descending, then product ID descending.
- `priceAsc`: price ascending, then product ID ascending.
- `priceDesc`: price descending, then product ID descending.
- `bestSelling`: delivered quantity descending, then product creation time and ID descending.

`bestSelling` derives `SUM(order_items.quantity)` through a read-only aggregate over Orders whose current status is exactly `DELIVERED`. `CANCELLED`, `RETURNED`, and `REFUNDED` Orders do not contribute. A left join and `COALESCE` retain public products with zero delivered sales.

The count and page queries share the same visibility and optional filter builder. Results use `LIMIT` and `OFFSET`, and expose total items, total pages, previous/next state, page, and size. Thymeleaf pagination links preserve all discovery parameters and encode them through Thymeleaf URL expressions. Dynamic content uses escaped Thymeleaf output; no `th:utext` was introduced.

## Top-rated status

**TOP_RATED BLOCKED BY REVIEW DATA SOURCE.** The current repository has no Review table, entity, repository, or approved public-rating read contract. The option is intentionally absent from the UI and sort enum. PROD-02 does not create a Review model, persist fake ratings, or derive ratings from unrelated data. A future Review integration can add the sort when its real visibility contract is available.

## Database and migration

No Flyway migration is required. Discovery reads the existing `products`, `shops`, `categories`, `orders`, and `order_items` schema without modifying Order, Cart, moderation, or commerce contracts.

## Verification

- Unit/service tests cover normalization, safe sort fallback, page metadata, and service-boundary price/size validation.
- MVC tests cover public rendering, query-parameter preservation, validation without a database query, escaped search output, and omission of the blocked top-rated option.
- PostgreSQL Testcontainers tests cover Flyway/schema validation, case-insensitive and literal wildcard search, SQL- and sort-injection resistance, combined filters and count, deterministic pagination and sort, delivered-only best-selling rank, zero-sale retention, and public visibility.
- `mvnw.cmd clean test`: PASS, 292 tests, 0 failures, 0 errors, 0 skipped.
- `mvnw.cmd package`: PASS, including the same 292-test suite.
- `mvnw.cmd -Ppostgres-it clean verify`: PASS, 292 unit/MVC tests plus 153 PostgreSQL integration tests; 445 total, 0 failures, 0 errors, 0 skipped.
- `ProductDiscoveryIT`: 7 PostgreSQL tests, all passing.
