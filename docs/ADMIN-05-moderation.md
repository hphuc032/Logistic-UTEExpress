# ADMIN-05 — Product and shop moderation

Owner: Quốc Đạt. Reviewer: Hoàng Phúc. Base: develop after VENDOR-02 (#22).

Admin and Manager can search products and shops by name at
`/{admin|manager}/moderation/products` and `/{admin|manager}/moderation/shops`.
All POST commands require CSRF, the current version, and a trimmed nonblank reason
of at most 1000 characters. Actor identity comes from CurrentAccountIdProvider;
the browser supplies no owner, actor, role, or arbitrary status.

Transitions:

| Target | Restrict | Release |
| --- | --- | --- |
| Product | ACTIVE → MODERATED | MODERATED → ACTIVE |
| Shop | APPROVED → SUSPENDED | SUSPENDED → APPROVED |

Vendor soft hiding remains HIDDEN. Ops cannot restore HIDDEN products, approve
PENDING applications, or resume REJECTED shops through these commands. Vendor
editing never changes a MODERATED product back to ACTIVE. If Vendor also hides
that product, it becomes HIDDEN and cannot be restored by Ops.

Existing CatalogQueryService and InventoryService filter to ACTIVE products in
APPROVED shops. VendorShopQueryService also requires APPROVED, so suspension
blocks new products and other existing vendor operations on that shop. Suspension
does not remove account roles or increment token_version.

Governance defines DTO/service contracts; Catalog and Shop own their implementation
and persistence. Each mutation locks the target row, checks its version and current
status, stores the latest reason, flushes the state, and appends audit in the same
transaction. Audit errors propagate and roll back the mutation. Audit uses safe
action/status codes rather than copying untrusted free text into audit summaries.
The latest human-readable reason is stored on the target; audit retains the
sequence of actions, actor IDs, versions and statuses.

The forward migration V20260928090000 adds moderation_reason and expands the
existing status constraints. Existing migration files are unchanged. Apply it
before running the new application; coordinate with HP before adding later
Catalog migrations or status transitions.

Verification covers both Ops roles, denial of USER/VENDOR/SHIPPER at service and
HTTP boundaries, CSRF, stale versions, reason validation, Vendor-hidden separation,
Catalog availability, suspended Vendor creation, unchanged owner roles/tokens,
and rollback when audit persistence fails.

Executed on Java 21, 2026-09-28:

- `./mvnw.cmd clean test`: PASS, 265 tests.
- `./mvnw.cmd package`: PASS.
- `./mvnw.cmd -Ppostgres-it verify`: PASS, 265 regular tests + 106 integration tests.
- ModerationWebTest: 2 PASS; ModerationIT: 6 PASS; ShopApprovalIT: 7 PASS.
- `git diff --check`: PASS.

No integration test was skipped for Docker availability. CI and HP review remain
required on the pushed PR head.
