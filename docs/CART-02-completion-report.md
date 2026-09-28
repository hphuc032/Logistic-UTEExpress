# CART-02: Complete cart actions

Owner: Tiến Đạt. Base: `develop` at `5d2f033` (VENDOR-02 PR #22 merged).
Branch: `feature/cart-02-cart-actions`.

## Implementation

CART-01's persistent Cart/CartItem, unique owner/product constraints, authenticated
owner provider and pessimistic cart lock are reused. No schema or merged migration
is changed. Removing the last line keeps the cart. Selection is a persisted
preference, including on invalid lines, and is not evidence of purchase eligibility.

New JSON actions, all authenticated and CSRF protected:

| Route | Request | Result |
| --- | --- | --- |
| POST `/user/cart/items/{id}/quantity` | `{"quantity": 2}` | Updated CartView |
| POST `/user/cart/items/{id}/remove` | No fields | Updated CartView |
| POST `/user/cart/items/{id}/selection` | `{"selected": false}` | Updated CartView |

The original GET `/user/cart` and POST `/user/cart/items` contracts remain available.
The Thymeleaf UI is GET `/user/cart/view`, with form actions under
`/user/cart/view/items/{id}/{quantity,remove,selection}` and POST/Redirect/GET.
It reuses the shared layout, alerts, Bootstrap controls and responsive table.
JSON unknown fields are rejected; MVC DTOs have explicit field allowlists.
Owner, cart and prices always come from the server. Foreign item IDs return the
same RESOURCE_NOT_FOUND as missing IDs; MVC displays a safe flash error.

## Product and Inventory integration

VENDOR-02 supplies `DatabaseInventoryService`. Add (including accumulated quantity)
and quantity update call `InventoryService.lockAndCheck` inside the cart transaction.
They never call `decrease` or `restore`. Quantity <= 0 and integer overflow are
validation failures; insufficient stock is CONFLICT; unavailable products use
RESOURCE_NOT_FOUND, matching the existing provider convention.

The existing Catalog boundary gains `findCartProducts(Set<Long>)`, returning immutable
`CartProductSnapshot` DTOs with current name, price, stock and purchasability.
Missing IDs are omitted; hidden/unavailable rows remain readable. Availability SQL
is shared with the strict `requirePurchasableProducts` path: ACTIVE product,
APPROVED shop and active category. VENDOR-02 soft deletion is HIDDEN status.
No Product entity, Inventory service or availability rule is copied into Cart.
The existing strict Catalog contract used by other consumers is unchanged.

Cart lines expose AVAILABLE, UNAVAILABLE, OUT_OF_STOCK or INSUFFICIENT_STOCK.
Missing products have an ID-based display label and null price, never a fabricated
price. Invalid rows stay in the cart and can be unselected or removed. Their line
subtotal is zero. Cart subtotal sums valid lines at current catalog prices;
selectedSubtotal sums selected valid lines. Neither total is persisted or accepted
from the client. A later stock reduction can invalidate an existing quantity;
the next read displays that state rather than silently changing the user's cart.

## Concurrency and integration limits

All mutations lock the owner's cart before reading/writing its lines, preserving
CART-01's serialization. Add/update then use Inventory's existing ordered Product
PESSIMISTIC_WRITE locks until transaction end. Independent quantity/selection
changes cannot overwrite each other. Absolute quantity assignments serialize;
the later assignment wins. Cart never reserves stock, so a successful update does
not guarantee future availability. Checkout must revalidate prices, availability
and inventory in its own transaction and must not trust selection alone.

Inventory's existing lockAndCheck contract accepts one batch per transaction;
cart actions are individual transaction entry points, not a bulk-checkout API.
Shop/category availability follows the existing provider's read-time semantics;
this task does not introduce new locking across those modules.

## Cross-owner files

- HP Catalog: `CatalogQueryService`, `DatabaseCatalogQueryService`,
  `CatalogReadRepository`, new `CartProductSnapshot`, and
  `DatabaseCatalogQueryServiceTest`: add and verify the tolerant display contract
  while keeping availability logic in its owning module.
- Shared UI: `templates/fragments/navbar.html`: add a link to the Cart page.
- HP Security: `SecurityConfig` and `security/controller/LoginController`: real-browser
  testing found the session authentication strategy cleared the CSRF cookie on every
  JWT-authenticated request, including CSS/JS loads. Keep ordinary JWT restoration
  from rotating CSRF; explicitly clear the token on successful login instead.
  CSRF validation, XOR form tokens, cookie attributes and logout clearing remain
  enabled. The integration test uses actual cookies and rendered tokens (no mocked
  `csrf()` helper) and checks login rotation, asset requests, valid submission,
  missing/invalid token rejection, flash rendering and logout invalidation.
- Product entity, ProductService, InventoryService, DatabaseInventoryService and
  existing migrations are unchanged.

## Verification

The existing CART-01 overflow test keeps its assertions; its fixture now sets
sufficient stock so the test still reaches arithmetic overflow under the new add
stock validation.

### Final CSRF integration fix

The null cookie was `anonymousCsrf`, read from GET `/login`, before the login POST
was even dispatched. The failed response had no `Set-Cookie` header and contained
an `HttpSessionCsrfTokenRepository.CSRF_TOKEN` session attribute. The cookie name
and MockMvc cookie accessor were correct.

Spring Security Test 7.1.1's `CsrfRequestPostProcessor.postProcessRequest` replaces
the shared `CsrfFilter` repository with `TestCsrfTokenRepository` wrapping
`HttpSessionCsrfTokenRepository` when another test uses `.with(csrf())`. That
mutation survives between methods sharing the application context. Consequently,
this was test-context contamination, not a failure of the production cookie
repository to issue the anonymous token.

`CartActionsIT.realCsrfCookieSurvivesJwtAssetRequestsAndProtectsFormSubmissions`
now uses method-level `@DirtiesContext(BEFORE_METHOD)` to load the production filter
chain before its fixture and requests. No test is disabled or reordered, and no
production code is changed for this final fix. Explicit cookie assertions identify
missing cookies at their source. Tokens are extracted from rendered HTML rather
than request attributes. The test also verifies persisted selection, rejects
missing/invalid logout tokens, checks both logout cookie deletions and rejects
the old JWT after logout.

The existing shared Security changes remain necessary for the stateless JWT form
flow: the default `CsrfAuthenticationStrategy` deletes an existing token when
authentication is restored, including on asset requests. `SecurityConfig` avoids
that per-request rotation; `LoginController` explicitly clears the anonymous token
only after successful credential authentication. CSRF validation and the default
logout CSRF handler remain enabled. The integration test exercises login rotation,
CSS/JS and repeated page requests, successful form submission, 403 responses for
missing/wrong tokens, and logout against real PostgreSQL-backed authentication.

Focused command (JDK 21, Maven Wrapper 3.9.11):

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-it '-Dit.test=CartActionsIT#realCsrfCookieSurvivesJwtAssetRequestsAndProtectsFormSubmissions' test-compile failsafe:integration-test failsafe:verify
```

Result: **1 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

Full gate command:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-it verify
git diff --check
```

Actual final gate results, 2026-09-28 (Maven exit code 0, **BUILD SUCCESS**):

| Scope | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit / MVC / architecture | 274 | 0 | 0 | 0 |
| PostgreSQL integration | 133 | 0 | 0 | 0 |
| Total | 407 | 0 | 0 | 0 |

Regression subsets within those totals (not additional tests):

- CART-01 `CartDatabaseIT`: 10/10 PASS.
- CART-02 `CartActionsIT`: 33/33 PASS, including the real-cookie test in the full suite.
- `CartServiceTest`: 17/17 PASS.
- Existing `com.uteexpress.security` tests: 42 unit/MVC + 5 integration = 47/47 PASS.
- `DatabaseBaselineIT`: 1/1 PASS; Flyway validates 14 migration entries, repeat
  migration is a no-op, and application contexts start with Hibernate schema
  validation enabled (`ddl-auto: validate`).
- `scripts/Test-DatabasePlan.ps1`: PASS (30 tables, valid owners/references, no cycle).
- `git diff --check`: PASS. Common secret-pattern and generated-artifact scans: PASS.

Local evidence: `.work-cart-final-fix-focused.log`,
`.work-cart-final-fix-verify.log`, `target/surefire-reports`,
`target/failsafe-reports`, and `.tools/cart-quality-result.json` (ignored artifacts).

Status: **READY FOR COMMIT**. No commit, push, PR creation or merge was performed.

## Deferred and next task dependencies

No checkout, order creation, shipping quote, payment, voucher or promotion is added.
USER-02 is merged via PR #20; its AddressQueryService is present. SHIP-00 is merged
via PR #12; its database-backed ShippingQuoteService is present. CART-02 requires
review/merge before being treated as an available develop dependency for CHK-01.
