# UI conventions

UI-01 provides the shared Thymeleaf and Bootstrap foundation. Feature owners add
their content inside these contracts instead of duplicating navigation, footer,
spacing, forms or status styling.

## Template structure

```text
templates/
├── fragments/
│   ├── alerts.html
│   ├── breadcrumbs.html
│   ├── components.html
│   ├── footer.html
│   ├── head.html
│   └── navbar.html
├── layout/
│   ├── base.html
│   └── dashboard.html
├── error/
│   ├── 403.html
│   ├── 404.html
│   └── 500.html
└── index.html
```

Public pages compose the standard layout with native Thymeleaf fragments:

```html
<html lang="vi" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>Page title | UTEExpress</title></head>
<body><main><!-- page content --></main></body>
</html>
```

`base.html` owns the skip link, shared head, navbar, alert region, page container,
footer, Bootstrap bundle and local JavaScript. `layout/dashboard :: dashboard`
is a structural sidebar/content shell for Vendor, Manager, Admin and Shipper;
it contains no role policy or business metrics.

## Bootstrap and CSS

Bootstrap 5.3.3 is pinned through jsDelivr with integrity metadata. No Node/npm
pipeline is required. `static/css/app.css` supplies design tokens and UTEExpress
components. Use Bootstrap layout utilities together with these project classes:

- Buttons: `btn btn-app-primary`, `btn-app-secondary`, `btn-app-ghost`.
- Page heading: `fragments/components :: pageHeader(title, description)`.
- Responsive table: `fragments/components :: tableContainer(content)` or
  `app-table-shell` containing `.table-responsive` and a Bootstrap `.table`.
- Table actions: `app-table-actions`.
- Empty result: `fragments/components :: emptyState(title, description)`.
- Status: `app-status-badge` plus one of `status-new`, `status-confirmed`,
  `status-shipping`, `status-delivered`, `status-cancelled`, `status-warning`.

Status classes are visual contracts only. They do not define Java enums or
business transitions; those remain owned by their modules.

## Forms and validation

Use a semantic `<form>` with an explicit `<label for>` for every control. Mark a
required label with `<span class="app-required" aria-hidden="true">*</span>` and
also set the input's `required`/`aria-required` state as appropriate. Use
Bootstrap `.form-control`, `.form-select`, `.form-text`, `.is-invalid` and
`.invalid-feedback`. Disabled controls must use the native `disabled` attribute.

Submit real forms with `th:action` so Spring Security's CSRF integration can add
the token. UI-01 does not provide a business POST form and does not disable CSRF.

## Alerts, escaping and JavaScript

The alert fragment accepts `successMessage`, `infoMessage`, `warningMessage` and
`errorMessage`. It renders each value with `th:text`. User-controlled content
must never use `th:utext`. JavaScript must assign user-visible text with
`textContent`, not `innerHTML`.

Server validation should return specific field messages through BindingResult;
do not display exception class names, SQL, stacktraces or rejected secrets.

## Responsive and accessibility rules

The shared layout supports a collapsed navbar and a single-column dashboard below
the Bootstrap `lg` breakpoint (992px). The dashboard's workspace menu uses the
existing Bootstrap collapse component: its native button controls
`workspaceNavigation`; desktop navigation remains visible. Do not duplicate this
fragment within one page, because its collapse target ID is page-scoped.

Wrap wide tables in `.table-responsive`; do not hide columns or clip the entire
page to conceal overflow. These regions contain positioned accessible labels,
keep a readable 40rem table minimum and receive a keyboard focus target and name
from `app.js` (a caption when present, otherwise the shared fallback). Existing
ARIA names and keyboard policies are preserved. Test ArrowLeft/ArrowRight while
the region has focus, and keep the focus indicator visible. Use the established
`app-table-shell` style when the page needs its border/background treatment.

Long names/references wrap within the content. Large `g-5` gutters are reduced
below `lg` so nested grids do not exceed the page container. Inline search forms
allow the input to shrink while retaining a readable submit button. Navbar buyer
links reuse the existing `buyerFeaturesAvailable` view capability; link visibility
never replaces route or service authorization.

UI-02's exact acceptance viewports are **375×812**, **768×1024** and **1440×900**
CSS pixels; full-page screenshot height can exceed viewport height. Route-level
results, source/build provenance and independent-review limitations are in
[the UI-02 report](testing/UI-02-responsive-report.md). A screenshot at one width
does not establish the other widths or another actor's access.

Keep heading order meaningful, use landmarks, native links/buttons and descriptive
labels, and retain the global focus indicator. Images need useful `alt` text unless
they are decorative. Do not make a `<div>` clickable.

## Security boundary

Authenticated navigation uses role groups rather than a flat row of every link.
Keep shared operations entries in `fragments/workspace-links.html` so dashboard and
navbar stay consistent. Use real HTML GET destinations, with record-level actions
inside their existing detail pages and protected forms. See
[the navigation map](ui-navigation-map.md) for the current inventory and limits.

Only `/css/**`, `/js/**`, `/images/**` and `/favicon.ico` were added to the public
security paths. The application still defaults all other unmatched routes to
authenticated access. Existing role routes, JSON 401/403 handlers and CSRF policy
remain unchanged. Authentication pages and role-aware navigation belong to AUTH.
