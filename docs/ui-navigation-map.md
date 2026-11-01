# Role-aware navigation

Base: `origin/develop` at `b823359`. Work branch: `codex/ui-navigation-links`.
Tested source is that base plus this branch's navigation changes, not the base
commit alone. Test execution preceded the commit; no exact-head CI result is claimed.
This change connects existing HTML screens. It does not add routes, permissions,
business actions, database changes, or PAY-03 behavior.

## Entry points

| Actor/menu | Existing destinations |
| --- | --- |
| Public | `/`, `/products`, `/shops`, `/login`, `/register`; login also links to `/verify-otp` and `/forgot-password` |
| USER or VENDOR / Mua sắm | `/user/cart/view`, `/user/checkout/view`, `/orders`, `/user/addresses`, `/user/favorites`, `/user/recently-viewed` |
| USER or VENDOR / account | `/user/profile`, `/user/shop`; non-vendor buyers also get `/user/shop/register` |
| VENDOR / Kênh người bán | `/vendor/dashboard`, `/user/shop`, `/vendor/products`, `/vendor/products/new`, `/vendor/orders`, `/vendor/vouchers`, `/vendor/vouchers/new` |
| MANAGER / Vận hành | `/manager/dashboard`, `/manager/orders`, `/manager/shippers`, `/manager/shipping/providers`, `/manager/shipping/rates`, `/manager/commissions`, `/manager/categories`, `/manager/accounts`, `/manager/products`, `/manager/shops/moderation` |
| ADMIN / Quản trị | Same operations screens under `/admin`, plus `/admin/shops`, `/admin/vouchers`, `/admin/promotions`, `/admin/dashboard/revenue`, `/admin/audit-logs` |
| SHIPPER / Giao hàng | `/shipper/dashboard`, `/shipper/shipments/view` |
| All authenticated users | `/notifications`; logout remains a CSRF-protected POST form |

Buyer menu visibility continues to use `buyerFeaturesAvailable`. Other workspace
flags are presentation projections from the existing authenticated principal.
Multi-role accounts see every matching workspace; no role implies another role.
Server authorization, ownership and CSRF remain authoritative and unchanged.
Manager commission management remains read-only under the existing service policy.

## Context-dependent screens/actions

- Catalog lists link to product/shop/category detail using actual identifiers.
- Cart links to checkout; checkout still requires a saved selection, owned address,
  shipping configuration and an effective commission policy for placement.
- Buyer orders lead to details, payment information, eligible returns and reviews.
- Vendor product rows already link to edit/images/product promotions. Order details
  retain confirm, ready, cancellation and return actions under existing conditions.
- Operations orders lead to detail, assignment/reassignment and existing lifecycle
  actions. Shipper lists lead to assigned shipment details and eligible actions.
- Configuration lists retain their create/edit links; approved shop applications now
  link back to product management, sales orders and the public shop.
- API-only endpoints, image/avatar content, metrics, WebSocket, provider callbacks,
  and mutation endpoints are not added as generic navigation links. They remain
  consumed by the existing forms/scripts and server-controlled workflows.

`fragments/workspace-links.html` is shared between the navbar and operations
dashboard sidebar. Bootstrap's existing dropdown implementation supplies menu
interaction; mobile menus stay in normal document flow with bounded scrolling.
No new JavaScript or dependency is introduced.

## Verification

Results are recorded after execution below. Browser interaction/responsive checks
are not equivalent to server-side rendering tests. At implementation time computer
use reports: `Local browser use is disabled in Work settings`.

- Focused MVC tests: 28 PASS (14 UI + 14 operations dashboard), zero failures/errors.
- Node regression: 48 PASS, zero failures/skips.
- New UI assertions cover all five individual roles, multi-role visibility, escaped
  usernames, anonymous visibility, GET handler existence for rendered navbar links,
  role denial and logout CSRF enforcement.
- Java 21 `mvnw.cmd clean test`: 719 PASS across 77 Surefire XML reports; zero
  failures, errors or skips (includes 14 UI tests and 7 architecture tests).
- `mvnw.cmd -DskipTests package`: PASS after the complete clean test run. This
  packaging invocation does not claim a second test execution.
- Local PostgreSQL application boot: PASS. HTTP 200 for home, login, app CSS and
  health; login includes verification link and CSS includes the role menu styles.
  Existing local database and encrypted local launcher secrets were reused.
- `git diff --check`: PASS. No security policy, DTO/API request contract, migration,
  production business service, QA-02 evidence or assertions changed. Navbar DTO and
  advice changes only project existing authorities into presentation flags.
- PostgreSQL/Testcontainers suite: NOT RUN for this presentation-only change. Local
  application startup is not claimed as full PostgreSQL integration regression.
- Browser dropdown/keyboard and 375/768/1440 viewport interaction: NOT RUN; browser
  control disabled. Needs human review before claiming browser acceptance.

## Deployment boundary

HP authorized committing and pushing this navigation branch after the local handoff.
Merge and deployment remain pending separate authorization.
Remote `deploy/render-demo` contains PAY-03 at `54a5df7`; that branch
was inspected, not reset or merged into this navigation change. Deployment must
reconcile both branches explicitly and verify PAY-03 separately. No QA-02/QA-03 or
release acceptance status is changed by this work.
