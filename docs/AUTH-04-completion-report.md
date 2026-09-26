# AUTH-04 — Forgot and reset password by email OTP

## Scope and architecture

AUTH-04 adds the public `GET`/`POST /forgot-password` and `GET`/`POST /reset-password`
flows. MVC controllers bind dedicated form DTOs and call `PasswordResetService`; they do
not access repositories or entities. The service reuses the AUTH-03 OTP generator, HMAC
hashing, configuration, persistence, attempt limits, cooldown, and mail infrastructure.

The task does not add profile, address, refresh-token, MFA, OAuth, Redis, password
history, CAPTCHA, or a second OTP table.

The branch was recovered from base `e3de894` and synchronized with `develop` at
`90a26e2` through merge commit `c42171d`. CART-01 source, tests, test-context mocks, and
`V20260925022804__td_cart01_foundation.sql` remain present.

## Persistence and OTP isolation

Migration `V20260926035526__hp_auth04_password_reset.sql` replaces only the existing
purpose check constraint so `otp_tokens.purpose` accepts `EMAIL_VERIFICATION` and
`RESET_PASSWORD`. It does not recreate the table or modify the merged AUTH-03 migration.

Reset lookup uses the latest row for `user_id + RESET_PASSWORD` under a pessimistic
write lock. The HMAC input also includes `RESET_PASSWORD`, so tokens cannot cross between
email activation and password recovery. Codes are never stored or logged in plaintext.

## Security behavior

- Forgot-password requests use one redirect and one generic message for known, unknown,
  ineligible, and mail-failure cases. Only `ACTIVE` accounts can receive a reset OTP.
- Reset requires the existing OTP TTL, resend cooldown, and maximum-attempt policy.
- Password validation matches registration: 8–64 characters and at most 72 UTF-8 bytes
  before BCrypt. Raw passwords are neither logged nor persisted.
- Successful reset atomically updates the BCrypt hash, consumes the OTP, and increments
  `token_version`. Existing JWTs consequently fail database token-version validation.
- Both POST routes remain protected by CSRF. No wildcard route was made public and JWT
  validation was unchanged.
- SMTP delivery follows OTP commit. A delivery failure leaves the token persisted; the
  public response remains generic and cooldown still applies.

## UI and endpoints

The Thymeleaf pages `auth/forgot-password` and `auth/reset-password` reuse the shared
layout, Bootstrap form conventions, server-side validation, and Spring Security CSRF
fields. The login page links to recovery and shows a generic success message after a
completed reset.

## Verification

Unit and MVC tests cover form validation, service-side password policy, eligibility,
generic responses, CSRF, cooldown, expiry, failed attempts, latest-token behavior,
purpose isolation, mail composition, mass-assignment resistance, and secret non-echo.

PostgreSQL 17.6 Testcontainers tests cover Flyway and Hibernate validation, both allowed
purposes plus invalid-purpose rejection, BCrypt replacement, old/new password login,
JWT invalidation through `token_version`, consumption and reuse, expiry, attempts,
resend/latest semantics, cross-purpose rejection, and concurrent one-time use.

Final local verification on Docker Desktop 29.4.3:

- `.\mvnw.cmd clean test`: PASS (216 tests, 0 failures, 0 errors)
- `.\mvnw.cmd package`: PASS (216 tests and executable JAR)
- `.\mvnw.cmd -Ppostgres-it clean verify`: PASS (216 unit/MVC tests plus 71
  PostgreSQL integration tests; 287 total, 0 failures, 0 errors)
- `git diff --check`: PASS

The PostgreSQL gate applies CART-01 before AUTH-04 on a fresh database, validates the
Hibernate schema and Flyway history, accepts both OTP purposes, rejects unsupported
purposes, and confirms a second Flyway migrate has no pending work.

## Deferred

SEC-02 owns broader abuse controls such as IP/account rate limiting. MFA, OAuth, refresh
tokens, Redis, password history, and CAPTCHA remain outside AUTH-04.
