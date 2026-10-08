# PROMO-02 implementation and verification

STATUS: READY FOR REVIEW. Owner: Tien Dat. Reviewer: Hoang Phuc.

Branch remains `feature/promo-02-discounts`; HEAD and local develop remain
`ea42598311c3b1dacbaaab156563db8c591d87d5`. Initial implementation preflight was clean.
Targeted audit-fix preflight found the expected 37 modified tracked and 19 untracked files;
all existing PROMO-02 work was preserved.
No branch switch/create, staging, commit, push, merge or rebase was performed.
The final worktree contains 37 modified tracked files and 19 untracked files;
all are included below. The index is unchanged.

## Result

Product-scoped percentage schedules, immutable application Product/creator identities,
versioned Vendor management and CSRF forms are implemented. All application mutations
lock Account -> Product -> approved Shop -> Promotion. The PostgreSQL exclusion constraint
retains the independent overlap guarantee; no fallback weakens it. Catalog dependencies
remain one-way into Promotion, with existing ArchUnit rules unchanged.

The explicit-time PostgreSQL price projection is shared by catalog/cart/engagement/checkout;
effective-price SQL filters, counts and sorting run before pagination. Java HALF_UP parity,
whole-VND per-unit rounding, half-open windows and microsecond normalization are verified.
Existing Product base prices and OrderItem columns retain their meanings. Vouchers apply
only to post-promotion subtotal, with voucher-only discount_total and existing quota,
commission, replay, cancellation, shipping, stock, payment and lifecycle semantics.
Order and shipping production files, ORD-05, SHIP-02 and PROMO-03 are unchanged.

## Targeted audit fixes: M1 and L1

Only these three files changed during this follow-up: VendorProductPromotionController.java,
ProductPromotionIT.java and this verification document. SHA-256 comparisons against the
follow-up preflight verify that all other 53 changed files were preserved byte-for-byte.

M1 root cause: the binder allowlists included productId/id to accommodate MVC URI variables.
The DTOs have no writable identifiers, so client-submitted IDs were ignored rather than
suppressed and rejected. The allowlists now contain only editable form fields. URI identifiers
are suppressed; rejectTampering separately checks the actual request parameter map and rejects
submitted productId/id even if they match the URL. Suppressed non-identifier fields still cause
rejection. Resource identity remains exclusively path-derived and creator remains principal-derived.

The PostgreSQL MockMvc matrix checks ten fields (productId, id, shopId, ownerId, vendorId,
createdBy, creator, ownership, price and scope) across create/update/disable: 30 rejected POSTs,
including all six forged-identifier/operation combinations. Create inputs use a valid adjacent
window and update inputs change name/rate, so overlap or a no-op cannot conceal a missing guard.
Create/update assert the specific invalidFields error; disable asserts safe HTTP 400 feedback.
Every rejected request independently compares all persisted state, including both authorized
and foreign targets, to its prior snapshot. Existing valid forms, real JWT/CSRF creation,
optimistic versions and PRG tests remain passing.

L1 root cause: two management writers shared the Vendor account, so the earlier test observed
a users-row wait. That test remains under an accurate Account-lock name. The Product-lock test
now uses a separate transaction holding only SELECT ... products ... FOR UPDATE, with no Account,
Shop or Promotion row lock and an empty promotion schedule. The authenticated owner creates in
another transaction. The test identifies both PostgreSQL backend PIDs and requires the writer's
pg_stat_activity to show a Lock wait on a Products query with the holder present in pg_blocking_pids.
No schedule is inserted before release; after release, one correctly owned schedule is committed.
One-owner Shop authority cannot provide two independently eligible Vendors for the same Product;
the Product-only holder isolates contention without weakening application ownership or lock order.

## Gates

Java `21.0.12.1`, PostgreSQL `17.6`, btree_gist `1.7`.

| Gate | Surefire | Failsafe | Failures | Errors | Skips | Reruns | Flakes | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Fresh targeted PROMO-02 and architecture | 71 | 59 | 0 | 0 | 0 | 0 | 0 | 0 |
| Fresh full `mvnw.cmd -Ppostgres-it clean verify` | 421 | 622 | 0 | 0 | 0 | 0 | 0 | 0 |

