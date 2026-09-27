# ADMIN-03 — Shop approval

ADMIN users can review pending Shop applications at `/admin/shops`, inspect one
application, and approve or reject it. MANAGER and other roles cannot access
these routes or the underlying approval service. Mutations use POST, CSRF, and
the submitted Shop version; a stale or already-decided application is rejected.

Approval changes a PENDING Shop to APPROVED, assigns VENDOR to its active owner,
increments that account's token version so old JWTs stop working, and appends a
safe audit record. All four writes participate in one transaction. The owner
must sign in again to use VENDOR features. USER remains assigned. Rejection
stores a trimmed, nonblank reason (maximum 1000 characters), changes the Shop
to REJECTED, and appends an audit record; it never grants VENDOR. The free-text
reason is not copied into the audit summary. No new migration is required by
ADMIN-03 because VENDOR-01 already created the Shop status and reason columns.

The list is paged by creation time. Shop-controlled text is rendered with
Thymeleaf escaping. ADMIN-03 does not implement rejected-application resubmission,
product moderation, or general role management; those belong to later tasks.

Verification: `mvnw.cmd clean package` passes 194 unit, MVC, and architecture
tests on Java 21. PostgreSQL Testcontainers tests cover role/token/audit atomicity,
rejection, stale versions, inactive owners, and service authorization. The local
Docker Desktop engine must be running to execute `mvnw.cmd -Ppostgres-it verify`.
