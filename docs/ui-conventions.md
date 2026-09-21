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

The shared layout supports a collapsed navbar below the Bootstrap `lg` breakpoint,
a single-column dashboard below 768px, scrollable tables and full-width primary
actions on narrow phones. Feature pages must be checked at approximately 375px,
768px, 1366px and 1440px or wider.

Keep heading order meaningful, use landmarks, native links/buttons and descriptive
labels, and retain the global focus indicator. Images need useful `alt` text unless
they are decorative. Do not make a `<div>` clickable.

## Security boundary

Only `/css/**`, `/js/**`, `/images/**` and `/favicon.ico` were added to the public
security paths. The application still defaults all other unmatched routes to
authenticated access. Existing role routes, JSON 401/403 handlers and CSRF policy
remain unchanged. Authentication pages and role-aware navigation belong to AUTH.
