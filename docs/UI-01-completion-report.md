# UI-01 completion report

Owner: Hoàng Phúc

Branch: `feature/ui-01-shared-layout`

Base: `develop` (`db694d0` when the branch was created)

## Delivered

- Added a reusable Thymeleaf base layout with shared head, navbar, flash alerts,
  page container, footer and JavaScript loading.
- Added native Thymeleaf fragments for breadcrumbs, page headings, validation
  feedback, responsive table containers, empty states and status badges.
- Added a generic dashboard sidebar/content shell without feature data or role logic.
- Added the first public landing page at `GET /` using static explanatory content.
- Added safe 403, 404 and 500 HTML templates without dynamic internal diagnostics.
- Added the UTEExpress design tokens and responsive component conventions in
  `static/css/app.css`; added minimal safe behavior in `static/js/app.js`.
- Pinned Bootstrap 5.3.3 CSS and bundle JavaScript through jsDelivr with SRI.
- Permitted only `/css/**`, `/js/**`, `/images/**` and `/favicon.ico` as new
  anonymous security paths. CSRF, role paths and JSON security handlers are unchanged.

No Maven dependency, database entity, migration or business module was changed.

## Security, accessibility and output safety

- The landing page and static assets are available anonymously; unmatched routes
  still require authentication.
- CSRF remains enabled and there is no demo POST form.
- Dynamic fragment values use `th:text`; no `th:utext` is present. Local JavaScript
  uses `textContent` and never `innerHTML`.
- The shell includes semantic landmarks, a keyboard skip link, visible focus states,
  native controls, accessible navigation labels and reduced-motion handling.
- Test-only rendering verifies that an HTML-like flash message is escaped and the
  500 template contains no stacktrace, Java exception class or SQL exception detail.

## Verification

| Check | Result |
| --- | --- |
| `mvnw.cmd clean test` | PASS — 39 tests |
| `mvnw.cmd package` | PASS — executable JAR created |
| UI-01 tests | PASS — 6 tests |
| SEC-01 tests | PASS — 13 tests |
| ARCH-01 HTTP tests | PASS — 14 tests |
| ArchUnit rules | PASS — 6 tests |
| Browser preview at 375px | PASS — collapsed navbar, no horizontal overflow |
| Browser preview at 768px | PASS — stacked hero, no horizontal overflow |
| Browser preview at 1366px | PASS — desktop navigation and two-column hero |
| Browser preview at 1440px | PASS — hero, cards, CTA and footer render correctly |
| Browser console | PASS — no errors or warnings |

The browser preview ran with the same persistence auto-configuration exclusions as
`application-test.yml`. Production-style manual boot was not run because DB credentials,
a reachable local PostgreSQL instance and a running Docker daemon were unavailable.
This result is not PostgreSQL integration evidence; DB-01 remains unchanged.

## Scope boundary

UI-01 does not implement registration, login/JWT, OTP, profiles, products, carts,
checkout, orders, role dashboards, shipping, payment, vouchers, reviews or WebSocket.
The Products, Shops, Login and Register links express the shared navigation convention;
their controllers and business flows remain with their owning tasks.

The current MVC exception advice and Security Filter Chain continue to return the
existing JSON contracts. A future content-negotiation design can connect the HTML error
templates to browser-specific error handling without changing SEC-01 behavior here.
