# PROD-01 — Public catalog

Owner: Hoàng Phúc. Reviewer: Tiến Đạt. Base: `develop` at
`f8fa1648c3e4b827dc8b60373c66498b687c6dbb`.

## Public browsing contract

The catalog-owned `PublicCatalogService` and `CatalogReadRepository` provide
database-backed read projections for:

- `GET /`
- `GET /products`
- `GET /products/{id}`
- `GET /categories/{slug}`
- `GET /shops` and `GET /shops/{slug}`
- `GET /products/{productId}/images/{imageId}/content`

Every Product read reuses one visibility predicate: Product `ACTIVE`, Shop
`APPROVED`, and Category active. Product `HIDDEN` or `MODERATED`, any Product in
a non-approved Shop, and any Product in a disabled Category are indistinguishable
from a missing resource. Zero-stock Products remain visible and show `Hết hàng`.
Basic lists use deterministic `created_at DESC, id DESC` ordering. Search,
filters, pagination, sorting controls, newest/best-selling/top-rated ranking, and
reviews remain with PROD-02 and later tasks.

The home page reads at most 12 Product cards with one projection query, including
the first ordered image identifier through a lateral join. Product cards and
detail views contain public display fields only. Controllers depend on the
service boundary and never access repositories or entities. Existing
`CatalogQueryService` Cart/Inventory methods and their commerce behavior remain
compatible and reuse the same visibility predicate.

## Media and rendering security

The public image endpoint accepts Product and image identifiers only. The
repository first proves that the image belongs to the requested publicly visible
Product, then the service reads the server-owned storage key through
`FileStorageService`. Storage paths and keys are never exposed. Responses use
the validated `StoredContent` MIME type, content length, `Cache-Control:
no-store`, and `X-Content-Type-Options: nosniff`. Moderation, hiding, Shop
suspension, and Category disabling immediately remove both HTML detail and image
access.

Product, Shop, Category, description, and image-alt values render with escaped
Thymeleaf expressions; public templates contain no `th:utext`. Route values use
bounded/canonical validation and parameterized SQL. Public browsing is GET-only,
and the existing CSRF and role policies were not changed.

## UI and demo data

The existing shared Bootstrap layout now includes reusable responsive Product
cards, gallery/detail styles, Category links, Shop cards, empty states, and
mobile rules at the existing Bootstrap-aligned breakpoints. The original landing
hero and shared navigation remain intact.

`ProductDemoSeeder` remains guarded by `@Profile("demo & !prod")` and
`uteexpress.demo.catalog.enabled=true`. It requires the configured approved
primary Shop and active Category, creates a reserved disabled demo owner and a
second approved demo Shop only in that explicit demo environment, and inserts 12
visible Products across the two Shops. Separate HIDDEN and MODERATED fixtures do
not count toward the public total. The transaction advisory lock and
insert-if-absent behavior make reruns deterministic and idempotent without
resetting edited rows.

## ADMIN-05 and regression behavior

Integration tests prove that `ACTIVE -> MODERATED` and `APPROVED -> SUSPENDED`
remove Product pages, Shop pages, and media immediately. Restoring the states
makes otherwise eligible Products public again. Vendor Product/image, Cart,
Admin moderation, Shop approval, security, UI, and Architecture suites remain
green. The existing Product/Shop/Category/Image schema already supports this
read-only feature, so PROD-01 adds no Flyway migration and modifies no merged
migration.

## Verification

Executed with Java 21 and Docker Desktop/PostgreSQL 17.6:

- `mvnw.cmd clean test`: PASS — 286 tests, 0 failures, 0 errors, 0 skipped.
- `mvnw.cmd package`: PASS — 286 tests and executable jar packaging.
- `mvnw.cmd -Ppostgres-it clean verify`: PASS — 286 unit/MVC/architecture tests
  plus 146 PostgreSQL integration tests; 432 total, 0 failures, 0 errors, 0
  skipped.
- PROD-01 contributes 10 unit/MVC tests and 7 PostgreSQL integration tests.
- PostgreSQL coverage includes fresh Flyway migration, Hibernate validation,
  public visibility, home count and multi-Shop reads, details, Shop/Category
  pages, media ownership, moderation/suspension transitions, XSS escaping, and
  demo-seed idempotency.
- `git diff --check`: PASS.
