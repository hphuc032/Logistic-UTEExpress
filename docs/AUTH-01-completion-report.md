# AUTH-01 registration and identity persistence

AUTH-01 adds the persistent identity model and public registration flow. It does not add
login, JWT, email delivery, OTP verification, password recovery, or role-management UI.

## Persistence

Migration `V20260921070156__hp_auth01_identity.sql` creates:

- `uteexpress.users`, with normalized unique email/username, BCrypt hash, account status,
  email verification timestamp, token version, UTC audit timestamps, and optimistic version;
- `uteexpress.roles`, seeded with `USER`, `VENDOR`, `MANAGER`, `ADMIN`, and `SHIPPER`;
- `uteexpress.user_roles`, with a composite primary key and named foreign keys.

Flyway remains the schema writer and Hibernate remains on `ddl-auto=validate`. No existing
migration was changed and no account or credential is seeded.

## Registration contract

`GET /register` renders the shared Thymeleaf layout. `POST /register` remains protected by
CSRF and uses an allowlisted MVC form containing only email, username, password, and password
confirmation. Email and username are trimmed and normalized with `Locale.ROOT`; password is
never normalized, logged, returned, or persisted in raw form. Validation rejects passwords
outside 8–64 characters or above BCrypt's 72-byte UTF-8 input limit.

`RegistrationService` runs in a transaction, checks normalized duplicates, loads the seeded
`USER` role, applies BCrypt, and flushes the new user. A database integrity race becomes the
same generic registration conflict shown for a pre-check duplicate. Successful public
registration always produces:

- status `PENDING_VERIFICATION`;
- `email_verified_at = NULL`;
- `token_version = 0`;
- exactly the `USER` role.

The success page says that email verification is still required and does not claim an OTP
was sent.

## Verification

- `mvnw.cmd clean test`: PASS (64 tests at implementation checkpoint).
- `mvnw.cmd -Ppostgres-it verify`: PASS with PostgreSQL 17.6 on Docker Desktop.
- DB-01 baseline integration: PASS (1 test).
- AUTH-01 registration integration: PASS (3 tests).

`AuthRegistrationIT` verifies Flyway, Hibernate schema validation, all three
tables, exact role seed compatibility with `RoleCode`, BCrypt persistence, initial account
state, normalized duplicates, the database unique constraint, and a second no-op migration.

## Deferred ownership

- AUTH-02: login, `UserDetailsService`, JWT issue/verification, cookie, logout, token-version checks.
- AUTH-03: OTP persistence/delivery/verification and account activation.
- AUTH-04: forgot/reset password.
- QA-00: any change to the GitHub Actions PostgreSQL job.
