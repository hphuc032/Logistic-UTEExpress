# ADMIN-04 — Category management

Owner: Quốc Đạt. Reviewer: Hoàng Phúc.
Branch: `feature/admin-04-categories`.
Base: AUTH-02 `08382f7`, which includes develop `1d93bf6`.

## Behavior

- Admin and Manager manage one-level categories on their respective
  `/admin/categories` and `/manager/categories` routes.
- Paginated list (20/page), validated create/edit, unique lowercase ASCII slug,
  enable/disable, and a dashboard navigation link.
- No hard-delete endpoint or repository operation. Disabling only changes
  visibility; existing IDs and future product references remain intact.
- `@Version` and a required expected version for changes reject stale updates.
- Service authorization uses RoleCode. Mutations resolve the persisted actor ID
  through AUTH-02's CurrentAccountIdProvider; no actor ID is accepted from forms.
- Audit and business changes share a transaction. Failure to write audit rolls
  back the category. Audit snapshots contain safe active/version values only.
- New Flyway migration creates categories; existing migrations are unchanged.

## Validation (2026-09-22)

- Java 21: `mvnw.cmd -B -ntp clean test`: PASS, 123 tests.
- `mvnw.cmd -B -ntp -Ppostgres-it verify`: PASS, 123 unit/MVC/architecture
  tests plus 24 PostgreSQL 17.6 integration tests; includes package lifecycle.
- New coverage: 6 MVC tests, 8 PostgreSQL integration tests (role matrix, CSRF,
  XSS escaping, invalid/duplicate data, stale versions, trusted actors, audit
  rollback, pagination, clean/repeated migrations and database constraints).
- Rendered real Thymeleaf responses inspected in browser at desktop and 375px:
  list and edit form fit without page-level horizontal overflow.
  These visual previews use MVC fixtures, not a production authenticated session.
- Existing HTTP tests add only a CategoryService mock because their profile
  intentionally excludes persistence; their assertions are unchanged.

## Merge and integration notes

Merge AUTH-02 PR #10 into develop first, then review/merge this task into develop.
This task uses that existing public service contract without changing security
configuration, login behavior, identity entities, or the AUTH-02 branch.

The Product module is not implemented on this base. Product creation must check
that a category is active through an agreed read service contract when PROD-00 /
VENDOR-02 is integrated; do not use the ops-only CategoryService from vendor code,
and do not directly import governance repositories/entities across modules.
There is no claim of a completed Product/category integration in this task.

Suggested commit: `feat(governance): implement ADMIN-04 category management`.