Focused gate: 5 Surefire suites and 1 Failsafe suite, **130 tests**, finished at
2026-10-08 10:53:32 UTC+7 in 50.165 seconds. Full gate: 59 Surefire suites and 35 Failsafe
suites, **1,043 tests**. Fresh XML reports followed clean verify. Build finished at
2026-10-08 10:59:12 UTC+7 in 4 minutes 47 seconds. ArchitectureTest has 7 passing cases.
Both commands returned exit 0 and BUILD SUCCESS; all failures/errors/skips/reruns/flakes are zero.
Failsafe summary has completed=622, failures/errors/skipped=0 and timeout=false.
A 30-second fork-shutdown diagnostic occurred after successful tests while cached pools
were closing. Maven returned exit 0 and BUILD SUCCESS; there was no test timeout.

Historical pre-fix checkpoints were focused 71/54, expanded regression 71/337 and full
421/617 (Surefire/Failsafe). They predate M1/L1 and are superseded by the fresh gates above.

Final focused command:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Ppostgres-it -Dtest=PromotionTest,ArchitectureTest,PublicCatalogServiceTest,CheckoutQuoteServiceTest,CartServiceTest -Dit.test=ProductPromotionIT verify
.\mvnw.cmd -Ppostgres-it clean verify
```

Focused ProductPromotionIT covers the full migration chain under a NOSUPERUSER database
owner, extension installation, validation and a no-op second migration; SQL overlap races;
Product sentinel waits including schedule commit/rollback; safe ownership/allowlists/
versions/CSRF and real JWT forms; SQL/Java parity and effective pagination; per-unit quantity
rounding; full-discount shipping; competing-buyer voucher quota; atomic rollback/retry and
historical snapshots/replay/cancellation. Existing regressions also pass.

`git diff --check` passes. Every untracked file also passes a separate
`git diff --no-index --check -- NUL <path>` inspection. All changed production files are
valid UTF-8 with no NUL bytes. Staged diff is empty. The disposable extension probe container
was removed; no shared database or credentials were changed.

Evidence retained under ignored `target/`: `promo02-targeted-verification.json`,
`promo02-targeted-full.log`, `promo02-targeted-focused.log`,
`surefire-reports/` and `failsafe-reports/`.

## Remaining issues and deployment boundary

M1 and L1 are addressed. Low finding L2 remains outside this targeted fix: pricing-query cost
has not been measured, and the availability-key query still includes the pricing join.
The earlier audit's additional coverage gaps remain, including rendered JWT edit/disable,
additional effective-price filter/boundary cases and populated-database upgrade/preinstalled
extension/permission-denial variants. Passing tests are not claimed to cover these scenarios.
Deployment must provision equivalent database CREATE and schema DDL/extension
permissions, or preinstall accessible btree_gist. The migration deliberately fails when
these prerequisites are absent. No live shared-database permissions were claimed or changed.

## Complete changed production inventory

M = modified tracked file. U = new untracked file. All 30 production files are listed.

| Status | Path |
| --- | --- |
| M | `src/main/java/com/uteexpress/cart/dto/CartItemView.java` |
| M | `src/main/java/com/uteexpress/cart/service/CartService.java` |
| U | `src/main/java/com/uteexpress/catalog/controller/VendorProductPromotionController.java` |
| M | `src/main/java/com/uteexpress/catalog/dto/CartProductSnapshot.java` |
| M | `src/main/java/com/uteexpress/catalog/dto/ProductCard.java` |
| M | `src/main/java/com/uteexpress/catalog/dto/ProductDetailView.java` |
| M | `src/main/java/com/uteexpress/catalog/dto/ProductSnapshot.java` |
| M | `src/main/java/com/uteexpress/catalog/repository/CatalogReadRepository.java` |
| U | `src/main/java/com/uteexpress/catalog/service/ProductPromotionManagementService.java` |
| M | `src/main/java/com/uteexpress/catalog/service/PublicCatalogService.java` |
| M | `src/main/java/com/uteexpress/checkout/service/CheckoutQuoteService.java` |
| M | `src/main/java/com/uteexpress/engagement/service/EngagementService.java` |
| U | `src/main/java/com/uteexpress/promotion/dto/ProductPrice.java` |
| U | `src/main/java/com/uteexpress/promotion/dto/PromotionDisableForm.java` |
| U | `src/main/java/com/uteexpress/promotion/dto/VendorPromotionForm.java` |
| U | `src/main/java/com/uteexpress/promotion/dto/VendorPromotionUpdateForm.java` |
| U | `src/main/java/com/uteexpress/promotion/dto/VendorPromotionView.java` |
| U | `src/main/java/com/uteexpress/promotion/entity/Promotion.java` |
| U | `src/main/java/com/uteexpress/promotion/repository/PromotionRepository.java` |
| U | `src/main/java/com/uteexpress/promotion/service/PromotionManagementService.java` |
| U | `src/main/java/com/uteexpress/promotion/service/PromotionPricingService.java` |
| U | `src/main/resources/db/migration/V20261008090000__td_promo02_product_promotions.sql` |
| M | `src/main/resources/templates/cart/view.html` |
| M | `src/main/resources/templates/checkout/quote.html` |
| M | `src/main/resources/templates/fragments/catalog.html` |
| M | `src/main/resources/templates/products/detail.html` |
| M | `src/main/resources/templates/vendor/products/list.html` |
| U | `src/main/resources/templates/vendor/promotions/error.html` |
| U | `src/main/resources/templates/vendor/promotions/form.html` |
| U | `src/main/resources/templates/vendor/promotions/list.html` |

## Test inventory

The 19 existing datasource-free MVC fixtures add only mocks of the two new persistence
services. PublicCatalogServiceTest additionally verifies the shared pricing instant.
The two new Promotion suites contain the feature tests.

| Status | Path |
| --- | --- |
| M | `src/test/java/com/uteexpress/FoundationHttpTest.java` |
| M | `src/test/java/com/uteexpress/account/controller/AddressControllerTest.java` |
| M | `src/test/java/com/uteexpress/account/controller/ProfileControllerTest.java` |
| M | `src/test/java/com/uteexpress/catalog/controller/PublicCatalogControllerTest.java` |
| M | `src/test/java/com/uteexpress/catalog/controller/VendorProductControllerTest.java` |
| M | `src/test/java/com/uteexpress/catalog/service/PublicCatalogServiceTest.java` |
| M | `src/test/java/com/uteexpress/governance/AccountGovernanceWebTest.java` |
| M | `src/test/java/com/uteexpress/governance/CategoryWebTest.java` |
| M | `src/test/java/com/uteexpress/governance/ModerationWebTest.java` |
| M | `src/test/java/com/uteexpress/governance/OpsDashboardTest.java` |
| M | `src/test/java/com/uteexpress/governance/ShopApprovalWebTest.java` |
| M | `src/test/java/com/uteexpress/identity/controller/EmailVerificationControllerTest.java` |
| M | `src/test/java/com/uteexpress/identity/controller/PasswordResetControllerTest.java` |
| M | `src/test/java/com/uteexpress/identity/controller/RegistrationControllerTest.java` |
| U | `src/test/java/com/uteexpress/promotion/ProductPromotionIT.java` |
| U | `src/test/java/com/uteexpress/promotion/PromotionTest.java` |
| M | `src/test/java/com/uteexpress/security/AuthenticationWebTest.java` |
| M | `src/test/java/com/uteexpress/security/CsrfCookieWebTest.java` |
| M | `src/test/java/com/uteexpress/security/SecurityFoundationTest.java` |
| M | `src/test/java/com/uteexpress/shipping/ShippingConfigWebTest.java` |
| M | `src/test/java/com/uteexpress/shop/controller/ShopRegistrationControllerTest.java` |
| M | `src/test/java/com/uteexpress/ui/UiLayoutTest.java` |

## Documentation inventory

| Status | Path |
| --- | --- |
| M | `docs/DATABASE_SCHEMA.md` |
| U | `docs/PROMO-02-product-promotions.md` |
| U | `docs/PROMO-02-verification.md` |
| M | `docs/order-contracts.md` |
