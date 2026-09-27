# USER-01 — Profile, avatar, and authenticated password change

## Scope

USER-01 adds authenticated profile management for accounts with `USER` or `VENDOR`
authority. The account can view its own username/email, update `full_name` and `phone`,
replace its avatar, and change its password after proving the current password. Email,
username, roles, status, addresses, and other media remain outside this task.

## Persistence and module boundary

Migration `V20260926085003__hp_user01_profile.sql` adds nullable bounded columns to
`uteexpress.users`: `full_name VARCHAR(120)`, `phone VARCHAR(32)`, and
`avatar_key VARCHAR(512)`. Nullable columns preserve existing accounts. Hibernate keeps
`ddl-auto=validate` and the existing optimistic `version` mapping.

The runtime dependency direction is:

`account.controller.ProfileController` → `account.service.ProfileService` →
`identity.service.AccountIdentityService` → identity repository/entity.

The account module consumes only the identity service and DTO contracts. It never imports
identity repositories or entities. Every operation obtains ownership from
`CurrentAccountIdProvider`; no route or form accepts a user/account ID.

## Profile and password security

- Profile binding allowlists only `fullName` and `phone`; username, email, role, status,
  token version, avatar key, and user ID remain server owned.
- Names are trimmed, bounded, and reject control characters. Phone values are optional,
  bounded, and accept a conservative international-friendly character set.
- Password change obtains a pessimistic user lock, checks the current password with the
  existing `PasswordEncoder`, and reuses AUTH-04 `PasswordPolicy` (8–64 characters and
  at most 72 UTF-8 bytes for BCrypt).
- A successful password change replaces only the BCrypt hash and increments
  `token_version` exactly once in the same transaction. The current JWT and all older JWTs
  then fail AUTH-02 token-version validation. Profile and avatar changes do not alter
  `token_version`.
- All three POST routes remain CSRF protected. Thymeleaf output is escaped and password
  fields are never prefilled.

## Safe avatar storage

`common.storage.FileStorageService` is a generic local image-storage primitive. Its root is
configured by `uteexpress.storage.root`, sourced from `UPLOAD_DIR` with local default
`./var/uploads`; the directory is outside the classpath and ignored by Git.

Avatar rules:

- maximum 2 MiB and dimensions from 1×1 through 4096×4096;
- JPEG, PNG, or WebP only;
- client extension, declared MIME, magic signature, ImageIO format, dimensions, and a full
  decode must agree;
- WebP decoding uses TwelveMonkeys ImageIO rather than trusting MIME or signature alone;
- generated keys use `avatars/<uuid>.<ext>` and never reuse a client filename;
- normalized paths, UUID key grammar, real-path containment, and symlink checks confine
  reads/writes/deletes to the configured root;
- uploaded content is served only through authenticated `GET /user/avatar` with a fixed
  image content type and `X-Content-Type-Options: nosniff`.

The service stores and validates a new file before updating the DB key. A failed DB update
deletes the new file. After a committed key replacement, the prior file is deleted best
effort; concurrent replacements serialize on the user row and the final DB key references
a valid file.

## Endpoints

- `GET /user/profile`
- `POST /user/profile`
- `POST /user/avatar`
- `GET /user/avatar`
- `POST /user/password`

## Verification

Verified locally with Java 21, Maven Wrapper 3.9.11, Docker Desktop 29.4.3, and
Testcontainers PostgreSQL 17.6:

- `./mvnw.cmd clean test`: PASS — 232 tests, 0 failures, 0 errors.
- `./mvnw.cmd package`: PASS — executable Spring Boot JAR created after the same 232
  tests passed.
- `./mvnw.cmd -Ppostgres-it clean verify`: PASS — 232 unit/MVC tests plus 77 PostgreSQL
  integration tests (309 total), 0 failures, 0 errors.
- PostgreSQL verification covers fresh migration, upgrade from AUTH-04 with an existing
  user, Hibernate validation, Flyway no-op rerun, real JWT ownership, mass assignment,
  avatar persistence/isolation, current-password proof, BCrypt replacement, exact
  token-version increment, old-password rejection, new-password login, and stale JWT
  rejection.
- Storage tests cover valid JPEG/PNG/WebP, spoofed metadata, truncated images, SVG,
  oversized files, extreme dimensions, generated UUID keys, read/delete, and traversal.
- ArchitectureTest remains unchanged and passes.
- `git diff --check`: PASS.

## Deferred

USER-02 owns addresses. Email change requires a separate re-verification flow. Username
rename, product/review media, account deletion, role management, image resizing/cropping,
cloud object storage, and CDN delivery are not implemented by USER-01.
